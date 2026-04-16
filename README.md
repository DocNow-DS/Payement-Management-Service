# Payment Gateway Service

Stripe-based payment microservice for the healthcare platform.

## Prerequisites
- Java 17
- Maven
- Docker Desktop

## Local Setup
1. Start dependencies:

```bash
docker-compose up -d
```

2. Set Stripe keys (PowerShell):

```powershell
$env:STRIPE_SECRET_KEY="sk_test_your_key_here"
$env:STRIPE_PUBLISHABLE_KEY="pk_test_your_key_here"
$env:STRIPE_WEBHOOK_SECRET="whsec_your_secret_here"
```

3. Build and run:

```bash
mvnw.cmd clean package -DskipTests
mvnw.cmd spring-boot:run
```

## Default Runtime
- Service URL: http://localhost:8085

## Key Environment Variables
- `STRIPE_SECRET_KEY`
- `STRIPE_PUBLISHABLE_KEY`
- `STRIPE_WEBHOOK_SECRET`
- `JWT_SECRET`
- `APPOINTMENT_SERVICE_URL`
- `SPRING_DATA_MONGODB_URI`

## Docker Run
```bash
mvnw.cmd clean package -DskipTests
docker-compose up --build
```

## Stop
```bash
docker-compose down
```