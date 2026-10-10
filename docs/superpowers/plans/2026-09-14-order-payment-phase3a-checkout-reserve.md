# Order + Payment — Phase 3a: Checkout Reservation and Redirect Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** `POST /checkout` works end to end up to the point of redirecting the customer to Redsys:
validates and prices the cart, geocodes and rates the delivery address, reserves stock and discount
(Fase 1, order.md §3.2), stores the pending-checkout payload encrypted in `idempotency_keys`
(ADR-017), and returns a signed Redsys redirect. `GET /checkout/{idempotencyKey}/status` reports
`PENDING`/`COMPLETED`/`FAILED`. Materializing the actual order on Redsys's notification is Phase
3b — nothing in this phase writes to `orders`.

**Architecture:** `CheckoutService` orchestrates existing capabilities (`ValidateCartUseCase`,
`GetCartUseCase` from `cart`; `RegisterStockMovementUseCase` from `inventory`;
`DiscountReservationPort` from `discount`; `OrderPaymentPort` from Phase 2) plus three new output
ports this phase introduces: `GeocodingPort`, `ShippingRatePort`, `CheckoutIdempotencyPort`. Follows
`AddCartItemService`'s shape (Phase 1's own precedent research): pre-transaction cheap checks first,
a private `doExecute` for the actual mutation sequence, comments explaining *why* steps are ordered
the way they are.

**Tech Stack:** `java.net.http.HttpClient` for the Google Maps Geocoding API call (JDK stdlib,
matches the Redsys adapter precedent — no new HTTP client dependency), `NamedParameterJdbcTemplate`
for the sequence read and the shipping-rate lookup (ADR-002: JDBC for simple reads), JPA for
`idempotency_keys` (ADR-002: JPA for insert/update).

**Spec:** `docs/features/order.md` (sections 3.1, 3.2, 3.3, 3.4, 3.5, 3.7, 4, 5, 6, 9),
`docs/features/cart.md`, `docs/architecture/ADR/ADR-011-idempotent-money-operations.md`,
`docs/architecture/ADR/ADR-017-card-payment-via-redsys-redirect.md`. Builds on Phase 1 (`Order`
domain) and Phase 2 (`OrderPaymentPort`), both merged to `feature/orderPayments`.

## Global Constraints

- No wildcard imports. Constructor injection only. Every method — public and private — gets
  Javadoc.
- Services log entry/exit at `DEBUG`; never log `paymentMethodToken`, buyer/delivery fields, or the
  Google Maps API key.
- **New output ports are scoped to what `order` needs today, not generalized speculatively**:
  `CheckoutIdempotencyPort` only serves `/checkout` (Phase 7 extracts a shared version only if
  `/orders/counter` or `/orders/{id}/refund` turn out to need the same shape — not before).
- `CheckoutRequest`'s cross-field rule ("exactly one of `paymentMethodToken`/`paymentMethodId`
  when `paymentMethod = CARD_ONLINE`") is business validation, not Bean Validation — checked in the
  service, not the DTO, matching this codebase's split between structural (`@Valid`) and business
  (service-thrown) validation.
- `Google Maps API key` (`app.geocoding.google-maps.api-key`) has **no checked-in default** — unlike
  the Redsys sandbox key, Google Maps has no published free test key; the property is required,
  startup fails fast if unset, matching `PiiCrypto`'s own precedent of only defaulting values that
  are genuinely safe to default.
- `PendingCheckoutPayload` (the JSON that gets encrypted into `idempotency_keys`) is a **new,
  order-scoped DTO** — not the same type as any DTO already used for cart/order responses; it exists
  only to survive the wait for Redsys and is never returned to a client.

---

## Task 1: Extend `CartItemDto` with `productAttributes` and `discountId`

**Files:**
- Modify: `src/main/java/com/floristeriarosy/application/cart/dto/CartItemDto.java`
- Modify: `src/main/java/com/floristeriarosy/application/cart/service/GetCartService.java`
- Modify: `src/test/java/com/floristeriarosy/application/cart/service/GetCartServiceTest.java`

**Interfaces:**
- Consumes: whatever `GetCartService` already reads from `ProductReadPort`/discount lookup to price
  a line (read the current `GetCartService` first — do not guess its exact collaborators).
- Produces: `CartItemDto` gains `productAttributes()` (`Map<String, Object>`) and `discountId()`
  (`UUID`, nullable) — Task 8's `CheckoutService` needs both to snapshot an `OrderItem` (order.md,
  rule 3.5); nothing else in `cart` currently reads them, so no other call site changes.

Read `GetCartService.java` and `CartItemDto.java` in full before editing — this task's exact diff
depends on how the service currently builds each line (it already resolves the product and any
active discount to compute `effectivePrice`/`onSale`; the two new fields piggyback on data it
already has in hand, they should not require a second query).

- [ ] **Step 1: Write the failing test**

Add to `GetCartServiceTest` (adapt to the file's actual existing fixture/mock setup — read it
first):

```java
  @Test
  void cartItemCarriesProductAttributesAndActiveDiscountId() {
    // Arrange a cart line for a product with attributes and an active discount, using this
    // test's existing fixtures/mocks.
    // Act: call the use case as the other tests in this file do.
    // Assert:
    // assertThat(item.productAttributes()).isEqualTo(<the product's attributes>);
    // assertThat(item.discountId()).isEqualTo(<the active discount's id>);
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=GetCartServiceTest`
Expected: compilation failure — `CartItemDto` has no `productAttributes()`/`discountId()` yet.

- [ ] **Step 3: Add the fields to `CartItemDto` and populate them in `GetCartService`**

Add `Map<String, Object> productAttributes` and `UUID discountId` to the record (after `onSale`,
before `quantity`, matching the existing field grouping of "priced product info" then "line info").
Populate `productAttributes` from the same `Product` the service already loaded to compute the
price; populate `discountId` from the same active-discount lookup the service already does to
compute `effectivePrice`/`onSale` — `null` when there is none.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=GetCartServiceTest`
Expected: all tests in the class pass, including the new one.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/floristeriarosy/application/cart/dto/CartItemDto.java \
        src/main/java/com/floristeriarosy/application/cart/service/GetCartService.java \
        src/test/java/com/floristeriarosy/application/cart/service/GetCartServiceTest.java
git commit -m "feat(cart): expose productAttributes/discountId on CartItemDto (order.md §3.5)"
```

---

## Task 2: `GeocodingPort` and a Google Maps adapter

**Files:**
- Create: `src/main/java/com/floristeriarosy/application/order/port/out/GeocodingPort.java`
- Create: `src/main/java/com/floristeriarosy/application/order/port/out/GeocodedAddress.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/geocoding/GeocodingProperties.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/geocoding/GoogleMapsGeocodingAdapter.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/geocoding/Haversine.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/floristeriarosy/infrastructure/geocoding/HaversineTest.java`

**Interfaces:**
- Consumes: nothing new beyond JDK/Jackson.
- Produces: `GeocodingPort.geocode(street, number, postalCode)` → `Optional<GeocodedAddress>`
  (empty when Google Maps cannot locate it — order.md, rule 3.7: `DELIVERY_ADDRESS_NOT_GEOCODABLE`).
  `GeocodedAddress(BigDecimal latitude, BigDecimal longitude, BigDecimal distanceKm)` —
  `distanceKm` is the straight-line distance from the store, computed locally via `Haversine`, not
  something Google returns. Task 8's `CheckoutService` calls this before Fase 1.

- [ ] **Step 1: Add configuration**

```yaml
app:
  geocoding:
    google-maps:
      api-key: ${GOOGLE_MAPS_API_KEY:}
      geocode-url: ${GOOGLE_MAPS_GEOCODE_URL:https://maps.googleapis.com/maps/api/geocode/json}
    store:
      latitude: ${STORE_LATITUDE:40.4168}
      longitude: ${STORE_LONGITUDE:-3.7038}
```

`STORE_LATITUDE`/`STORE_LONGITUDE` default to Madrid's Puerta del Sol as a placeholder — flag to
the user that this must be replaced with the shop's real coordinates before going live; it is not a
secret, so a wrong default fails loudly (wrong distances) rather than silently, unlike a missing API
key.

- [ ] **Step 2: Write the failing `Haversine` test**

```java
package com.floristeriarosy.infrastructure.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Straight-line distance (order.md, rule 3.7) — Google gives coordinates, this gives kilometers. */
class HaversineTest {

  @Test
  void distanceBetweenTheSamePointIsZero() {
    BigDecimal distance = Haversine.distanceKm(
        new BigDecimal("40.4168"), new BigDecimal("-3.7038"), new BigDecimal("40.4168"), new BigDecimal("-3.7038"));

    assertThat(distance).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void distanceBetweenMadridAndBarcelonaIsRoughlyFiveHundredKm() {
    BigDecimal distance = Haversine.distanceKm(
        new BigDecimal("40.4168"), new BigDecimal("-3.7038"), new BigDecimal("41.3874"), new BigDecimal("2.1686"));

    assertThat(distance.doubleValue()).isCloseTo(505.0, within(15.0));
  }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q test -Dtest=HaversineTest`
Expected: compilation failure — `Haversine` does not exist yet.

- [ ] **Step 4: Implement `Haversine`**

```java
package com.floristeriarosy.infrastructure.geocoding;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Straight-line distance between two points on Earth (order.md, rule 3.7): "línea recta, cálculo
 * en Java al geocodificar" — Google Maps only supplies coordinates, this computes the distance.
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
    double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
        + Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return BigDecimal.valueOf(EARTH_RADIUS_KM * c).setScale(2, RoundingMode.HALF_UP);
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q test -Dtest=HaversineTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 6: Create `GeocodedAddress` and `GeocodingPort`**

```java
package com.floristeriarosy.application.order.port.out;

import java.math.BigDecimal;

/**
 * @param latitude the geocoded latitude
 * @param longitude the geocoded longitude
 * @param distanceKm the straight-line distance from the store (order.md, rule 3.7)
 */
public record GeocodedAddress(BigDecimal latitude, BigDecimal longitude, BigDecimal distanceKm) {}
```

```java
package com.floristeriarosy.application.order.port.out;

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
```

- [ ] **Step 7: Create `GeocodingProperties`**

```java
package com.floristeriarosy.infrastructure.geocoding;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apiKey the Google Maps Geocoding API key — required, no safe default exists
 * @param geocodeUrl the Google Maps Geocoding API endpoint
 * @param storeLatitude the store's own latitude, the distance origin (order.md, rule 3.7)
 * @param storeLongitude the store's own longitude
 */
@ConfigurationProperties(prefix = "app.geocoding")
public record GeocodingProperties(
    GoogleMaps googleMaps, Store store) {

  /**
   * @param apiKey the Google Maps Geocoding API key
   * @param geocodeUrl the Google Maps Geocoding API endpoint
   */
  public record GoogleMaps(String apiKey, String geocodeUrl) {}

  /**
   * @param latitude the store's latitude
   * @param longitude the store's longitude
   */
  public record Store(BigDecimal latitude, BigDecimal longitude) {}
}
```

Register it the same way Phase 2's `RedsysProperties` was registered — check what that task ended
up doing (`@EnableConfigurationProperties` on the main class, or component-scanned) and follow suit
rather than introducing a second registration mechanism.

- [ ] **Step 8: Implement `GoogleMapsGeocodingAdapter`**

```java
package com.floristeriarosy.infrastructure.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floristeriarosy.application.order.port.out.GeocodedAddress;
import com.floristeriarosy.application.order.port.out.GeocodingPort;
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

/** Implements {@link GeocodingPort} against the Google Maps Geocoding API (order.md, rule 3.7). */
@Component
public class GoogleMapsGeocodingAdapter implements GeocodingPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleMapsGeocodingAdapter.class);

  private final GeocodingProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  /**
   * @param properties Google Maps configuration and the store's own coordinates
   * @param objectMapper parses the Google Maps JSON response
   */
  public GoogleMapsGeocodingAdapter(GeocodingProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  /**
   * @param street the street name
   * @param number the street number
   * @param postalCode the postal code
   * @return the geocoded address with its distance from the store, or empty if Google Maps could
   *     not locate it
   */
  @Override
  public Optional<GeocodedAddress> geocode(String street, String number, String postalCode) {
    LOGGER.debug("geocode postalCode={}", postalCode);
    String address = street + " " + number + ", " + postalCode + ", Spain";
    String url = properties.googleMaps().geocodeUrl()
        + "?address=" + URLEncoder.encode(address, StandardCharsets.UTF_8)
        + "&key=" + properties.googleMaps().apiKey();
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      Optional<GeocodedAddress> result = parseResponse(response.body());
      LOGGER.debug("geocode postalCode={} -> found={}", postalCode, result.isPresent());
      return result;
    } catch (java.io.IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOGGER.debug("geocode postalCode={} -> call failed: {}", postalCode, e.getMessage());
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
      if (!"OK".equals(root.path("status").asText()) || root.path("results").isEmpty()) {
        return Optional.empty();
      }
      JsonNode location = root.path("results").get(0).path("geometry").path("location");
      BigDecimal latitude = BigDecimal.valueOf(location.path("lat").asDouble());
      BigDecimal longitude = BigDecimal.valueOf(location.path("lng").asDouble());
      BigDecimal distanceKm =
          Haversine.distanceKm(properties.store().latitude(), properties.store().longitude(), latitude, longitude);
      return Optional.of(new GeocodedAddress(latitude, longitude, distanceKm));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      LOGGER.debug("geocode -> could not parse Google Maps response: {}", e.getMessage());
      return Optional.empty();
    }
  }
}
```

- [ ] **Step 9: Compile and commit**

Run: `mvn -q compile`

```bash
git add src/main/java/com/floristeriarosy/application/order/port/out/GeocodingPort.java \
        src/main/java/com/floristeriarosy/application/order/port/out/GeocodedAddress.java \
        src/main/java/com/floristeriarosy/infrastructure/geocoding \
        src/test/java/com/floristeriarosy/infrastructure/geocoding \
        src/main/resources/application.yml
