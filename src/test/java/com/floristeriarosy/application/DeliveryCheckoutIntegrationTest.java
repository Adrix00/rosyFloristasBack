package com.floristeriarosy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.application.order.command.CheckoutCommand;
import com.floristeriarosy.application.order.dto.CheckoutRedirectDto;
import com.floristeriarosy.application.order.port.in.CheckoutUseCase;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.support.PendingCheckoutPayloadReader;
import com.floristeriarosy.application.payment.port.in.ReceiveRedsysNotificationUseCase;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.order.DeliveryAddressNotGeocodableException;
import com.floristeriarosy.domain.exception.order.DeliveryOutOfRangeException;
import com.floristeriarosy.domain.exception.order.GeocodingUnavailableException;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysBase64;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysOrderId;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysProperties;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysSignature;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Delivery checkout end to end on a real PostgreSQL through the real services (order.md §3.7,
 * ADR-017). Only Google Maps is faked: a local server answers in the shape of the real API, so the
 * default build needs no key. Covers what the mock-based {@code CheckoutServiceTest} cannot: the
 * tariff read from {@code shipping_rates}, the {@code free_from_amount} boundary, an order
 * materialized with encrypted coordinates, and that a rejected address reserves nothing.
 *
 * <p>The shop sits at (40.0, -3.0) in this test; addresses are placed at known distances from it
 * with the same great-circle formula the client uses.
 */
@Testcontainers
@SpringBootTest
class DeliveryCheckoutIntegrationTest {

