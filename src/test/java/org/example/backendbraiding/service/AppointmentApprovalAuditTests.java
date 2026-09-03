package org.example.backendbraiding.service;

import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCaptureParams;
import org.example.backendbraiding.controller.StripeWebhookController;
import org.example.backendbraiding.dto.PaymentCaptureRequest;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.AppointmentSettings;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.example.backendbraiding.repository.AppointmentSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression tests. Stripe and repositories are isolated; no real cards are charged. */
class AppointmentApprovalAuditTests {
    private AppointmentRepository appointments;
    private AppointmentSettingsRepository settingsRepository;
    private AppointmentSettings settings;
    private Appointment appointment;
    private PaymentService payments;
    private PaymentIntent intent;
    private NotificationOutboxService outbox;

    @BeforeEach
    void setUp() {
        appointments = mock(AppointmentRepository.class);
        settingsRepository = mock(AppointmentSettingsRepository.class);
        settings = new AppointmentSettings();
        settings.setRequireApproval(false);
        when(settingsRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(settings));
        appointment = new Appointment();
        appointment.setId(1L);
        appointment.setRequireApproval(false);
        appointment.setPaymentIntentId("pi_audit");
        appointment.setAppointmentDateTime(LocalDateTime.now().plusDays(2));
        when(appointments.findByPaymentIntentId("pi_audit")).thenReturn(Optional.of(appointment));
        when(appointments.findByPaymentIntentIdForUpdate("pi_audit")).thenReturn(Optional.of(appointment));
        when(appointments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointments.findAppointmentsNeedingPaymentReconciliation()).thenReturn(List.of(appointment));
        var templates = mock(AppointmentNotificationTemplates.class);
        var notification = new AppointmentNotificationTemplates.Notification("subject", "body", "sms");
        when(templates.adminNewBooking(any())).thenReturn(notification);
        when(templates.pending(any())).thenReturn(notification);
        when(templates.expired(any())).thenReturn(notification);
        when(templates.approved(any(), any())).thenReturn(notification);
        when(templates.ownerDepositPaid(any(), any())).thenReturn(notification);
        outbox = mock(NotificationOutboxService.class);
        var tokens = mock(AppointmentManagementTokenService.class);
        when(tokens.issue(any())).thenReturn("https://example.test/manage/audit");
        payments = new PaymentService(appointments,
                mock(BookingPaymentTokenService.class), outbox, mock(AppointmentEventService.class),
                templates, tokens,
                mock(OwnerDepositTokenService.class));
        intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn("pi_audit");
        when(intent.getStatus()).thenReturn("requires_capture");
        when(intent.getAmount()).thenReturn(5000L);
        when(intent.getAmountCapturable()).thenReturn(5000L);
        when(intent.getAmountReceived()).thenReturn(5000L);
    }

    private void synchronize() {
        try (var stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_audit")).thenReturn(intent);
            payments.synchronizePaymentIntent("pi_audit");
        }
    }

    private StripeWebhookController controller(PaymentService service) {
        return new StripeWebhookController(mock(StripeWebhookEventService.class), mock(NoShowService.class), new PaymentRecoveryService(service, appointments));
    }

    private void authorizedWebhook(PaymentService service) {
        ReflectionTestUtils.invokeMethod(controller(service),
                "handlePaymentIntentAmountCapturableUpdated", "pi_audit");
    }

    @Test
    void enabledSettingLeavesAuthorizationPendingAndNotifiesCustomer() {
        settings.setRequireApproval(true);
        appointment.setRequireApproval(true);
        synchronize();
        assertEquals(Appointment.AppointmentStatus.PENDING, appointment.getStatus());
        assertEquals(Appointment.PaymentStatus.AUTHORIZED, appointment.getPaymentStatus());
        assertNull(appointment.getApprovedAt());
        verify(outbox).enqueueCustomerAndSalon(eq(appointment), any(), any(), any(), any(), any(), any());
        var mockPayments = mock(PaymentService.class);
        authorizedWebhook(mockPayments);
        verify(mockPayments, never()).capturePayment(any());
    }