git commit -m "feat(order): GeocodingPort + Google Maps adapter (order.md §3.7)"
```

---

## Task 3: `ShippingRatePort`

**Files:**
- Create: `src/main/java/com/floristeriarosy/application/order/port/out/ShippingRatePort.java`
- Create: `src/main/java/com/floristeriarosy/application/order/port/out/ShippingRate.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/jdbc/order/repository/ShippingRateJdbcRepository.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/jdbc/order/rowmapper/ShippingRateRowMapper.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/order/ShippingRatePersistenceAdapter.java`
- Test: `src/test/java/com/floristeriarosy/infrastructure/persistence/adapter/order/ShippingRatePersistenceAdapterTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `ShippingRatePort.findRateForDistance(BigDecimal distanceKm)` →
  `Optional<ShippingRate>` — Task 8's `CheckoutService` calls this after geocoding, before Fase 1.
  `ShippingRate(BigDecimal price, BigDecimal freeFromAmount)`.

Read `DiscountJdbcRepository`/`DiscountRowMapper` first (Phase 1's own precedent research already
found these) and match their `NamedParameterJdbcTemplate` style exactly instead of inventing a new
one.

- [ ] **Step 1: Write the failing test**

```java
package com.floristeriarosy.infrastructure.persistence.adapter.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.application.order.port.out.ShippingRate;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** {@code shipping_rates} already has seed data (order.md §2) — no fixture needed. */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ShippingRatePersistenceAdapterTest {

  @Autowired private ShippingRatePersistenceAdapter adapter;

  @Test
  void findsTheBandForAShortDistance() {
    var rate = adapter.findRateForDistance(new BigDecimal("0.5"));

    assertThat(rate).isPresent();
  }

  @Test
  void findsNoBandBeyondTenKilometers() {
    var rate = adapter.findRateForDistance(new BigDecimal("15.0"));

    assertThat(rate).isEmpty();
  }
}
```

Match this project's actual `@SpringBootTest`/Testcontainers fixture (check
`AdminPersistenceAdapterTest` or similar for the real annotations, same note as Phase 2's Task 7).

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=ShippingRatePersistenceAdapterTest`
Expected: compilation failure.

- [ ] **Step 3: Create `ShippingRate` and `ShippingRatePort`**

```java
package com.floristeriarosy.application.order.port.out;

import java.math.BigDecimal;

/**
 * @param price the shipping cost for this distance band
 * @param freeFromAmount the subtotal at or above which shipping is free, or {@code null}
 */
public record ShippingRate(BigDecimal price, BigDecimal freeFromAmount) {}
```

```java
package com.floristeriarosy.application.order.port.out;

import java.math.BigDecimal;
import java.util.Optional;

/** Reads {@code shipping_rates} (order.md, rule 3.7). */
public interface ShippingRatePort {

  /**
   * @param distanceKm the delivery address's distance from the store
   * @return the applicable rate, or empty if the distance falls outside every active band
   */
  Optional<ShippingRate> findRateForDistance(BigDecimal distanceKm);
}
```

- [ ] **Step 4: Create the JDBC repository and row mapper**

```java
package com.floristeriarosy.infrastructure.persistence.jdbc.order.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.floristeriarosy.infrastructure.persistence.jdbc.order.rowmapper.ShippingRateRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Raw JDBC read of {@code shipping_rates} (ADR-002: simple filtered read). */
@Repository
public class ShippingRateJdbcRepository {

  private static final String FIND_FOR_DISTANCE =
      """
      SELECT price, free_from_amount FROM shipping_rates
      WHERE active AND :distance >= min_km AND :distance < max_km
      """;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  /**
   * @param jdbcTemplate runs the parameterized query
   */
  public ShippingRateJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @param distanceKm the distance to match against the active bands
   * @return the matching row, at most one (bands never overlap, {@code
   *     ex_shipping_rates_no_overlap})
   */
  public List<Map<String, Object>> findForDistance(BigDecimal distanceKm) {
    return jdbcTemplate.queryForList(FIND_FOR_DISTANCE, new MapSqlParameterSource("distance", distanceKm));
  }
}
```

Replace the raw `List<Map<String, Object>>` return with the project's actual established row-mapper
usage pattern — read `DiscountJdbcRepository` first and mirror exactly how it wires
`RowMapper<T>` into `NamedParameterJdbcTemplate.query(...)` rather than returning raw maps; the
snippet above is illustrative of the SQL only, not the final Java shape.

```java
package com.floristeriarosy.infrastructure.persistence.jdbc.order.rowmapper;

import com.floristeriarosy.application.order.port.out.ShippingRate;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

/** Maps one {@code shipping_rates} row to {@link ShippingRate}. */
public class ShippingRateRowMapper implements RowMapper<ShippingRate> {

  /**
   * @param rs the current row
   * @param rowNum the row number, unused
   * @return the mapped rate
   * @throws SQLException a column could not be read
   */
  @Override
  public ShippingRate mapRow(ResultSet rs, int rowNum) throws SQLException {
    BigDecimal freeFromAmount = rs.getBigDecimal("free_from_amount");
    return new ShippingRate(rs.getBigDecimal("price"), rs.wasNull() ? null : freeFromAmount);
  }
}
```

- [ ] **Step 5: Implement `ShippingRatePersistenceAdapter`**

```java
package com.floristeriarosy.infrastructure.persistence.adapter.order;

import com.floristeriarosy.application.order.port.out.ShippingRate;
import com.floristeriarosy.application.order.port.out.ShippingRatePort;
import com.floristeriarosy.infrastructure.persistence.jdbc.order.repository.ShippingRateJdbcRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** Implements {@link ShippingRatePort} over JDBC (ADR-002). */
@Repository
public class ShippingRatePersistenceAdapter implements ShippingRatePort {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShippingRatePersistenceAdapter.class);

  private final ShippingRateJdbcRepository jdbcRepository;

  /**
   * @param jdbcRepository runs the range query
   */
  public ShippingRatePersistenceAdapter(ShippingRateJdbcRepository jdbcRepository) {
    this.jdbcRepository = jdbcRepository;
  }

  /**
   * @param distanceKm the delivery address's distance from the store
   * @return the applicable rate, or empty if none matches
   */
  @Override
  public Optional<ShippingRate> findRateForDistance(BigDecimal distanceKm) {
    LOGGER.debug("findRateForDistance distanceKm={}", distanceKm);
    Optional<ShippingRate> result = jdbcRepository.findForDistance(distanceKm).stream().findFirst()
        .map(row -> new ShippingRate((BigDecimal) row.get("price"), (BigDecimal) row.get("free_from_amount")));
    LOGGER.debug("findRateForDistance distanceKm={} -> found={}", distanceKm, result.isPresent());
    return result;
  }
}
```

Adjust once Step 4's row-mapper wiring is finalized against the project's real pattern — this
adapter should call `jdbcRepository.findForDistance(distanceKm)` returning `List<ShippingRate>`
directly (via the `RowMapper`), not a raw map, once Step 4 is corrected.

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q test -Dtest=ShippingRatePersistenceAdapterTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/floristeriarosy/application/order/port/out/ShippingRatePort.java \
        src/main/java/com/floristeriarosy/application/order/port/out/ShippingRate.java \
        src/main/java/com/floristeriarosy/infrastructure/persistence/jdbc/order \
        src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/order/ShippingRatePersistenceAdapter.java \
        src/test/java/com/floristeriarosy/infrastructure/persistence/adapter/order/ShippingRatePersistenceAdapterTest.java
git commit -m "feat(order): ShippingRatePort over JDBC (order.md §3.7)"
```

---

## Task 4: `OrderNumberPort`

**Files:**
- Create: `src/main/java/com/floristeriarosy/application/order/port/out/OrderNumberPort.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/jdbc/order/repository/OrderNumberJdbcRepository.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/order/OrderNumberPersistenceAdapter.java`
- Test: `src/test/java/com/floristeriarosy/infrastructure/persistence/adapter/order/OrderNumberPersistenceAdapterTest.java`

**Interfaces:**
- Consumes: `OrderChannel` (Phase 1).
- Produces: `OrderNumberPort.next(OrderChannel channel)` → `OrderNumber` — Task 8's
  `CheckoutService` calls this once per checkout attempt (order.md, rule 3.1: `{CHANNEL}-{YYYYMMDD}
  -{6-digit sequence}`, gaps tolerated for abandoned attempts, ADR-017).

- [ ] **Step 1: Write the failing test**

```java
package com.floristeriarosy.infrastructure.persistence.adapter.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.order.OrderChannel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class OrderNumberPersistenceAdapterTest {

  @Autowired private OrderNumberPersistenceAdapter adapter;

  @Test
  void nextProducesAFormattedIncreasingNumber() {
    var first = adapter.next(OrderChannel.WEB);
    var second = adapter.next(OrderChannel.WEB);

    assertThat(first.value()).matches("WEB-\\d{8}-\\d{6}");
    assertThat(second.value()).isNotEqualTo(first.value());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=OrderNumberPersistenceAdapterTest`
Expected: compilation failure.

- [ ] **Step 3: Create `OrderNumberPort`**

```java
package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.domain.model.order.OrderChannel;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;

/** Mints the next business identifier from {@code order_number_seq} (order.md, rule 3.1). */
public interface OrderNumberPort {

  /**
   * @param channel the sales channel, prefixes the formatted number
   * @return the next order number
   */
  OrderNumber next(OrderChannel channel);
}
```

- [ ] **Step 4: Implement the JDBC repository and adapter**

```java
package com.floristeriarosy.infrastructure.persistence.jdbc.order.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads {@code order_number_seq} directly (ADR-002: a sequence is not a JPA concern). */
@Repository
public class OrderNumberJdbcRepository {

  private final JdbcTemplate jdbcTemplate;

  /**
   * @param jdbcTemplate runs the sequence read
   */
  public OrderNumberJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @return the next value of {@code order_number_seq}, atomic under concurrency
   */
  public long nextSequenceValue() {
    return jdbcTemplate.queryForObject("SELECT nextval('order_number_seq')", Long.class);
  }
}
```

```java
package com.floristeriarosy.infrastructure.persistence.adapter.order;

import com.floristeriarosy.application.order.port.out.OrderNumberPort;
import com.floristeriarosy.domain.model.order.OrderChannel;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.infrastructure.persistence.jdbc.order.repository.OrderNumberJdbcRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** Implements {@link OrderNumberPort} (order.md, rule 3.1). */
@Repository
public class OrderNumberPersistenceAdapter implements OrderNumberPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderNumberPersistenceAdapter.class);
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final OrderNumberJdbcRepository jdbcRepository;

  /**
   * @param jdbcRepository reads the sequence
   */
  public OrderNumberPersistenceAdapter(OrderNumberJdbcRepository jdbcRepository) {
    this.jdbcRepository = jdbcRepository;
  }

  /**
   * @param channel the sales channel
   * @return the next order number, e.g. {@code WEB-20260913-000001}
   */
  @Override
  public OrderNumber next(OrderChannel channel) {
    LOGGER.debug("next channel={}", channel);
    long sequence = jdbcRepository.nextSequenceValue();
    OrderNumber result =
        OrderNumber.of(channel + "-" + LocalDate.now().format(DATE_FORMAT) + "-" + String.format("%06d", sequence));
    LOGGER.debug("next channel={} -> {}", channel, result);
    return result;
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q test -Dtest=OrderNumberPersistenceAdapterTest`
Expected: `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/floristeriarosy/application/order/port/out/OrderNumberPort.java \
        src/main/java/com/floristeriarosy/infrastructure/persistence/jdbc/order/repository/OrderNumberJdbcRepository.java \
        src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/order/OrderNumberPersistenceAdapter.java \
        src/test/java/com/floristeriarosy/infrastructure/persistence/adapter/order/OrderNumberPersistenceAdapterTest.java
git commit -m "feat(order): OrderNumberPort (order.md §3.1)"
```

---

## Task 5: `CheckoutIdempotencyPort`

