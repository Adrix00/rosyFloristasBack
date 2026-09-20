package com.floristeriarosy.infrastructure.geocoding;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs once at startup when the geocoding configuration would make delivery quietly wrong: a blank
 * {@code GOOGLE_MAPS_API_KEY} (every delivery address would be reported as not found) or store
 * coordinates equal to the old central-Madrid placeholder (every distance, and so every tariff,
 * would be measured from the wrong place). The one signal an operator has, from the logs, that the
 * environment was never configured. Never logs the key.
 */
@Component
public class GeocodingConfigurationWarning {

  private static final Logger LOGGER = LoggerFactory.getLogger(GeocodingConfigurationWarning.class);
  private static final BigDecimal PLACEHOLDER_LATITUDE = new BigDecimal("40.4168");
  private static final BigDecimal PLACEHOLDER_LONGITUDE = new BigDecimal("-3.7038");

  /**
   * @param properties the geocoding configuration to check
   */
  public GeocodingConfigurationWarning(GeocodingProperties properties) {
    String apiKey = properties.googleMaps().apiKey();
    if (apiKey == null || apiKey.isBlank()) {
      LOGGER.warn(
          "GOOGLE_MAPS_API_KEY is blank — every delivery address will be reported as not"
              + " geocodable until it is set");
    }
    if (isPlaceholder(properties.store())) {
      LOGGER.warn(
          "The store coordinates are the central-Madrid placeholder — set STORE_LATITUDE and"
              + " STORE_LONGITUDE, or every delivery distance and tariff will be wrong");
    }
  }

  /**
   * @param store the configured store coordinates
   * @return whether they equal the placeholder that used to be the default
   */
  private boolean isPlaceholder(GeocodingProperties.Store store) {
    return store.latitude().compareTo(PLACEHOLDER_LATITUDE) == 0
        && store.longitude().compareTo(PLACEHOLDER_LONGITUDE) == 0;
  }
}
