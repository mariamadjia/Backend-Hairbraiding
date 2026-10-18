package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.CheckoutDTO;
import org.example.backendbraiding.dto.CheckoutRefundRequest;
import org.example.backendbraiding.dto.ExternalCheckoutPaymentRequest;
import org.example.backendbraiding.dto.ReceiptRequest;
import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.AppointmentCheckout;
import org.example.backendbraiding.model.CheckoutTransaction;
import org.example.backendbraiding.repository.AdminRepository;
import org.example.backendbraiding.repository.AppointmentCheckoutRepository;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.example.backendbraiding.repository.CheckoutTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CheckoutService {
    private static final ZoneId SALON_ZONE = ZoneId.of("America/Chicago");

    private final AppointmentRepository appointmentRepository;
    private final AppointmentCheckoutRepository checkoutRepository;
    private final CheckoutTransactionRepository transactionRepository;
    private final AdminRepository adminRepository;
    private final AppointmentEventService eventService;
    private final NotificationOutboxService outboxService;
    private final CheckoutReceiptTemplates templates;

    @Transactional(readOnly = true)
    public Page<CheckoutDTO> list(String tab, Pageable pageable) {
        String normalized = tab == null ? "READY" : tab.trim().toUpperCase(Locale.ROOT);
        LocalDateTime now = salonNow();
        Specification<Appointment> specification = switch (normalized) {
            case "READY" -> (root, query, cb) -> cb.and(
                    cb.equal(root.get("status"), Appointment.AppointmentStatus.APPROVED),
                    cb.lessThanOrEqualTo(root.get("appointmentDateTime"), now));
            case "PAST" -> (root, query, cb) -> root.get("status").in(
                    Appointment.AppointmentStatus.COMPLETED,
                    Appointment.AppointmentStatus.CANCELLED,
                    Appointment.AppointmentStatus.NO_SHOW);
            default -> throw new IllegalArgumentException("Checkout tab must be READY or PAST");
        };
        return appointmentRepository.findAll(specification, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public CheckoutDTO get(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        return toDto(appointment);
    }

    @Transactional
    public CheckoutDTO recordExternalPayment(Long appointmentId, Long adminId, ExternalCheckoutPaymentRequest request) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        requireCheckoutEligible(appointment);
        Admin admin = requireAdmin(adminId);
        CheckoutTransaction.PaymentMethod method = externalMethod(request.paymentMethod());
        AppointmentCheckout checkout = checkoutRepository.findByAppointmentIdForUpdate(appointmentId)
                .orElseGet(() -> createCheckout(appointment));

        if (checkout.getStatus() == AppointmentCheckout.Status.PAID) return toDto(appointment, checkout);
        if (checkout.getStatus() != AppointmentCheckout.Status.OPEN) {
            throw new IllegalStateException("This checkout cannot accept another payment");
        }

        long tip = request.tipCents() == null ? 0 : request.tipCents();
        checkout.setTipCents(tip);
        long balance = openBalance(checkout);
        if (balance <= 0) throw new IllegalStateException("This appointment has no remaining balance");

        CheckoutTransaction transaction = new CheckoutTransaction();
        transaction.setCheckout(checkout);
        transaction.setTransactionType(CheckoutTransaction.Type.PAYMENT);
        transaction.setPaymentMethod(method);
        transaction.setStatus(CheckoutTransaction.Status.SUCCEEDED);
        transaction.setAmountCents(balance);
        transaction.setExternalReference(clean(request.externalReference()));
        transaction.setRecordedBy(admin);
        transactionRepository.save(transaction);

        checkout.setAmountPaidCents(checkout.getAmountPaidCents() + balance);
        checkout.setStatus(AppointmentCheckout.Status.PAID);
        checkout.setCompletedAt(salonNow());
        checkout.setCompletedBy(admin);
        checkoutRepository.save(checkout);

        appointment.setStatus(Appointment.AppointmentStatus.COMPLETED);
        if (request.adminNotes() != null && !request.adminNotes().isBlank()) {
            appointment.setAdminNotes(request.adminNotes().trim());
        }
        appointmentRepository.save(appointment);
        eventService.record(appointment, "CHECKOUT_COMPLETED", admin,
                "Final balance " + MoneySupport.fromNonNegativeCents(balance) + " recorded via " + method.name());

        CheckoutReceiptTemplates.Receipt receipt = templates.checkout(appointment, checkout, transaction);
        outboxService.enqueueSelected(appointment, receipt.subject(), receipt.emailBody(), receipt.smsBody(), true, true);
        return toDto(appointment, checkout);
    }

    @Transactional
    public CheckoutDTO recordExternalRefund(Long appointmentId, Long adminId, CheckoutRefundRequest request) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        AppointmentCheckout checkout = checkoutRepository.findByAppointmentIdForUpdate(appointmentId)
                .orElseThrow(() -> new IllegalStateException("This appointment does not have a checkout payment"));
        Admin admin = requireAdmin(adminId);
        long refundable = checkout.getAmountPaidCents() - checkout.getAmountRefundedCents();
        if (request.amountCents() > refundable) {
            throw new IllegalArgumentException("Refund cannot exceed the unrefunded checkout payment");
        }
        CheckoutTransaction original = transactionRepository.findByCheckoutIdOrderByCreatedAtAsc(checkout.getId()).stream()
                .filter(item -> item.getTransactionType() == CheckoutTransaction.Type.PAYMENT
                        && item.getStatus() == CheckoutTransaction.Status.SUCCEEDED)
                .findFirst().orElseThrow(() -> new IllegalStateException("The original checkout payment is unavailable"));
        if (original.getPaymentMethod() == CheckoutTransaction.PaymentMethod.STRIPE_TERMINAL) {
            throw new IllegalStateException("Stripe refunds are not enabled until Terminal checkout is connected");
        }

        CheckoutTransaction refund = new CheckoutTransaction();
        refund.setCheckout(checkout);
        refund.setTransactionType(CheckoutTransaction.Type.REFUND);
        refund.setPaymentMethod(original.getPaymentMethod());
        refund.setStatus(CheckoutTransaction.Status.SUCCEEDED);
        refund.setAmountCents(request.amountCents());
        refund.setReason(request.reason().trim());
        refund.setExternalReference(clean(request.externalReference()));
        refund.setRecordedBy(admin);
        transactionRepository.save(refund);

        checkout.setAmountRefundedCents(checkout.getAmountRefundedCents() + request.amountCents());
        checkout.setStatus(checkout.getAmountRefundedCents().equals(checkout.getAmountPaidCents())
                ? AppointmentCheckout.Status.REFUNDED : AppointmentCheckout.Status.PARTIALLY_REFUNDED);
        checkoutRepository.save(checkout);
        eventService.record(appointment, "CHECKOUT_REFUND_RECORDED", admin,
                "External refund " + MoneySupport.fromNonNegativeCents(request.amountCents()) + ": " + request.reason().trim());
        CheckoutReceiptTemplates.Receipt receipt = templates.refund(appointment, checkout, refund);
        outboxService.enqueueSelected(appointment, receipt.subject(), receipt.emailBody(), receipt.smsBody(), true, true);
        return toDto(appointment, checkout);
    }

    @Transactional
    public CheckoutDTO sendReceipt(Long appointmentId, ReceiptRequest request) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        AppointmentCheckout checkout = checkoutRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new IllegalStateException("This appointment does not have a checkout receipt"));
        CheckoutTransaction payment = transactionRepository.findByCheckoutIdOrderByCreatedAtAsc(checkout.getId()).stream()
                .filter(item -> item.getTransactionType() == CheckoutTransaction.Type.PAYMENT
                        && item.getStatus() == CheckoutTransaction.Status.SUCCEEDED)
                .findFirst().orElseThrow(() -> new IllegalStateException("The checkout payment is unavailable"));
        boolean email = request == null || request.email() == null || request.email();
        boolean sms = request == null || request.sms() == null || request.sms();
        if (!email && !sms) throw new IllegalArgumentException("Select email or text receipt");
        CheckoutReceiptTemplates.Receipt receipt = templates.checkout(appointment, checkout, payment);
        if (!outboxService.enqueueSelected(appointment, receipt.subject(), receipt.emailBody(), receipt.smsBody(), email, sms)) {
            throw new IllegalStateException("The customer does not have an eligible receipt delivery channel");
        }
        return toDto(appointment, checkout);
    }

    private AppointmentCheckout createCheckout(Appointment appointment) {
        AppointmentCheckout checkout = new AppointmentCheckout();
        checkout.setAppointment(appointment);
        checkout.setServiceTotalCents(MoneySupport.requirePositiveCents(appointment.getPrice(), "Appointment price"));
        long captured = appointment.getPaymentStatus() == Appointment.PaymentStatus.CAPTURED
                ? (appointment.getAmountCaptured() == null ? defaultLong(appointment.getDepositAmount()) : appointment.getAmountCaptured()) : 0;
        checkout.setDepositCreditCents(Math.min(checkout.getServiceTotalCents(), Math.max(0, captured)));
        return checkoutRepository.save(checkout);
    }

    private CheckoutDTO toDto(Appointment appointment) {
        return toDto(appointment, checkoutRepository.findByAppointmentId(appointment.getId()).orElse(null));
    }

    private CheckoutDTO toDto(Appointment appointment, AppointmentCheckout checkout) {
        long total = checkout == null ? MoneySupport.requirePositiveCents(appointment.getPrice(), "Appointment price") : checkout.getServiceTotalCents();
        long deposit = checkout == null && appointment.getPaymentStatus() == Appointment.PaymentStatus.CAPTURED
                ? Math.min(total, Math.max(0, appointment.getAmountCaptured() == null
                ? defaultLong(appointment.getDepositAmount()) : appointment.getAmountCaptured()))
                : checkout == null ? 0 : checkout.getDepositCreditCents();
        long tip = checkout == null ? 0 : checkout.getTipCents();
        long paid = checkout == null ? 0 : checkout.getAmountPaidCents();
        long refunded = checkout == null ? 0 : checkout.getAmountRefundedCents();
        long balance = checkout == null || checkout.getStatus() == AppointmentCheckout.Status.OPEN
                ? Math.max(0, total + tip - deposit - paid) : 0;
        List<CheckoutDTO.TransactionDTO> transactions = checkout == null ? List.of()
                : transactionRepository.findByCheckoutIdOrderByCreatedAtAsc(checkout.getId()).stream().map(item ->
                new CheckoutDTO.TransactionDTO(item.getId(), item.getTransactionType().name(), item.getPaymentMethod().name(),
                        item.getStatus().name(), item.getAmountCents(), item.getExternalReference(), item.getReason(), item.getCreatedAt())).toList();
        String service = appointment.getSelectedService();
        if ((service == null || service.isBlank()) && appointment.getService() != null) service = appointment.getService().getName();
        return new CheckoutDTO(appointment.getId(), appointment.getCustomer().getId(),
                appointment.getCustomer().getFirstName() + " " + appointment.getCustomer().getLastName(),
                appointment.getCustomer().getEmail(), appointment.getCustomer().getPhoneNumber(), service,
                appointment.getAppointmentDateTime(), appointment.getAppointmentEndDateTime(), appointment.getStatus().name(),
                appointment.getPaymentStatus() == null ? null : appointment.getPaymentStatus().name(), total, deposit, tip,
                paid, refunded, balance, checkout == null ? "OPEN" : checkout.getStatus().name(),
                checkout == null ? null : checkout.getCompletedAt(), appointment.getNotificationStatus(),
                appointment.getBookingSource() == null ? Appointment.BookingSource.CUSTOMER.name() : appointment.getBookingSource().name(),
                appointment.getDepositPolicyVersion(), appointment.getDepositPolicyAcceptedAt(),
                appointment.getBookingPolicySnapshot(),
                appointment.getOffSessionConsentPolicyVersion(), appointment.getOffSessionConsentAt(), transactions);
    }

    private void requireCheckoutEligible(Appointment appointment) {
        if (appointment.getStatus() != Appointment.AppointmentStatus.APPROVED) {
            throw new IllegalStateException("Only an approved appointment can be checked out");
        }
        if (appointment.getAppointmentDateTime().isAfter(salonNow())) {
            throw new IllegalStateException("A future appointment cannot be checked out");
        }
    }

    private Admin requireAdmin(Long id) {
        return adminRepository.findById(id).filter(admin -> "ACTIVE".equalsIgnoreCase(admin.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Administrator not found"));
    }

    private CheckoutTransaction.PaymentMethod externalMethod(String value) {
        try {
            CheckoutTransaction.PaymentMethod method = CheckoutTransaction.PaymentMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (method == CheckoutTransaction.PaymentMethod.STRIPE_TERMINAL) throw new IllegalArgumentException();
            return method;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Payment method must be CASH, ZELLE, CASH_APP, or OTHER");
        }
    }

    private long openBalance(AppointmentCheckout checkout) {
        return Math.max(0, checkout.getServiceTotalCents() + checkout.getTipCents()
                - checkout.getDepositCreditCents() - checkout.getAmountPaidCents());
    }

    private long defaultLong(Long value) { return value == null ? 0 : value; }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private LocalDateTime salonNow() { return LocalDateTime.now(SALON_ZONE); }
}