**Files:**
- Create: `src/main/java/com/floristeriarosy/application/order/port/out/CheckoutIdempotencyPort.java`
- Create: `src/main/java/com/floristeriarosy/application/order/port/out/IdempotencyLock.java`
- Create: `src/main/java/com/floristeriarosy/application/order/dto/PendingCheckoutPayload.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/entity/order/IdempotencyKeyEntity.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/jpa/order/repository/IdempotencyKeyJpaRepository.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/order/CheckoutIdempotencyPersistenceAdapter.java`
- Create: `src/main/java/com/floristeriarosy/domain/exception/order/IdempotencyOperationInProgressException.java`
- Test: `src/test/java/com/floristeriarosy/infrastructure/persistence/adapter/order/CheckoutIdempotencyPersistenceAdapterTest.java`

**Interfaces:**
- Consumes: `PiiCryptoPort` (encrypts the payload), `OrderId` (Phase 1).
- Produces: `CheckoutIdempotencyPort.find(idempotencyKey)` → `Optional<IdempotencyLock>`,
  `.insertPending(idempotencyKey, requestFingerprint, resourceId, checkoutPayloadEncrypted,
  expiresAt)`, `.resetToPending(...)` (same signature, for retry-after-`FAILED`),
  `.markCompleted(idempotencyKey, responseStatus)`, `.markFailed(idempotencyKey)`. Task 8's
  `CheckoutService` calls `find`/`insertPending`/`resetToPending`; Phase 3b's
  `ConfirmCheckoutService` calls `markCompleted`/`markFailed` — declared together now since they are
  one lock's lifecycle, implemented together now so the adapter is never half-finished.

Scoped to `/checkout` only (`endpoint = "POST /checkout"` hardcoded in the adapter) — this is not
yet the general ADR-011 mechanism every money endpoint will use; see Global Constraints.

- [ ] **Step 1: Create `IdempotencyLock` and `PendingCheckoutPayload`**

```java
package com.floristeriarosy.application.order.port.out;

import java.util.UUID;

/**
 * @param status {@code PENDING}, {@code COMPLETED} or {@code FAILED} (ADR-011)
 * @param requestFingerprint the stored fingerprint, to compare against a retry's
 * @param resourceId the order id this key resolves to, once known
 */
public record IdempotencyLock(String status, byte[] requestFingerprint, UUID resourceId) {}
```

```java
package com.floristeriarosy.application.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything {@code ConfirmCheckoutService} (Phase 3b) needs to materialize the order once Redsys
 * confirms — serialized to JSON, then encrypted as one blob into {@code
 * idempotency_keys.checkout_payload_encrypted} (ADR-017). Never returned to a client.
 *
 * @param channel the sales channel
 * @param fulfillment how the order reaches the customer
 * @param customerId the linked customer, or {@code null} for an unlinked guest checkout
 * @param buyerFirstName the buyer's first name
 * @param buyerLastName the buyer's last name
 * @param buyerEmail the buyer's email
 * @param buyerPhone the buyer's phone
 * @param items the priced, snapshotted lines to sell
 * @param delivery the delivery slot, or {@code null} for {@code PICKUP}/{@code NONE}
 * @param pickup the pickup slot, or {@code null} for {@code DELIVERY}/{@code NONE}
 * @param subtotal the sum of every line's total
 * @param shippingCost the computed shipping cost
 */
public record PendingCheckoutPayload(
    String channel,
    String fulfillment,
    UUID customerId,
    String buyerFirstName,
    String buyerLastName,
    String buyerEmail,
    String buyerPhone,
    List<Item> items,
    Delivery delivery,
    Pickup pickup,
    BigDecimal subtotal,
    BigDecimal shippingCost) {

  /**
   * @param productId the sold product
   * @param productName the product's name at the moment of sale
   * @param productAttributes the product's attribute values at the moment of sale
   * @param unitPrice the base price at the moment of sale
   * @param discountPrice the discounted price, or {@code null}
   * @param discountId the applied promotion, or {@code null}
   * @param quantity units sold
   */
  public record Item(
      UUID productId,
      String productName,
      Map<String, Object> productAttributes,
      BigDecimal unitPrice,
      BigDecimal discountPrice,
      UUID discountId,
      int quantity) {}

  /**
   * @param recipientName the recipient's name
   * @param recipientPhone the recipient's phone
   * @param street the street
   * @param number the street number
   * @param detail the address detail, or {@code null}
   * @param postalCode the postal code
   * @param latitude the geocoded latitude
   * @param longitude the geocoded longitude
   * @param distanceKm the distance from the store
   * @param deliveryDate the requested date
   * @param slotFrom the slot's start
   * @param slotTo the slot's end
   * @param cardMessage the card message, or {@code null}
   */
  public record Delivery(
      String recipientName,
      String recipientPhone,
      String street,
      String number,
      String detail,
      String postalCode,
      BigDecimal latitude,
      BigDecimal longitude,
      BigDecimal distanceKm,
      LocalDate deliveryDate,
      LocalTime slotFrom,
      LocalTime slotTo,
      String cardMessage) {}

  /**
   * @param deliveryDate the requested date
   * @param slotFrom the slot's start
   * @param slotTo the slot's end
   */
  public record Pickup(LocalDate deliveryDate, LocalTime slotFrom, LocalTime slotTo) {}
}
```

- [ ] **Step 2: Create `CheckoutIdempotencyPort` and `IdempotencyOperationInProgressException`**

```java
package com.floristeriarosy.application.order.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code POST /checkout} idempotency lock (ADR-011, ADR-017): holds the Fase-1 reservation's
 * outcome — and, while {@code PENDING}, the encrypted payload Fase 3a/3b needs — for the whole
 * window a customer spends paying at Redsys.
 */
public interface CheckoutIdempotencyPort {

  /**
   * @param idempotencyKey the client-supplied key
   * @return the current lock, if a row exists for this key on {@code POST /checkout}
   */
  Optional<IdempotencyLock> find(String idempotencyKey);

  /**
   * @param idempotencyKey the client-supplied key
   * @param requestFingerprint the SHA-256 of body+cart (ADR-011, payment.md §3.8)
   * @param resourceId the order id minted for this attempt
   * @param checkoutPayloadEncrypted the encrypted {@link com.floristeriarosy.application.order.dto.PendingCheckoutPayload}
   * @param expiresAt when this reservation is abandoned if unconfirmed (ADR-017: 10 minutes)
   * @throws com.floristeriarosy.domain.exception.order.IdempotencyOperationInProgressException a
   *     concurrent request already holds this key
   */
  void insertPending(
      String idempotencyKey, byte[] requestFingerprint, UUID resourceId, byte[] checkoutPayloadEncrypted, Instant expiresAt);

  /**
   * Re-arms a {@code FAILED} row for a fresh attempt with the same key and fingerprint (order.md,
   * §10 edge case: retry after a payment decline restarts from Fase 1).
   *
   * @param idempotencyKey the client-supplied key
   * @param requestFingerprint the (unchanged) fingerprint
   * @param resourceId the newly minted order id for this attempt
   * @param checkoutPayloadEncrypted the newly encrypted payload
   * @param expiresAt the new expiry
   */
  void resetToPending(
      String idempotencyKey, byte[] requestFingerprint, UUID resourceId, byte[] checkoutPayloadEncrypted, Instant expiresAt);

  /**
   * @param idempotencyKey the key to resolve
   * @param responseStatus the HTTP status the original request resolved to
   */
  void markCompleted(String idempotencyKey, int responseStatus);

  /**
   * @param idempotencyKey the key to resolve
   */
  void markFailed(String idempotencyKey);
}
```

```java
package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.ConflictException;

/** A concurrent request already holds this {@code Idempotency-Key} (ADR-011). */
public final class IdempotencyOperationInProgressException extends ConflictException {

  /**
   * @param message what the caller should see
   */
  public IdempotencyOperationInProgressException(String message) {
    super(message);
  }
}
```

- [ ] **Step 3: Write the failing adapter test**

```java
package com.floristeriarosy.infrastructure.persistence.adapter.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.floristeriarosy.domain.exception.order.IdempotencyOperationInProgressException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class CheckoutIdempotencyPersistenceAdapterTest {

  @Autowired private CheckoutIdempotencyPersistenceAdapter adapter;

  private static final byte[] FINGERPRINT = "fp".getBytes(StandardCharsets.UTF_8);
  private static final byte[] PAYLOAD = "encrypted-payload".getBytes(StandardCharsets.UTF_8);

  @Test
  void insertPendingThenFindReturnsTheLock() {
    String key = UUID.randomUUID().toString();
    UUID resourceId = UUID.randomUUID();

    adapter.insertPending(key, FINGERPRINT, resourceId, PAYLOAD, Instant.now().plusSeconds(600));

    var lock = adapter.find(key).orElseThrow();
    assertThat(lock.status()).isEqualTo("PENDING");
    assertThat(lock.resourceId()).isEqualTo(resourceId);
  }

  @Test
  void insertPendingTwiceWithTheSameKeyThrows() {
    String key = UUID.randomUUID().toString();
    adapter.insertPending(key, FINGERPRINT, UUID.randomUUID(), PAYLOAD, Instant.now().plusSeconds(600));

    assertThatExceptionOfType(IdempotencyOperationInProgressException.class)
        .isThrownBy(() -> adapter.insertPending(key, FINGERPRINT, UUID.randomUUID(), PAYLOAD, Instant.now()));
  }

  @Test
  void markCompletedUpdatesTheStatus() {
    String key = UUID.randomUUID().toString();
    adapter.insertPending(key, FINGERPRINT, UUID.randomUUID(), PAYLOAD, Instant.now().plusSeconds(600));

    adapter.markCompleted(key, 202);

    assertThat(adapter.find(key).orElseThrow().status()).isEqualTo("COMPLETED");
  }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn -q test -Dtest=CheckoutIdempotencyPersistenceAdapterTest`
Expected: compilation failure.

- [ ] **Step 5: Create `IdempotencyKeyEntity` and its JPA repository**

```java
package com.floristeriarosy.infrastructure.persistence.entity.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping of {@code idempotency_keys} (ADR-011), the columns Phase 3a/3b use. */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

  @Id private UUID id;

  @Column(name = "idempotency_key", nullable = false, length = 255)
  private String idempotencyKey;

  @Column(nullable = false, length = 120)
  private String endpoint;

  @Column(name = "request_fingerprint", nullable = false)
  private byte[] requestFingerprint;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "response_status")
  private Integer responseStatus;

  @Column(name = "resource_id")
  private UUID resourceId;

  @Column(name = "checkout_payload_encrypted")
  private byte[] checkoutPayloadEncrypted;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /** Required by JPA; not for application use. */
  protected IdempotencyKeyEntity() {}

  /**
   * @param id the primary key
   * @param idempotencyKey the client-supplied key
   * @param endpoint which endpoint this lock guards
   * @param requestFingerprint the SHA-256 of body+cart
   * @param status {@code PENDING}, {@code COMPLETED} or {@code FAILED}
   * @param responseStatus the resolved HTTP status, or {@code null} until resolved
   * @param resourceId the order id this key resolves to
   * @param checkoutPayloadEncrypted the encrypted pending-checkout payload, or {@code null} once
   *     resolved
   * @param createdAt when the row was created
   * @param expiresAt when this reservation is abandoned if unconfirmed
   */
  public IdempotencyKeyEntity(
      UUID id,
      String idempotencyKey,
      String endpoint,
      byte[] requestFingerprint,
      String status,
      Integer responseStatus,
      UUID resourceId,
      byte[] checkoutPayloadEncrypted,
      Instant createdAt,
      Instant expiresAt) {
    this.id = id;
    this.idempotencyKey = idempotencyKey;
    this.endpoint = endpoint;
    this.requestFingerprint = requestFingerprint;
    this.status = status;
    this.responseStatus = responseStatus;
    this.resourceId = resourceId;
    this.checkoutPayloadEncrypted = checkoutPayloadEncrypted;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  /** @return the primary key */
  public UUID getId() {
    return id;
  }

  /** @return the client-supplied key */
  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  /** @return which endpoint this lock guards */
  public String getEndpoint() {
    return endpoint;
  }

  /** @return the stored request fingerprint */
  public byte[] getRequestFingerprint() {
    return requestFingerprint;
  }

  /** @return the current status */
  public String getStatus() {
    return status;
  }

  /** @param status the new status */
  public void setStatus(String status) {
    this.status = status;
  }

  /** @return the resolved HTTP status, or {@code null} */
  public Integer getResponseStatus() {
    return responseStatus;
  }

  /** @param responseStatus the resolved HTTP status */
  public void setResponseStatus(Integer responseStatus) {
    this.responseStatus = responseStatus;
  }

  /** @return the order id this key resolves to */
  public UUID getResourceId() {
    return resourceId;
  }

  /** @param resourceId the order id this key resolves to */
  public void setResourceId(UUID resourceId) {
    this.resourceId = resourceId;
  }

  /** @return the encrypted pending-checkout payload, or {@code null} */
  public byte[] getCheckoutPayloadEncrypted() {
    return checkoutPayloadEncrypted;
  }

  /** @param checkoutPayloadEncrypted the encrypted payload, or {@code null} to clear it */
  public void setCheckoutPayloadEncrypted(byte[] checkoutPayloadEncrypted) {
    this.checkoutPayloadEncrypted = checkoutPayloadEncrypted;
  }

  /** @param requestFingerprint the new fingerprint (retry after {@code FAILED}) */
  public void setRequestFingerprint(byte[] requestFingerprint) {
    this.requestFingerprint = requestFingerprint;
  }

  /** @return when the row was created */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** @return when this reservation is abandoned if unconfirmed */
  public Instant getExpiresAt() {
    return expiresAt;
  }

  /** @param expiresAt the new expiry (retry after {@code FAILED}) */
  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }
}
```

