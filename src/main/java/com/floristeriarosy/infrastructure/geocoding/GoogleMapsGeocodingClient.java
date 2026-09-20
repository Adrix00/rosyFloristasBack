package com.floristeriarosy.infrastructure.geocoding;

import com.floristeriarosy.application.order.dto.GeocodedAddress;
import com.floristeriarosy.application.order.port.out.GeocodingPort;
import com.floristeriarosy.domain.exception.order.GeocodingUnavailableException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Implements {@link GeocodingPort} against the Google Maps Geocoding API (order.md, rule 3.7).
 *
 * <p>Two different outcomes must never be confused. "The address is not real" (not found, too
 * imprecise, wrong postal code) is an empty result. "We could not check" (denied key, quota, HTTP
 * error, timeout, unreadable answer) is a {@link GeocodingUnavailableException}: reporting it as
 * empty would tell a customer with a perfectly good address that it is fake.
 */
@Component
public class GoogleMapsGeocodingClient implements GeocodingPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleMapsGeocodingClient.class);

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
  private static final Set<String> ACCEPTED_LOCATION_TYPES =
      Set.of("ROOFTOP", "RANGE_INTERPOLATED", "GEOMETRIC_CENTER");
  private static final Set<String> DELIVERABLE_TYPES =
      Set.of("street_address", "route", "premise", "subpremise", "point_of_interest", "establishment");

  private final GeocodingProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final Duration requestTimeout;

  /**
   * @param properties Google Maps configuration and the store's own coordinates
   * @param objectMapper parses the Google Maps JSON response; rebuilt so this client does not hold
   *     the caller's mutable reference (SpotBugs EI_EXPOSE_REP2)
   */
  @Autowired
  public GoogleMapsGeocodingClient(GeocodingProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, REQUEST_TIMEOUT);
  }

  /**
   * @param properties Google Maps configuration and the store's own coordinates
   * @param objectMapper parses the Google Maps JSON response
   * @param requestTimeout how long a call may wait for Google's answer; exposed for tests
   */
  GoogleMapsGeocodingClient(GeocodingProperties properties, ObjectMapper objectMapper, Duration requestTimeout) {
    this.properties = properties;
    this.objectMapper = objectMapper.rebuild().build();
    this.requestTimeout = requestTimeout;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  /**
   * @param street the street name
   * @param number the street number
   * @param postalCode the postal code
   * @return the geocoded address with its distance from the store, or empty if the address is not
   *     real
   * @throws GeocodingUnavailableException Google Maps could not be asked or did not answer usefully
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
            + URLEncoder.encode(properties.googleMaps().apiKey(), StandardCharsets.UTF_8);
    String body = fetch(url);
    Optional<GeocodedAddress> result = parseResponse(body, postalCode);
    LOGGER.debug("geocode postalCode={} -> found={}", postalCode, result.isPresent());
    return result;
  }

  /**
   * @param url the full request URL, which carries the API key
   * @return the response body of a 200 answer
   * @throws GeocodingUnavailableException the call failed or the answer was not a 200
   */
  private String fetch(String url) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url)).timeout(requestTimeout).GET().build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        LOGGER.warn("geocode -> HTTP {}", response.statusCode());
        throw new GeocodingUnavailableException();
      }
      return response.body();
    } catch (IOException | InterruptedException | IllegalArgumentException e) {
      // Only the class name is logged: the request URI carries the API key, and the messages of
      // URI.create and of the HTTP client's exceptions can quote it.
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOGGER.warn("geocode -> call failed: {}", e.getClass().getSimpleName());
      throw new GeocodingUnavailableException();
    }
  }

  /**
   * @param body the raw Google Maps JSON response
   * @param postalCode the postal code the customer typed
   * @return the parsed address with its distance from the store, or empty if Google found nothing or
   *     only something that cannot be delivered to
   * @throws GeocodingUnavailableException the status is anything but {@code OK} or {@code
   *     ZERO_RESULTS} (denied key, quota, unknown error), or the body is not readable JSON
   */
  private Optional<GeocodedAddress> parseResponse(String body, String postalCode) {
    try {
      JsonNode root = objectMapper.readTree(body);
      String status = root.path("status").asText();
      if ("ZERO_RESULTS".equals(status)) {
        LOGGER.debug("geocode -> status={}", status);
        return Optional.empty();
      }
      if (!"OK".equals(status)) {
        LOGGER.warn("geocode -> status={}", status);
        throw new GeocodingUnavailableException();
      }
      if (root.path("results").isEmpty()) {
        // Google documents OK as "at least one result": an empty OK is an anomaly, not a fake address.
        LOGGER.warn("geocode -> status OK without results");
        throw new GeocodingUnavailableException();
      }
      JsonNode first = root.path("results").get(0);
      requireWellFormed(first);
      if (!isDeliverable(first, postalCode)) {
        return Optional.empty();
      }
      JsonNode location = first.path("geometry").path("location");
      BigDecimal latitude = BigDecimal.valueOf(location.path("lat").asDouble());
      BigDecimal longitude = BigDecimal.valueOf(location.path("lng").asDouble());
      BigDecimal distanceKm =
          Haversine.distanceKm(
              properties.store().latitude(), properties.store().longitude(), latitude, longitude);
      return Optional.of(new GeocodedAddress(latitude, longitude, distanceKm));
    } catch (JacksonException e) {
      LOGGER.warn("geocode -> could not parse Google Maps response: {}", e.getClass().getSimpleName());
      throw new GeocodingUnavailableException();
    }
  }

  /**
   * Google answers {@code OK} for things that are not the address the customer typed, so an
   * {@code OK} is only accepted when the result pins that very address down (order.md §3.7 — a
   * delivery address must be real). Checked against real responses: a nonsense address came back as
   * the centre of Spain ({@code APPROXIMATE}, 8.88 km from the shop, inside the delivery range); a
   * street with a non-existent house number came back as the street ({@code partial_match}); and
   * with a wrong postal code Google silently returned the same building under the right one, a
   * perfect-looking {@code ROOFTOP}, so the postal code component must equal the one typed.
   *
   * @param result the first Google Maps result
   * @param postalCode the postal code the customer typed
   * @return whether the result is a non-partial, non-approximate street or building match whose
   *     postal code is the one typed
   */
  private boolean isDeliverable(JsonNode result, String postalCode) {
    String locationType = result.path("geometry").path("location_type").asText();
    List<String> failed = new ArrayList<>();
    if (!ACCEPTED_LOCATION_TYPES.contains(locationType)) {
      failed.add("locationType=" + locationType);
    }
    if (result.path("partial_match").asBoolean(false)) {
      failed.add("partialMatch");
    }
    if (!hasAnyType(result.path("types"), DELIVERABLE_TYPES)) {
      failed.add("types");
    }
    if (!postalCodeOf(result).map(code -> code.equals(postalCode.trim())).orElse(false)) {
      failed.add("postalCode");
    }
    if (!failed.isEmpty()) {
      // Names only, so support can tell why a good-looking address was refused without any address
      // data reaching the log.
      LOGGER.debug("geocode -> not a deliverable match, failed: {}", failed);
    }
    return failed.isEmpty();
  }

  /**
   * A body that cannot be judged is "could not check", never "the address is wrong": only a
   * well-formed answer that says the address is wrong may produce an empty result. Without a numeric
   * latitude and longitude the coordinates would silently become (0, 0), thousands of kilometres
   * away, and a good address would be refused as out of range.
   *
   * @param result the first Google Maps result
   * @throws GeocodingUnavailableException a field the decision depends on is missing or of the wrong
   *     kind
   */
  private void requireWellFormed(JsonNode result) {
    JsonNode geometry = result.path("geometry");
    boolean wellFormed =
        geometry.path("location_type").isTextual()
            && !geometry.path("location_type").asText().isBlank()
            && geometry.path("location").path("lat").isNumber()
            && geometry.path("location").path("lng").isNumber()
            && result.path("types").isArray()
            && result.path("address_components").isArray();
    if (!wellFormed) {
      LOGGER.warn("geocode -> malformed result");
      throw new GeocodingUnavailableException();
    }
  }

  /**
   * @param types a Google Maps {@code types} array
   * @param wanted the types that count
   * @return whether the array contains at least one of them
   */
  private boolean hasAnyType(JsonNode types, Set<String> wanted) {
    for (JsonNode type : types) {
      if (wanted.contains(type.asText())) {
        return true;
      }
    }
    return false;
  }

  /**
   * @param result a Google Maps result
   * @return its {@code postal_code} address component, if it has one
   */
  private Optional<String> postalCodeOf(JsonNode result) {
    for (JsonNode component : result.path("address_components")) {
      if (hasAnyType(component.path("types"), Set.of("postal_code"))) {
        return Optional.of(component.path("long_name").asText());
      }
    }
    return Optional.empty();
  }
}
