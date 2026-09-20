package com.floristeriarosy.infrastructure.geocoding;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param googleMaps Google Maps Geocoding API configuration
 * @param store the store's own coordinates, used as the distance origin (order.md, rule 3.7)
 */
@ConfigurationProperties(prefix = "app.geocoding")
public record GeocodingProperties(GoogleMaps googleMaps, Store store) {

  /**
   * @param apiKey the Google Maps Geocoding API key — required, no safe default exists
   * @param geocodeUrl the Google Maps Geocoding API endpoint
   */
  public record GoogleMaps(String apiKey, String geocodeUrl) {

    /**
     * @return a description that never includes the API key
     */
    @Override
    public String toString() {
      return "GoogleMaps[geocodeUrl=" + geocodeUrl + ", apiKey=***]";
    }
  }

  /**
   * @param latitude the store's latitude
   * @param longitude the store's longitude
   */
  public record Store(BigDecimal latitude, BigDecimal longitude) {}
}
