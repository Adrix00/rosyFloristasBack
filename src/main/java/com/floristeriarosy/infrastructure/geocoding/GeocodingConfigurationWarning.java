package com.floristeriarosy.infrastructure.geocoding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs once at startup when {@code GOOGLE_MAPS_API_KEY} is blank: every delivery address would then
 * be refused as unverifiable. The one signal an operator has, from the logs, that the environment
 * was never configured. Never logs the key.
 */
@Component
public class GeocodingConfigurationWarning {

  private static final Logger LOGGER = LoggerFactory.getLogger(GeocodingConfigurationWarning.class);

  /**
   * @param properties the geocoding configuration to check
   */
  public GeocodingConfigurationWarning(GeocodingProperties properties) {
    String apiKey = properties.googleMaps().apiKey();
    if (apiKey == null || apiKey.isBlank()) {
      LOGGER.warn(
          "GOOGLE_MAPS_API_KEY is blank — every delivery checkout will fail as unverifiable until it"
              + " is set");
    }
  }
}
