# Payment Management Service

> Stripe payment microservice for the Healthcare platform.

## Tech Stack
- **Java 17** + **Spring Boot 3.2.5**
- **MongoDB** (Spring Data MongoDB)
- **Stripe Java SDK 28.2.0** (Checkout Sessions)
- **JWT** authentication (shared secret with other services)

## API Endpoints

| Method | Endpoint | Auth | Description |
|--------|--------|------|-------------|
| `POST` | `/api/v1/payments/checkout-session` | JWT | Create a Stripe Checkout Session |
| `POST` | `/api/v1/payments/webhook` | Stripe Sig | Receive Stripe webhook events |
| `GET` | `/api/v1/payments/stripe-config` | Public | Get Stripe publishable key for frontend |
| `GET` | `/api/v1/payments/{paymentId}` | JWT | Get payment by internal ID |
| `GET` | `/api/v1/payments/consultation/{consultationId}` | JWT | Get payment by consultation ID |
| `GET` | `/api/v1/payments/patient/my-payments` | JWT | Get all payments for authenticated patient |

## Checkout Session Request

```json
POST /api/v1/payments/checkout-session
Authorization: Bearer <jwt_token>

{
  "amountLKR": 5000,
  "currency": "lkr",
  "consultationId": "cons_123",
  "customerEmail": "user@example.com",
  "successUrl": "https://yourapp.com/consultation/success",
  "cancelUrl": "https://yourapp.com/consultation/cancel"
}
```

### Response
```json
{
  "sessionId": "cs_test_...",
  "checkoutUrl": "https://checkout.stripe.com/c/pay/cs_test_..."
}
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `STRIPE_SECRET_KEY` | `sk_test_...` | Stripe secret API key |
| `STRIPE_PUBLISHABLE_KEY` | `pk_test_...` | Stripe publishable key used by frontend |
| `STRIPE_WEBHOOK_SECRET` | `whsec_...` | Stripe webhook signing secret |
| `JWT_SECRET` | `dev-secret-change-me` | Shared JWT signing secret |
| `APPOINTMENT_SERVICE_URL` | `http://localhost:8080` | Appointment service URL |
| `SPRING_DATA_MONGODB_URI` | (see application.properties) | MongoDB connection string |

## Quick Start

### 1. Set Stripe keys
```bash
set STRIPE_SECRET_KEY=sk_test_your_key_here
set STRIPE_PUBLISHABLE_KEY=pk_test_your_key_here
set STRIPE_WEBHOOK_SECRET=whsec_your_secret_here
```

### 2. Build & Run
```bash
mvnw.cmd clean package -DskipTests
mvnw.cmd spring-boot:run
```

Service starts on **http://localhost:8085**

### 3. Test webhooks locally (optional)
```bash
stripe listen --forward-to localhost:8085/api/v1/payments/webhook
```

## Docker

```bash
mvnw.cmd clean package -DskipTests
docker-compose up --build
```

## Architecture

```
com.healthcare.payment
├── config/           # SecurityConfig, WebConfig, MongoConfig
├── controller/       # PaymentController (REST endpoints)
├── dto/              # CheckoutRequest, CheckoutResponse, PaymentResponse
├── exception/        # GlobalExceptionHandler
├── model/            # PaymentSession, PaymentStatus
├── repository/       # PaymentSessionRepository
├── security/         # JwtUtil, JwtAuthenticationFilter
├── service/          # PaymentService (business logic)
└── stripe/           # StripeClientAdapter (Stripe SDK wrapper)
```