# AH Braiding Backend

Spring Boot backend for the AH Braiding hair salon booking system.

## Features

- 🔐 JWT Authentication & Authorization
- 📅 Appointment Booking System
- 💳 Stripe Payment Integration
- 📧 Email Notifications
- 📱 SMS Notifications (Vonage)
- 🖼️ Gallery Management
- 👤 User & Admin Management
- 🎨 Homepage Customization

## Prerequisites

- Java 17 or higher
- PostgreSQL 12 or higher
- Maven 3.6+

## Setup

### 1. Clone the repository

```bash
git clone <your-repo-url>
cd Backend-Hairbraiding
```

### 2. Create PostgreSQL Database

```sql
CREATE DATABASE braiding;
```

### 3. Configure Environment Variables

Create a `.env` file or set environment variables:

```bash
# Database
DB_PASSWORD=your_postgres_password

# JWT
JWT_SECRET=your_jwt_secret_key_here

# Stripe
STRIPE_SECRET_KEY=sk_test_your_stripe_secret_key
STRIPE_PUBLIC_KEY=pk_test_your_stripe_public_key
STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret

# Email (Gmail)
EMAIL_USERNAME=your-email@gmail.com
EMAIL_PASSWORD=your_app_specific_password

# Vonage SMS
VONAGE_API_KEY=your_vonage_api_key
VONAGE_API_SECRET=your_vonage_api_secret
VONAGE_PHONE_NUMBER=your_vonage_phone_number
```

### 4. Run the Application

```bash
mvn spring-boot:run
```

Or build and run:

```bash
mvn clean package
java -jar target/Backend-Braiding-0.0.1-SNAPSHOT.jar
```

The server will start on `http://localhost:8080`

## API Documentation

### Authentication
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login user
- `POST /api/auth/refresh` - Refresh JWT token

### Appointments
- `GET /api/appointments` - Get all appointments
- `POST /api/appointments` - Create appointment
- `PUT /api/appointments/{id}` - Update appointment
- `DELETE /api/appointments/{id}` - Delete appointment

### Categories & Services
- `GET /api/categories` - Get all categories
- `POST /api/categories` - Create category (Admin)
- `GET /api/subcategories` - Get all subcategories

### Gallery
- `GET /api/gallery` - Get all gallery images
- `POST /api/gallery/upload` - Upload image (Admin)
- `DELETE /api/gallery/{id}` - Delete image (Admin)

### Homepage Settings
- `GET /api/homepage-settings` - Get homepage configuration
- `POST /api/homepage-settings` - Update homepage settings (Admin)

See individual API documentation files for more details:
- [Appointment Booking API](APPOINTMENT_BOOKING_API.md)
- [Gallery System](GALLERY_SYSTEM.md)
- [Stripe Payment Integration](STRIPE_PAYMENT_INTEGRATION.md)

## Security Notes

⚠️ **IMPORTANT**: Never commit sensitive credentials to version control!

- Use environment variables for all secrets
- The `application.properties` file uses `${VAR:default}` syntax for safe defaults
- Copy `application.properties.example` for reference
- Keep your `.env` file in `.gitignore`

## Project Structure

```
src/
├── main/
│   ├── java/org/example/backendbraiding/
│   │   ├── config/          # Security, CORS, Web config
│   │   ├── controller/      # REST API endpoints
│   │   ├── dto/             # Data Transfer Objects
│   │   ├── model/           # JPA Entities
│   │   ├── repository/      # Database repositories
│   │   ├── security/        # JWT, Auth filters
│   │   └── service/         # Business logic
│   └── resources/
│       └── application.properties
└── test/
```

## Contributing

1. Create a feature branch
2. Make your changes
3. Test thoroughly
4. Submit a pull request

## License

Private - All Rights Reserved
