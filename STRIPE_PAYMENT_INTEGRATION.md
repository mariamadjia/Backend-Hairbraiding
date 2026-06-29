# Stripe Payment Integration Guide

## Overview

This system integrates Stripe payments with **manual capture** for booking deposits. Customers enter their card information when booking, the card is authorized (funds held), but payment is only captured when the admin approves the booking.

## Features

✅ **Card Authorization** - Customer card is validated and funds are held  
✅ **Manual Capture** - Payment only processes after admin approval  
✅ **Automatic Cancellation** - Authorization released if booking is denied  
✅ **Multiple Payment Methods** - Cards, Apple Pay, Google Pay  
✅ **Webhook Support** - Real-time payment status updates  
✅ **7-Day Hold** - Card authorizations valid for ~7 days  

---

## Setup Instructions

### 1. Get Your Stripe API Keys

1. Go to [Stripe Dashboard](https://dashboard.stripe.com)
2. Navigate to **Developers > API Keys**
3. Copy your **Secret Key** (starts with `sk_test_` or `sk_live_`)
4. Copy your **Publishable Key** (starts with `pk_test_` or `pk_live_`)

### 2. Configure Application Properties

Update `src/main/resources/application.properties`:

```properties
# Stripe API Keys
stripe.api.key=sk_test_YOUR_SECRET_KEY_HERE
stripe.public.key=pk_test_YOUR_PUBLISHABLE_KEY_HERE
stripe.webhook.secret=whsec_YOUR_WEBHOOK_SECRET_HERE
```

### 3. Set Up Stripe Webhooks (Optional but Recommended)

1. Go to **Developers > Webhooks** in Stripe Dashboard
2. Click **Add endpoint**
3. Enter your webhook URL: `https://yourdomain.com/api/webhooks/stripe`
4. Select these events:
   - `payment_intent.succeeded`
   - `payment_intent.payment_failed`
   - `payment_intent.canceled`
   - `payment_intent.amount_capturable_updated`
5. Copy the **Signing secret** (starts with `whsec_`)
6. Add it to `application.properties` as `stripe.webhook.secret`

---

## API Endpoints

### 1. Create Payment Intent (Customer)

**Endpoint:** `POST /api/payments/create-intent`  
**Auth:** Public (no authentication required)

**Request Body:**
```json
{
  "amount": 5000,
  "currency": "usd",
  "paymentMethodId": "pm_1234567890",
  "appointmentId": 123,
  "customerEmail": "customer@example.com",
  "customerName": "John Doe"
}
```

**Response:**
```json
{
  "paymentIntentId": "pi_1234567890",
  "clientSecret": "pi_1234567890_secret_abc123",
  "status": "requires_capture",
  "amount": 5000,
  "currency": "usd",
  "message": "Payment authorized successfully. Awaiting admin approval.",
  "appointmentId": 123
}
```

### 2. Capture Payment (Admin)

**Endpoint:** `POST /api/payments/capture`  
**Auth:** Admin only

**Request Body:**
```json
{
  "paymentIntentId": "pi_1234567890",
  "amountToCapture": 5000
}
```

**Response:**
```json
{
  "paymentIntentId": "pi_1234567890",
  "status": "succeeded",
  "amount": 5000,
  "currency": "usd",
  "message": "Payment captured successfully",
  "appointmentId": 123
}
```

### 3. Cancel Payment (Admin)

**Endpoint:** `POST /api/payments/cancel/{paymentIntentId}`  
**Auth:** Admin only

**Response:**
```json
{
  "paymentIntentId": "pi_1234567890",
  "status": "canceled",
  "amount": 5000,
  "currency": "usd",
  "message": "Payment authorization cancelled successfully",
  "appointmentId": 123
}
```

### 4. Get Payment Status

**Endpoint:** `GET /api/payments/status/{paymentIntentId}`  
**Auth:** Public

**Response:**
```json
{
  "paymentIntentId": "pi_1234567890",
  "status": "requires_capture",
  "amount": 5000,
  "currency": "usd",
  "message": "Payment status retrieved successfully",
  "appointmentId": 123
}
```

---

## Booking Flow with Payment

### Customer Side

1. **Customer fills booking form** with card details
2. **Frontend creates payment method** using Stripe.js
3. **Frontend calls** `POST /api/payments/create-intent` with:
   - Payment method ID
   - Amount: `5000` (= $50.00 in cents)
   - Appointment details
4. **Card is authorized** - funds held, customer sees pending charge
5. **Booking created** with status `PENDING`

### Admin Side

**Option 1: Approve Booking**
1. Admin calls `PUT /api/appointments/{id}/approve`
2. System automatically captures payment
3. Customer is charged $50
4. SMS notification sent to customer

**Option 2: Deny Booking**
1. Admin calls `PUT /api/appointments/{id}/deny`
2. System automatically cancels payment authorization
3. Funds released back to customer
4. SMS notification sent to customer

---

## Frontend Integration Example

### 1. Install Stripe.js

```html
<script src="https://js.stripe.com/v3/"></script>
```

### 2. Create Payment Method

```javascript
const stripe = Stripe('pk_test_YOUR_PUBLISHABLE_KEY');

// Create card element
const elements = stripe.elements();
const cardElement = elements.create('card');
cardElement.mount('#card-element');

// When customer submits booking
async function handleBookingSubmit(bookingData) {
  // Create payment method
  const { paymentMethod, error } = await stripe.createPaymentMethod({
    type: 'card',
    card: cardElement,
    billing_details: {
      name: bookingData.customerName,
      email: bookingData.customerEmail,
    },
  });

  if (error) {
    console.error(error);
    return;
  }

  // Create payment intent
  const response = await fetch('/api/payments/create-intent', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      amount: 5000, // $50.00
      currency: 'usd',
      paymentMethodId: paymentMethod.id,
      appointmentId: bookingData.appointmentId,
      customerEmail: bookingData.customerEmail,
      customerName: bookingData.customerName,
    }),
  });

  const result = await response.json();
  
  if (result.status === 'requires_capture') {
    // Success! Payment authorized
    alert('Booking submitted! Payment will be processed after admin approval.');
  }
}
```

### 3. Using Stripe Payment Element (Recommended)

```javascript
const stripe = Stripe('pk_test_YOUR_PUBLISHABLE_KEY');

// Create Payment Element (supports cards, Apple Pay, Google Pay)
const elements = stripe.elements();
const paymentElement = elements.create('payment');
paymentElement.mount('#payment-element');

async function handleBookingSubmit(bookingData) {
  // Confirm payment
  const { error, paymentMethod } = await stripe.createPaymentMethod({
    elements,
  });

  if (error) {
    console.error(error);
    return;
  }

  // Rest is same as above...
}
```

---

## Database Schema Changes

### Appointment Table - New Fields

| Field | Type | Description |
|-------|------|-------------|
| `payment_intent_id` | VARCHAR | Stripe PaymentIntent ID |
| `payment_status` | ENUM | PENDING, AUTHORIZED, CAPTURED, CANCELLED, FAILED |
| `deposit_amount` | BIGINT | Amount in cents (e.g., 5000 = $50) |
| `payment_captured_at` | TIMESTAMP | When payment was captured |
| `payment_method_last4` | VARCHAR | Last 4 digits of card |
| `payment_method_brand` | VARCHAR | Card brand (visa, mastercard, etc.) |

---

## Payment Statuses

| Status | Description |
|--------|-------------|
| `PENDING` | Initial state, no payment attempt yet |
| `AUTHORIZED` | Card authorized, funds held |
| `CAPTURED` | Payment captured, customer charged |
| `CANCELLED` | Authorization cancelled, funds released |
| `FAILED` | Payment failed (invalid card, insufficient funds, etc.) |

---

## Important Notes

### 7-Day Authorization Limit
- Card authorizations expire after ~7 days
- Admin must approve/deny within this timeframe
- After expiration, funds automatically release

### Amount in Cents
- Always use cents: `5000` = $50.00
- Stripe requires amounts in smallest currency unit

### Testing Cards

Use these test cards in **test mode**:

| Card Number | Description |
|-------------|-------------|
| `4242 4242 4242 4242` | Successful payment |
| `4000 0000 0000 9995` | Insufficient funds |
| `4000 0000 0000 0002` | Card declined |

**Expiry:** Any future date  
**CVC:** Any 3 digits  
**ZIP:** Any 5 digits

---

## Troubleshooting

### Payment Not Capturing
- Check if `paymentIntentId` is stored in appointment
- Verify payment status is `AUTHORIZED`
- Check Stripe Dashboard for payment intent status

### Webhook Not Working
- Verify webhook secret in `application.properties`
- Check webhook endpoint is publicly accessible
- Test webhook using Stripe CLI: `stripe listen --forward-to localhost:8080/api/webhooks/stripe`

### Card Authorization Fails
- Verify Stripe API key is correct
- Check card details are valid
- Review Stripe Dashboard logs

---

## Security Best Practices

1. **Never expose secret key** - Keep in `application.properties`, not in frontend
2. **Use webhook secret** - Verify webhook signatures
3. **Validate amounts** - Always verify payment amount matches booking
4. **Log everything** - Track all payment events for auditing
5. **Use HTTPS** - Required for production webhooks

---

## Going Live

### Before Production

1. Replace test keys with live keys
2. Update webhook endpoint to production URL
3. Test with real card (small amount)
4. Enable webhook signature verification
5. Set up monitoring and alerts

### Stripe Dashboard Settings

1. Enable **3D Secure** for fraud protection
2. Set up **email receipts** for customers
3. Configure **dispute notifications**
4. Review **risk settings**

---

## Support

- **Stripe Documentation:** https://stripe.com/docs
- **Stripe Support:** https://support.stripe.com
- **Test Mode:** Use test keys for development
- **Stripe CLI:** https://stripe.com/docs/stripe-cli

---

## Summary

This integration provides a secure, professional payment flow where:
- ✅ Customers enter card details upfront
- ✅ Funds are held but not charged
- ✅ Admin controls when payment is captured
- ✅ Automatic refund if booking denied
- ✅ Supports cards, Apple Pay, Google Pay

The $50 deposit is perfect for this flow - no need for "buy now, pay later" options!
