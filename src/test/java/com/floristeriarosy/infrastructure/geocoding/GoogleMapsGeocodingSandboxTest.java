package com.floristeriarosy.infrastructure.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.floristeriarosy.application.order.dto.GeocodedAddress;
import com.floristeriarosy.domain.exception.order.GeocodingUnavailableException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * Real calls to Google Maps (opt-in: {@code mvn -Psandbox test}); reported as skipped without a
 * {@code GOOGLE_MAPS_API_KEY}. The key is read from the environment and never printed or asserted
 * on. Distances are measured from the shop's real coordinates, so a landmark next to the shop must
 * be well inside the 10 km delivery limit and one in another town well outside it. A delivery
 * address must be real (order.md §3.7): a wrong house number, a misspelled street, a wrong postal
 * code and a nonsense address are all refused.
 */
@Tag("sandbox")
@EnabledIfEnvironmentVariable(named = "GOOGLE_MAPS_API_KEY", matches = ".+")
class GoogleMapsGeocodingSandboxTest {

  private static final String GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json";
  private static final GeocodingProperties.Store SHOP =
      new GeocodingProperties.Store(new BigDecimal("40.393944"), new BigDecimal("-3.6980998"));

  @Test
  void aLandmarkNextToTheShopIsFoundWithASmallDistance() {
    // Matadero Madrid, a public cultural centre a few hundred metres from the shop.
    Optional<GeocodedAddress> result = realClient().geocode("Paseo de la Chopera", "14", "28045");

    assertThat(result).isPresent();
    assertThat(result.get().distanceKm()).isLessThan(new BigDecimal("2"));
  }

  @Test
  void aLandmarkInAnotherTownIsFoundButBeyondTheDeliveryLimit() {
    // Plaza de Cervantes, Alcalá de Henares: some 25 km from the shop.
    Optional<GeocodedAddress> result = realClient().geocode("Plaza de Cervantes", "1", "28801");

    assertThat(result).isPresent();
    assertThat(result.get().distanceKm()).isGreaterThan(new BigDecimal("10"));
  }

  @Test
  void aNonExistentHouseNumberIsRefused() {
    assertThat(realClient().geocode("Paseo de la Chopera", "9999", "28045")).isEmpty();
  }

  @Test
  void aMisspelledStreetIsRefused() {
    assertThat(realClient().geocode("Paseo de la Chopra", "14", "28045")).isEmpty();
  }

  @Test
  void aWrongPostalCodeIsRefusedEvenThoughGoogleFindsTheBuildingUnderTheRightOne() {
    assertThat(realClient().geocode("Paseo de la Chopera", "14", "28001")).isEmpty();
  }

  @Test
  void aNonsenseAddressIsRefused() {
    assertThat(realClient().geocode("Zzqxjv Qwkjhg", "99999", "00000")).isEmpty();
  }

  @Test
  void aRejectedKeyIsUnavailableNotAFakeAddressAndIsLoggedAtWarnWithTheStatusOnlyAndNeverTheKey() {
    String badKey = "not-a-real-key-" + System.nanoTime();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    ((Logger) LoggerFactory.getLogger(GoogleMapsGeocodingClient.class)).addAppender(appender);
    GeocodingProperties properties =
        new GeocodingProperties(new GeocodingProperties.GoogleMaps(badKey, GEOCODE_URL), SHOP);

    assertThatThrownBy(
            () -> new GoogleMapsGeocodingClient(properties, new ObjectMapper()).geocode("Paseo de la Chopera", "14", "28045"))
        .isInstanceOf(GeocodingUnavailableException.class);

    assertThat(appender.list)
        .filteredOn(event -> event.getLevel() == Level.WARN)
        .singleElement()
        .satisfies(event -> assertThat(event.getFormattedMessage()).contains("REQUEST_DENIED"));
    assertThat(appender.list).noneMatch(event -> event.getFormattedMessage().contains(badKey));
  }

  private GoogleMapsGeocodingClient realClient() {
    GeocodingProperties properties =
        new GeocodingProperties(
            new GeocodingProperties.GoogleMaps(System.getenv("GOOGLE_MAPS_API_KEY"), GEOCODE_URL), SHOP);
    return new GoogleMapsGeocodingClient(properties, new ObjectMapper());
  }
}
