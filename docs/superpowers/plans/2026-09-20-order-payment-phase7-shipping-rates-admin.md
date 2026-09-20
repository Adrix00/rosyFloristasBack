# Phase 7 — Admin-configurable shipping rates (order.md §3.7, §4, §7-9)

Branch: `feature/orderPayments`. Start only after the geocoding/checkout-transaction fixes are committed
and REVIEWER has passed them. Spec is already written in `order.md` (§3.7 rules, §4 endpoints, §7 use
cases, §8 ports, §9 errors); read those sections first, do not re-decide them.

Goal: an `ADMIN` manages delivery bands (`shipping_rates`: `min_km`, `max_km`, `price`,
`free_from_amount`, `active`) from the panel, and the delivery radius stops being a separate constant.

## Decisions already made

- No schema change: `shipping_rates` already has the checks and the `ex_shipping_rates_no_overlap`
  exclusion constraint for active bands. No `version` column (no ADR-009 change): concurrent edits are
  serialized by locking all `shipping_rates` rows (`SELECT ... FOR UPDATE`) inside the write transaction.
- No delete, only `active = false`. Editing never touches existing orders (they store `shipping_cost`).
- Radius = `max_km` of the furthest ACTIVE band. Remove `MAX_DELIVERY_DISTANCE_KM` from
  `CheckoutService`; `DELIVERY_OUT_OF_RANGE` means "no active band covers this distance", and its
  `maxDistanceKm` detail comes from `ShippingRatePort.findMaxDeliveryDistanceKm()`. Fixes REVIEWER's
  note that the detail was a constant unrelated to the bands.
- Coherence rules (service, after every change, over the resulting set of active bands, sorted by
  `min_km`): at least one active band; first `min_km = 0`; each `min_km` equals the previous `max_km`
  (no gaps, no overlaps). Violation -> 422 `SHIPPING_RATES_INVALID` with a message saying which rule,
  nothing changed. The DB constraint stays as backstop; if it fires anyway, translate it to the same
  error (not a 500).
- Store location stays in configuration (not in the panel).

## Tasks (11-step workflow, commits per task, red-first where it fixes behavior)

1. `SHIPPING_RATE_NOT_FOUND` (404) and `SHIPPING_RATES_INVALID` (422) in `OrderErrorCode` plus their
   exception classes (NotFound / Unprocessable base classes, `HasErrorCode`), fixed messages, no PII.
2. Domain: a `ShippingRate` domain type if none exists (today only an application DTO is used by the
   read port; check first and reuse if a suitable type exists), with the per-band invariants
   (`max_km > min_km`, `min_km >= 0`, `price >= 0`, `free_from_amount` null or > 0). The set-level
   coherence rule (contiguity) is a pure function over a list of bands: put it where it is unit-testable
   without Spring (domain service or a static method on a small value type), not in the controller or
   the adapter.
3. Ports and persistence (JPA for writes/simple reads, JDBC for reads only if a filter is needed; ADR-002):
   extend `ShippingRatePort` as listed in order.md §8 (`findAll`, `findAllForUpdate` with a pessimistic
   lock, `findById`, `save`, `findMaxDeliveryDistanceKm`). Remember the lesson of Phases 4-5: an adapter
   that updates an existing row needs a transactional real-DB test.
4. Use cases: `GetShippingRatesUseCase`, `CreateShippingRateUseCase`, `UpdateShippingRateUseCase`
   (`@PreAuthorize("hasRole('ADMIN')")`). Create/update: lock all rows, apply the change to the in-memory
   set, check coherence, then save; one transaction. Audit each write per ADR-010 exactly like the other
   admin writes (find the existing pattern, e.g. inventory or product, and reuse the same mechanism; do not
   invent a new one).
5. Web: requests `CreateShippingRateRequest` / `UpdateShippingRateRequest` (Bean Validation:
   `@NotNull`, `@DecimalMin`, `@Digits` matching the NUMERIC(5,2)/(10,2) columns; DTOs in
   `web.request.order` so the 422 code derives as `ORDER_VALIDATION_FAILED`), response
   `ShippingRatesResponse` (bands plus `deliveryRadiusKm`), controller `ShippingRateController`
   (`GET/POST /shipping-rates`, `PUT /shipping-rates/{id}`), CSRF applies to POST/PUT.
6. Wire the checkout: `CheckoutService` uses `findMaxDeliveryDistanceKm()` for the out-of-range detail and
   drops the constant; a distance that no active band covers still throws `DELIVERY_OUT_OF_RANGE`. Existing
   delivery tests keep passing (the seed bands are unchanged).
7. Tests: unit tests for the coherence function (ok, no active band, first band not starting at 0, gap,
   overlap, single band, unordered input); service tests with mocked ports; controller tests with the real
   `GlobalExceptionHandler` (200/201, 404, 422 with the two codes, 403 for a non-admin, csrf on POST/PUT);
   a real-DB Testcontainers test through the real transactional services: change a band's price and see
   the next checkout quote use it; deactivating the last band is refused; creating a band that leaves a gap
   is refused; extending the last band's `max_km` to 15 makes a 12 km address deliverable and changes the
   `maxDistanceKm` detail; two concurrent updates serialize (no lost update, no incoherent final set).
8. Docs stay with ARCHITECT (order.md is done); report anything in the spec that turned out wrong.

## Done when

`mvn clean verify` green (Docker up, no API key, one Maven session at a time), REVIEWER's pass clean.
