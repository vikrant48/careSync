package com.vikrant.careSync.service.interfaces;

import com.vikrant.careSync.security.dto.*;

/**
 * Service interface for Authentication operations
 * Defines all authentication and authorization related operations
 */
public interface IAuthenticationService {

    /**
     * Register a new user (doctor or patient)
     * @param request Registration request containing user details
     * @return Authentication response with tokens
     */
    AuthenticationResponse register(RegisterRequest request);

    /**
     * Authenticate user login
     * @param request Authentication request with credentials
     * @param ipAddress Client IP address
     * @param userAgent Client user agent
     * @return Authentication response with tokens
     */
    AuthenticationResponse authenticate(AuthenticationRequest request, String ipAddress, String userAgent);

    /**
     * Change user password
     * @param request Change password request
     * @param username Username of the user
     */
    void changePassword(ChangePasswordRequest request, String username);

    /**
     * Initiate forgot password process
     * @param request Forgot password request with email
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * Reset password using token
     * @param request Reset password request with token and new password
     */
    void resetPassword(ResetPasswordRequest request);
}