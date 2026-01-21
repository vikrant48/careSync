# CareSync - Advanced Healthcare Management System (Backend)

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-green?style=for-the-badge&logo=spring-boot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=for-the-badge&logo=postgresql)
![Docker](https://img.shields.io/badge/Docker-Enabled-2496ED?style=for-the-badge&logo=docker)

CareSync is an enterprise-grade Digital Health Platform designed to bridge the gap between patients and healthcare providers. It orchestrates complex medical workflows including real-time appointment scheduling, encrypted medical history management, and secure telemedicine integration.

This repository houses the **RESTful API Backend**, built with a focus on scalability, security, and clean architecture.

---

## 🏗️ Architectural Highlights

The system follows a **Layered Architecture** leveraging the **DTO Pattern** to decouple internal entities from the API contract, ensuring maintainability and security.

### 🔐 Advanced Security & Auth
- **Stateless Authentication**: Implemented a robust **JWT (JSON Web Token)** based security layer with `Spring Security`.
- **Dual-Token Mechanism**: engineered a secure `Access Token` + `Refresh Token` rotation flow to balance user experience (seamless sessions) with security (short-lived access).
- **RBAC (Role-Based Access Control)**: Granular permissions for `PATIENT`, `DOCTOR`, and `ADMIN` roles using custom `Annotation-based` security expression handling.
- **BCrypt Hashing**: Industry-standard password encryption.

### ⚡ Real-Time & Async Communication
- **WebSocket Integration**: Implemented `Spring WebSocket` with **STOMP** protocol to push real-time notifications to the frontend.
- **Event-Driven Actions**: appointment status changes trigger instant alerts without client polling, reducing server load.

### 💳 Financial Integration
- **Razorpay Payment Gateway**: Seamlessly integrated Razorpay for booking payments.
- **Webhook Handling**: Secure webhook endpoints verify payment checksums to ensure transaction integrity and handle async payment success/failure events.

### 🧠 AI-Powered Healthcare
- **Google Gemini Integration**: Leveraged **Gemini 1.5 Flash** model to provide intelligent insights.
- **Medical Summarization**: Automatically generates concise summaries of complex patient medical histories.
- **Diagnostic Assistant**: Analyzes reported symptoms to suggest potential diagnoses to doctors (Decision Support).
- **Interactive Chat**: Context-aware AI assistant for patients and doctors.

### 🚀 Performance & Scalability
- **AWS S3 / Supabase Storage**: Migrated from Cloudinary to **AWS S3 SDK** (via Supabase) for secure, compliant, and scalable storage of medical documents and prescriptions.
- **Redis Caching**: Implemented **Redis** as a distributed cache to speed up master data retrieval and manage user sessions, significantly reducing database latency.
- **Database Optimization**: Utilized JPA/Hibernate with optimized queries and relationships (One-To-Many, Many-To-Many) to handle complex entity graphs efficiently.

---

## 🛠️ Tech Stack & Tools

| Category | Technology |
|----------|------------|
| **Core Framework** | Spring Boot 3.5, Java 21 |
| **Database** | PostgreSQL (Production), H2 (Test) |
| **ORM** | Hibernate / Spring Data JPA |
| **Security** | Spring Security 6, JWT (jjwt) |
| **Real-time** | Spring WebSocket, STOMP |
| **AI / ML** | Google Gemini Generative AI |
| **Caching** | Redis (Lettuce Client) |
| **Payments** | Razorpay SDK |
| **Cloud Storage** | AWS S3 SDK (Supabase) |
| **Documentation** | OpenAPI / Swagger UI |

---

## 🧩 Key Modules

### 1. Appointment Orchestration
Handles the complex lifecycle of a medical appointment:
- Slot checking with concurrency handling.
- State transitions: `BOOKED` -> `CONFIRMED` -> `COMPLETED` / `CANCELLED`.
- Automated slot release on payment timeout.

### 2. Digital Medical Records (EMR)
- Securely stores patient history, diagnosis, and prescriptions.
- Encrypted data handling for sensitive health information (PHI).

### 3. Smart Search & Analytics
- Dynamic filtering for doctors by specialization, rating, and availability.
- Reporting endpoints aggregating clinic performance, revenue, and patient demographics.

---

## 🗄️ Database ERD

Link to the database entity-relationship diagram:
[View Database Diagram](https://dbdiagram.io/d/6970e7c2bd82f5fce22c9b1d)

---

## 🔗 Live API Documentation
The API is fully documented using Swagger/OpenAPI.
- **Swagger UI**: `http://caresync-backend-aq8e.onrender.com/swagger-ui`
- **OpenAPI Json**: `http://caresync-backend-aq8e.onrender.com/v3/api-docs`

---
*Built by Vikrant - Focused on Clean Code, Scalability, and Modern Java Principles.*
