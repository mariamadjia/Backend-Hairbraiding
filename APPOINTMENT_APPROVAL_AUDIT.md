# Appointment approval audit

> Historical findings from before the fixes. Implementation and current verification results are documented in [APPOINTMENT_APPROVAL_FIXES.md](APPOINTMENT_APPROVAL_FIXES.md). The audit tests now assert corrected behavior.

Date: August 30, 2026

**Verdict: both happy paths exist, but the feature is not reliable enough to treat automatic confirmation as guaranteed.** Recovery, appointment-state validation, and customer messaging need correction.

## Scope and evidence

- Backend: settings read/write and validation; customer and owner booking creation; authorization, capture, cancellation, reconciliation and expiration; admin approval/denial/cancellation; webhook processing; notification templates/outbox; appointment status and dashboard actions.
- Frontend source: `/Users/gloriadjonret/Documents/Hair-braiding` contains the exact toggle text and a version-aware settings editor. `/Users/gloriadjonret/Documents/hair-frontend` contains an older copy with a different request contract. Neither was verified as the production deployment.
- Added 12 isolated characterization tests in `src/test/java/org/example/backendbraiding/service/AppointmentApprovalAuditTests.java`. Stripe and persistence dependencies are mocked. Tests named `observedDefect...` assert the existing broken behavior to document reproduction; passing these tests does NOT mean those defects are fixed. Invert their assertions when implementing fixes.
- Full suite: 86 tests discovered, 84 passed, zero failures/errors, two PostgreSQL integration tests skipped because Docker was unavailable.
- No production settings, bookings, payments, emails, or application source were changed. Only this report and audit tests were added.
- No browser session, real database transaction, production logs, Stripe dashboard configuration, or delivery of real email/SMS was tested. This is a source and isolated-execution audit, not production certification.

## Expected and implemented behavior

| Scenario | Current implementation |
| --- | --- |
| Customer booking, approval enabled | Starts PENDING; authorized deposit waits for admin approval. Successful capture changes it to APPROVED and queues confirmation. |
| Customer booking, approval disabled | Still starts PENDING. Authorization webhook sets approvedAt and captures the deposit; successful capture changes it to APPROVED. |
| Setting missing | Defaults to requiring approval. |
| Owner-created booking, no deposit | Owner creation immediately approves it. |
| Owner-created booking, deposit required | Successful deposit payment confirms it, without another approval. The owner has already initiated the booking. |
| Toggle switched while bookings exist | Only saves the setting. Does not process existing pending appointments. The webhook reads the setting at processing time, not a per-booking snapshot. |
| Customer reschedules an approved booking | Retains approval, subject to existing self-service restrictions; does not ask for renewed approval. |

The switch changes local form state until **Save Settings** is clicked. The text should explicitly describe customer bookings and successful deposit payment, plus whether changes apply to existing requests.

## Findings

### 1. P1 — Automatic confirmation can stall permanently, and recovery removes the retry action

**Reproduced in isolated tests.**

`StripeWebhookController.java:130–138` catches capture failures and does not rethrow. `handleEvent` therefore marks the event PROCESSED and returns HTTP 200. The ordinary Stripe retry path cannot recover that failure.

`PaymentService.java:497–508` reconciles payment status but never retries capture or applies automatic approval. If Stripe still reports `requires_capture`, `recordAuthorization` sets local paymentStatus back to AUTHORIZED. It leaves approvedAt populated and appointment status PENDING.

Both frontend copies show Retry capture only for CAPTURE_FAILED. Consequently, after reconciliation, the retry action disappears. Approve, deny, and cancel are blocked by the existing approvedAt marker. If the original authorization webhook is missing altogether, reconciliation also fails to initiate automatic approval.

Reproduction: disable approval; authorize payment; simulate capture failing before Stripe captures; process reconciliation while Stripe still reports requires_capture. Observe PENDING + AUTHORIZED + approvedAt, no capture retry, and no normal dashboard recovery action.

Fix direction: use a durable approval/capture workflow shared by webhook and reconciliation. Preserve failed/in-progress operation state separately from provider payment state; make capture retries idempotent and expose a recovery action. Do not report a failed operation as fully processed unless another durable worker owns recovery.

### 2. P1 — Automatic approval can reopen denied/cancelled bookings or approve past bookings

