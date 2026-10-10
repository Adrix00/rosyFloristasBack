# Phase 5 — Redsys hardening and real-API test coverage

Branch: `feature/orderPayments`, on top of Phase 4 (`209974a`, closed by REVIEWER). Origin: REVIEWER's
payment test audit found that no test anywhere calls Redsys or Google Maps, and that this gap hides
three real defects. Scope is the `payment` module's Redsys adapter, `order`'s use of the order
number as Redsys's order id, and the tests. No new endpoints, no schema change.

## Defects to fix first (each with a failing test before the fix)

**A. `refund()` throws NPE on a Redsys rejection.** `parseRefundResponse` Base64-decodes
`Ds_MerchantParameters` without a null check. Redsys's REST endpoint answers a rejection with a body
like `{"errorCode":"SIS0xxx"}` and no `Ds_MerchantParameters`. Required behavior: a rejection, a non-200
status, a malformed body, a connection failure and a timeout all return a failed
`GatewayRefundResult` (never an uncaught exception); a success response has its `Ds_Signature`
verified with `RedsysSignature`, like charge and notification already do; an invalid signature is a
failed result, not a success.

**B. `Ds_Merchant_Order` is invalid for Redsys.** We send our business number
(`WEB-20260826-000001`, 19 chars, non-numeric prefix). The local Redsys manual
(`docs/pasarelaGetNet/GetNet.md`) states a standard order number is at most 12 positions and that
exceeding it returns error `SIS0014`; extended numbers (up to 256) need the terminal to be enabled for
them, which the contracted terminal is not known to be. Redsys also requires the first 4 characters to
be numeric. Every real payment would be rejected, and mock tests can never see it.

Decision (ARCHITECT): the value sent to Redsys as `Ds_Merchant_Order` is **the order number's
sequence part zero-padded to 12 digits** (`WEB-20260826-000001` → `000000000001`), derived
deterministically, no new column, no new table. Reasons: the sequence is already globally unique and
never reset (order.md §3.1), so uniqueness is inherited; 12 numeric digits satisfies both the length
and the numeric-prefix rule; it works on any terminal without bank-side configuration. Implementation:
one small pure function (in the `payment` module, next to `RedsysSignature`; something like
`RedsysOrderId.from(OrderNumber)`) used by `charge()`, by the refund call and by the per-order 3DES key
diversification (which must use the exact string Redsys sees). The notification handler receives that
12-digit value back in `Ds_Order`; it must not use it to look anything up (the lookup key stays
`Ds_MerchantData` = idempotency key, ADR-017), and `ConfirmCheckoutCommand.orderNumber` must come from
the lock's resource id / pre-minted order, not from `Ds_Order`. Verify in `ReceiveRedsysNotificationService`
where it takes the order number today and fix if it takes it from `Ds_Order`.

Amend ADR-017 (the section that says the order number becomes `Ds_Merchant_Order`) and the two mentions
in `payment.md`/`order.md` to describe this mapping. Documentation must state what is implemented.

**C. Base64 alphabet on notifications.** Redsys notifications may arrive URL-safe (`-_`) instead of
standard (`+/`). `ReceiveRedsysNotificationService` and `RedsysSignature` use the standard decoder
only. Required: decode `Ds_MerchantParameters` tolerantly (accept both alphabets) and compare
`Ds_Signature` after normalizing both alphabets, still with a constant-time comparison. Signature
verification remains the first thing that happens, before any state change.

## Test tasks

1. **Refund unit tests, no new dependency.** `RedsysProperties.restUrl` already makes the URL
   configurable; use the JDK's `com.sun.net.httpserver.HttpServer` on localhost in the test. Cases:
   success `0000`; `Ds_Response` outside 0000-0099; `{"errorCode":...}` body (drives fix A); non-200;
   valid JSON with an invalid response signature; connection refused; timeout.
2. **Signature known-answer test.** One test using a documented Redsys example (order, merchant
   parameters, expected signature) from Redsys's own integration manual or sample code, so a wrong
   3DES/HMAC implementation cannot pass on self-consistency alone. If the local docs contain no such
   vector, say so and use the sandbox test below as the source of truth; do not invent an expected value.
   Plus a notification fixture with URL-safe Base64 (drives fix C) and one for `RedsysOrderId` (fix B).
3. **Geocoding client unit tests.** `GoogleMapsGeocodingClient` has none. Same local `HttpServer`
   technique (make the base URL a `GeocodingProperties` property if it is not already): `OK`;
   `ZERO_RESULTS` → `DeliveryAddressNotGeocodableException`; `OVER_QUERY_LIMIT`/`REQUEST_DENIED`;
   malformed JSON. Follow the existing behavior for these cases; if a case has no defined behavior,
   ask ARCHITECT rather than choosing.
4. **Opt-in sandbox tests (real calls).** Classes named `*SandboxTest`, `@Tag("sandbox")`, plus
   `Assumptions.assumeTrue(reachable(host))` so an offline run skips rather than fails.
   Public sandbox values (safe to keep in the test): merchant `999008881`, key
   `sq7HjrUOBfKmC576ILgskD5srU870gJ7`, `sis-t.redsys.es:25443`. Tests: (i) REST authorization with the
   documented test card, then our `refund()` on that same order, asserting success and a verifying
   response signature; (ii) our `charge()` output is accepted by Redsys as well-formed (signature and
   order-format, covering fix B; a browser is needed to complete a payment, so assert only that Redsys
   does not answer with a signature or order-format error); (iii) a tampered signature is rejected by
   Redsys with its signature error. Verify against the Redsys docs that (i) is possible on the sandbox
   before writing it; if it is not, report and skip that case, do not fake it.
   Google Maps real test: `@EnabledIfEnvironmentVariable(named = "GOOGLE_MAPS_API_KEY", matches = ".+")`,
   so it reports as skipped without a key (the user has not supplied one yet).
5. **Keep sandbox tests out of the default build.** surefire `<excludedGroups>sandbox</excludedGroups>`
   by default; a `sandbox` Maven profile that clears the exclusion and sets `<groups>sandbox</groups>`,
   run manually or in a separate scheduled job, never on PRs. No new plugin. Sandbox failures are
   advisory, not release-blocking (the sandbox can be down or rate-limit).

## Order of work and checks

Fix tests first (A, B, C each red before green), then the mock/local-server tests (1-3), then the
sandbox tests (4-5). Commits per task as usual. `mvn clean verify` must stay green with Docker up and
without network: the default run must not depend on Redsys or Google being reachable. Then run
`mvn -Psandbox test` once manually and report which sandbox cases passed, failed or were skipped, with
Redsys's actual error codes for any failure. A failing sandbox case that reveals another defect in the
adapter is expected value, not a reason to weaken the test.

## Out of scope

Saved cards (`paymentMethodId`, Phase 6 in the original breakdown), `POST /orders/counter`, admin order
listing, the reservation-expiry cleanup task, and any Google Maps real-call test beyond the
key-gated one.
