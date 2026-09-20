package com.floristeriarosy.infrastructure.geocoding;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.application.order.dto.GeocodedAddress;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

/**
 * Real call to Google Maps (opt-in: {@code mvn -Psandbox test}); reported as skipped without a
 * {@code GOOGLE_MAPS_API_KEY}.
 */
@Tag("sandbox")
@EnabledIfEnvironmentVariable(named = "GOOGLE_MAPS_API_KEY", matches = ".+")
class GoogleMapsGeocodingSandboxTest {

  @Test
  void geocodesARealMadridAddress() {
    GeocodingProperties properties =
        new GeocodingProperties(
            new GeocodingProperties.GoogleMaps(
                System.getenv("GOOGLE_MAPS_API_KEY"),
                "https://maps.googleapis.com/maps/api/geocode/json"),
            new GeocodingProperties.Store(new BigDecimal("40.4168"), new BigDecimal("-3.7038")));

    Optional<GeocodedAddress> result =
        new GoogleMapsGeocodingClient(properties, new ObjectMapper())
            .geocode("Calle de Alcalá", "1", "28014");

    assertThat(result).isPresent();
    assertThat(result.get().distanceKm()).isLessThan(new BigDecimal("5"));
  }
}
