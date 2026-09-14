# Phase 3a — Implementation approach and real-code deviations

**Goal:** execute the approved Phase 3a plan (`2026-09-14-order-payment-phase3a-checkout-reserve.md`) task-by-task, then run `mvn verify` green. No Phase 3b work starts until `/ultrareview` passes on this phase.

## Discoveries from reading the live codebase

These affect the literal draft in the ARCHITECT plan and must be resolved during implementation:

1. **Cart pricing pipeline.** `CartItemDto` is built by `CartDtoAssembler` from `CartCatalogEntryDto`, which comes from `CartProjectionJdbcRepository.findCatalogEntries`. Adding `productAttributes` and `discountId` therefore requires changes in three layers:
   - `CartCatalogEntryDto` gains the two fields.
   - `CartProjectionJdbcRepository.findCatalogEntries` selects `p.attributes` (JSONB) and the active discount `id` alongside `sale_price`.
   - `CartDtoAssembler.toItemDto` passes them through.
   `ProductActiveSalePriceSql.CORRELATED_SUBQUERY` currently only returns `sale_price` and is reused by product search/admin listing; I will **not** modify it. The cart projection will use a local correlated subquery that returns both `sale_price` and `discount_id` (or `NULL`), keeping the existing shared SQL stable.

2. **Inventory reservation.** `RegisterStockMovementUseCase.execute(UUID productId, StockMovementType.SALE, int quantity, UUID adminUserId, String note)` is the right primitive overload. `InventoryInsufficientStockException` does **not** carry the available quantity, so `CheckoutService` must read current stock after catching it (diagnostic read, same as `RegisterStockMovementService.insufficientOrNotManaged`) to build `CheckoutInsufficientStockException` with `availableQuantity`.

3. **Discount reservation.** `DiscountReservationPort.reserve(DiscountId, int quantity)` returns `boolean`; `false` becomes `CheckoutDiscountExhaustedException`.

4. **`OrderPaymentPort.charge` is primitive-parameter.** Phase 2 ended with `OrderPaymentPort.charge(OrderId, OrderNumber, BigDecimal, PaymentMethod, String paymentMethodToken, String idempotencyKey)` and `ChargeOrderPaymentUseCase` has a default overload. `CheckoutService` will call the primitive form directly; no `ChargeCommand` record exists anymore.

5. **Jackson 3.** All ObjectMapper references must use `tools.jackson.databind.ObjectMapper` and `tools.jackson.core.JacksonException`, matching `RateLimitFilter` and `RedsysPaymentGateway`. Spring Boot auto-configures the bean because the pom pins `jackson-bom.version=3.1.5`.
6. **Configuration properties registration.** `AppApplication` currently lists `RateLimitProperties` and `RedsysProperties` in `@EnableConfigurationProperties`. Any new `@ConfigurationProperties` classes created in Phase 3a (e.g. `GoogleMapsProperties`) must be added there.

6. **Auth / session-token resolution, copied from `CartController`.**
   - Current customer id: read from the JWT via `SecurityContextHolder`, checking `subject_type = CUSTOMER` (`AccessTokenJwtClaims.SUBJECT_TYPE`).
   - Guest session token: read from the `cart_session` cookie (`CartController.CART_SESSION_COOKIE`).
   These are controller-level concerns and will be mirrored exactly in `CheckoutController`, not delegated to `@AuthenticationPrincipal` because the codebase does not use that annotation anywhere today.

7. **Web layer pattern.** Controller tests use `@WebMvcTest(XController.class) @Import({WebMapper.class, SecurityConfig.class})` and `@MockitoBean` for use cases plus `PiiCryptoPort` and `AccessTokenPort` (needed by `SecurityConfig`/`RateLimitFilter`). `CheckoutControllerTest` follows the same shape.

8. **SpotBugs / ArchUnit habits already applied in Phase 2.** New records with `Map`/`List` fields get defensive-copy compact constructors; `ObjectMapper` injected into infrastructure is rebuilt (`objectMapper.rebuild().build()`) to avoid `EI_EXPOSE_REP2`; no wildcard imports.

## Task breakdown and commits (same as the approved plan)

1. **Task 1 — CartItemDto enrichment** (`feat(cart): expose productAttributes/discountId on CartItemDto`).
2. **Task 2 — GeocodingPort + Google Maps adapter** (`feat(order): GeocodingPort + Google Maps adapter`).
3. **Task 3 — ShippingRatePort over JDBC** (`feat(order): ShippingRatePort over JDBC`).
4. **Task 4 — OrderNumberPort** (`feat(order): OrderNumberPort`).
5. **Task 5 — CheckoutIdempotencyPort** (`feat(order): CheckoutIdempotencyPort over idempotency_keys`).
6. **Task 6 — OrderErrorCode + Fase-1 exceptions** (`feat(order): OrderErrorCode and Fase-1 exceptions`).
7. **Task 7 — CheckoutUseCase / GetCheckoutStatusUseCase ports** (`feat(order): CheckoutUseCase/GetCheckoutStatusUseCase ports`).
8. **Task 8 — CheckoutService with real inventory/discount wiring** (`feat(order): CheckoutService — Fase 1`).
9. **Task 9 — GetCheckoutStatusService** (`feat(order): GetCheckoutStatusService`).
10. **Task 10 — `POST /checkout`, `GET /checkout/{key}/status`** (`feat(order): POST /checkout, GET /checkout/{key}/status`).

Each task is test-driven (failing test → implementation → green test → commit) as CLAUDE.md prescribes.

## Open questions / decisions for the user

- **Google Maps API key:** the plan requires `GOOGLE_MAPS_API_KEY` with no checked-in default. Startup will fail fast if unset, matching the plan. Tests mock `GeocodingPort`, so `mvn verify` does not need a real key.
- **Store coordinates:** default to Madrid Puerta del Sol per the plan, flagged as a placeholder.

## Verification

- `mvn verify` (or `mvn clean verify` if JaCoCo complains) must pass before exiting plan mode / before Phase 3a is considered done.