  private static final BigDecimal SHOP_LATITUDE = new BigDecimal("40.0");
  private static final double KM_PER_DEGREE_OF_LATITUDE = 111.19492664;
  private static volatile CountDownLatch googleReached = new CountDownLatch(1);
  private static volatile CountDownLatch googleMayAnswer = new CountDownLatch(1);
  private static final HttpServer GOOGLE = startFakeGoogle();

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("app.geocoding.google-maps.api-key", () -> "test-key");
    registry.add(
        "app.geocoding.google-maps.geocode-url",
        () -> "http://localhost:" + GOOGLE.getAddress().getPort() + "/geocode");
    registry.add("app.geocoding.store.latitude", SHOP_LATITUDE::toString);
    registry.add("app.geocoding.store.longitude", () -> "-3.0");
  }

  @AfterAll
  static void stopFakeGoogle() {
    GOOGLE.stop(0);
  }

  @Autowired private CheckoutUseCase checkoutUseCase;
  @Autowired private ReceiveRedsysNotificationUseCase receiveRedsysNotificationUseCase;
  @Autowired private CheckoutIdempotencyPort idempotencyPort;
  @Autowired private PendingCheckoutPayloadReader payloadReader;
  @Autowired private PiiCryptoPort piiCryptoPort;
  @Autowired private RedsysProperties redsysProperties;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private UUID productId;
  private String key;

  @BeforeEach
  void newKey() {
    key = UUID.randomUUID().toString();
  }

  @Test
  void theNearestBandChargesItsPriceForASmallCart() {
    String sessionToken = seedCart("35.00", 1);

    CheckoutRedirectDto redirect = checkoutUseCase.execute(deliveryCommand(sessionToken, "Calle Muy Cerca"));

    assertThat(chargedCents(redirect)).isEqualTo("4000");
  }

  @Test
  void aMiddleBandBelowTheFreeThresholdChargesThatBandsPriceAndTheOrderIsMaterializedWithGeocodedData()
      throws Exception {
    String sessionToken = seedCart("34.99", 2);

    CheckoutRedirectDto redirect = checkoutUseCase.execute(deliveryCommand(sessionToken, "Calle Cerca"));

    assertThat(chargedCents(redirect)).isEqualTo("7798");
    String orderNumber = payloadReader.read(idempotencyPort.find(key).orElseThrow().checkoutPayloadEncrypted()).orderNumber();

    approve(orderNumber, "7798");

    Map<String, Object> order = jdbcTemplate.queryForMap("SELECT id, shipping_cost, subtotal, total, status FROM orders WHERE order_number = ?", orderNumber);
    assertThat((BigDecimal) order.get("shipping_cost")).isEqualByComparingTo("8.00");
    assertThat((BigDecimal) order.get("total")).isEqualByComparingTo("77.98");
    assertThat(order.get("status")).isEqualTo("PENDING");
    Map<String, Object> delivery =
        jdbcTemplate.queryForMap("SELECT distance_km, postal_code, latitude_encrypted, longitude_encrypted FROM order_deliveries WHERE order_id = ?", order.get("id"));
    assertThat((BigDecimal) delivery.get("distance_km")).isBetween(new BigDecimal("2.99"), new BigDecimal("3.01"));
    assertThat(delivery.get("postal_code")).isEqualTo("28001");
    BigDecimal storedLatitude = new BigDecimal(piiCryptoPort.decrypt((byte[]) delivery.get("latitude_encrypted")));
    assertThat(storedLatitude.doubleValue()).isCloseTo(40.0 + 3.0 / KM_PER_DEGREE_OF_LATITUDE, org.assertj.core.data.Offset.offset(0.0001));
    assertThat(new BigDecimal(piiCryptoPort.decrypt((byte[]) delivery.get("longitude_encrypted")))).isEqualByComparingTo("-3.0");
  }

  @Test
  void aSubtotalExactlyAtTheFreeFromAmountShipsForFree() {
    String sessionToken = seedCart("35.00", 2);

    CheckoutRedirectDto redirect = checkoutUseCase.execute(deliveryCommand(sessionToken, "Calle Cerca"));

    assertThat(chargedCents(redirect)).isEqualTo("7000");
  }

  @Test
  void theFarthestBandChargesItsPrice() {
    String sessionToken = seedCart("35.00", 1);

    CheckoutRedirectDto redirect = checkoutUseCase.execute(deliveryCommand(sessionToken, "Calle Media"));

    assertThat(chargedCents(redirect)).isEqualTo("4500");
  }

  @Test
  void anAddressBeyondTenKilometresIsRefusedAndReservesNothing() {
    String sessionToken = seedCart("35.00", 1);

    assertThatThrownBy(() -> checkoutUseCase.execute(deliveryCommand(sessionToken, "Calle Lejos")))
        .isInstanceOf(DeliveryOutOfRangeException.class)
        .satisfies(e -> assertThat(((HasErrorCode) e).errorCode()).isEqualTo("DELIVERY_OUT_OF_RANGE"));

    assertNothingReserved();
  }

  @Test
  void anAddressGoogleCannotFindIsRefusedAndReservesNothing() {
    String sessionToken = seedCart("35.00", 1);

    assertThatThrownBy(() -> checkoutUseCase.execute(deliveryCommand(sessionToken, "Calle Nada")))
        .isInstanceOf(DeliveryAddressNotGeocodableException.class)
        .satisfies(
            e -> assertThat(((HasErrorCode) e).errorCode()).isEqualTo("DELIVERY_ADDRESS_NOT_GEOCODABLE"));

    assertNothingReserved();
  }

  @Test
  void anAddressWhoseRealPostalCodeIsNotTheOneTypedIsRefusedAndReservesNothing() {
    String sessionToken = seedCart("35.00", 1);

    assertThatThrownBy(() -> checkoutUseCase.execute(deliveryCommand(sessionToken, "Calle Otro Postal")))
        .isInstanceOf(DeliveryAddressNotGeocodableException.class);

    assertNothingReserved();
  }

  @Test
  void aGoogleThatCannotCheckTheAddressIsUnavailableNotAFakeAddressAndReservesNothing() {
    String sessionToken = seedCart("35.00", 1);

    assertThatThrownBy(() -> checkoutUseCase.execute(deliveryCommand(sessionToken, "Calle Denegada")))
        .isInstanceOf(GeocodingUnavailableException.class)
        .satisfies(e -> assertThat(((HasErrorCode) e).errorCode()).isEqualTo("DELIVERY_GEOCODING_UNAVAILABLE"));

    assertNothingReserved();
  }

  @Test
  void aSlowGeocoderDoesNotHoldADatabaseTransactionOpenWhileItAnswers() throws Exception {
    String sessionToken = seedCart("35.00", 1);
    googleReached = new CountDownLatch(1);
    googleMayAnswer = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<CheckoutRedirectDto> checkout =
          executor.submit(() -> checkoutUseCase.execute(deliveryCommand(sessionToken, "Calle Lenta")));
      assertThat(googleReached.await(10, TimeUnit.SECONDS)).as("checkout reached Google").isTrue();

      Integer idleInTransaction =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database()"
                  + " AND pid <> pg_backend_pid() AND state = 'idle in transaction'",
              Integer.class);

      googleMayAnswer.countDown();
      assertThat(checkout.get(10, TimeUnit.SECONDS)).isNotNull();
      assertThat(idleInTransaction).as("sessions idle in a transaction while Google answers").isZero();
    } finally {
      googleMayAnswer.countDown();
      executor.shutdownNow();
    }
  }

  private void assertNothingReserved() {
    assertThat(idempotencyPort.find(key)).isEmpty();
    assertThat(jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId))
        .isEqualTo(10);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE product_id = ?", Integer.class, productId))
        .isZero();
  }

  private String chargedCents(CheckoutRedirectDto redirect) {
    try {
      Map<String, String> parameters =
          objectMapper.readValue(
              RedsysBase64.decode(redirect.formFields().get("Ds_MerchantParameters")),
              new TypeReference<Map<String, String>>() {});
      return parameters.get("Ds_Merchant_Amount");
    } catch (RuntimeException e) {
      throw new IllegalStateException(e);
    }
  }

  private void approve(String orderNumber, String cents) {
    String redsysOrderId = RedsysOrderId.from(OrderNumber.of(orderNumber));
    String json =
        "{\"Ds_Order\":\""
            + redsysOrderId
            + "\",\"Ds_Response\":\"0000\",\"Ds_Amount\":\""
            + cents
            + "\",\"Ds_AuthorisationCode\":\"auth-1\",\"Ds_Merchant_MerchantData\":\""
            + key
            + "\"}";
    String params = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    receiveRedsysNotificationUseCase.execute(
        params, RedsysSignature.sign(redsysProperties.secretKey(), redsysOrderId, params));
  }

  private String seedCart(String unitPrice, int quantity) {
    productId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID cartId = UUID.randomUUID();
    String sessionToken = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO categories (id, name, slug, status) VALUES (?, 'Ramos', ?, 'ACTIVE')",
        categoryId,
        "ramos-" + categoryId);
    jdbcTemplate.update(
        "INSERT INTO products (id, name, slug, price, stock, status) VALUES (?, ?, ?, ?, 10, 'ACTIVE')",
        productId,
        "Delivery " + productId,
        "delivery-" + productId,
        new BigDecimal(unitPrice));
    jdbcTemplate.update(
        "INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)", productId, categoryId);
    jdbcTemplate.update(
        "INSERT INTO carts (id, session_token, expires_at) VALUES (?, ?, now() + interval '1 day')",
        cartId,
        sessionToken);
    jdbcTemplate.update(
        "INSERT INTO cart_items (id, cart_id, product_id, quantity) VALUES (?, ?, ?, ?)",
        UUID.randomUUID(),
        cartId,
        productId,
        quantity);
    return sessionToken;
  }

  private CheckoutCommand deliveryCommand(String sessionToken, String street) {
    return new CheckoutCommand(
        null,
        sessionToken,
        OrderFulfillment.DELIVERY,
        PaymentMethod.CARD_ONLINE,
        "tok_visa",
        null,
        "Ana",
        "García",
        key + "@example.com",
        "600111222",
        null,
        "Luis",
        "600333444",
        street,
        "1",
        null,
        "28001",
        LocalDate.now().plusDays(2),
        LocalTime.of(10, 0),
        LocalTime.of(12, 0),
        "Felicidades",
        key);
  }

  /** A local server that answers the Geocoding API's JSON shape for a few known street names. */
  private static HttpServer startFakeGoogle() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext(
          "/geocode",
          exchange -> {
            String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            if (query.contains("Calle Lenta")) {
              waitUntilTheTestLetsGoogleAnswer();
            }
            String body = responseFor(query);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void waitUntilTheTestLetsGoogleAnswer() {
    googleReached.countDown();
    try {
      googleMayAnswer.await(20, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static String responseFor(String query) {
    if (query.contains("Calle Lenta")) {
      return found(3.0, "28001");
    }
    if (query.contains("Calle Muy Cerca")) {
      return found(0.5, "28001");
    }
    if (query.contains("Calle Cerca")) {
      return found(3.0, "28001");
    }
    if (query.contains("Calle Media")) {
      return found(7.0, "28001");
    }
    if (query.contains("Calle Lejos")) {
      return found(15.0, "28001");
    }
    if (query.contains("Calle Otro Postal")) {
      return found(3.0, "28999");
    }
    if (query.contains("Calle Denegada")) {
      return "{\"status\":\"REQUEST_DENIED\",\"results\":[]}";
    }
    return "{\"status\":\"ZERO_RESULTS\",\"results\":[]}";
  }

  private static String found(double kilometresNorthOfTheShop, String postalCode) {
    double latitude = 40.0 + kilometresNorthOfTheShop / KM_PER_DEGREE_OF_LATITUDE;
    return "{\"status\":\"OK\",\"results\":[{\"partial_match\":false,\"types\":[\"street_address\"],"
        + "\"address_components\":[{\"long_name\":\""
        + postalCode
        + "\",\"short_name\":\""
        + postalCode
        + "\",\"types\":[\"postal_code\"]}],"
        + "\"geometry\":{\"location_type\":\"ROOFTOP\",\"location\":{\"lat\":"
        + latitude
        + ",\"lng\":-3.0}}}]}";
  }
}