**Denied and past cases reproduced in isolated tests; cancelled case follows the same unguarded branch.**

`StripeWebhookController.java:126–135` only checks paymentStatus AUTHORIZED. It unconditionally sets appointment status PENDING and approvedAt, then captures. It does not require the current appointment to be pending or its appointment time to be in the future.

A reachable case is a denied/cancelled booking whose authorization release failed, followed by a delayed/retried authorization event. Synchronization sees the still-live authorization; automatic approval can overwrite the denial/cancellation and charge the deposit. A booking denied before authorization is also relevant: `AppointmentService.java:352–355` only releases customer payment intents already marked AUTHORIZED, leaving a still-unconfirmed intent uncancelled.

Manual approval has pending, future-time and authorization-expiry checks (`AppointmentService.java:264–279`); automatic approval bypasses these checks. Reopening a slot already released by denial/cancellation can also conflict with a replacement booking.

Fix direction: use one guarded approval operation for both modes, with transactional state checks. Never transition terminal bookings back to pending on payment events. Cancel outstanding customer payment intents when terminating unpaid bookings, and recover failed releases.

### 3. P1 — Direct capture can charge a booking without approving it

**Reproduced in an isolated test. This endpoint is admin-protected, not a public authorization bypass.**

`PaymentController.java:27–32` exposes an admin capture endpoint. `PaymentService.java:335–359` validates Stripe capture readiness and amount, but not the appointment's approval decision, terminal status or date.

Call capture for an authorized customer booking with approvedAt unset: payment becomes CAPTURED while the appointment stays PENDING because `recordCapture` requires approvedAt before confirming customer bookings (`PaymentService.java:245–246`). Approval then rejects the booking because paymentStatus is no longer AUTHORIZED. Confirmation is not queued.

Fix direction: allow the retry endpoint only for a valid, already-requested approval, and route initial approval through the appointment workflow. Handle captured-but-unconfirmed inconsistencies explicitly rather than charging again.

### 4. P2 — Reconciliation continually extends the local authorization deadline

**Reproduced in an isolated test.**

`PaymentService.java:218` sets paymentAuthorizationExpiresAt to now + six days every time authorization is synchronized. The scheduler reconciles authorized bookings every five minutes by default. Thus a deadline one hour away becomes almost six days away again.

This does not extend Stripe's real authorization. It makes the local expiration guard and dashboard expiry information inaccurate and prevents the intended local six-day release from occurring while reconciliation keeps seeing requires_capture. Stripe cancellation can still eventually be synchronized.

Fix direction: establish the deadline once for each authorization, preferably from provider data, and preserve it during repeated synchronization. Treat replacement authorizations as separate lifecycles.

### 5. P2 — Customer and salon messaging contradict automatic confirmation

**Confirmed by frontend/backend source inspection.**

In `Hair-braiding/components/BookingCalendar.tsx:1141–1155`, the success screen always says Appointment Request Submitted, that the salon will review the request before capture, and that the salon will contact the customer after review. The payment component accepts both requires_capture and succeeded as success, but does not pass a booking approval state to this screen. The checkout policy text at lines 78–79 also assumes salon review.

`AppointmentNotificationTemplates.java:117–124` always tells the salon to approve or deny the request. `PaymentService.recordAuthorization` uses this same template when requireApproval is false.

The customer can therefore see a manual-review message after an automatically captured deposit. The public booking-status endpoint returns payment state, not appointment approval state; payment success alone cannot resolve every inconsistency described above.

Fix direction: return a token-protected booking state and render awaiting authorization, awaiting review, confirmation processing, confirmed, and failure appropriately. Use a separate salon message for automatic confirmation. Ensure policy text describes the selected workflow accurately.

### 6. P2 — Settings load failures silently display defaults as though they were saved values

**Confirmed by source inspection in both frontend copies.**

`Hair-braiding/components/AppointmentSettingsTab.tsx:69–97` only handles successful responses; non-2xx responses are ignored, and network errors are logged without setting a user-visible error. Loading ends with the initial requireApproval=true state still displayed.

An expired session, network outage or server error can therefore make the screen appear to show approval enabled when the saved setting is disabled. The same concern applies to the other settings initialized alongside it.

Fix direction: distinguish unloaded, failed and loaded state; display a fetch error with retry, and disable saving until authoritative settings have loaded.

