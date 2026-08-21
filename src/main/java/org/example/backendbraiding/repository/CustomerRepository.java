package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findFirstByEmailIgnoreCaseOrderByIdAsc(String email);
    Optional<Customer> findByPhoneNumber(String phoneNumber);

    interface CustomerSummaryView {
        Long getId();
        String getFirstName();
        String getLastName();
        String getEmail();
        String getPhoneNumber();
        LocalDateTime getLastAppointmentDate();
        LocalDateTime getNextAppointmentDate();
        Integer getTotalAppointments();
        Integer getCompletedVisits();
        Long getCapturedCents();
    }

    @Query(value = """
        SELECT c.id, c.first_name AS firstName, c.last_name AS lastName,
               c.email, c.phone_number AS phoneNumber,
               max(a.appointment_date_time) FILTER (
                   WHERE a.status = 'COMPLETED' AND a.appointment_date_time <= (CURRENT_TIMESTAMP AT TIME ZONE 'America/Chicago')
               ) AS lastAppointmentDate,
               min(a.appointment_date_time) FILTER (
                   WHERE a.status IN ('PENDING','APPROVED') AND a.appointment_date_time > (CURRENT_TIMESTAMP AT TIME ZONE 'America/Chicago')
               ) AS nextAppointmentDate,
               count(a.id)::integer AS totalAppointments,
               count(a.id) FILTER (WHERE a.status = 'COMPLETED')::integer AS completedVisits,
               coalesce(sum(CASE WHEN a.payment_status = 'CAPTURED'
                                 THEN coalesce(a.amount_captured, a.deposit_amount, 0) ELSE 0 END), 0)::bigint AS capturedCents
        FROM customers c
        LEFT JOIN appointments a ON a.customer_id = c.id
        WHERE (:query = '' OR lower(concat_ws(' ', c.first_name, c.last_name, c.email, c.phone_number)) LIKE '%' || :query || '%'
               OR (:phoneQuery <> '' AND regexp_replace(c.phone_number, '[^0-9]', '', 'g') LIKE '%' || :phoneQuery || '%'))
        GROUP BY c.id
        HAVING (:segment = 'ALL'
            OR (:segment = 'UPCOMING' AND count(a.id) FILTER (WHERE a.status IN ('PENDING','APPROVED') AND a.appointment_date_time > (CURRENT_TIMESTAMP AT TIME ZONE 'America/Chicago')) > 0)
            OR (:segment = 'COMPLETED' AND count(a.id) FILTER (WHERE a.status = 'COMPLETED') > 0)
            OR (:segment = 'CANCELLED' AND count(a.id) FILTER (WHERE a.status IN ('CANCELLED','DENIED')) > 0)
            OR (:segment = 'NO_UPCOMING' AND count(a.id) FILTER (WHERE a.status IN ('PENDING','APPROVED') AND a.appointment_date_time > (CURRENT_TIMESTAMP AT TIME ZONE 'America/Chicago')) = 0))
        ORDER BY
          CASE WHEN :sort = 'NAME_ASC' THEN lower(c.last_name || ' ' || c.first_name) END ASC,
          CASE WHEN :sort = 'NAME_DESC' THEN lower(c.last_name || ' ' || c.first_name) END DESC,
          CASE WHEN :sort = 'LAST_VISIT' THEN max(a.appointment_date_time) FILTER (WHERE a.status = 'COMPLETED') END DESC NULLS LAST,
          CASE WHEN :sort = 'NEXT_APPOINTMENT' THEN min(a.appointment_date_time) FILTER (WHERE a.status IN ('PENDING','APPROVED') AND a.appointment_date_time > (CURRENT_TIMESTAMP AT TIME ZONE 'America/Chicago')) END ASC NULLS LAST,
          CASE WHEN :sort = 'VALUE' THEN coalesce(sum(CASE WHEN a.payment_status = 'CAPTURED' THEN coalesce(a.amount_captured, a.deposit_amount, 0) ELSE 0 END), 0) END DESC,
          CASE WHEN :sort = 'APPOINTMENTS' THEN count(a.id) END DESC,
          lower(c.last_name || ' ' || c.first_name) ASC, c.id ASC
        """, countQuery = """
        SELECT count(*) FROM (
          SELECT c.id
          FROM customers c LEFT JOIN appointments a ON a.customer_id = c.id
          WHERE (:query = '' OR lower(concat_ws(' ', c.first_name, c.last_name, c.email, c.phone_number)) LIKE '%' || :query || '%'
                 OR (:phoneQuery <> '' AND regexp_replace(c.phone_number, '[^0-9]', '', 'g') LIKE '%' || :phoneQuery || '%'))
          GROUP BY c.id
          HAVING (:segment = 'ALL'
              OR (:segment = 'UPCOMING' AND count(a.id) FILTER (WHERE a.status IN ('PENDING','APPROVED') AND a.appointment_date_time > (CURRENT_TIMESTAMP AT TIME ZONE 'America/Chicago')) > 0)
              OR (:segment = 'COMPLETED' AND count(a.id) FILTER (WHERE a.status = 'COMPLETED') > 0)
              OR (:segment = 'CANCELLED' AND count(a.id) FILTER (WHERE a.status IN ('CANCELLED','DENIED')) > 0)
              OR (:segment = 'NO_UPCOMING' AND count(a.id) FILTER (WHERE a.status IN ('PENDING','APPROVED') AND a.appointment_date_time > (CURRENT_TIMESTAMP AT TIME ZONE 'America/Chicago')) = 0))
        ) filtered
        """, nativeQuery = true)
    Page<CustomerSummaryView> findCustomerSummaries(@Param("query") String query,
                                                     @Param("phoneQuery") String phoneQuery,
                                                     @Param("segment") String segment,
                                                     @Param("sort") String sort,
                                                     Pageable pageable);
}
