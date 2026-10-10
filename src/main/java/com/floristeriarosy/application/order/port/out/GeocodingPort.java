package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.application.order.dto.GeocodedAddress;
import java.util.Optional;

/** Turns a delivery address into coordinates and a distance from the store (order.md, rule 3.7). */
public interface GeocodingPort {

  /**
   * @param street the street name
   * @param number the street number
   * @param postalCode the postal code
   * @return the geocoded address, or empty if the address is not real: not found, too imprecise, or
   *     its postal code does not match the one given
   * @throws com.floristeriarosy.domain.exception.order.GeocodingUnavailableException the address
   *     could not be checked at all (denied, over quota, timeout, unreadable answer); this is
   *     never reported as "empty", which would read as a fake address
   */
  Optional<GeocodedAddress> geocode(String street, String number, String postalCode);
}