### 7. P2 — Settings save response may contain the pre-commit version

**Source-level persistence concern; not reproduced against a database in this audit.**

`AppointmentService.java:728–730` calls save and immediately maps the version into a DTO inside a transaction. AppointmentSettings uses Hibernate @Version, which normally increments when the update is flushed. There is no explicit flush before mapping the response. The frontend reuses that returned version (`Hair-braiding/components/AppointmentSettingsTab.tsx:125–126`).

For a managed settings entity with no incidental flush, save can return the old version, while transaction commit increments the database version. A second save from the same screen then fails the version check even without another editor. A related problem is that this OptimisticLockingFailureException falls into the generic HTTP 500 handler, so the useful reload message is hidden.

Fix direction: flush before mapping the saved version, return a conflict response for stale versions, and reload the current settings on conflict. Verify with a real transactional integration test: GET, PUT, PUT again using the first PUT's returned version; then test two genuinely competing editors.

### 8. Conditional P1 — Older frontend copy cannot save to this backend contract

**Contract mismatch confirmed; deployment impact unknown.**

`hair-frontend/components/AppointmentSettingsTab.tsx:9–15` and its save request omit version, bufferTimeBetweenAppointments and timezone. All three are mandatory in the current backend AppointmentSettingsDTO. Requests from that copy fail validation, so its toggle cannot persist against this backend.

The `Hair-braiding` settings component includes those fields. Verify which frontend source/build is deployed before attributing this mismatch to production. The shared `lib/api/appointments.ts` settings type in the newer copy is also outdated, although the inspected settings component does not use it for saving.

## Behavior that appears sound within the audited scope

- Admin method security is enabled. Settings writes, appointment approval and direct capture require ADMIN; the public payments URL matcher does not remove method-level protection.
- Settings require a non-null Boolean and default to approval enabled; missing settings fail closed in the webhook.
- Customer bookings use manual capture. Manual approval validates pending status, a future appointment, authorization and the local expiry deadline, then schedules capture after the approval transaction commits.
- A normal successful capture confirms the booking and queues customer confirmation. Sequential repeated success synchronization does not queue another confirmation, as tested. Concurrent delivery was not database-tested.
- Capture uses an idempotency key and rejects partial deposit capture.
- Webhooks verify signatures; malformed signatures are rejected. Missing webhook configuration returns service unavailable. Events that throw outside the swallowed capture branch return an error for retry.
- Owner-created appointments intentionally have their own confirmation flow, and customer rescheduling retains approval.

## Policy and deployment checks still needed

1. Decide whether changing the toggle affects only new bookings, not-yet-authorized bookings, or all pending requests. Current behavior is tied to webhook processing time and does not snapshot the decision.
2. Verify the deployed frontend revision, then test enable → save → reload → disable → save → reload using the real admin session.
3. Confirm webhook endpoint/secret and event subscriptions for authorization, success, failure and cancellation in the deployed Stripe environment. Check delivery failures without exposing secrets.
4. In a test environment, cover delayed/duplicate events, a provider outage before capture, an ambiguous timeout after capture, cancellation racing authorization, and an appointment time passing before a delayed event.
5. Run real database transaction tests for settings version responses and competing approval/cancellation actions. Test email/SMS delivery and dashboard recovery end to end.

## Suggested repair order

1. Centralize guarded approval and durable capture/release recovery; preserve terminal states and fix the direct-capture guard.
2. Preserve authorization deadlines and operation failures during reconciliation.
3. Correct settings load/save/version behavior and align the deployed frontend contract.
4. Make customer/salon messaging reflect actual booking state and clarify toggle scope.
5. Convert audit characterizations into regression tests asserting the repaired behavior, then execute database and test-mode Stripe/browser scenarios.

## Reproduce the local test run

The first attempt could not attach Mockito dynamically under the local JDK/sandbox. Supplying the already-installed Mockito agent explicitly resolved it; no build configuration changes were necessary.

```sh
./mvnw -q -DargLine=-javaagent:/Users/gloriadjonret/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar test
```

This machine-specific path should be replaced with the local Maven dependency path on other machines. The full run log is `/tmp/appointment-approval-audit-tests.log`; Maven reports are in `target/surefire-reports`.
