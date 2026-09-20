package com.floristeriarosy.infrastructure.geocoding;

import com.floristeriarosy.application.order.dto.GeocodedAddress;
import com.floristeriarosy.application.order.port.out.GeocodingPort;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Implements {@link GeocodingPort} against the Google Maps Geocoding API (order.md, rule 3.7). */
@Component
public class GoogleMapsGeocodingClient implements GeocodingPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleMapsGeocodingClient.class);

  private final GeocodingProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  /**
   * @param properties Google Maps configuration and the store's own coordinates
   * @param objectMapper parses the Google Maps JSON response; rebuilt so this client does not hold
   *     the caller's mutable reference (SpotBugs EI_EXPOSE_REP2)
   */
  public GoogleMapsGeocodingClient(GeocodingProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper.rebuild().build();
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  /**
   * @param street the street name
   * @param number the street number
   * @param postalCode the postal code
   * @return the geocoded address with its distance from the store, or empty if Google Maps could not
   *     locate it
   */
  @Override
  public Optional<GeocodedAddress> geocode(String street, String number, String postalCode) {
    LOGGER.debug("geocode postalCode={}", postalCode);
    String address = street + " " + number + ", " + postalCode + ", Spain";
    String url =
        properties.googleMaps().geocodeUrl()
            + "?address="
            + URLEncoder.encode(address, StandardCharsets.UTF_8)
            + "&key="
            + properties.googleMaps().apiKey();
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      Optional<GeocodedAddress> result = parseResponse(response.body());
      LOGGER.debug("geocode postalCode={} -> found={}", postalCode, result.isPresent());
      return result;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOGGER.warn("geocode postalCode={} -> call failed: {}", postalCode, e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  /**
   * @param body the raw Google Maps JSON response
   * @return the parsed address with its distance from the store, or empty if {@code status} is not
   *     {@code OK} or there are no results
   */
  private Optional<GeocodedAddress> parseResponse(String body) {
    try {
      JsonNode root = objectMapper.readTree(body);
      String status = root.path("status").asText();
      if (!"OK".equals(status) || root.path("results").isEmpty()) {
        logNotFound(status);
        return Optional.empty();
      }
      JsonNode location = root.path("results").get(0).path("geometry").path("location");
      BigDecimal latitude = BigDecimal.valueOf(location.path("lat").asDouble());
      BigDecimal longitude = BigDecimal.valueOf(location.path("lng").asDouble());
      BigDecimal distanceKm =
          Haversine.distanceKm(
              properties.store().latitude(), properties.store().longitude(), latitude, longitude);
      return Optional.of(new GeocodedAddress(latitude, longitude, distanceKm));
    } catch (JacksonException e) {
      LOGGER.warn("geocode -> could not parse Google Maps response: {}", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  /**
   * @param status Google Maps's {@code status}; {@code ZERO_RESULTS} is an ordinary miss (DEBUG),
   *     anything else (quota, denied key, unknown) is an operator signal (WARN)
   */
  private void logNotFound(String status) {
    if ("ZERO_RESULTS".equals(status)) {
      LOGGER.debug("geocode -> status={}", status);
    } else {
      LOGGER.warn("geocode -> status={}", status);
    }
  }
}
