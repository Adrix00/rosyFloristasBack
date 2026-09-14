package com.floristeriarosy.infrastructure.geocoding;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Straight-line distance between two points on Earth (order.md, rule 3.7): "línea recta, cálculo en
 * Java al geocodificar" — Google Maps only supplies coordinates, this computes the distance.
 */
public final class Haversine {

  private static final double EARTH_RADIUS_KM = 6371.0;

  private Haversine() {}

  /**
   * @param lat1 first point's latitude
   * @param lon1 first point's longitude
   * @param lat2 second point's latitude
   * @param lon2 second point's longitude
   * @return the great-circle distance between the two points, in kilometers, rounded to 2 decimals
   */
  public static BigDecimal distanceKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
    double phi1 = Math.toRadians(lat1.doubleValue());
    double phi2 = Math.toRadians(lat2.doubleValue());
    double deltaPhi = Math.toRadians(lat2.subtract(lat1).doubleValue());
    double deltaLambda = Math.toRadians(lon2.subtract(lon1).doubleValue());
    double a =
        Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
            + Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return BigDecimal.valueOf(EARTH_RADIUS_KM * c).setScale(2, RoundingMode.HALF_UP);
  }
}
