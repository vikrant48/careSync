package com.vikrant.careSync.constants;

public final class AppConstants {
    
    private AppConstants() {
        // Private constructor to prevent instantiation
    }
    

    
    // Error Messages
    public static final class ErrorMessages {
        public static final String USER_NOT_FOUND = "User not found";
        public static final String DOCTOR_NOT_FOUND = "Doctor not found";
        public static final String PATIENT_NOT_FOUND = "Patient not found";
        public static final String APPOINTMENT_NOT_FOUND = "Appointment not found";
        public static final String EDUCATION_NOT_FOUND = "Education not found";
        public static final String EXPERIENCE_NOT_FOUND = "Experience not found";
        public static final String CERTIFICATE_NOT_FOUND = "Certificate not found";
        public static final String FEEDBACK_NOT_FOUND = "Feedback not found";
        public static final String MEDICAL_HISTORY_NOT_FOUND = "Medical history not found";
        
        public static final String UNAUTHORIZED_ACCESS = "Unauthorized access";
        public static final String INVALID_CREDENTIALS = "Invalid credentials";
        public static final String ACCOUNT_LOCKED = "Account is locked";
        public static final String IP_BLOCKED = "IP address is blocked";
        public static final String SESSION_EXPIRED = "Session has expired";
        public static final String TOKEN_INVALID = "Invalid token";
        
        public static final String VALIDATION_ERROR = "Validation error";
        public static final String DUPLICATE_USERNAME = "Username already exists";
        public static final String DUPLICATE_EMAIL = "Email already exists";
        public static final String WEAK_PASSWORD = "Password does not meet security requirements";
        
        public static final String FILE_UPLOAD_ERROR = "File upload failed";
        public static final String FILE_SIZE_EXCEEDED = "File size exceeds maximum limit";
        public static final String INVALID_FILE_TYPE = "Invalid file type";
        
        public static final String DATABASE_ERROR = "Database operation failed";

        public static final String EXTERNAL_SERVICE_ERROR = "External service unavailable";
        
        public static final String ERROR_FORGOT_PASSWORD_FAILED = "Failed to process forgot password request";
        public static final String ERROR_PASSWORD_RESET_FAILED = "Failed to reset password";
        public static final String ERROR_PASSWORD_CHANGE_FAILED = "Failed to change password";
        public static final String ERROR_AUTHENTICATION_FAILED = "Authentication failed";
        public static final String ERROR_USER_REGISTRATION_FAILED = "User registration failed";
    }
    
    // Success Messages
    public static final class SuccessMessages {
        public static final String USER_CREATED = "User created successfully";
        public static final String USER_UPDATED = "User updated successfully";
        public static final String USER_DELETED = "User deleted successfully";
        
        public static final String APPOINTMENT_CREATED = "Appointment created successfully";
        public static final String APPOINTMENT_UPDATED = "Appointment updated successfully";
        public static final String APPOINTMENT_CANCELLED = "Appointment cancelled successfully";
        public static final String APPOINTMENT_COMPLETED = "Appointment completed successfully";
        
        public static final String PROFILE_UPDATED = "Profile updated successfully";
        public static final String PASSWORD_CHANGED = "Password changed successfully";
        public static final String PASSWORD_RESET = "Password reset successfully";
        
        public static final String FILE_UPLOADED = "File uploaded successfully";
        public static final String DATA_EXPORTED = "Data exported successfully";
        
        public static final String LOGIN_SUCCESSFUL = "Login successful";
        public static final String LOGOUT_SUCCESSFUL = "Logout successful";
        public static final String REGISTRATION_SUCCESSFUL = "Registration successful";
    }
    
    // Application Configuration
    public static final class Config {
        public static final int DEFAULT_PAGE_SIZE = 20;
        public static final int MAX_PAGE_SIZE = 100;
        public static final int MIN_PASSWORD_LENGTH = 8;
        public static final int MAX_LOGIN_ATTEMPTS = 5;
        public static final long LOGIN_ATTEMPT_WINDOW_MS = 900000; // 15 minutes
        public static final long SESSION_TIMEOUT_MS = 3600000; // 1 hour
        public static final long IP_BLOCK_DURATION_MS = 3600000; // 1 hour
        
        public static final String DEFAULT_PROFILE_IMAGE = "default-profile.png";
        public static final String UPLOAD_PATH = "uploads/";
        public static final long MAX_FILE_SIZE = 10485760; // 10MB
        
        public static final String[] ALLOWED_IMAGE_EXTENSIONS = {"jpg", "jpeg", "png", "gif"};
        public static final String[] ALLOWED_DOCUMENT_EXTENSIONS = {"pdf", "doc", "docx", "txt"};
    }
    
    // User Roles
    public static final class Roles {
        public static final String DOCTOR = "DOCTOR";
        public static final String PATIENT = "PATIENT";
        public static final String ADMIN = "ADMIN";
        
        public static final String ROLE_DOCTOR = "ROLE_DOCTOR";
        public static final String ROLE_PATIENT = "ROLE_PATIENT";
        public static final String ROLE_ADMIN = "ROLE_ADMIN";
    }
    
    // Appointment Status
    public static final class AppointmentStatus {
        public static final String SCHEDULED = "SCHEDULED";
        public static final String CONFIRMED = "CONFIRMED";
        public static final String IN_PROGRESS = "IN_PROGRESS";
        public static final String COMPLETED = "COMPLETED";
        public static final String CANCELLED = "CANCELLED";
        public static final String NO_SHOW = "NO_SHOW";
    }
    
    // Date and Time Formats
    public static final class DateTimeFormats {
        public static final String DATE_FORMAT = "yyyy-MM-dd";
        public static final String TIME_FORMAT = "HH:mm";
        public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
        public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    }
    
    // API Response Codes
    public static final class ResponseCodes {
        public static final String SUCCESS = "SUCCESS";
        public static final String ERROR = "ERROR";
        public static final String WARNING = "WARNING";
        public static final String INFO = "INFO";
    }
    

}