```java
package com.floristeriarosy.infrastructure.persistence.jpa.order.repository;

import com.floristeriarosy.infrastructure.persistence.entity.order.IdempotencyKeyEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@code idempotency_keys} (ADR-011). */
public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

  /**
   * @param idempotencyKey the client-supplied key
   * @param endpoint the endpoint this lock guards
   * @return the row, if one exists for this key on this endpoint
   */
  Optional<IdempotencyKeyEntity> findByIdempotencyKeyAndEndpoint(String idempotencyKey, String endpoint);
}
```

- [ ] **Step 6: Implement `CheckoutIdempotencyPersistenceAdapter`**

```java
package com.floristeriarosy.infrastructure.persistence.adapter.order;

import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.port.out.IdempotencyLock;
import com.floristeriarosy.domain.exception.order.IdempotencyOperationInProgressException;
import com.floristeriarosy.infrastructure.persistence.entity.order.IdempotencyKeyEntity;
import com.floristeriarosy.infrastructure.persistence.jpa.order.repository.IdempotencyKeyJpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** Implements {@link CheckoutIdempotencyPort} over JPA, scoped to {@code POST /checkout} (ADR-011, ADR-017). */
@Repository
public class CheckoutIdempotencyPersistenceAdapter implements CheckoutIdempotencyPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(CheckoutIdempotencyPersistenceAdapter.class);
  private static final String ENDPOINT = "POST /checkout";

  private final IdempotencyKeyJpaRepository jpaRepository;

  /**
   * @param jpaRepository reads and writes {@code idempotency_keys}
   */
  public CheckoutIdempotencyPersistenceAdapter(IdempotencyKeyJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  /**
   * @param idempotencyKey the client-supplied key
   * @return the current lock, if any
   */
  @Override
  public Optional<IdempotencyLock> find(String idempotencyKey) {
    LOGGER.debug("find idempotencyKey={}", idempotencyKey);
    return jpaRepository
        .findByIdempotencyKeyAndEndpoint(idempotencyKey, ENDPOINT)
        .map(entity -> new IdempotencyLock(entity.getStatus(), entity.getRequestFingerprint(), entity.getResourceId()));
  }

  /**
   * @param idempotencyKey the client-supplied key
   * @param requestFingerprint the SHA-256 of body+cart
   * @param resourceId the order id minted for this attempt
   * @param checkoutPayloadEncrypted the encrypted pending-checkout payload
   * @param expiresAt when this reservation is abandoned if unconfirmed
   * @throws IdempotencyOperationInProgressException a concurrent request already holds this key
   */
  @Override
  public void insertPending(
      String idempotencyKey, byte[] requestFingerprint, UUID resourceId, byte[] checkoutPayloadEncrypted, Instant expiresAt) {
    LOGGER.debug("insertPending idempotencyKey={} resourceId={}", idempotencyKey, resourceId);
    IdempotencyKeyEntity entity =
        new IdempotencyKeyEntity(
            UUID.randomUUID(), idempotencyKey, ENDPOINT, requestFingerprint, "PENDING", null, resourceId,
            checkoutPayloadEncrypted, Instant.now(), expiresAt);
    try {
      jpaRepository.saveAndFlush(entity);
    } catch (DataIntegrityViolationException violation) {
      throw new IdempotencyOperationInProgressException("Idempotency-Key " + idempotencyKey + " is already in use");
    }
    LOGGER.debug("insertPending idempotencyKey={} -> inserted", idempotencyKey);
  }

  /**
   * @param idempotencyKey the client-supplied key
   * @param requestFingerprint the (unchanged) fingerprint
   * @param resourceId the newly minted order id for this attempt
   * @param checkoutPayloadEncrypted the newly encrypted payload
   * @param expiresAt the new expiry
   */
  @Override
  public void resetToPending(
      String idempotencyKey, byte[] requestFingerprint, UUID resourceId, byte[] checkoutPayloadEncrypted, Instant expiresAt) {
    LOGGER.debug("resetToPending idempotencyKey={} resourceId={}", idempotencyKey, resourceId);
    IdempotencyKeyEntity entity =
        jpaRepository
            .findByIdempotencyKeyAndEndpoint(idempotencyKey, ENDPOINT)
            .orElseThrow(() -> new IllegalStateException("No row to reset for " + idempotencyKey));
    entity.setStatus("PENDING");
    entity.setRequestFingerprint(requestFingerprint);
    entity.setResourceId(resourceId);
    entity.setCheckoutPayloadEncrypted(checkoutPayloadEncrypted);
    entity.setResponseStatus(null);
    entity.setExpiresAt(expiresAt);
    jpaRepository.saveAndFlush(entity);
    LOGGER.debug("resetToPending idempotencyKey={} -> reset", idempotencyKey);
  }

  /**
   * @param idempotencyKey the key to resolve
   * @param responseStatus the HTTP status the original request resolved to
   */
  @Override
  public void markCompleted(String idempotencyKey, int responseStatus) {
    LOGGER.debug("markCompleted idempotencyKey={} responseStatus={}", idempotencyKey, responseStatus);
    IdempotencyKeyEntity entity =
        jpaRepository
            .findByIdempotencyKeyAndEndpoint(idempotencyKey, ENDPOINT)
            .orElseThrow(() -> new IllegalStateException("No row to complete for " + idempotencyKey));
    entity.setStatus("COMPLETED");
    entity.setResponseStatus(responseStatus);
    entity.setCheckoutPayloadEncrypted(null);
    jpaRepository.saveAndFlush(entity);
    LOGGER.debug("markCompleted idempotencyKey={} -> completed", idempotencyKey);
  }

  /**
   * @param idempotencyKey the key to resolve
   */
  @Override
  public void markFailed(String idempotencyKey) {
    LOGGER.debug("markFailed idempotencyKey={}", idempotencyKey);
    IdempotencyKeyEntity entity =
        jpaRepository
            .findByIdempotencyKeyAndEndpoint(idempotencyKey, ENDPOINT)
            .orElseThrow(() -> new IllegalStateException("No row to fail for " + idempotencyKey));
    entity.setStatus("FAILED");
    entity.setCheckoutPayloadEncrypted(null);
    jpaRepository.saveAndFlush(entity);
    LOGGER.debug("markFailed idempotencyKey={} -> failed", idempotencyKey);
  }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `mvn -q test -Dtest=CheckoutIdempotencyPersistenceAdapterTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/floristeriarosy/application/order/port/out/CheckoutIdempotencyPort.java \
        src/main/java/com/floristeriarosy/application/order/port/out/IdempotencyLock.java \
        src/main/java/com/floristeriarosy/application/order/dto/PendingCheckoutPayload.java \
        src/main/java/com/floristeriarosy/domain/exception/order/IdempotencyOperationInProgressException.java \
        src/main/java/com/floristeriarosy/infrastructure/persistence/entity/order/IdempotencyKeyEntity.java \
        src/main/java/com/floristeriarosy/infrastructure/persistence/jpa/order/repository/IdempotencyKeyJpaRepository.java \
        src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/order/CheckoutIdempotencyPersistenceAdapter.java \
        src/test/java/com/floristeriarosy/infrastructure/persistence/adapter/order/CheckoutIdempotencyPersistenceAdapterTest.java
git commit -m "feat(order): CheckoutIdempotencyPort over idempotency_keys (ADR-011, ADR-017)"
```

---

## Task 6: `OrderErrorCode` and Fase-1 domain exceptions

**Files:**
- Create: `src/main/java/com/floristeriarosy/domain/exception/order/OrderErrorCode.java`
- Create: `src/main/java/com/floristeriarosy/domain/exception/order/CheckoutInsufficientStockException.java`
- Create: `src/main/java/com/floristeriarosy/domain/exception/order/CheckoutDiscountExhaustedException.java`
- Create: `src/main/java/com/floristeriarosy/domain/exception/order/DeliveryOutOfRangeException.java`
- Create: `src/main/java/com/floristeriarosy/domain/exception/order/DeliveryAddressNotGeocodableException.java`
- Create: `src/main/java/com/floristeriarosy/domain/exception/order/OrderValidationFailedException.java`
- Create: `src/main/java/com/floristeriarosy/domain/exception/order/CheckoutNotFoundException.java`
- Create: `src/main/java/com/floristeriarosy/domain/exception/order/IdempotencyKeyReusedException.java`

**Interfaces:**
- Consumes: `HasErrorCode`/`HasErrorDetails`, `UnprocessableException`/`ConflictException`/
  `NotFoundException` base classes (already exist, Phase 1 research confirmed their shape).
- Produces: all seven exceptions plus `OrderErrorCode` — Task 8's `CheckoutService` and Task 9's
  `GetCheckoutStatusService` throw these; the existing generic `GlobalExceptionHandler` maps them by
  base class, no advice changes needed (Phase 1's own research already confirmed this).

This is the first phase to give `order` any error condition, closing the gap Phase 1 deliberately
left open.

No test file of its own for this task — each exception is exercised by the service test that
throws it (Task 8/9).

- [ ] **Step 1: Create `OrderErrorCode`**

```java
package com.floristeriarosy.domain.exception.order;

/** Business error codes published by the order module (ADR-012, order.md §9). */
public enum OrderErrorCode {
  ORDER_NOT_FOUND,
  CHECKOUT_NOT_FOUND,
  CHECKOUT_INSUFFICIENT_STOCK,
  CHECKOUT_DISCOUNT_EXHAUSTED,
  CHECKOUT_PAYMENT_DECLINED,
  DELIVERY_OUT_OF_RANGE,
  DELIVERY_ADDRESS_NOT_GEOCODABLE,
  ORDER_INVALID_TRANSITION,
  ORDER_NOT_CANCELABLE,
  ORDER_VALIDATION_FAILED,
  IDEMPOTENCY_KEY_REUSED
}
```

Only `CHECKOUT_NOT_FOUND`, `CHECKOUT_INSUFFICIENT_STOCK`, `CHECKOUT_DISCOUNT_EXHAUSTED`,
`DELIVERY_OUT_OF_RANGE`, `DELIVERY_ADDRESS_NOT_GEOCODABLE`, `ORDER_VALIDATION_FAILED`,
`IDEMPOTENCY_KEY_REUSED` get an exception class in this task — `ORDER_NOT_FOUND`,
`CHECKOUT_PAYMENT_DECLINED`, `ORDER_INVALID_TRANSITION`, `ORDER_NOT_CANCELABLE` are declared now
(order.md §9 lists the full set together) but their exceptions are added in Phase 3b/4, when their
first thrower exists.

- [ ] **Step 2: Create the six exceptions**

```java
package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.HasErrorDetails;
import com.floristeriarosy.domain.exception.UnprocessableException;
import java.util.Map;

/** Fase 1: a line's stock ran out between the cart check and the reservation (order.md, §9). */
public final class CheckoutInsufficientStockException extends UnprocessableException
    implements HasErrorCode, HasErrorDetails {

  private final int availableQuantity;

  /**
   * @param message what the caller should see
   * @param availableQuantity how many units remain
   */
  public CheckoutInsufficientStockException(String message, int availableQuantity) {
    super(message);
    this.availableQuantity = availableQuantity;
  }

  /** @return {@link OrderErrorCode#CHECKOUT_INSUFFICIENT_STOCK} */
  @Override
  public String errorCode() {
    return OrderErrorCode.CHECKOUT_INSUFFICIENT_STOCK.name();
  }

  /** @return {@code {"availableQuantity": ...}} */
  @Override
  public Map<String, Object> errorDetails() {
    return Map.of("availableQuantity", availableQuantity);
  }
}
```

```java
package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/** Fase 1: the promotion sold out between the cart price-view and payment (order.md, §9). */
public final class CheckoutDiscountExhaustedException extends ConflictException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public CheckoutDiscountExhaustedException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#CHECKOUT_DISCOUNT_EXHAUSTED} */
  @Override
  public String errorCode() {
    return OrderErrorCode.CHECKOUT_DISCOUNT_EXHAUSTED.name();
  }
}
```

```java
package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** The delivery address is more than 10 km from the store — no shipping band applies (order.md, §3.7, §9). */
public final class DeliveryOutOfRangeException extends UnprocessableException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public DeliveryOutOfRangeException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#DELIVERY_OUT_OF_RANGE} */
  @Override
  public String errorCode() {
    return OrderErrorCode.DELIVERY_OUT_OF_RANGE.name();
  }
}
```

```java
package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** Google Maps could not locate the delivery address (order.md, §3.7, §9). */
public final class DeliveryAddressNotGeocodableException extends UnprocessableException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public DeliveryAddressNotGeocodableException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#DELIVERY_ADDRESS_NOT_GEOCODABLE} */
  @Override
  public String errorCode() {
    return OrderErrorCode.DELIVERY_ADDRESS_NOT_GEOCODABLE.name();
  }
}
```

```java
package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** {@code CheckoutRequest}'s cross-field business rule failed (order.md, §5, §9). */
public final class OrderValidationFailedException extends UnprocessableException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public OrderValidationFailedException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#ORDER_VALIDATION_FAILED} */
  @Override
  public String errorCode() {
    return OrderErrorCode.ORDER_VALIDATION_FAILED.name();
  }
}
```

```java
package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.NotFoundException;

