# CareSync - Healthcare Management System

A comprehensive healthcare management system built with Spring Boot that connects doctors and patients through a secure, feature-rich platform.

## 🏥 Overview

CareSync is a modern healthcare management application that facilitates seamless interaction between healthcare providers and patients. The system provides secure authentication, appointment management, medical history tracking, and comprehensive user profiles for both doctors and patients.

## ✨ Key Features

### For Doctors
- **Professional Profiles**: Complete profile management with specialization, experience, education, and certifications
- **Appointment Management**: View, confirm, and manage patient appointments
- **Medical Records**: Access and update patient medical histories
- **Patient Feedback**: Receive and view patient feedback and ratings
- **Analytics Dashboard**: Track patient retention and appointment statistics

### For Patients
- **Personal Health Records**: Maintain comprehensive medical history
- **Appointment Booking**: Schedule appointments with available doctors
- **Doctor Search**: Find doctors by specialization and availability
- **Feedback System**: Rate and review healthcare providers
- **Profile Management**: Update personal information and health details

### Security & Authentication
- **JWT-based Authentication**: Secure token-based authentication system
- **Role-based Access Control**: Separate access levels for doctors and patients
- **Session Management**: Track and manage user sessions
- **Security Monitoring**: Login attempt tracking and IP blocking
- **Password Reset**: Secure password recovery system

## 🛠️ Technology Stack

- **Backend**: Spring Boot 3.x
- **Security**: Spring Security with JWT
- **Database**: MySQL with JPA/Hibernate
- **Authentication**: Custom JWT implementation
- **File Upload**: Support for profile images and certificates
- **API Documentation**: RESTful API design

## 📊 Database Schema

View the complete Entity Relationship Diagram (ERD) of the database schema:

🔗 **[View ERD on dbdiagram.io](https://dbdiagram.io/d/68ab2efd1e7a6119675968e7)**

The database includes the following main entities:
- **Users**: Doctors and Patients (with inheritance)
- **Appointments**: Booking and scheduling system
- **Medical History**: Patient health records
- **Feedback**: Rating and review system
- **Security**: Authentication and session management

## 🚀 Getting Started

### Prerequisites
- Java 17 or higher
- MySQL 8.0 or higher
- Maven 3.6 or higher

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd careSync
   ```

2. **Configure Database**
   - Create a MySQL database named `caresync`
   - Update `src/main/resources/application.properties` with your database credentials

3. **Build and Run**
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

4. **Access the Application**
   - API Base URL: `http://localhost:8080`
   - The application will automatically create database tables on first run

## 📁 Project Structure

```
src/main/java/com/vikrant/careSync/
├── controller/          # REST API endpoints
├── entity/             # JPA entities and data models
├── repository/         # Data access layer
├── service/            # Business logic layer
├── security/           # Authentication and security
├── dto/               # Data transfer objects
└── config/            # Configuration classes
```

## 🔐 API Authentication

The API uses JWT (JSON Web Tokens) for authentication:

1. **Login**: `POST /api/auth/login`
2. **Register**: `POST /api/auth/register`
3. **Refresh Token**: `POST /api/auth/refresh`
4. **Logout**: `POST /api/auth/logout`

Include the JWT token in the Authorization header:
```
Authorization: Bearer <your-jwt-token>
```

## 👥 User Roles

- **DOCTOR**: Access to patient management, appointments, and medical records
- **PATIENT**: Access to personal health records, appointment booking, and doctor search
- **ADMIN**: System administration capabilities

## 🔒 Security Features

- Password encryption using BCrypt
- JWT token expiration and refresh mechanism
- Login attempt monitoring and IP blocking
- Session management and tracking
- Secure password reset functionality


## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 📞 Support

For support and questions, please contact the development team or create an issue in the repository.

---

**CareSync** - Connecting Healthcare, Empowering Lives 🏥💙