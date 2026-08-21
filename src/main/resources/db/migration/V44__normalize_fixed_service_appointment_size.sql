-- Fixed-price items without length choices are complete services, not size
-- selections. Normalize legacy snapshots so admin, customer-management, and
-- notification surfaces no longer render the service name as a size.
UPDATE appointments appointment
SET selected_size = NULL
FROM service_items service
WHERE appointment.service_id = service.id
  AND service.pricing_mode = 'FIXED'
  AND NOT EXISTS (
      SELECT 1
      FROM length_options length_option
      WHERE length_option.service_item_id = service.id
  );
