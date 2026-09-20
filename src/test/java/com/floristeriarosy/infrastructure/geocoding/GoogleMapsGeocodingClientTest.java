package com.floristeriarosy.infrastructure.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.floristeriarosy.application.order.dto.GeocodedAddress;
import com.floristeriarosy.domain.exception.order.GeocodingUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link GoogleMapsGeocodingClient} against a local server standing in for Google Maps. The
 * responses in {@code src/test/resources/geocoding} are real ones, captured once from the Geocoding
 * API for a legitimate address, a non-existent house number, a misspelled street, a nonsense
 * address and a wrong postal code.
 */
class GoogleMapsGeocodingClientTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String POSTAL_CODE = "28045";

  @Test
  void aLegitimateAddressIsGeocodedWithItsDistanceFromTheStore() throws IOException {
    Optional<GeocodedAddress> result = geocodeAgainst(200, fixture("legit"), POSTAL_CODE);

    assertThat(result).isPresent();
    assertThat(result.get().latitude()).isBetween(new BigDecimal("40.39"), new BigDecimal("40.40"));
    assertThat(result.get().distanceKm()).isPositive();
  }

  @Test
  void thePostalCodeTypedWithSurroundingSpacesStillMatches() throws IOException {
    assertThat(geocodeAgainst(200, fixture("legit"), " 28045 ")).isPresent();
  }

  @Test
  void aNonExistentHouseNumberIsNotARealAddress() throws IOException {
    assertThat(geocodeAgainst(200, fixture("wrong_number"), POSTAL_CODE)).isEmpty();
  }

  @Test
  void aMisspelledStreetIsNotARealAddress() throws IOException {
    assertThat(geocodeAgainst(200, fixture("misspelled_street"), POSTAL_CODE)).isEmpty();
  }

  @Test
  void aNonsenseAddressIsNotARealAddressEvenThoughGoogleAnswersOkWithTheCentreOfSpain()
      throws IOException {
    assertThat(geocodeAgainst(200, fixture("nonsense"), "00000")).isEmpty();
  }

  @Test
  void aWrongPostalCodeIsNotARealAddressEvenThoughGoogleAnswersAPerfectRooftopUnderTheRightOne()
      throws IOException {
    assertThat(geocodeAgainst(200, fixture("wrong_postal_code"), "28001")).isEmpty();
  }

  @Test
  void interpolatedAndGeometricCenterResultsAreAcceptedWhenPreciseAndOnTheRightPostalCode()
      throws IOException {
    assertThat(geocodeAgainst(200, body("RANGE_INTERPOLATED", false, "street_address", POSTAL_CODE), POSTAL_CODE))
        .isPresent();
    assertThat(geocodeAgainst(200, body("GEOMETRIC_CENTER", false, "premise", POSTAL_CODE), POSTAL_CODE))
        .isPresent();
  }

  @Test
  void approximatePartialUntypedOrPostalLessResultsAreRefused() throws IOException {
    assertThat(geocodeAgainst(200, body("APPROXIMATE", false, "street_address", POSTAL_CODE), POSTAL_CODE)).isEmpty();
    assertThat(geocodeAgainst(200, body("ROOFTOP", true, "street_address", POSTAL_CODE), POSTAL_CODE)).isEmpty();
    assertThat(geocodeAgainst(200, body("GEOMETRIC_CENTER", false, "locality", POSTAL_CODE), POSTAL_CODE)).isEmpty();
    assertThat(geocodeAgainst(200, body("ROOFTOP", false, "street_address", null), POSTAL_CODE)).isEmpty();
    String noLocationType =
        "{\"status\":\"OK\",\"results\":[{\"types\":[\"street_address\"],\"geometry\":{\"location\":{\"lat\":40.5,\"lng\":-3.7}}}]}";
    assertThat(geocodeAgainst(200, noLocationType, POSTAL_CODE)).isEmpty();
  }

  @Test
  void zeroResultsIsAnEmptyResultAndOnlyADebugLine() throws IOException {
    ListAppender<ILoggingEvent> appender = attachAppender();

    assertThat(geocodeAgainst(200, "{\"status\":\"ZERO_RESULTS\",\"results\":[]}", POSTAL_CODE)).isEmpty();

    assertThat(messages(appender, Level.WARN)).isEmpty();
  }

  @Test
  void aDeniedKeyAnOverQuotaAndAnUnknownErrorAreUnavailableNotAFakeAddress() {
    for (String status : List.of("REQUEST_DENIED", "OVER_QUERY_LIMIT", "OVER_DAILY_LIMIT", "UNKNOWN_ERROR", "INVALID_REQUEST")) {
      assertThatThrownBy(() -> geocodeAgainst(200, "{\"status\":\"" + status + "\",\"results\":[]}", POSTAL_CODE))
          .as(status)
          .isInstanceOf(GeocodingUnavailableException.class);
    }
  }

  @Test
  void aDeniedRequestWarnsWithTheStatusOnlyAndNeverTheApiKey() {
    ListAppender<ILoggingEvent> appender = attachAppender();

    assertThatThrownBy(() -> geocodeAgainst(200, "{\"status\":\"REQUEST_DENIED\",\"results\":[]}", POSTAL_CODE))
        .isInstanceOf(GeocodingUnavailableException.class)
        .hasMessageNotContaining("test-key");

    assertThat(messages(appender, Level.WARN)).singleElement().satisfies(m -> assertThat(m).contains("REQUEST_DENIED"));
    assertThat(allMessages(appender)).noneMatch(m -> m.contains("test-key"));
  }

  @Test
  void aServerErrorAMalformedBodyAndAnEmptyBodyAreUnavailable() {
    assertThatThrownBy(() -> geocodeAgainst(500, "{\"status\":\"OK\"}", POSTAL_CODE))
        .isInstanceOf(GeocodingUnavailableException.class);
    assertThatThrownBy(() -> geocodeAgainst(200, "not json", POSTAL_CODE))
        .isInstanceOf(GeocodingUnavailableException.class);
    assertThatThrownBy(() -> geocodeAgainst(200, "", POSTAL_CODE))
        .isInstanceOf(GeocodingUnavailableException.class);
  }

  @Test
  void anUnreachableGoogleIsUnavailable() {
    GeocodingProperties properties = properties("http://localhost:1/no-one-listens");

    assertThatThrownBy(() -> new GoogleMapsGeocodingClient(properties, OBJECT_MAPPER).geocode("Calle Mayor", "1", POSTAL_CODE))
        .isInstanceOf(GeocodingUnavailableException.class);
  }

  @Test
  void aGoogleThatDoesNotAnswerInTimeIsUnavailable() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          try {
            Thread.sleep(2_000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          exchange.close();
        });
    server.start();
    try {
      GeocodingProperties properties = properties("http://localhost:" + server.getAddress().getPort());

      assertThatThrownBy(
              () ->
                  new GoogleMapsGeocodingClient(properties, OBJECT_MAPPER, Duration.ofMillis(300))
                      .geocode("Calle Mayor", "1", POSTAL_CODE))
          .isInstanceOf(GeocodingUnavailableException.class);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void anInvalidUrlIsUnavailableAndNeverLeaksTheApiKeyIntoTheLog() {
    ListAppender<ILoggingEvent> appender = attachAppender();
    GeocodingProperties properties = properties("http://not a valid host");

    assertThatThrownBy(() -> new GoogleMapsGeocodingClient(properties, OBJECT_MAPPER).geocode("Calle Mayor", "1", POSTAL_CODE))
        .isInstanceOf(GeocodingUnavailableException.class);

    assertThat(messages(appender, Level.WARN)).singleElement().satisfies(m -> assertThat(m).contains("IllegalArgumentException"));
    assertThat(allMessages(appender)).noneMatch(m -> m.contains("test-key"));
    assertThat(appender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
  }

  @Test
  void thePropertiesNeverPrintTheApiKey() {
    assertThat(new GeocodingProperties.GoogleMaps("test-key", "https://example.invalid").toString())
        .doesNotContain("test-key");
  }

  private String body(String locationType, boolean partialMatch, String type, String postalCode) {
    String components =
        postalCode == null
            ? "[]"
            : "[{\"long_name\":\"" + postalCode + "\",\"short_name\":\"" + postalCode + "\",\"types\":[\"postal_code\"]}]";
    return "{\"status\":\"OK\",\"results\":[{\"partial_match\":"
        + partialMatch
        + ",\"types\":[\""
        + type
        + "\"],\"address_components\":"
        + components
        + ",\"geometry\":{\"location_type\":\""
        + locationType
        + "\",\"location\":{\"lat\":40.5,\"lng\":-3.7}}}]}";
  }

  private String fixture(String name) throws IOException {
    try (InputStream in = getClass().getResourceAsStream("/geocoding/" + name + ".json")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private ListAppender<ILoggingEvent> attachAppender() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    ((Logger) LoggerFactory.getLogger(GoogleMapsGeocodingClient.class)).addAppender(appender);
    return appender;
  }

  private List<String> messages(ListAppender<ILoggingEvent> appender, Level level) {
    return appender.list.stream()
        .filter(event -> event.getLevel() == level)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  private List<String> allMessages(ListAppender<ILoggingEvent> appender) {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  private GeocodingProperties properties(String geocodeUrl) {
    return new GeocodingProperties(
        new GeocodingProperties.GoogleMaps("test-key", geocodeUrl),
        new GeocodingProperties.Store(new BigDecimal("40.0"), new BigDecimal("-3.0")));
  }

  private Optional<GeocodedAddress> geocodeAgainst(int statusCode, String body, String postalCode)
      throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(statusCode, bytes.length == 0 ? -1 : bytes.length);
          if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
          }
          exchange.close();
        });
    server.start();
    try {
      return new GoogleMapsGeocodingClient(properties("http://localhost:" + server.getAddress().getPort()), OBJECT_MAPPER)
          .geocode("Calle Mayor", "1", postalCode);
    } finally {
      server.stop(0);
    }
  }
}