    @Test
    void disabledSettingConfirmsAfterSuccessfulWebhookCapture() throws Exception {
        when(intent.capture(any(PaymentIntentCaptureParams.class), any(RequestOptions.class))).thenAnswer(call -> {
            when(intent.getStatus()).thenReturn("succeeded");
            return intent;
        });
        try (var stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_audit")).thenReturn(intent);
            authorizedWebhook(payments);
        }
        assertEquals(Appointment.AppointmentStatus.APPROVED, appointment.getStatus());
        assertEquals(Appointment.PaymentStatus.CAPTURED, appointment.getPaymentStatus());
        verify(outbox).enqueueBoth(eq(appointment), any(), any(), any());
    }

    @Test
    void missingSettingsFailClosedToManualApproval() {
        when(settingsRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.empty());
        appointment.setRequireApproval(null);
        appointment.setPaymentStatus(Appointment.PaymentStatus.AUTHORIZED);
        var mockPayments = mock(PaymentService.class);
        authorizedWebhook(mockPayments);
        verify(mockPayments, never()).capturePayment(any());
    }

    @Test
    void reconciliationAutoApprovesWithoutWebhook() throws Exception {
        when(intent.capture(any(PaymentIntentCaptureParams.class), any(RequestOptions.class))).thenReturn(intent);
        try (var stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_audit")).thenReturn(intent);
            new PaymentRecoveryService(payments, appointments).reconcilePaymentStates();
        }
        assertEquals(Appointment.PaymentStatus.CAPTURED, appointment.getPaymentStatus());
        assertEquals(Appointment.AppointmentStatus.APPROVED, appointment.getStatus());
        assertNotNull(appointment.getApprovedAt());
    }

    @Test
    void reconciliationRetriesCaptureFailure() throws Exception {
        when(intent.capture(any(PaymentIntentCaptureParams.class), any(RequestOptions.class))).thenReturn(intent);
        appointment.setApprovedAt(LocalDateTime.now());
        appointment.setPaymentStatus(Appointment.PaymentStatus.CAPTURE_FAILED);
        try (var stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_audit")).thenReturn(intent);
            new PaymentRecoveryService(payments, appointments).reconcilePaymentStates();
        }
        assertEquals(Appointment.PaymentStatus.CAPTURED, appointment.getPaymentStatus());
        assertEquals(Appointment.AppointmentStatus.APPROVED, appointment.getStatus());
        assertNotNull(appointment.getApprovedAt());
        verify(intent).capture(any(PaymentIntentCaptureParams.class), any(RequestOptions.class));
    }

    @Test
    void reconciliationPreservesAuthorizationDeadline() {
        appointment.setPaymentStatus(Appointment.PaymentStatus.AUTHORIZED);
        LocalDateTime original = LocalDateTime.now().plusHours(1);
        appointment.setPaymentAuthorizationExpiresAt(original);
        synchronize();
        assertEquals(original, appointment.getPaymentAuthorizationExpiresAt());
    }

    @Test
    void autoApprovalNeverReopensDeniedAppointment() {
        appointment.setStatus(Appointment.AppointmentStatus.DENIED);
        appointment.setPaymentStatus(Appointment.PaymentStatus.AUTHORIZED);
        var mockPayments = mock(PaymentService.class);
        authorizedWebhook(mockPayments);
        assertEquals(Appointment.AppointmentStatus.DENIED, appointment.getStatus());
        verify(mockPayments, never()).capturePayment(any());
        verify(mockPayments).cancelPayment("pi_audit");
    }

    @Test
    void autoApprovalRejectsPastAppointments() {
        appointment.setAppointmentDateTime(LocalDateTime.now().minusDays(1));
        appointment.setPaymentStatus(Appointment.PaymentStatus.AUTHORIZED);
        var mockPayments = mock(PaymentService.class);
        authorizedWebhook(mockPayments);
        assertNull(appointment.getApprovedAt());
        verify(mockPayments, never()).capturePayment(any());
        verify(mockPayments).cancelPayment("pi_audit");
    }

