package com.vikrant.careSync.controller;

import com.vikrant.careSync.dto.BookingRequest;
import com.vikrant.careSync.dto.BookingResponse;
import com.vikrant.careSync.dto.PaymentRequestDto;
import com.vikrant.careSync.dto.PatientBookingWithPaymentRequest;
import com.vikrant.careSync.entity.Booking;
import com.vikrant.careSync.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BookingController {

    private final BookingService bookingService;

    /**
     * Create booking with payment for patients (integrated flow)
     * Only accessible by patients
     */
    @PostMapping("/patient/with-payment")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> createPatientBookingWithPayment(@Valid @RequestBody PatientBookingWithPaymentRequest request) {
        try {
            BookingResponse booking = bookingService.createBookingWithPayment(
                request.getBookingRequest(), 
                request.getPaymentRequest()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(booking);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while creating the booking with payment");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Create booking for doctors (no payment required)
     * Only accessible by doctors
     */
    @PostMapping("/doctor")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> createDoctorBooking(@Valid @RequestBody BookingRequest request) {
        try {
            BookingResponse booking = bookingService.createDoctorBooking(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(booking);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while creating the doctor booking");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Create a new booking (legacy endpoint - will route based on user role)
     * Accessible by both doctors and patients
     */
    @PostMapping
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT')")
    public ResponseEntity<?> createBooking(@Valid @RequestBody BookingRequest request) {
        try {
            BookingResponse booking = bookingService.createBooking(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(booking);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while creating the booking");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get all bookings for a specific patient
     * Accessible by doctors, admins, and the patient themselves
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN') or (hasRole('PATIENT') and @bookingService.isCurrentUser(#userId))")
    public ResponseEntity<?> getBookingsByUserId(@PathVariable Long userId) {
        try {
            List<BookingResponse> bookings = bookingService.getBookingsByUserId(userId);
            return ResponseEntity.ok(bookings);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while retrieving bookings");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get all bookings (for admin/doctor)
     * Only accessible by doctors and admins
     */
    @GetMapping
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
    public ResponseEntity<?> getAllBookings() {
        try {
            List<BookingResponse> bookings = bookingService.getAllBookings();
            return ResponseEntity.ok(bookings);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while retrieving bookings");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get current user's bookings (for patients)
     * Only accessible by patients
     */
    @GetMapping("/my-bookings")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getCurrentUserBookings() {
        try {
            List<BookingResponse> bookings = bookingService.getCurrentUserBookings();
            return ResponseEntity.ok(bookings);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while retrieving your bookings");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get booking by ID
     * Accessible by doctors, admins, and the patient who owns the booking
     */
    @GetMapping("/{bookingId}")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN') or (hasRole('PATIENT') and @bookingService.isBookingOwner(#bookingId))")
    public ResponseEntity<?> getBookingById(@PathVariable Long bookingId) {
        try {
            BookingResponse booking = bookingService.getBookingById(bookingId);
            return ResponseEntity.ok(booking);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while retrieving the booking");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Update booking status
     * Only accessible by doctors and admins
     */
    @PutMapping("/{bookingId}/status")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
    public ResponseEntity<?> updateBookingStatus(@PathVariable Long bookingId, 
                                                @RequestParam Booking.BookingStatus status) {
        try {
            BookingResponse booking = bookingService.updateBookingStatus(bookingId, status);
            return ResponseEntity.ok(booking);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while updating the booking status");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get bookings by status
     * Only accessible by doctors and admins
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
    public ResponseEntity<?> getBookingsByStatus(@PathVariable Booking.BookingStatus status) {
        try {
            List<BookingResponse> bookings = bookingService.getBookingsByStatus(status);
            return ResponseEntity.ok(bookings);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while retrieving bookings by status");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get bookings count for a specific user
     * Accessible by doctors, admins, and the patient themselves
     */
    @GetMapping("/user/{userId}/count")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN') or (hasRole('PATIENT') and @bookingService.isCurrentUser(#userId))")
    public ResponseEntity<?> getBookingsCountByUserId(@PathVariable Long userId) {
        try {
            Long count = bookingService.getBookingsCountByUserId(userId);
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("bookingCount", count);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while retrieving booking count");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get bookings within a date range
     * Only accessible by doctors and admins
     */
    @GetMapping("/date-range")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
    public ResponseEntity<?> getBookingsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        try {
            if (startDate.isAfter(endDate)) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Start date must be before end date");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            List<BookingResponse> bookings = bookingService.getBookingsByDateRange(startDate, endDate);
            return ResponseEntity.ok(bookings);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while retrieving bookings by date range");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Cancel a booking (for patients to cancel their own bookings)
     * Only accessible by patients who own the booking
     */
    @PutMapping("/{bookingId}/cancel")
    @PreAuthorize("hasRole('PATIENT') and @bookingService.isBookingOwner(#bookingId)")
    public ResponseEntity<?> cancelBooking(@PathVariable Long bookingId) {
        try {
            BookingResponse booking = bookingService.cancelBooking(bookingId);
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("message", "Booking cancelled successfully");
            successResponse.put("booking", booking);
            return ResponseEntity.ok(successResponse);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while cancelling the booking");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}