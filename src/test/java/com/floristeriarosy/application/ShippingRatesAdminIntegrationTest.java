package com.floristeriarosy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.floristeriarosy.application.order.command.CheckoutCommand;
import com.floristeriarosy.application.order.command.CreateShippingRateCommand;
import com.floristeriarosy.application.order.command.UpdateShippingRateCommand;
import com.floristeriarosy.application.order.dto.CheckoutRedirectDto;
import com.floristeriarosy.application.order.dto.ShippingBandsDto;
import com.floristeriarosy.application.order.port.in.CheckoutUseCase;
import com.floristeriarosy.application.order.port.in.CreateShippingRateUseCase;
import com.floristeriarosy.application.order.port.in.GetShippingRatesUseCase;
import com.floristeriarosy.application.order.port.in.UpdateShippingRateUseCase;
import com.floristeriarosy.domain.exception.HasErrorDetails;
import com.floristeriarosy.domain.exception.order.DeliveryOutOfRangeException;
import com.floristeriarosy.domain.exception.order.ShippingRatesInvalidException;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysBase64;
import com.floristeriarosy.infrastructure.persistence.adapter.order.ShippingRatePersistenceAdapter;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The delivery tariff administered from the panel (order.md §3.7, §4) through the real transactional
 * services on a real PostgreSQL: an edit is seen by the next checkout quote, an incoherent change is
 * refused and leaves nothing behind, the radius follows the furthest active band, and two
 * administrators editing at once are serialized by the row lock. Only Google Maps is faked.
 */
@Testcontainers
@SpringBootTest
@WithMockUser(roles = "ADMIN")
class ShippingRatesAdminIntegrationTest {