/** {@code GET /checkout/{idempotencyKey}/status}: no checkout attempt with that key (order.md, §9). */
public final class CheckoutNotFoundException extends NotFoundException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public CheckoutNotFoundException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#CHECKOUT_NOT_FOUND} */
  @Override
  public String errorCode() {
    return OrderErrorCode.CHECKOUT_NOT_FOUND.name();
  }
}
```

```java
package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** Same {@code Idempotency-Key}, different cart contents — treated as a client bug (ADR-011). */
public final class IdempotencyKeyReusedException extends UnprocessableException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public IdempotencyKeyReusedException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#IDEMPOTENCY_KEY_REUSED} */
  @Override
  public String errorCode() {
    return OrderErrorCode.IDEMPOTENCY_KEY_REUSED.name();
  }
}
```

- [ ] **Step 3: Compile and commit**

Run: `mvn -q compile`

```bash
git add src/main/java/com/floristeriarosy/domain/exception/order
git commit -m "feat(order): OrderErrorCode and Fase-1 exceptions (order.md §9)"
```

---

## Task 7: `CheckoutCommand`, `GetCheckoutStatusQuery`, and `CheckoutUseCase`/`GetCheckoutStatusUseCase`

**Files:**
- Create: `src/main/java/com/floristeriarosy/application/order/command/CheckoutCommand.java`
- Create: `src/main/java/com/floristeriarosy/application/order/query/GetCheckoutStatusQuery.java`
- Create: `src/main/java/com/floristeriarosy/application/order/dto/CheckoutRedirectDto.java`
- Create: `src/main/java/com/floristeriarosy/application/order/dto/CheckoutStatusDto.java`
- Create: `src/main/java/com/floristeriarosy/application/order/port/in/CheckoutUseCase.java`
- Create: `src/main/java/com/floristeriarosy/application/order/port/in/GetCheckoutStatusUseCase.java`

**Interfaces:**
- Consumes: `OrderChannel`, `OrderFulfillment` (Phase 1), `PaymentMethod` (Phase 1).
- Produces: `CheckoutUseCase.execute(CheckoutCommand)` → `CheckoutRedirectDto`,
  `GetCheckoutStatusUseCase.execute(GetCheckoutStatusQuery)` → `CheckoutStatusDto`. Task 8/9's
  services implement these; Task 10's controller calls them.

No test — pure declarations.

- [ ] **Step 1: Create the command and query**

```java
package com.floristeriarosy.application.order.command;

import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * @param customerId the logged-in customer, or {@code null} for a guest checkout
 * @param sessionToken the guest cart's session token, or {@code null} with a session
 * @param fulfillment how the order reaches the customer
 * @param paymentMethod always {@code CARD_ONLINE} for {@code WEB} (order.md, §5)
 * @param paymentMethodToken the gateway token for a new card
 * @param paymentMethodId a saved card's id — accepted by the DTO, not yet supported (Phase 6)
 * @param buyerFirstName the buyer's first name (guest form, or ignored with a session)
 * @param buyerLastName the buyer's last name
 * @param buyerEmail the buyer's email
 * @param buyerPhone the buyer's phone
 * @param addressId a saved address id — accepted by the DTO, not yet supported (Phase 6)
 * @param recipientName the delivery recipient's name
 * @param recipientPhone the delivery recipient's phone
 * @param street the delivery street
 * @param number the delivery street number
 * @param detail the delivery address detail, or {@code null}
 * @param postalCode the delivery postal code
 * @param deliveryDate the requested delivery/pickup date
 * @param slotFrom the requested slot's start
 * @param slotTo the requested slot's end
 * @param cardMessage the card message, or {@code null}
 * @param idempotencyKey the {@code Idempotency-Key} header value
 */
public record CheckoutCommand(
    UUID customerId,
    String sessionToken,
    OrderFulfillment fulfillment,
    PaymentMethod paymentMethod,
    String paymentMethodToken,
    UUID paymentMethodId,
    String buyerFirstName,
    String buyerLastName,
    String buyerEmail,
    String buyerPhone,
    UUID addressId,
    String recipientName,
    String recipientPhone,
    String street,
    String number,
    String detail,
    String postalCode,
    LocalDate deliveryDate,
    LocalTime slotFrom,
    LocalTime slotTo,
    String cardMessage,
    String idempotencyKey) {}
```

```java
package com.floristeriarosy.application.order.query;

/**
 * @param idempotencyKey the {@code Idempotency-Key} used on the original {@code POST /checkout}
 */
public record GetCheckoutStatusQuery(String idempotencyKey) {}
```

- [ ] **Step 2: Create the result DTOs**

```java
package com.floristeriarosy.application.order.dto;

import java.util.Map;

/**
 * @param redirectUrl the Redsys form-post target
 * @param formFields the signed {@code Ds_*} fields to post
 */
public record CheckoutRedirectDto(String redirectUrl, Map<String, String> formFields) {}
```

```java
package com.floristeriarosy.application.order.dto;

/**
 * @param status {@code PENDING}, {@code COMPLETED} or {@code FAILED}
 * @param orderNumber the confirmed order's number, present only when {@code COMPLETED}
 * @param reason why it failed, present only when {@code FAILED}
 */
public record CheckoutStatusDto(String status, String orderNumber, String reason) {}
```

- [ ] **Step 3: Create the input ports**

```java
package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.command.CheckoutCommand;
import com.floristeriarosy.application.order.dto.CheckoutRedirectDto;

/** Fase 1 of the checkout (order.md, §3.2): reserve, then redirect. */
public interface CheckoutUseCase {

  /**
   * @param command everything needed to reserve and build the redirect
   * @return the signed Redsys redirect
   */
  CheckoutRedirectDto execute(CheckoutCommand command);
}
```

```java
package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.dto.CheckoutStatusDto;
import com.floristeriarosy.application.order.query.GetCheckoutStatusQuery;

/** Lets the frontend resolve a checkout attempt after returning from Redsys (ADR-017). */
public interface GetCheckoutStatusUseCase {

  /**
   * @param query the idempotency key to look up
   * @return the current status
   */
  CheckoutStatusDto execute(GetCheckoutStatusQuery query);
}
```

- [ ] **Step 4: Compile and commit**

Run: `mvn -q compile`

```bash
git add src/main/java/com/floristeriarosy/application/order/command/CheckoutCommand.java \
        src/main/java/com/floristeriarosy/application/order/query/GetCheckoutStatusQuery.java \
        src/main/java/com/floristeriarosy/application/order/dto/CheckoutRedirectDto.java \
        src/main/java/com/floristeriarosy/application/order/dto/CheckoutStatusDto.java \
        src/main/java/com/floristeriarosy/application/order/port/in/CheckoutUseCase.java \
        src/main/java/com/floristeriarosy/application/order/port/in/GetCheckoutStatusUseCase.java
git commit -m "feat(order): CheckoutUseCase/GetCheckoutStatusUseCase ports (order.md §7)"
```

---

## Task 8: `CheckoutService`

**Files:**
- Create: `src/main/java/com/floristeriarosy/application/order/service/CheckoutService.java`
- Test: `src/test/java/com/floristeriarosy/application/order/service/CheckoutServiceTest.java`

**Interfaces:**
- Consumes: `ValidateCartUseCase`, `GetCartUseCase` (`cart`, existing), `RegisterStockMovementUseCase`
  (`inventory`, existing), `DiscountReservationPort` (`discount`, existing), `GeocodingPort` (Task
  2), `ShippingRatePort` (Task 3), `OrderNumberPort` (Task 4), `CheckoutIdempotencyPort` (Task 5),
  `OrderPaymentPort` (Phase 2), `PiiCryptoPort` (existing, ADR-005), `ObjectMapper` (Jackson, already
  a Spring bean), Task 6's exceptions, Task 7's port/command/DTOs.
- Produces: `CheckoutService implements CheckoutUseCase` — Task 10's controller injects this.

Read `AddCartItemService.java` in full before writing this (Phase 1's own research already fetched
it) — mirror its shape: constructor injection of every collaborator with one Javadoc `@param` line
naming its role, a public `execute()` that does cheap checks first, comments on *why* a step
precedes another.

- [ ] **Step 1: Write the failing tests**

```java
package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.dto.CartItemDto;
import com.floristeriarosy.application.cart.dto.CartValidationDto;
import com.floristeriarosy.application.cart.port.in.GetCartUseCase;
import com.floristeriarosy.application.cart.port.in.ValidateCartUseCase;
import com.floristeriarosy.application.order.command.CheckoutCommand;
import com.floristeriarosy.application.order.dto.CheckoutRedirectDto;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.port.out.GeocodedAddress;
import com.floristeriarosy.application.order.port.out.GeocodingPort;
import com.floristeriarosy.application.order.port.out.OrderNumberPort;
import com.floristeriarosy.application.order.port.out.ChargeCommand;
import com.floristeriarosy.application.order.port.out.ChargeResult;
import com.floristeriarosy.application.order.port.out.OrderPaymentPort;
import com.floristeriarosy.application.order.port.out.ShippingRate;
import com.floristeriarosy.application.order.port.out.ShippingRatePort;
import com.floristeriarosy.application.payment.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.order.DeliveryAddressNotGeocodableException;
import com.floristeriarosy.domain.exception.order.DeliveryOutOfRangeException;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.order.OrderChannel;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link CheckoutService} — Fase 1 (order.md, §3.2). */
class CheckoutServiceTest {

  private final ValidateCartUseCase validateCartUseCase = mock(ValidateCartUseCase.class);
  private final GetCartUseCase getCartUseCase = mock(GetCartUseCase.class);
  private final GeocodingPort geocodingPort = mock(GeocodingPort.class);
  private final ShippingRatePort shippingRatePort = mock(ShippingRatePort.class);
  private final OrderNumberPort orderNumberPort = mock(OrderNumberPort.class);
  private final CheckoutIdempotencyPort idempotencyPort = mock(CheckoutIdempotencyPort.class);
  private final OrderPaymentPort orderPaymentPort = mock(OrderPaymentPort.class);
  private final PiiCryptoPort piiCryptoPort = mock(PiiCryptoPort.class);
  // Add mocks for RegisterStockMovementUseCase and DiscountReservationPort once their exact
  // package/method names are confirmed by reading inventory.md/product-discounts.md's real code.

  private CheckoutService service;

