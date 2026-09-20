package com.floristeriarosy.infrastructure.geocoding;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.floristeriarosy.application.order.dto.GeocodedAddress;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/** {@link GoogleMapsGeocodingClient} against a local server standing in for Google Maps. */
class GoogleMapsGeocodingClientTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void geocodeReturnsCoordinatesAndDistanceForAnOkResponse() throws IOException {
    Optional<GeocodedAddress> result = geocodeAgainst(200, okBody("ROOFTOP", false));

    assertThat(result).isPresent();
    assertThat(result.get().latitude()).isEqualByComparingTo("40.5");
    assertThat(result.get().longitude()).isEqualByComparingTo("-3.7");
    assertThat(result.get().distanceKm()).isPositive();
  }

  @Test
  void geocodeAcceptsAnInterpolatedStreetNumberAndAGeometricCenter() throws IOException {
    assertThat(geocodeAgainst(200, okBody("RANGE_INTERPOLATED", false))).isPresent();
    assertThat(geocodeAgainst(200, okBody("GEOMETRIC_CENTER", false))).isPresent();
  }

  @Test
  void geocodeRejectsAnApproximateResultLikeTheCentreOfTheCountryGoogleReturnsForNonsense()
      throws IOException {
    assertThat(geocodeAgainst(200, okBody("APPROXIMATE", false))).isEmpty();
  }

  @Test
  void geocodeRejectsAPartialMatchEvenWhenItLooksPrecise() throws IOException {
    assertThat(geocodeAgainst(200, okBody("ROOFTOP", true))).isEmpty();
  }

  @Test
  void geocodeRejectsAResultWithoutALocationType() throws IOException {
    String body = "{\"status\":\"OK\",\"results\":[{\"geometry\":{\"location\":{\"lat\":40.5,\"lng\":-3.7}}}]}";

    assertThat(geocodeAgainst(200, body)).isEmpty();
  }

  @Test
  void geocodeReturnsEmptyForZeroResults() throws IOException {
    assertThat(geocodeAgainst(200, "{\"status\":\"ZERO_RESULTS\",\"results\":[]}")).isEmpty();
  }

  @Test
  void geocodeReturnsEmptyWhenTheQuotaIsExceeded() throws IOException {
    assertThat(geocodeAgainst(200, "{\"status\":\"OVER_QUERY_LIMIT\",\"results\":[]}")).isEmpty();
  }

  @Test
  void geocodeReturnsEmptyWhenTheRequestIsDenied() throws IOException {
    assertThat(geocodeAgainst(200, "{\"status\":\"REQUEST_DENIED\",\"results\":[]}")).isEmpty();
  }

  @Test
  void geocodeReturnsEmptyForAMalformedBody() throws IOException {
    assertThat(geocodeAgainst(200, "not json")).isEmpty();
  }

  @Test
  void geocodeReturnsEmptyWhenGoogleMapsIsUnreachable() {
    GeocodingProperties properties =
        new GeocodingProperties(
            new GeocodingProperties.GoogleMaps("test-key", "http://localhost:1/no-one-listens"),
            new GeocodingProperties.Store(new BigDecimal("40.0"), new BigDecimal("-3.0")));

    assertThat(
            new GoogleMapsGeocodingClient(properties, OBJECT_MAPPER)
                .geocode("Calle Mayor", "1", "28013"))
        .isEmpty();
  }

  @Test
  void aDeniedRequestWarnsWithTheStatusOnlyAndNeverTheApiKey() throws IOException {
    ListAppender<ILoggingEvent> appender = attachAppender();

    assertThat(geocodeAgainst(200, "{\"status\":\"REQUEST_DENIED\",\"results\":[]}")).isEmpty();

    assertThat(messages(appender, Level.WARN)).singleElement().satisfies(m -> assertThat(m).contains("REQUEST_DENIED"));
    assertThat(allMessages(appender)).noneMatch(m -> m.contains("test-key"));
  }

  @Test
  void zeroResultsIsOnlyADebugLine() throws IOException {
    ListAppender<ILoggingEvent> appender = attachAppender();

    assertThat(geocodeAgainst(200, "{\"status\":\"ZERO_RESULTS\",\"results\":[]}")).isEmpty();

    assertThat(messages(appender, Level.WARN)).isEmpty();
  }

  @Test
  void anInvalidUrlNeverLeaksTheApiKeyIntoTheLog() {
    ListAppender<ILoggingEvent> appender = attachAppender();
    GeocodingProperties properties =
        new GeocodingProperties(
            new GeocodingProperties.GoogleMaps("test-key", "http://not a valid host"),
            new GeocodingProperties.Store(new BigDecimal("40.0"), new BigDecimal("-3.0")));

    Optional<GeocodedAddress> result =
        new GoogleMapsGeocodingClient(properties, OBJECT_MAPPER).geocode("Calle Mayor", "1", "28013");

    assertThat(result).isEmpty();
    assertThat(allMessages(appender)).noneMatch(m -> m.contains("test-key"));
    assertThat(appender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
  }

  @Test
  void thePropertiesNeverPrintTheApiKey() {
    assertThat(new GeocodingProperties.GoogleMaps("test-key", "https://example.invalid").toString())
        .doesNotContain("test-key");
  }

  private String okBody(String locationType, boolean partialMatch) {
    return "{\"status\":\"OK\",\"results\":[{\"partial_match\":"
        + partialMatch
        + ",\"geometry\":{\"location_type\":\""
        + locationType
        + "\",\"location\":{\"lat\":40.5,\"lng\":-3.7}}}]}";
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

  private Optional<GeocodedAddress> geocodeAgainst(int statusCode, String body) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(statusCode, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      GeocodingProperties properties =
          new GeocodingProperties(
              new GeocodingProperties.GoogleMaps(
                  "test-key", "http://localhost:" + server.getAddress().getPort()),
              new GeocodingProperties.Store(new BigDecimal("40.0"), new BigDecimal("-3.0")));
      return new GoogleMapsGeocodingClient(properties, OBJECT_MAPPER)
          .geocode("Calle Mayor", "1", "28013");
    } finally {
      server.stop(0);
    }
  }
}
