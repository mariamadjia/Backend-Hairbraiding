# Calendar Booking Flow System - API Documentation

## Overview
This system allows customers to book appointments and admins to approve or deny those requests.

## System Flow

### Customer Flow:
1. Customer fills out appointment form with their information
2. System creates/updates customer record
3. Appointment is created with PENDING status
4. Admin receives notification of new appointment

### Admin Flow:
1. Admin views pending appointments
2. Admin reviews customer information and appointment details
3. Admin can:
   - **Approve** the appointment (with optional notes)
   - **Deny** the appointment (with optional notes explaining why)

## Database Models

### Customer
- `id`: Long (Primary Key)
- `firstName`: String (required)
- `lastName`: String (required)
- `email`: String (required, unique)
- `phoneNumber`: String (required)
- `createdAt`: LocalDateTime
- `updatedAt`: LocalDateTime

### Appointment
- `id`: Long (Primary Key)
- `customer`: Customer (Foreign Key)
- `service`: ServiceItem (Foreign Key, optional)
- `appointmentDateTime`: LocalDateTime (required)
- `status`: Enum (PENDING, APPROVED, DENIED, CANCELLED, COMPLETED)
- `notes`: String (customer notes, max 1000 chars)
- `adminNotes`: String (admin notes, max 500 chars)
- `approvedBy`: Admin (Foreign Key)
- `approvedAt`: LocalDateTime
- `createdAt`: LocalDateTime
- `updatedAt`: LocalDateTime

## API Endpoints

### Public Endpoints (No Authentication Required)

#### Create Appointment
```http
POST /api/appointments
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phoneNumber": "+1234567890",
  "appointmentDateTime": "2026-05-15T14:30:00",
  "serviceId": 1,
  "notes": "I prefer afternoon appointments"
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "customer": {
    "id": 1,
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phoneNumber": "+1234567890"
  },
  "service": {
    "id": 1,
    "name": "Box Braids",
    "description": "Traditional box braids styling"
  },
  "appointmentDateTime": "2026-05-15T14:30:00",
  "status": "PENDING",
  "notes": "I prefer afternoon appointments",
  "adminNotes": null,
  "approvedByName": null,
  "approvedAt": null,
  "createdAt": "2026-04-30T14:05:00",
  "updatedAt": "2026-04-30T14:05:00"
}
```

### Admin Endpoints (Require ADMIN Role)

#### Get All Appointments
```http
GET /api/appointments
Authorization: Bearer {admin_token}
```

#### Get Pending Appointments
```http
GET /api/appointments/pending
Authorization: Bearer {admin_token}
```

#### Get Upcoming Appointments
```http
GET /api/appointments/upcoming
Authorization: Bearer {admin_token}
```

#### Get Appointments by Status
```http
GET /api/appointments/status/{status}
Authorization: Bearer {admin_token}
```
Status values: `PENDING`, `APPROVED`, `DENIED`, `CANCELLED`, `COMPLETED`

#### Get Appointment by ID
```http
GET /api/appointments/{id}
Authorization: Bearer {admin_token}
```

#### Get Appointments by Date Range
```http
GET /api/appointments/date-range?startDate=2026-05-01T00:00:00&endDate=2026-05-31T23:59:59
Authorization: Bearer {admin_token}
```

#### Approve Appointment
```http
PUT /api/appointments/{id}/approve
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "adminNotes": "Confirmed for 2:30 PM. Please arrive 10 minutes early."
}
```

**Response (200 OK):**
```json
{
  "id": 1,
  "customer": {
    "id": 1,
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phoneNumber": "+1234567890"
  },
  "service": {
    "id": 1,
    "name": "Box Braids",
    "description": "Traditional box braids styling"
  },
  "appointmentDateTime": "2026-05-15T14:30:00",
  "status": "APPROVED",
  "notes": "I prefer afternoon appointments",
  "adminNotes": "Confirmed for 2:30 PM. Please arrive 10 minutes early.",
  "approvedByName": "Jane Admin",
  "approvedAt": "2026-04-30T15:00:00",
  "createdAt": "2026-04-30T14:05:00",
  "updatedAt": "2026-04-30T15:00:00"
}
```

#### Deny Appointment
```http
PUT /api/appointments/{id}/deny
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "adminNotes": "Unfortunately, we are fully booked for that time slot. Please choose another time."
}
```

**Response (200 OK):**
```json
{
  "id": 1,
  "customer": {
    "id": 1,
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phoneNumber": "+1234567890"
  },
  "service": {
    "id": 1,
    "name": "Box Braids",
    "description": "Traditional box braids styling"
  },
  "appointmentDateTime": "2026-05-15T14:30:00",
  "status": "DENIED",
  "notes": "I prefer afternoon appointments",
  "adminNotes": "Unfortunately, we are fully booked for that time slot. Please choose another time.",
  "approvedByName": "Jane Admin",
  "approvedAt": "2026-04-30T15:00:00",
  "createdAt": "2026-04-30T14:05:00",
  "updatedAt": "2026-04-30T15:00:00"
}
```

## Validation Rules

### Appointment Request
- `firstName`: Required, not blank
- `lastName`: Required, not blank
- `email`: Required, valid email format
- `phoneNumber`: Required, valid phone format
- `appointmentDateTime`: Required, must be in the future
- `notes`: Optional, max 1000 characters
- `serviceId`: Optional

### Appointment Action (Approve/Deny)
- `adminNotes`: Optional, max 500 characters

## Business Rules

1. **Customer Creation**: If a customer with the same email exists, the system uses the existing customer record
2. **Appointment Status**: Only PENDING appointments can be approved or denied
3. **Admin Tracking**: The system tracks which admin approved/denied each appointment
4. **Timestamps**: All approvals/denials are timestamped

## Error Responses

### 400 Bad Request
```json
{
  "timestamp": "2026-04-30T14:05:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "email",
      "message": "Email should be valid"
    }
  ]
}
```

### 404 Not Found
```json
{
  "timestamp": "2026-04-30T14:05:00",
  "status": 404,
  "error": "Not Found",
  "message": "Appointment not found"
}
```

### 403 Forbidden
```json
{
  "timestamp": "2026-04-30T14:05:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied"
}
```

## Next Steps

### Recommended Enhancements:
1. **Email Notifications**: Send emails to customers when appointments are approved/denied
2. **SMS Notifications**: Send SMS reminders for upcoming appointments
3. **Calendar Integration**: Sync with Google Calendar or other calendar systems
4. **Availability Management**: Add admin interface to manage available time slots
5. **Conflict Detection**: Prevent double-booking of time slots
6. **Customer Portal**: Allow customers to view/cancel their appointments
7. **Recurring Appointments**: Support for recurring appointment schedules
8. **Payment Integration**: Require deposit or full payment when booking
9. **Waitlist**: Automatically notify customers when slots become available
10. **Analytics Dashboard**: Track booking patterns and popular time slots

## Database Migration

Make sure to run database migrations to create the new tables:
- `customers`
- `appointments`

The system will automatically create the tables based on the JPA entities when the application starts (if using `spring.jpa.hibernate.ddl-auto=update` or `create`).
