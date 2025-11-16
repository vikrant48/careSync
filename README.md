# CareSync Backend — Integrated Features

A concise summary of the backend capabilities implemented in this project.

## Database ERD

Link to the database entity-relationship diagram:
https://dbdiagram.io/d/6919c7fb6735e11170077862

## Core Modules
- `Auth`: Registration, login, refresh tokens, logout, password change/reset with OTP.
- `Appointments`: Booking, rescheduling, cancellation; patient/doctor-specific views; emergency booking; available slots.
- `Doctors & Patients`: Profiles, education, experience, certificates, profile images, search and public endpoints.
- `Medical History`: Create, update, and view patient medical records.
- `Lab Tests`: Create and manage lab test records.
- `Feedback`: Submit and fetch feedback; sentiment used in analytics.
- `Booking`: Link payments to bookings and manage flows.
- `Notifications`: In-app and email notifications for reminders, confirmations, cancellations, reschedules; unread counts and feeds.
- `Reporting & Analytics`: Doctor-centric analytics (peak hours, day-of-week, retention, demographics, cancellations, seasonal trends) and reporting endpoints.
- `File Uploads`: Upload and serve documents, certificates, medical documents, and profile images via Cloudinary.
- `Payments`: Razorpay integration for payment initiation, booking payments, webhooks, verification, statistics, and cancellation.

## Security & Access Control
- `JWT` authentication using `jjwt` with refresh token flow.
- `Spring Security` with role-based access: `PATIENT`, `DOCTOR`, `ADMIN`.
- Configurable `CORS` origins.
- Login attempt limiting and IP block settings.
- Stateless sessions (`SessionCreationPolicy.STATELESS`).

## Integrations & Tooling
- `PostgreSQL` via JPA/Hibernate (Neon-hosted config present); optional MySQL driver included.
- `Swagger/OpenAPI` at `/swagger-ui.html` and `/v3/api-docs`.
- `Cloudinary` for file storage.
- `Razorpay` SDK for payments.
- `Actuator` for health checks.
- `Jackson` JSR310 for date/time handling.
- `ZXing` for QR code generation (utility available).

## Project Notes
- Spring Boot `3.5`, Java `21`.
- Devtools enabled for hot reload in development.
- Verbose logging configured for security, web, and SQL.

This README intentionally focuses on what’s implemented. No clone/run instructions included.