    @Test
    void captureFailureRequestsWebhookRetry() {
        appointment.setPaymentStatus(Appointment.PaymentStatus.AUTHORIZED);
        appointment.setApprovedAt(LocalDateTime.now());
        var mockPayments = mock(PaymentService.class);
        when(mockPayments.capturePayment(any())).thenThrow(new IllegalStateException("simulated outage"));
        var events = mock(StripeWebhookEventService.class);
        when(events.begin("evt_audit", "payment_intent.amount_capturable_updated")).thenReturn(true);
        var controller = new StripeWebhookController(events, mock(NoShowService.class), new PaymentRecoveryService(mockPayments, appointments));
        Event event = mock(Event.class, RETURNS_DEEP_STUBS);
        when(event.getId()).thenReturn("evt_audit");
        when(event.getType()).thenReturn("payment_intent.amount_capturable_updated");
        when(event.getDataObjectDeserializer().getObject()).thenReturn(Optional.of(intent));
        ResponseEntity<String> response = ReflectionTestUtils.invokeMethod(controller, "handleEvent", event);
        assertNotNull(response);
        assertEquals(500, response.getStatusCode().value());
        verify(events, never()).processed("evt_audit");
        verify(events).failed(eq("evt_audit"), any());
        verify(mockPayments).markCaptureFailed("pi_audit", "simulated outage");
    }

    @Test
    void directCaptureRequiresApprovalBeforeContactingStripe() throws Exception {
        try (var stripe = mockStatic(PaymentIntent.class)) {
            assertThrows(IllegalStateException.class,
                    () -> payments.capturePayment(new PaymentCaptureRequest("pi_audit", null)));
            stripe.verifyNoInteractions();
        }
        assertEquals(Appointment.PaymentStatus.PENDING, appointment.getPaymentStatus());
        assertEquals(Appointment.AppointmentStatus.PENDING, appointment.getStatus());
        verify(outbox, never()).enqueueBoth(any(), any(), any(), any());
    }

    @Test
    void ownerDepositConfirmsWithoutSecondAdminApproval() {
        settings.setRequireApproval(true);
        appointment.setRequireApproval(true);
        appointment.setBookingSource(Appointment.BookingSource.OWNER);
        when(intent.getStatus()).thenReturn("succeeded");
        synchronize();
        assertEquals(Appointment.AppointmentStatus.APPROVED, appointment.getStatus());
        verify(outbox).enqueueBoth(eq(appointment), any(), any(), any());
    }

    @Test
    void repeatedSuccessDoesNotSendAnotherConfirmation() {
        appointment.setApprovedAt(LocalDateTime.now());
        when(intent.getStatus()).thenReturn("succeeded");
        synchronize();
        synchronize();
        verify(outbox, times(1)).enqueueBoth(eq(appointment), any(), any(), any());
    }
    @Test
    void synchronizationPreservesFailureAndDoesNotSendAnotherPendingEmail() {
        appointment.setPaymentStatus(Appointment.PaymentStatus.CAPTURE_FAILED);
        appointment.setAmountAuthorized(5000L);
        appointment.setApprovedAt(LocalDateTime.now());
        synchronize();
        assertEquals(Appointment.PaymentStatus.CAPTURE_FAILED, appointment.getPaymentStatus());
        verifyNoInteractions(outbox);
    }

    @Test
    void changingGlobalSettingDoesNotApproveAnExistingManualBooking() {
        appointment.setRequireApproval(true);
        settings.setRequireApproval(false);
        synchronize();
        assertNull(appointment.getApprovedAt());
    }

