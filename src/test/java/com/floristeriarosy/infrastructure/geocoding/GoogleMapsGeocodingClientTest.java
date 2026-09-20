package com.floristeriarosy.infrastructure.geocoding;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.application.order.dto.GeocodedAddress;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** {@link GoogleMapsGeocodingClient} against a local server standing in for Google Maps. */
class GoogleMapsGeocodingClientTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void geocodeReturnsCoordinatesAndDistanceForAnOkResponse() throws IOException {
    String body =
        "{\"status\":\"OK\",\"results\":[{\"geometry\":{\"location\":{\"lat\":40.5,\"lng\":-3.7}}}]}";

    Optional<GeocodedAddress> result = geocodeAgainst(200, body);

    assertThat(result).isPresent();
    assertThat(result.get().latitude()).isEqualByComparingTo("40.5");
    assertThat(result.get().longitude()).isEqualByComparingTo("-3.7");
    assertThat(result.get().distanceKm()).isPositive();
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
