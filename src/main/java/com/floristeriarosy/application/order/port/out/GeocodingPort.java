package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.application.order.dto.GeocodedAddress;
import java.util.Optional;

/** Turns a delivery address into coordinates and a distance from the store (order.md, rule 3.7). */
public interface GeocodingPort {

  /**
   * @param street the street name
   * @param number the street number
   * @param postalCode the postal code
   * @return the geocoded address, or empty if it could not be located
   */
  Optional<GeocodedAddress> geocode(String street, String number, String postalCode);
}
