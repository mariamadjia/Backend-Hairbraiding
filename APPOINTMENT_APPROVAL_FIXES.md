# Appointment approval fixes

Implemented August 30, 2026. Changes are local; not deployed.

## Backend

- New bookings snapshot `requireApproval`. Migration V48 conservatively assigns manual review to legacy bookings because their booking-time policy is unknown. An existing approval/capture request (`approvedAt`) is still recovered.
- `PaymentRecoveryService` coordinates committed payment synchronization followed by guarded capture or cancellation. Both webhooks and scheduled reconciliation use it. A failed capture returns a webhook error and remains retryable by the worker and dashboard.
- Capture failures and cancellation failures survive synchronization; later failures cannot overwrite a completed capture. Reconciliation recovers an ambiguous successful capture without charging again.
- Payment state transitions and admin decisions lock the appointment. Automatic approval does not reopen denied/cancelled appointments or authorize past/expired bookings. Direct capture requires an approval decision. Outstanding unpaid customer intents are cancelled on denial/cancellation.
- Authorization deadlines are established once per authorization, rather than extended at every poll. Expired authorizations are released; expiry notification and event are written in the cancellation transaction.
- Settings use `saveAndFlush` before returning the version. Optimistic locking failures return HTTP 409.
- Token-protected booking status includes `appointmentStatus`, `paymentStatus`, `requireApproval`, and `approvalRequested`.
- Salon messages distinguish automatic confirmation from manual review.
- A future, already-captured but unapproved legacy booking can be explicitly approved from the dashboard without another capture charge. It appears in the approval workflow.

## Frontend

Updated `/Users/gloriadjonret/Documents/Hair-braiding`, plus the older `/Users/gloriadjonret/Documents/hair-frontend` copy's affected source files:

- Settings load failures display an error/retry state; unsaved defaults are not shown as authoritative settings.
- Both copies send required version, timezone, and buffer fields. Saving uses the returned version; conflicts reload saved values for review. Controls are disabled during save.
- Toggle copy explains that changes apply to new customer bookings after saving and confirmation follows successful payment.
- Booking confirmation uses actual token-protected appointment status. Payment submission alone does not display “confirmed.” Capture processing is distinct from manual review. Network errors tell customers not to submit another booking.
- Confirmation status polls while processing, and stops on confirmed/denied/cancelled or awaiting manual review.
- The dashboard exposes approval for eligible legacy captured-but-unconfirmed bookings.

## Verification

- Backend: 98 tests discovered; 96 passed, zero failures/errors, two PostgreSQL tests skipped because Docker is unavailable.
- Includes 21 approval/recovery regression tests, two settings service tests, and a new automatic-notification test. Settings tests exercise service behavior with repositories mocked; real Hibernate flush/version behavior still needs database integration verification.
- Frontend: 11 tests passed, including toggle saves, version reuse, conflict reload, load failure and status messaging.
- The `Hair-braiding` production build passed. Source-only TypeScript checking passed. The initial standalone check found a stale generated `.next/dev` reference to a missing policies page; the production build regenerated its own route types successfully.
- Both repository diffs pass whitespace checks.
- The older `hair-frontend` copy has no installed dependencies, so it was updated by source inspection but was not independently built.
- No real Stripe calls, charges, live bookings, messages or production settings were used for verification.

Commands used:

```sh
./mvnw -q -DargLine=-javaagent:/Users/gloriadjonret/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar test
```

In `/Users/gloriadjonret/Documents/Hair-braiding`:

```sh
npm test -- --reporter=dot
npm run build
```

## Release checks

1. Deploy the backend and run Flyway V48 before deploying the frontend that consumes the additional booking-status fields.
2. Verify which frontend project is deployed; `Hair-braiding` is the copy built and tested here.
3. Existing future bookings with approval already requested and failed capture will be retried automatically. Legacy unapproved bookings retain manual review. No bulk approval is performed merely by changing the setting.
4. In Stripe test mode verify both settings, save/reload/re-save, missing webhooks, capture outage/retry, duplicate events, expired authorization, cancellation racing payment, and confirmation email/SMS delivery.
5. Run PostgreSQL integration tests and inspect deployment webhook configuration and delivery health. These were not verified locally.