    @Test
    void captureFailureCannotOverwriteSuccessfulCapture() {
        appointment.setPaymentStatus(Appointment.PaymentStatus.CAPTURED);
        payments.markCaptureFailed("pi_audit", "late failure");
        assertEquals(Appointment.PaymentStatus.CAPTURED, appointment.getPaymentStatus());
    }

    @Test
    void unpaidReservationIsNotCancelledBeforeItsDeadline() {
        appointment.setPaymentPendingExpiresAt(LocalDateTime.now().plusMinutes(10));
        var service = mock(PaymentService.class);
        new PaymentRecoveryService(service, appointments).process("pi_audit");
        verify(service, never()).cancelPayment(any());
        verify(service, never()).capturePayment(any());
    }

    @Test
    void expiredAuthorizationIsReleasedWithoutCapturing() throws Exception {
        appointment.setPaymentStatus(Appointment.PaymentStatus.AUTHORIZED);
        appointment.setPaymentAuthorizationExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(intent.cancel()).thenReturn(intent);
        try (var stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_audit")).thenReturn(intent);
            new PaymentRecoveryService(payments, appointments).process("pi_audit");
        }
        assertEquals(Appointment.AppointmentStatus.CANCELLED, appointment.getStatus());
        verify(intent).cancel();
        verify(intent, never()).capture(any(PaymentIntentCaptureParams.class), any(RequestOptions.class));
        verify(outbox).enqueueBoth(eq(appointment), any(), any(), any());
    }

    @Test
    void ambiguousSuccessfulCaptureIsReconciledWithoutChargingAgain() throws Exception {
        appointment.setApprovedAt(LocalDateTime.now());
        appointment.setPaymentStatus(Appointment.PaymentStatus.CAPTURE_FAILED);
        when(intent.getStatus()).thenReturn("succeeded");
        try (var stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_audit")).thenReturn(intent);
            new PaymentRecoveryService(payments, appointments).process("pi_audit");
        }
        assertEquals(Appointment.AppointmentStatus.APPROVED, appointment.getStatus());
        assertEquals(Appointment.PaymentStatus.CAPTURED, appointment.getPaymentStatus());
        verify(intent, never()).capture(any(PaymentIntentCaptureParams.class), any(RequestOptions.class));
    }

    @Test
    void failedCaptureRecoversOnNextScheduledAttempt() throws Exception {
        when(intent.capture(any(PaymentIntentCaptureParams.class), any(RequestOptions.class)))
                .thenThrow(new IllegalStateException("temporary outage")).thenReturn(intent);
        var recovery = new PaymentRecoveryService(payments, appointments);
        try (var stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_audit")).thenReturn(intent);
            assertThrows(IllegalStateException.class, () -> recovery.process("pi_audit"));
            assertEquals(Appointment.PaymentStatus.CAPTURE_FAILED, appointment.getPaymentStatus());
            assertNotNull(appointment.getApprovedAt());
            recovery.process("pi_audit");
        }
        assertEquals(Appointment.AppointmentStatus.APPROVED, appointment.getStatus());
        verify(intent, times(2)).capture(any(PaymentIntentCaptureParams.class), any(RequestOptions.class));
    }

    @Test
    void cancellationFailureIsPreservedAndNeverAutoApproved() {
        appointment.setPaymentStatus(Appointment.PaymentStatus.CANCELLATION_FAILED);
        synchronize();
        assertEquals(Appointment.PaymentStatus.CANCELLATION_FAILED, appointment.getPaymentStatus());
        assertNull(appointment.getApprovedAt());
    }

    @Test
    void directCaptureRejectsTerminalAppointmentsBeforeStripeLookup() {
        appointment.setStatus(Appointment.AppointmentStatus.DENIED);
        appointment.setApprovedAt(LocalDateTime.now());
        try (var stripe = mockStatic(PaymentIntent.class)) {
            assertThrows(IllegalStateException.class,
                    () -> payments.capturePayment(new PaymentCaptureRequest("pi_audit", null)));
            stripe.verifyNoInteractions();
        }
    }

}