  @BeforeEach
  void setUp() {
    when(piiCryptoPort.encrypt(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn("encrypted".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    when(piiCryptoPort.hmac(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn("hash".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    service =
        new CheckoutService(
            validateCartUseCase, getCartUseCase, geocodingPort, shippingRatePort, orderNumberPort,
            idempotencyPort, orderPaymentPort, piiCryptoPort, new ObjectMapper()
            /* , registerStockMovementUseCase, discountReservationPort */);
  }

  private CheckoutCommand pickupCommand() {
    return new CheckoutCommand(
        null, "session-token", OrderFulfillment.PICKUP, PaymentMethod.CARD_ONLINE, "tok_visa", null,
        "Ana", "García", "ana@example.com", "600111222", null, null, null, null, null, null, null,
        LocalDate.now().plusDays(2), LocalTime.of(10, 0), LocalTime.of(11, 0), null, "idem-1");
  }

  @Test
  void pickupSkipsGeocodingAndShippingEntirely() {
    when(validateCartUseCase.execute(any())).thenReturn(new CartValidationDto(true, List.of(), List.of()));
    when(getCartUseCase.execute(any()))
        .thenReturn(
            new CartDto(
                UUID.randomUUID(), null, "session-token",
                List.of(
                    new CartItemDto(
                        UUID.randomUUID(), "Rosa roja", "rosa-roja", null, new BigDecimal("10.00"),
                        new BigDecimal("10.00"), false, Map.of(), null, 2, new BigDecimal("20.00"), 5)),
                new BigDecimal("20.00"), 1));
    when(orderNumberPort.next(OrderChannel.WEB)).thenReturn(OrderNumber.of("WEB-20260914-000001"));
    when(orderPaymentPort.charge(any()))
        .thenReturn(new ChargeResult.RedirectRequired("https://sis-t.redsys.es/sis/realizarPago", Map.of("k", "v")));

    CheckoutRedirectDto result = service.execute(pickupCommand());

    assertThat(result.redirectUrl()).isEqualTo("https://sis-t.redsys.es/sis/realizarPago");
    org.mockito.Mockito.verifyNoInteractions(geocodingPort, shippingRatePort);
  }

  @Test
  void deliveryOutOfRangeIsRejectedBeforeReservingAnything() {
    when(validateCartUseCase.execute(any())).thenReturn(new CartValidationDto(true, List.of(), List.of()));
    when(geocodingPort.geocode(any(), any(), any()))
        .thenReturn(Optional.of(new GeocodedAddress(new BigDecimal("40.0"), new BigDecimal("-3.0"), new BigDecimal("15.0"))));
    when(shippingRatePort.findRateForDistance(new BigDecimal("15.0"))).thenReturn(Optional.empty());

    CheckoutCommand command =
        new CheckoutCommand(
            null, "session-token", OrderFulfillment.DELIVERY, PaymentMethod.CARD_ONLINE, "tok_visa", null,
            "Ana", "García", "ana@example.com", "600111222", null, "Luis", "600333444", "Gran Vía", "1",
            null, "28013", LocalDate.now().plusDays(2), LocalTime.of(10, 0), LocalTime.of(11, 0), null,
            "idem-2");

    assertThatExceptionOfType(DeliveryOutOfRangeException.class).isThrownBy(() -> service.execute(command));
    org.mockito.Mockito.verifyNoInteractions(orderNumberPort, idempotencyPort, orderPaymentPort);
  }

  @Test
  void nonGeocodableAddressIsRejected() {
    when(validateCartUseCase.execute(any())).thenReturn(new CartValidationDto(true, List.of(), List.of()));
    when(geocodingPort.geocode(any(), any(), any())).thenReturn(Optional.empty());

    CheckoutCommand command =
        new CheckoutCommand(
            null, "session-token", OrderFulfillment.DELIVERY, PaymentMethod.CARD_ONLINE, "tok_visa", null,
            "Ana", "García", "ana@example.com", "600111222", null, "Luis", "600333444", "Nowhere", "1",
            null, "00000", LocalDate.now().plusDays(2), LocalTime.of(10, 0), LocalTime.of(11, 0), null,
            "idem-3");

    assertThatExceptionOfType(DeliveryAddressNotGeocodableException.class).isThrownBy(() -> service.execute(command));
  }
}
```

The mocks/imports for `RegisterStockMovementUseCase`/`DiscountReservationPort` are intentionally
left as a comment: read their real package/method names from Phase 1's own research before wiring
them in, rather than guessing — the implementer fills these in as part of Step 3, not before.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=CheckoutServiceTest`
Expected: compilation failure — `CheckoutService` does not exist yet.

- [ ] **Step 3: Implement `CheckoutService`**

```java
package com.floristeriarosy.application.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.dto.CartItemDto;
import com.floristeriarosy.application.cart.dto.CartValidationDto;
import com.floristeriarosy.application.cart.port.in.GetCartUseCase;
import com.floristeriarosy.application.cart.port.in.ValidateCartUseCase;
import com.floristeriarosy.application.order.command.CheckoutCommand;
import com.floristeriarosy.application.order.dto.CheckoutRedirectDto;
import com.floristeriarosy.application.order.dto.PendingCheckoutPayload;
import com.floristeriarosy.application.order.port.in.CheckoutUseCase;
import com.floristeriarosy.application.order.port.out.ChargeCommand;
import com.floristeriarosy.application.order.port.out.ChargeResult;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.port.out.GeocodedAddress;
import com.floristeriarosy.application.order.port.out.GeocodingPort;
import com.floristeriarosy.application.order.port.out.IdempotencyLock;
import com.floristeriarosy.application.order.port.out.OrderNumberPort;
import com.floristeriarosy.application.order.port.out.OrderPaymentPort;
import com.floristeriarosy.application.order.port.out.ShippingRate;
import com.floristeriarosy.application.order.port.out.ShippingRatePort;
import com.floristeriarosy.application.payment.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.order.DeliveryAddressNotGeocodableException;
import com.floristeriarosy.domain.exception.order.DeliveryOutOfRangeException;
import com.floristeriarosy.domain.exception.order.IdempotencyKeyReusedException;
import com.floristeriarosy.domain.model.order.OrderChannel;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link CheckoutUseCase} — Fase 1 of order.md §3.2: validate and price the cart,
 * geocode and rate delivery (before anything is reserved, order.md §3.7), reserve stock/discount,
 * store the pending-checkout payload, and hand off to Redsys via {@link OrderPaymentPort}. Never
 * writes to {@code orders} — that is {@code ConfirmCheckoutService} (Phase 3b), driven by Redsys's
 * notification.
 */
@Service
@Transactional
public class CheckoutService implements CheckoutUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(CheckoutService.class);
  private static final String ENDPOINT = "POST /checkout";
  private static final BigDecimal MAX_DELIVERY_DISTANCE_KM = new BigDecimal("10.0");
  private static final java.time.Duration RESERVATION_TTL = java.time.Duration.ofMinutes(10);

  private final ValidateCartUseCase validateCartUseCase;
  private final GetCartUseCase getCartUseCase;
  private final GeocodingPort geocodingPort;
  private final ShippingRatePort shippingRatePort;
  private final OrderNumberPort orderNumberPort;
  private final CheckoutIdempotencyPort idempotencyPort;
  private final OrderPaymentPort orderPaymentPort;
  private final PiiCryptoPort piiCryptoPort;
  private final ObjectMapper objectMapper;

  /**
   * @param validateCartUseCase rejects a cart with removed or now-insufficient lines (cart.md §3.4)
   * @param getCartUseCase reads the priced lines to sell
   * @param geocodingPort locates a delivery address and its distance from the store
   * @param shippingRatePort rates a delivery distance
   * @param orderNumberPort mints the order number this attempt is diversified by (ADR-017)
   * @param idempotencyPort holds the Fase-1 reservation for the wait on Redsys
   * @param orderPaymentPort builds the signed redirect
   * @param piiCryptoPort encrypts the pending-checkout payload
   * @param objectMapper serializes the pending-checkout payload before encrypting it
   */
  public CheckoutService(
      ValidateCartUseCase validateCartUseCase,
      GetCartUseCase getCartUseCase,
      GeocodingPort geocodingPort,
      ShippingRatePort shippingRatePort,
      OrderNumberPort orderNumberPort,
      CheckoutIdempotencyPort idempotencyPort,
      OrderPaymentPort orderPaymentPort,
      PiiCryptoPort piiCryptoPort,
      ObjectMapper objectMapper) {
    this.validateCartUseCase = validateCartUseCase;
    this.getCartUseCase = getCartUseCase;
    this.geocodingPort = geocodingPort;
    this.shippingRatePort = shippingRatePort;
    this.orderNumberPort = orderNumberPort;
    this.idempotencyPort = idempotencyPort;
    this.orderPaymentPort = orderPaymentPort;
    this.piiCryptoPort = piiCryptoPort;
    this.objectMapper = objectMapper;
  }

  /**
   * @param command everything needed to reserve and build the redirect
   * @return the signed Redsys redirect
   * @throws DeliveryOutOfRangeException the address is more than 10 km away
   * @throws DeliveryAddressNotGeocodableException Google Maps could not locate the address
   * @throws IdempotencyKeyReusedException the same key was used with a different cart
   */
  @Override
  public CheckoutRedirectDto execute(CheckoutCommand command) {
    LOGGER.debug("checkout customerId={} fulfillment={} idempotencyKey={}", command.customerId(),
        command.fulfillment(), command.idempotencyKey());

    // Validate the cart first (cart.md §3.4) — a soft, read-only check that gives a specific
    // early error before the atomic reservation below, which is the one that really counts under
    // concurrency (order.md §3.4).
    validateCartUseCase.execute(toValidateCartCommand(command));
    CartDto cart = getCartUseCase.execute(toGetCartQuery(command));

    // Geocode and rate delivery BEFORE reserving anything (order.md §3.7): a bad address must
    // fail before stock or discount is touched, not after.
    GeocodedAddress geocoded = command.fulfillment() == OrderFulfillment.DELIVERY ? geocodeDelivery(command) : null;
    ShippingRate rate = geocoded != null ? rateDelivery(geocoded) : null;
    BigDecimal shippingCost = rate != null ? computeShippingCost(cart.subtotal(), rate) : BigDecimal.ZERO;

    byte[] fingerprint = computeFingerprint(command, cart);
    java.util.Optional<IdempotencyLock> existing = idempotencyPort.find(command.idempotencyKey());
    if (existing.isPresent()) {
      return resumeExisting(existing.get(), fingerprint, command, cart, geocoded, shippingCost);
    }

    return reserveAndRedirect(command, cart, geocoded, shippingCost, fingerprint, false);
  }

  /**
   * @param lock the existing lock for this key
   * @param fingerprint this attempt's fingerprint
   * @param command the incoming command
   * @param cart the priced cart
   * @param geocoded the geocoded delivery, or {@code null}
   * @param shippingCost the computed shipping cost
   * @return the redirect for a fresh {@code FAILED}-retry; an already-{@code PENDING}/{@code
   *     COMPLETED} key never reaches Fase 1 again, per order.md §10 — the controller layer (Task
   *     10) is responsible for translating those into the right response, not this method
   * @throws IdempotencyKeyReusedException the fingerprint changed
   */
  private CheckoutRedirectDto resumeExisting(
      IdempotencyLock lock, byte[] fingerprint, CheckoutCommand command, CartDto cart, GeocodedAddress geocoded,
      BigDecimal shippingCost) {
    if (!MessageDigest.isEqual(lock.requestFingerprint(), fingerprint)) {
      throw new IdempotencyKeyReusedException("Idempotency-Key " + command.idempotencyKey() + " was reused with a different cart");
    }
    if (!"FAILED".equals(lock.status())) {
      throw new IllegalStateException(
          "Idempotency-Key " + command.idempotencyKey() + " is " + lock.status() + " — the controller must handle this before Fase 1 runs again");
    }
    return reserveAndRedirect(command, cart, geocoded, shippingCost, fingerprint, true);
  }

  /**
   * @param command the incoming command
   * @param cart the priced cart
   * @param geocoded the geocoded delivery, or {@code null}
   * @param shippingCost the computed shipping cost
   * @param fingerprint this attempt's fingerprint
   * @param isRetry whether this re-arms a {@code FAILED} row instead of inserting fresh
   * @return the signed Redsys redirect
   */
  private CheckoutRedirectDto reserveAndRedirect(
      CheckoutCommand command, CartDto cart, GeocodedAddress geocoded, BigDecimal shippingCost, byte[] fingerprint,
      boolean isRetry) {
    UUID orderId = UUID.randomUUID();
    OrderNumber orderNumber = orderNumberPort.next(OrderChannel.WEB);

    // Fase 1 (order.md §3.2): reserve stock and discount for every line. A real implementation
    // calls RegisterStockMovementUseCase (SALE) and DiscountReservationPort.reserve() here, inside
    // this same @Transactional method, before anything else — if either fails, the whole method
    // rolls back and Redsys is never mentioned. Wire these once their exact signatures are
    // confirmed (Phase 1's own research already has RegisterStockMovementUseCase's shape).
    reserveInventoryAndDiscounts(cart.items());

    PendingCheckoutPayload payload = buildPendingPayload(command, cart, geocoded, shippingCost);
    byte[] encryptedPayload = piiCryptoPort.encrypt(serialize(payload));
    Instant expiresAt = Instant.now().plus(RESERVATION_TTL);
    if (isRetry) {
      idempotencyPort.resetToPending(command.idempotencyKey(), fingerprint, orderId, encryptedPayload, expiresAt);
    } else {
      idempotencyPort.insertPending(command.idempotencyKey(), fingerprint, orderId, encryptedPayload, expiresAt);
    }

    ChargeResult result =
        orderPaymentPort.charge(
            new ChargeCommand(
                com.floristeriarosy.domain.model.order.valueobject.OrderId.of(orderId), orderNumber, cart.subtotal().add(shippingCost),
                command.paymentMethod(), command.paymentMethodToken(), command.idempotencyKey()));
    // WEB checkout only ever sends CARD_ONLINE (order.md §5) — charge() always returns
    // RedirectRequired here; a Captured result would mean a MANUAL method reached this method,
    // which the controller-level request validation (Task 10) must never allow through.
    ChargeResult.RedirectRequired redirect = (ChargeResult.RedirectRequired) result;
    LOGGER.debug("checkout idempotencyKey={} -> redirect built", command.idempotencyKey());
    return new CheckoutRedirectDto(redirect.redirectUrl(), redirect.formFields());
  }

  /**
   * @param items the cart lines to reserve stock and discount for
   */
  private void reserveInventoryAndDiscounts(List<CartItemDto> items) {
    // Placeholder for the real RegisterStockMovementUseCase/DiscountReservationPort calls — see
    // the comment in reserveAndRedirect. Left as its own method so Step 3's implementer wires the
    // two real collaborators here without touching the surrounding orchestration.
    for (CartItemDto item : items) {
      LOGGER.debug("reserve productId={} quantity={}", item.productId(), item.quantity());
    }
  }

  /**
   * @param command the incoming command
   * @return the delivery address's coordinates and distance from the store
   * @throws DeliveryAddressNotGeocodableException Google Maps could not locate it
   */
  private GeocodedAddress geocodeDelivery(CheckoutCommand command) {
    return geocodingPort
        .geocode(command.street(), command.number(), command.postalCode())
        .orElseThrow(() -> new DeliveryAddressNotGeocodableException("Could not locate the delivery address"));
  }

  /**
   * @param geocoded the geocoded delivery address
   * @return the applicable shipping rate
   * @throws DeliveryOutOfRangeException no band covers this distance
   */
  private ShippingRate rateDelivery(GeocodedAddress geocoded) {
    if (geocoded.distanceKm().compareTo(MAX_DELIVERY_DISTANCE_KM) > 0) {
      throw new DeliveryOutOfRangeException("Delivery address is " + geocoded.distanceKm() + " km away, beyond the 10 km limit");
    }
    return shippingRatePort
        .findRateForDistance(geocoded.distanceKm())
        .orElseThrow(() -> new DeliveryOutOfRangeException("No shipping band covers " + geocoded.distanceKm() + " km"));
  }

  /**
   * @param subtotal the cart's subtotal, compared against {@code free_from_amount} (order.md §3.7:
   *     against the subtotal, before shipping)
   * @param rate the applicable band
   * @return zero if free-shipping applies, otherwise the band's price
   */
  private BigDecimal computeShippingCost(BigDecimal subtotal, ShippingRate rate) {
    boolean freeShipping = rate.freeFromAmount() != null && subtotal.compareTo(rate.freeFromAmount()) >= 0;
    return freeShipping ? BigDecimal.ZERO : rate.price();
  }

  /**
   * @param command the incoming command
   * @param cart the priced cart
   * @return the {@code ValidateCartUseCase} command, built from the same identity fields
   */
  private com.floristeriarosy.application.cart.command.ValidateCartCommand toValidateCartCommand(CheckoutCommand command) {
    return new com.floristeriarosy.application.cart.command.ValidateCartCommand(command.customerId(), command.sessionToken());
  }

  /**
   * @param command the incoming command
   * @return the {@code GetCartUseCase} query, built from the same identity fields
   */
  private com.floristeriarosy.application.cart.query.GetCartQuery toGetCartQuery(CheckoutCommand command) {
    return new com.floristeriarosy.application.cart.query.GetCartQuery(command.customerId(), command.sessionToken());
  }

  /**
   * @param command the incoming command
   * @param cart the priced cart
   * @return the SHA-256 fingerprint (ADR-011): the request plus the cart's contents, not the
   *     request alone (payment.md §3.8 — {@code POST /checkout} carries no line items itself)
   */
  private byte[] computeFingerprint(CheckoutCommand command, CartDto cart) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(serialize(command).getBytes(StandardCharsets.UTF_8));
      digest.update(serialize(cart).getBytes(StandardCharsets.UTF_8));
      return digest.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 must be available on every JVM", e);
    }
  }

  /**
   * @param command the incoming command
   * @param cart the priced cart
   * @param geocoded the geocoded delivery, or {@code null}
   * @param shippingCost the computed shipping cost
   * @return the payload to encrypt into {@code idempotency_keys}
   */
  private PendingCheckoutPayload buildPendingPayload(
      CheckoutCommand command, CartDto cart, GeocodedAddress geocoded, BigDecimal shippingCost) {
    List<PendingCheckoutPayload.Item> items =
        cart.items().stream()
            .map(
                item ->
                    new PendingCheckoutPayload.Item(
                        item.productId(), item.productName(), item.productAttributes(), item.unitPrice(),
                        item.onSale() ? item.effectivePrice() : null, item.discountId(), item.quantity()))
            .toList();
    PendingCheckoutPayload.Delivery delivery =
        command.fulfillment() == OrderFulfillment.DELIVERY
            ? new PendingCheckoutPayload.Delivery(
                command.recipientName(), command.recipientPhone(), command.street(), command.number(),
                command.detail(), command.postalCode(), geocoded.latitude(), geocoded.longitude(),
                geocoded.distanceKm(), command.deliveryDate(), command.slotFrom(), command.slotTo(),
                command.cardMessage())
            : null;
    PendingCheckoutPayload.Pickup pickup =
        command.fulfillment() == OrderFulfillment.PICKUP
            ? new PendingCheckoutPayload.Pickup(command.deliveryDate(), command.slotFrom(), command.slotTo())
            : null;
    return new PendingCheckoutPayload(
        OrderChannel.WEB.name(), command.fulfillment().name(), command.customerId(), command.buyerFirstName(),
        command.buyerLastName(), command.buyerEmail(), command.buyerPhone(), items, delivery, pickup,
        cart.subtotal(), shippingCost);
  }

  /**
   * @param value the object to serialize
   * @return its JSON representation
   */
  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Could not serialize " + value.getClass().getSimpleName(), e);
    }
  }
}
```

**Notes for the implementer, do not skip:**
- `reserveInventoryAndDiscounts` is an intentional stub in this draft — replace it with the real
  `RegisterStockMovementUseCase`/`DiscountReservationPort` calls (Phase 1's research already has
  their exact signatures) before this task is considered done. Catch
  `InventoryInsufficientStockException` and re-throw `CheckoutInsufficientStockException` with the
  real `availableQuantity` — Phase 1's research flagged that the inventory exception does not carry
  this detail itself, so an extra read may be needed to report it.
- `CartItemDto`'s constructor call in the test uses a guessed field order/arity — fix it against
  the real record once Task 1 lands.
- `ChargeCommand`/`OrderPaymentPort.charge(...)`'s exact parameter shape may have moved to
  primitives during Phase 2 (see that phase's Task 2 note about ArchUnit) — call it however Phase 2
  actually left it, not as drafted here.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=CheckoutServiceTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0` once the notes above are resolved.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/floristeriarosy/application/order/service/CheckoutService.java \
        src/test/java/com/floristeriarosy/application/order/service/CheckoutServiceTest.java
git commit -m "feat(order): CheckoutService — Fase 1 (order.md §3.2, §3.4, §3.7, ADR-017)"
```

---

## Task 9: `GetCheckoutStatusService`

**Files:**
- Create: `src/main/java/com/floristeriarosy/application/order/service/GetCheckoutStatusService.java`
- Test: `src/test/java/com/floristeriarosy/application/order/service/GetCheckoutStatusServiceTest.java`

**Interfaces:**
- Consumes: `CheckoutIdempotencyPort` (Task 5), `GetCheckoutStatusUseCase`/`CheckoutStatusDto`
  (Task 7).
- Produces: `GetCheckoutStatusService implements GetCheckoutStatusUseCase` — Task 10's controller
  injects this.

- [ ] **Step 1: Write the failing tests**

```java
package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.port.out.IdempotencyLock;
import com.floristeriarosy.application.order.query.GetCheckoutStatusQuery;
import com.floristeriarosy.domain.exception.order.CheckoutNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetCheckoutStatusServiceTest {

  private final CheckoutIdempotencyPort idempotencyPort = mock(CheckoutIdempotencyPort.class);
  private final GetCheckoutStatusService service = new GetCheckoutStatusService(idempotencyPort);

  @Test
  void pendingReportsPendingWithNoOrderNumber() {
    when(idempotencyPort.find("idem-1")).thenReturn(Optional.of(new IdempotencyLock("PENDING", new byte[0], UUID.randomUUID())));

    var status = service.execute(new GetCheckoutStatusQuery("idem-1"));

    assertThat(status.status()).isEqualTo("PENDING");
    assertThat(status.orderNumber()).isNull();
  }

  @Test
  void throwsWhenNoAttemptExistsForTheKey() {
    when(idempotencyPort.find("idem-2")).thenReturn(Optional.empty());

    assertThatExceptionOfType(CheckoutNotFoundException.class)
        .isThrownBy(() -> service.execute(new GetCheckoutStatusQuery("idem-2")));
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=GetCheckoutStatusServiceTest`
Expected: compilation failure.

- [ ] **Step 3: Implement `GetCheckoutStatusService`**

```java
package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.order.dto.CheckoutStatusDto;
import com.floristeriarosy.application.order.port.in.GetCheckoutStatusUseCase;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.port.out.IdempotencyLock;
import com.floristeriarosy.application.order.query.GetCheckoutStatusQuery;
import com.floristeriarosy.domain.exception.order.CheckoutNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link GetCheckoutStatusUseCase}: a thin read over the {@code POST /checkout}
 * idempotency lock, for the frontend to resolve a checkout attempt after returning from Redsys
 * (ADR-017). Does not resolve {@code orderNumber} for a {@code COMPLETED} attempt in this phase —
 * that needs {@code OrderReadPort} (Phase 3b); leaving it out here is the deliberate scope
 * boundary between the two phases, not an oversight.
 */
@Service
public class GetCheckoutStatusService implements GetCheckoutStatusUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetCheckoutStatusService.class);

  private final CheckoutIdempotencyPort idempotencyPort;

  /**
   * @param idempotencyPort reads the checkout lock
   */
  public GetCheckoutStatusService(CheckoutIdempotencyPort idempotencyPort) {
    this.idempotencyPort = idempotencyPort;
  }

  /**
   * @param query the idempotency key to look up
   * @return the current status
   * @throws CheckoutNotFoundException no attempt exists for that key
   */
  @Override
  public CheckoutStatusDto execute(GetCheckoutStatusQuery query) {
    LOGGER.debug("getCheckoutStatus idempotencyKey={}", query.idempotencyKey());
    IdempotencyLock lock =
        idempotencyPort
            .find(query.idempotencyKey())
            .orElseThrow(() -> new CheckoutNotFoundException("No checkout attempt for " + query.idempotencyKey()));
    CheckoutStatusDto result = new CheckoutStatusDto(lock.status(), null, null);
    LOGGER.debug("getCheckoutStatus idempotencyKey={} -> {}", query.idempotencyKey(), lock.status());
    return result;
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=GetCheckoutStatusServiceTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/floristeriarosy/application/order/service/GetCheckoutStatusService.java \
        src/test/java/com/floristeriarosy/application/order/service/GetCheckoutStatusServiceTest.java
git commit -m "feat(order): GetCheckoutStatusService (ADR-017)"
```

---

## Task 10: Web layer — DTOs and `CheckoutController`

**Files:**
- Create: `src/main/java/com/floristeriarosy/infrastructure/web/request/order/CheckoutRequest.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/web/request/order/BuyerRequest.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/web/request/order/DeliveryRequest.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/web/request/order/PickupRequest.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/web/response/order/CheckoutResponse.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/web/response/order/CheckoutStatusResponse.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/web/controller/order/CheckoutController.java`
- Test: `src/test/java/com/floristeriarosy/infrastructure/web/controller/order/CheckoutControllerTest.java`

**Interfaces:**
- Consumes: `CheckoutUseCase`, `GetCheckoutStatusUseCase` (Task 7).
- Produces: `POST /checkout` (202, requires `Idempotency-Key`), `GET
  /checkout/{idempotencyKey}/status` (200) — order.md §4.

Read an existing controller (e.g. category's or cart's) plus its `@WebMvcTest` in full first — Phase
1's own research already fetched a controller + test example; match the exact annotation shape
(`@PostMapping`, `.with(csrf())`, `@Import` of `SecurityConfig`) instead of guessing.

- [ ] **Step 1: Create the request DTOs**

```java
package com.floristeriarosy.infrastructure.web.request.order;

import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * @param fulfillment how the order reaches the customer
 * @param paymentMethod always {@code CARD_ONLINE} for {@code WEB} (order.md, §5)
 * @param paymentMethodToken a new card's gateway token — alternative to {@code paymentMethodId}
 * @param paymentMethodId a saved card's id — alternative to {@code paymentMethodToken}, session only
 * @param buyer the buyer's details, required without a session
 * @param addressId a saved address id — alternative to {@code delivery}
 * @param delivery the delivery details, required when {@code fulfillment = DELIVERY} without
 *     {@code addressId}
 * @param pickup the pickup details, required when {@code fulfillment = PICKUP}
 */
public record CheckoutRequest(
    @NotNull OrderFulfillment fulfillment,
    @NotNull PaymentMethod paymentMethod,
    String paymentMethodToken,
    UUID paymentMethodId,
    @Valid BuyerRequest buyer,
    UUID addressId,
    @Valid DeliveryRequest delivery,
    @Valid PickupRequest pickup) {}
```

```java
package com.floristeriarosy.infrastructure.web.request.order;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * @param firstName the buyer's first name
 * @param lastName the buyer's last name
 * @param email the buyer's email
 * @param phone the buyer's phone
 */
public record BuyerRequest(
    @NotBlank String firstName, @NotBlank String lastName, @NotBlank @Email String email, @NotBlank String phone) {}
```

```java
package com.floristeriarosy.infrastructure.web.request.order;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * @param recipientName who receives the delivery
 * @param recipientPhone the recipient's phone
 * @param street the street
 * @param number the street number
 * @param detail the address detail, optional
 * @param postalCode the postal code
 * @param deliveryDate the requested date
 * @param slotFrom the requested slot's start
 * @param slotTo the requested slot's end
 * @param cardMessage the card message, optional
 */
public record DeliveryRequest(
    @NotBlank String recipientName,
    @NotBlank String recipientPhone,
    @NotBlank String street,
    @NotBlank String number,
    String detail,
    @NotBlank String postalCode,
    @NotNull @Future LocalDate deliveryDate,
    @NotNull LocalTime slotFrom,
    @NotNull LocalTime slotTo,
    @Size(max = 500) String cardMessage) {}
```

```java
package com.floristeriarosy.infrastructure.web.request.order;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * @param deliveryDate the requested pickup date
 * @param slotFrom the requested slot's start
 * @param slotTo the requested slot's end
 */
public record PickupRequest(@NotNull @Future LocalDate deliveryDate, @NotNull LocalTime slotFrom, @NotNull LocalTime slotTo) {}
```

- [ ] **Step 2: Create the response DTOs**

```java
package com.floristeriarosy.infrastructure.web.response.order;

import java.util.Map;

/**
 * @param redirectUrl where the frontend must POST the customer's browser
 * @param formFields the signed fields to post as a form
 */
public record CheckoutResponse(String redirectUrl, Map<String, String> formFields) {}
```

```java
package com.floristeriarosy.infrastructure.web.response.order;

/**
 * @param status {@code PENDING}, {@code COMPLETED} or {@code FAILED}
 * @param orderNumber present only when {@code COMPLETED}
 * @param reason present only when {@code FAILED}
 */
public record CheckoutStatusResponse(String status, String orderNumber, String reason) {}
```

- [ ] **Step 3: Write the failing controller test**

```java
package com.floristeriarosy.infrastructure.web.controller.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.floristeriarosy.application.order.dto.CheckoutRedirectDto;
import com.floristeriarosy.application.order.dto.CheckoutStatusDto;
import com.floristeriarosy.application.order.port.in.CheckoutUseCase;
import com.floristeriarosy.application.order.port.in.GetCheckoutStatusUseCase;
import com.floristeriarosy.infrastructure.web.request.order.CheckoutRequest;
import com.floristeriarosy.infrastructure.web.request.order.PickupRequest;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/** {@code POST /checkout}/{@code GET /checkout/{key}/status} — CSRF required on the mutating one. */
@WebMvcTest(CheckoutController.class)
class CheckoutControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockBean private CheckoutUseCase checkoutUseCase;
  @MockBean private GetCheckoutStatusUseCase getCheckoutStatusUseCase;

  @Test
  void checkoutRequiresCsrf() throws Exception {
    when(checkoutUseCase.execute(any()))
        .thenReturn(new CheckoutRedirectDto("https://sis-t.redsys.es/sis/realizarPago", Map.of("k", "v")));
    CheckoutRequest request =
        new CheckoutRequest(
            OrderFulfillment.PICKUP, PaymentMethod.CARD_ONLINE, "tok_visa", null, null, null, null,
            new PickupRequest(LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0)));

    mockMvc
        .perform(
            post("/api/v1/checkout")
                .with(csrf())
                .header("Idempotency-Key", "idem-1")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isAccepted());
  }

  @Test
  void getStatusReturnsTheCurrentStatus() throws Exception {
    when(getCheckoutStatusUseCase.execute(any())).thenReturn(new CheckoutStatusDto("PENDING", null, null));

    mockMvc.perform(get("/api/v1/checkout/idem-1/status")).andExpect(status().isOk());
  }
}
```

Match the exact `@Import`/security setup another `@WebMvcTest` in this codebase uses — the snippet
above omits it deliberately, fill it in from the real example rather than guessing.

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn -q test -Dtest=CheckoutControllerTest`
Expected: compilation failure.

- [ ] **Step 5: Implement `CheckoutController`**

```java
package com.floristeriarosy.infrastructure.web.controller.order;

import com.floristeriarosy.application.order.command.CheckoutCommand;
import com.floristeriarosy.application.order.dto.CheckoutRedirectDto;
import com.floristeriarosy.application.order.dto.CheckoutStatusDto;
import com.floristeriarosy.application.order.port.in.CheckoutUseCase;
import com.floristeriarosy.application.order.port.in.GetCheckoutStatusUseCase;
import com.floristeriarosy.application.order.query.GetCheckoutStatusQuery;
import com.floristeriarosy.domain.exception.order.OrderValidationFailedException;
import com.floristeriarosy.infrastructure.web.request.order.CheckoutRequest;
import com.floristeriarosy.infrastructure.web.response.order.CheckoutResponse;
import com.floristeriarosy.infrastructure.web.response.order.CheckoutStatusResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /checkout}, {@code GET /checkout/{idempotencyKey}/status} (order.md, §4). */
@RestController
@RequestMapping("/api/v1")
public class CheckoutController {

  private static final Logger LOGGER = LoggerFactory.getLogger(CheckoutController.class);

  private final CheckoutUseCase checkoutUseCase;
  private final GetCheckoutStatusUseCase getCheckoutStatusUseCase;

  /**
   * @param checkoutUseCase runs Fase 1 and builds the redirect
   * @param getCheckoutStatusUseCase reads a checkout attempt's status
   */
  public CheckoutController(CheckoutUseCase checkoutUseCase, GetCheckoutStatusUseCase getCheckoutStatusUseCase) {
    this.checkoutUseCase = checkoutUseCase;
    this.getCheckoutStatusUseCase = getCheckoutStatusUseCase;
  }

  /**
   * @param request the checkout details
   * @param idempotencyKey required, ADR-011
   * @param customerId the logged-in customer, or {@code null} for a guest — resolved by whatever
   *     mechanism this codebase already uses to inject the authenticated principal (check an
   *     existing authenticated endpoint, e.g. in {@code cart}, instead of guessing the type here)
   * @return 202 with the signed Redsys redirect
   * @throws OrderValidationFailedException {@code paymentMethod = CARD_ONLINE} but not exactly one
   *     of {@code paymentMethodToken}/{@code paymentMethodId} was supplied
   */
  @PostMapping("/checkout")
  public ResponseEntity<CheckoutResponse> checkout(
      @Valid @RequestBody CheckoutRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @AuthenticationPrincipal UUID customerId) {
    LOGGER.debug("POST /checkout fulfillment={} idempotencyKey={}", request.fulfillment(), idempotencyKey);
    requireExactlyOnePaymentMethodReference(request);
    CheckoutRedirectDto result = checkoutUseCase.execute(toCommand(request, idempotencyKey, customerId));
    LOGGER.debug("POST /checkout idempotencyKey={} -> redirect built", idempotencyKey);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(new CheckoutResponse(result.redirectUrl(), result.formFields()));
  }

  /**
   * @param idempotencyKey the key used on the original {@code POST /checkout}
   * @return the current status
   */
  @GetMapping("/checkout/{idempotencyKey}/status")
  public ResponseEntity<CheckoutStatusResponse> getStatus(@PathVariable String idempotencyKey) {
    LOGGER.debug("GET /checkout/{}/status", Encode.forJava(idempotencyKey));
    CheckoutStatusDto result = getCheckoutStatusUseCase.execute(new GetCheckoutStatusQuery(idempotencyKey));
    return ResponseEntity.ok(new CheckoutStatusResponse(result.status(), result.orderNumber(), result.reason()));
  }

  /**
   * @param request the incoming checkout request
   * @throws OrderValidationFailedException the cross-field rule failed (order.md, §5)
   */
  private void requireExactlyOnePaymentMethodReference(CheckoutRequest request) {
    boolean hasToken = request.paymentMethodToken() != null;
    boolean hasSavedCard = request.paymentMethodId() != null;
    if (hasToken == hasSavedCard) {
      throw new OrderValidationFailedException("Exactly one of paymentMethodToken/paymentMethodId is required");
    }
  }

  /**
   * @param request the incoming checkout request
   * @param idempotencyKey the {@code Idempotency-Key} header
   * @param customerId the logged-in customer, or {@code null}
   * @return the application command
   */
  private CheckoutCommand toCommand(CheckoutRequest request, String idempotencyKey, UUID customerId) {
    var delivery = request.delivery();
    var pickup = request.pickup();
    var buyer = request.buyer();
    return new CheckoutCommand(
        customerId,
        null,
        request.fulfillment(),
        request.paymentMethod(),
        request.paymentMethodToken(),
        request.paymentMethodId(),
        buyer != null ? buyer.firstName() : null,
        buyer != null ? buyer.lastName() : null,
        buyer != null ? buyer.email() : null,
        buyer != null ? buyer.phone() : null,
        request.addressId(),
        delivery != null ? delivery.recipientName() : null,
        delivery != null ? delivery.recipientPhone() : null,
        delivery != null ? delivery.street() : null,
        delivery != null ? delivery.number() : null,
        delivery != null ? delivery.detail() : null,
        delivery != null ? delivery.postalCode() : null,
        delivery != null ? delivery.deliveryDate() : pickup != null ? pickup.deliveryDate() : null,
        delivery != null ? delivery.slotFrom() : pickup != null ? pickup.slotFrom() : null,
        delivery != null ? delivery.slotTo() : pickup != null ? pickup.slotTo() : null,
        delivery != null ? delivery.cardMessage() : null,
        idempotencyKey);
  }
}
```

**Note for the implementer:** `sessionToken` is passed as `null` in `toCommand` — check how `cart`'s
own controller resolves a guest's session token (cookie? header?) and wire the same mechanism here,
rather than leaving it genuinely null. Also confirm `@AuthenticationPrincipal UUID customerId`
against how an existing authenticated-or-anonymous endpoint in this codebase actually reads the
current customer (do not guess the annotation/type — `auth.md`'s real implementation is the source
of truth).

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q test -Dtest=CheckoutControllerTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 7: Run the full build**

Run: `mvn -q verify`
Expected: `BUILD SUCCESS` once every "note for the implementer" in Tasks 8/10 has been resolved
against this codebase's real, already-existing code — none of them are optional polish.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/floristeriarosy/infrastructure/web/request/order \
        src/main/java/com/floristeriarosy/infrastructure/web/response/order \
        src/main/java/com/floristeriarosy/infrastructure/web/controller/order \
        src/test/java/com/floristeriarosy/infrastructure/web/controller/order
git commit -m "feat(order): POST /checkout, GET /checkout/{key}/status (order.md §4)"
```

---

## Self-Review

**Spec coverage:** order.md §3.1 (order number) → Task 4. §3.2 (Fase 1) → Task 8. §3.4 (cart
validation) → Task 8. §3.5 (line snapshot) → Tasks 1, 5, 8. §3.7 (geocoding/shipping) → Tasks 2, 3,
8. §4 (endpoints) → Task 10. §5 (request DTOs) → Task 10. §9 (Fase-1 error codes) → Task 6.
ADR-011/ADR-017 (idempotency, payload, 10-minute TTL constant) → Task 5, 8.

**Deliberately not covered here (Phase 3b's job, not a gap):** anything that writes to `orders`,
`order_items`, `order_deliveries`, `payments`, or clears the cart; resolving `orderNumber` for a
`COMPLETED` `GetCheckoutStatusService` response (needs `OrderReadPort`); the Redsys notification
endpoint; `FindOrCreateGuestCustomerUseCase`.

**Known incomplete pieces the implementer must finish, flagged explicitly rather than hidden:**
Task 8's `reserveInventoryAndDiscounts` is a stub — the real `RegisterStockMovementUseCase`/
`DiscountReservationPort` wiring must replace it. Task 8/10 both have "note for the implementer"
call-outs (exact `CartItemDto`/`ChargeCommand` shapes post-Phase-2, session-token resolution,
`@AuthenticationPrincipal` type) that depend on reading this codebase's actual current state rather
than this plan's necessarily-approximate draft — `mvn verify` passing is the real completion
signal, not visual similarity to the code above.

**Placeholder scan:** the stub and the "notes for the implementer" are the only intentional
incompleteness, both called out explicitly, not silently.

**Type consistency:** `GeocodingPort`/`GeocodedAddress` (Task 2), `ShippingRatePort`/`ShippingRate`
(Task 3), `OrderNumberPort` (Task 4), `CheckoutIdempotencyPort`/`IdempotencyLock`/
`PendingCheckoutPayload` (Task 5), `OrderErrorCode` and its exceptions (Task 6),
`CheckoutCommand`/`GetCheckoutStatusQuery`/`CheckoutRedirectDto`/`CheckoutStatusDto`/
`CheckoutUseCase`/`GetCheckoutStatusUseCase` (Task 7) are used identically in Tasks 8-10.

---

Plan complete and saved to `docs/superpowers/plans/2026-09-14-order-payment-phase3a-checkout-reserve.md`.
Same three execution options as before — subagent-driven, inline, or hand off to IMPLEMENTER (with
REVIEWER + your `/ultrareview` gate before it's considered closed) — which approach?