  private static final UUID NEAR = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID MIDDLE = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID FAR = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final double KM_PER_DEGREE_OF_LATITUDE = 111.19492664;
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
    registry.add("app.geocoding.store.latitude", () -> "40.0");
    registry.add("app.geocoding.store.longitude", () -> "-3.0");
  }

  @AfterAll
  static void stopFakeGoogle() {
    GOOGLE.stop(0);
  }

  @Autowired private GetShippingRatesUseCase getShippingRatesUseCase;
  @Autowired private CreateShippingRateUseCase createShippingRateUseCase;
  @Autowired private UpdateShippingRateUseCase updateShippingRateUseCase;
  @Autowired private CheckoutUseCase checkoutUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoSpyBean private ShippingRatePersistenceAdapter shippingRateAdapter;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ExecutorService executor =
      new DelegatingSecurityContextExecutorService(Executors.newFixedThreadPool(2));
  private UUID adminId;

  @BeforeEach
  void restoreTheSeedTariffAndSeedAnAdmin() {
    jdbcTemplate.update("DELETE FROM audit_log WHERE entity_type = 'shipping_rate'");
    jdbcTemplate.update("DELETE FROM shipping_rates WHERE id NOT IN (?, ?, ?)", NEAR, MIDDLE, FAR);
    restore(NEAR, 0, 1, "5.00", "50.00");
    restore(MIDDLE, 1, 5, "8.00", "70.00");
    restore(FAR, 5, 10, "10.00", "90.00");
    adminId = UUID.randomUUID();
    byte[] unique = adminId.toString().getBytes(StandardCharsets.UTF_8);
    jdbcTemplate.update(
        "INSERT INTO admin_users (id, email_encrypted, email_hash, password_hash, role)"
            + " VALUES (?, ?, ?, 'x', 'ADMIN')",
        adminId,
        unique,
        unique);
  }

  @AfterEach
  void stopTheWorkerThreads() {
    executor.shutdownNow();
  }

  @Test
  void theTariffListsEveryBandWithTheCurrentRadius() {
    ShippingBandsDto tariff = getShippingRatesUseCase.execute();

    assertThat(tariff.bands()).extracting(b -> b.minKm().intValue()).containsExactly(0, 1, 5);
    assertThat(tariff.deliveryRadiusKm()).isEqualByComparingTo("10");
  }

  @Test
  void changingABandsPriceChangesTheNextCheckoutQuoteAndIsAudited() {
    updateShippingRateUseCase.execute(update(MIDDLE, 1, 5, "9.00", "70.00", true));

    // 2 x 34.99 = 69.98 (below the 70.00 free threshold) + the band's new price 9.00
    assertThat(chargedCents(checkoutUseCase.execute(deliveryCommand(seedCart("34.99", 2), "Calle Cerca"))))
        .isEqualTo("7898");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT changed_fields::text FROM audit_log WHERE entity_type = 'shipping_rate'"
                    + " AND action = 'UPDATE' AND entity_id = ?",
                String.class,
                MIDDLE))
        .isEqualTo("{price}");
  }

  @Test
  void extendingTheLastBandToFifteenMakesATwelveKilometreAddressDeliverableAndMovesTheRadius() {
    String cart = seedCart("35.00", 1);
    assertThatThrownBy(() -> checkoutUseCase.execute(deliveryCommand(cart, "Calle Doce")))
        .isInstanceOf(DeliveryOutOfRangeException.class)
        .satisfies(e -> assertThat(((HasErrorDetails) e).errorDetails().get("maxDistanceKm")).isEqualTo(new BigDecimal("10.00")));

    updateShippingRateUseCase.execute(update(FAR, 5, 15, "10.00", "90.00", true));

    assertThat(chargedCents(checkoutUseCase.execute(deliveryCommand(seedCart("35.00", 1), "Calle Doce"))))
        .isEqualTo("4500");
    assertThat(getShippingRatesUseCase.execute().deliveryRadiusKm()).isEqualByComparingTo("15");
    assertThatThrownBy(() -> checkoutUseCase.execute(deliveryCommand(seedCart("35.00", 1), "Calle Veinte")))
        .isInstanceOf(DeliveryOutOfRangeException.class)
        .satisfies(e -> assertThat(((HasErrorDetails) e).errorDetails().get("maxDistanceKm")).isEqualTo(new BigDecimal("15.00")));
  }

  @Test
  void deactivatingBandsShrinksTheRadiusButTheLastActiveOneCannotBeDeactivated() {
    updateShippingRateUseCase.execute(update(FAR, 5, 10, "10.00", "90.00", false));
    updateShippingRateUseCase.execute(update(MIDDLE, 1, 5, "8.00", "70.00", false));
    assertThat(getShippingRatesUseCase.execute().deliveryRadiusKm()).isEqualByComparingTo("1");

    assertThatThrownBy(() -> updateShippingRateUseCase.execute(update(NEAR, 0, 1, "5.00", "50.00", false)))
        .isInstanceOf(ShippingRatesInvalidException.class)
        .hasMessageContaining("At least one");

    assertThat(jdbcTemplate.queryForObject("SELECT active FROM shipping_rates WHERE id = ?", Boolean.class, NEAR)).isTrue();
    assertThat(getShippingRatesUseCase.execute().deliveryRadiusKm()).isEqualByComparingTo("1");
  }

  @Test
  void aBandThatLeavesAGapIsRefusedAndNothingIsCreatedOrAudited() {
    assertThatThrownBy(() -> createShippingRateUseCase.execute(create(12, 15)))
        .isInstanceOf(ShippingRatesInvalidException.class)
        .hasMessageContaining("gap");

    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shipping_rates", Integer.class)).isEqualTo(3);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_type = 'shipping_rate'", Integer.class))
        .isZero();
  }

  @Test
  void aContiguousBandIsCreatedAndAudited() {
    var created = createShippingRateUseCase.execute(create(10, 15));

    assertThat(getShippingRatesUseCase.execute().deliveryRadiusKm()).isEqualByComparingTo("15");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'CREATE' AND entity_type = 'shipping_rate' AND entity_id = ?",
                Integer.class,
                created.id()))
        .isEqualTo(1);
  }

  @Test
  @WithMockUser(roles = "CUSTOMER")
  void aNonAdminCannotChangeTheTariff() {
    assertThatThrownBy(() -> updateShippingRateUseCase.execute(update(MIDDLE, 1, 5, "1.00", "70.00", true)))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> createShippingRateUseCase.execute(create(10, 15))).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> getShippingRatesUseCase.execute()).isInstanceOf(AccessDeniedException.class);

    assertThat(jdbcTemplate.queryForObject("SELECT price FROM shipping_rates WHERE id = ?", BigDecimal.class, MIDDLE))
        .isEqualByComparingTo("8.00");
  }

  @Test
  void twoAdministratorsEditingAtOnceAreSerializedSoTheFinalTariffStaysCoherent() throws Exception {
    // A shrinks the last band to end at 8; B adds a band starting at 10. Each is coherent against the
    // seed tariff, but together they leave a gap between 8 and 10 that no database constraint
    // catches: only the row lock lets the second one see the first one's result.
    AtomicBoolean firstSave = new AtomicBoolean();
    CountDownLatch aIsSaving = new CountDownLatch(1);
    doAnswer(
            call -> {
              if (firstSave.compareAndSet(false, true)) {
                aIsSaving.countDown();
                awaitASessionWaitingOnALock();
              }
              return call.callRealMethod();
            })
        .when(shippingRateAdapter)
        .save(any());

    Future<?> a = executor.submit(() -> updateShippingRateUseCase.execute(update(FAR, 5, 8, "10.00", "90.00", true)));
    assertThat(aIsSaving.await(10, TimeUnit.SECONDS)).as("A reached its save holding the locks").isTrue();
    Future<?> b = executor.submit(() -> createShippingRateUseCase.execute(create(10, 15)));

    a.get(15, TimeUnit.SECONDS);
    assertThatThrownBy(() -> b.get(15, TimeUnit.SECONDS))
        .hasCauseInstanceOf(ShippingRatesInvalidException.class)
        .hasMessageContaining("gap");
    assertThat(getShippingRatesUseCase.execute().deliveryRadiusKm()).isEqualByComparingTo("8");
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shipping_rates", Integer.class)).isEqualTo(3);
  }

  private void awaitASessionWaitingOnALock() {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      Integer waiting =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM pg_stat_activity"
                  + " WHERE datname = current_database() AND wait_event_type = 'Lock'",
              Integer.class);
      if (waiting != null && waiting > 0) {
        return;
      }
      try {
        Thread.sleep(25);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
    throw new AssertionError("the second administrator never blocked on the row locks");
  }

  private UpdateShippingRateCommand update(
      UUID id, int minKm, int maxKm, String price, String freeFrom, boolean active) {
    return new UpdateShippingRateCommand(
        id,
        adminId,
        new BigDecimal(minKm),
        new BigDecimal(maxKm),
        new BigDecimal(price),
        new BigDecimal(freeFrom),
        active);
  }

  private CreateShippingRateCommand create(int minKm, int maxKm) {
    return new CreateShippingRateCommand(
        adminId, new BigDecimal(minKm), new BigDecimal(maxKm), new BigDecimal("12.00"), null, true);
  }

  private void restore(UUID id, int minKm, int maxKm, String price, String freeFrom) {
    jdbcTemplate.update(
        "UPDATE shipping_rates SET min_km = ?, max_km = ?, price = ?, free_from_amount = ?, active = true WHERE id = ?",
        minKm,
        maxKm,
        new BigDecimal(price),
        new BigDecimal(freeFrom),
        id);
  }

  private String chargedCents(CheckoutRedirectDto redirect) {
    Map<String, String> parameters =
        objectMapper.readValue(
            RedsysBase64.decode(redirect.formFields().get("Ds_MerchantParameters")),
            new TypeReference<Map<String, String>>() {});
    return parameters.get("Ds_Merchant_Amount");
  }

  private String seedCart(String unitPrice, int quantity) {
    UUID productId = UUID.randomUUID();
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
        "Tariff " + productId,
        "tariff-" + productId,
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
    String key = UUID.randomUUID().toString();
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

  /** A local server answering the Geocoding API's JSON shape for a few known street names. */
  private static HttpServer startFakeGoogle() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext(
          "/geocode",
          exchange -> {
            String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            byte[] bytes = responseFor(query).getBytes(StandardCharsets.UTF_8);
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

  private static String responseFor(String query) {
    if (query.contains("Calle Cerca")) {
      return found(3.0);
    }
    if (query.contains("Calle Doce")) {
      return found(12.0);
    }
    if (query.contains("Calle Veinte")) {
      return found(20.0);
    }
    return "{\"status\":\"ZERO_RESULTS\",\"results\":[]}";
  }

  private static String found(double kilometresNorthOfTheShop) {
    double latitude = 40.0 + kilometresNorthOfTheShop / KM_PER_DEGREE_OF_LATITUDE;
    return "{\"status\":\"OK\",\"results\":[{\"partial_match\":false,\"types\":[\"street_address\"],"
        + "\"address_components\":[{\"long_name\":\"28001\",\"short_name\":\"28001\",\"types\":[\"postal_code\"]}],"
        + "\"geometry\":{\"location_type\":\"ROOFTOP\",\"location\":{\"lat\":"
        + latitude
        + ",\"lng\":-3.0}}}]}";
  }
}
