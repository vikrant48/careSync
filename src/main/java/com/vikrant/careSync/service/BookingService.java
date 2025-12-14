package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.BookingRequest;
import com.vikrant.careSync.dto.BookingResponse;
import com.vikrant.careSync.dto.LabTestDto;
import com.vikrant.careSync.dto.PaymentRequestDto;
import com.vikrant.careSync.dto.PaymentResponseDto;
import com.vikrant.careSync.dto.DocumentDto; // Added
import com.vikrant.careSync.entity.Booking;
import com.vikrant.careSync.entity.LabTest;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.Document; // Added
import com.vikrant.careSync.repository.BookingRepository;
import com.vikrant.careSync.repository.LabTestRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.DocumentRepository; // Added
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class BookingService {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private LabTestRepository labTestRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private DocumentRepository documentRepository; // Added

    @Autowired
    private LabTestService labTestService;

    @Autowired
    private PaymentService paymentService;

    /**
     * Create booking with payment for patients (integrated flow)
     */
    public BookingResponse createBookingWithPayment(BookingRequest request, PaymentRequestDto paymentRequest) {
        // Validate authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        String currentUsername = authentication.getName();

        // Only patients can use this method
        Patient currentPatient = patientRepository.findByUsername(currentUsername).orElse(null);
        if (currentPatient == null) {
            throw new RuntimeException("Only patients can create bookings with payment");
        }

        // Patient is booking for themselves
        Patient patient = currentPatient;

        // Handle full body checkup
        List<Long> testIds;
        if (request.getIsFullBodyCheckup() != null && request.getIsFullBodyCheckup()) {
            // Get all active lab tests for full body checkup
            List<LabTest> allActiveTests = labTestRepository.findAllActiveTestsOrderByName();
            testIds = allActiveTests.stream().map(LabTest::getId).collect(Collectors.toList());
        } else {
            testIds = request.getSelectedTestIds();
        }

        // Validate selected tests
        if (testIds == null || testIds.isEmpty()) {
            throw new RuntimeException("At least one test must be selected");
        }

        List<LabTest> selectedTests = labTestRepository.findAllById(testIds);
        if (selectedTests.size() != testIds.size()) {
            throw new RuntimeException("One or more selected tests not found");
        }

        // Validate all tests are active
        boolean hasInactiveTest = selectedTests.stream().anyMatch(test -> !test.getIsActive());
        if (hasInactiveTest) {
            throw new RuntimeException("One or more selected tests are not available");
        }

        // Calculate total price
        BigDecimal totalPrice = labTestRepository.calculateTotalPriceByIds(testIds);

        // Validate payment amount matches booking total
        if (paymentRequest.getAmount().compareTo(totalPrice) != 0) {
            throw new RuntimeException("Payment amount does not match booking total");
        }

        // Create booking entity first
        Booking booking = new Booking();
        booking.setPatient(patient);
        booking.setSelectedTests(selectedTests);
        booking.setTotalPrice(totalPrice);
        booking.setPrescribedBy("Self");
        booking.setBookingDate(request.getBookingDate() != null ? request.getBookingDate() : LocalDateTime.now());
        booking.setNotes(request.getNotes());
        booking.setStatus(Booking.BookingStatus.PENDING); // Initially pending until payment is successful

        // Save booking
        booking = bookingRepository.save(booking);

        // Set booking ID in payment request
        paymentRequest.setBookingId(booking.getId());
        paymentRequest.setPatientId(patient.getId());

        try {
            // Process payment through PaymentService
            PaymentResponseDto paymentResponse = paymentService.initiatePayment(paymentRequest);

            // If payment initiation is successful, update booking status to COMPLETED
            booking.setStatus(Booking.BookingStatus.COMPLETED);
            booking = bookingRepository.save(booking);

            // Return booking response with payment details
            BookingResponse response = convertToBookingResponse(booking);
            response.setPaymentTransactionId(paymentResponse.getTransactionId());
            response.setPaymentStatus(paymentResponse.getPaymentStatus().toString());

            return response;

        } catch (Exception e) {
            // If payment fails, keep booking as PENDING or delete it
            // For now, we'll keep it as PENDING so patient can retry payment later
            throw new RuntimeException("Payment failed: " + e.getMessage());
        }
    }

    /**
     * Create booking for doctors (no payment required)
     */
    public BookingResponse createDoctorBooking(BookingRequest request) {
        // Validate authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        String currentUsername = authentication.getName();

        // Only doctors can use this method
        Doctor currentDoctor = doctorRepository.findByUsername(currentUsername).orElse(null);
        if (currentDoctor == null) {
            throw new RuntimeException("Only doctors can create bookings for patients");
        }

        // Doctor must specify patient ID
        if (request.getPatientId() == null) {
            throw new RuntimeException("Patient ID is required when doctor creates booking");
        }

        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + request.getPatientId()));

        // Handle full body checkup
        List<Long> testIds;
        if (request.getIsFullBodyCheckup() != null && request.getIsFullBodyCheckup()) {
            // Get all active lab tests for full body checkup
            List<LabTest> allActiveTests = labTestRepository.findAllActiveTestsOrderByName();
            testIds = allActiveTests.stream().map(LabTest::getId).collect(Collectors.toList());
        } else {
            testIds = request.getSelectedTestIds();
        }

        // Validate selected tests
        if (testIds == null || testIds.isEmpty()) {
            throw new RuntimeException("At least one test must be selected");
        }

        List<LabTest> selectedTests = labTestRepository.findAllById(testIds);
        if (selectedTests.size() != testIds.size()) {
            throw new RuntimeException("One or more selected tests not found");
        }

        // Validate all tests are active
        boolean hasInactiveTest = selectedTests.stream().anyMatch(test -> !test.getIsActive());
        if (hasInactiveTest) {
            throw new RuntimeException("One or more selected tests are not available");
        }

        // Calculate total price
        BigDecimal totalPrice = labTestRepository.calculateTotalPriceByIds(testIds);

        // Create booking entity
        Booking booking = new Booking();
        booking.setPatient(patient);
        booking.setSelectedTests(selectedTests);
        booking.setTotalPrice(totalPrice);
        booking.setDoctor(currentDoctor);
        booking.setPrescribedBy("Doctor " + currentDoctor.getFirstName() + " " + currentDoctor.getLastName());
        booking.setBookingDate(request.getBookingDate() != null ? request.getBookingDate() : LocalDateTime.now());
        booking.setNotes(request.getNotes());
        booking.setStatus(Booking.BookingStatus.PENDING); // Pending until patient pays

        // Save booking
        booking = bookingRepository.save(booking);

        return convertToBookingResponse(booking);
    }

    /**
     * Create booking (legacy method - will route to appropriate method based on
     * user role)
     */
    public BookingResponse createBooking(BookingRequest request) {
        // Get current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        String currentUsername = authentication.getName();

        // Determine if current user is doctor or patient
        Doctor currentDoctor = doctorRepository.findByUsername(currentUsername).orElse(null);
        Patient currentPatient = patientRepository.findByUsername(currentUsername).orElse(null);

        if (currentDoctor == null && currentPatient == null) {
            throw new RuntimeException("Current user not found");
        }

        // Get the patient for the booking
        Patient patient;
        if (request.getPatientId() != null) {
            // Doctor is booking for a patient
            if (currentDoctor == null) {
                throw new RuntimeException("Only doctors can book for other patients");
            }
            patient = patientRepository.findById(request.getPatientId())
                    .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + request.getPatientId()));
        } else {
            // Patient is booking for themselves
            if (currentPatient == null) {
                throw new RuntimeException("Patient ID is required when doctor creates booking");
            }
            patient = currentPatient;
        }

        // Handle full body checkup
        List<Long> testIds;
        if (request.getIsFullBodyCheckup() != null && request.getIsFullBodyCheckup()) {
            // Get all active lab tests for full body checkup
            List<LabTest> allActiveTests = labTestRepository.findAllActiveTestsOrderByName();
            testIds = allActiveTests.stream().map(LabTest::getId).collect(Collectors.toList());
        } else {
            testIds = request.getSelectedTestIds();
        }

        // Validate selected tests
        if (testIds == null || testIds.isEmpty()) {
            throw new RuntimeException("At least one test must be selected");
        }

        List<LabTest> selectedTests = labTestRepository.findAllById(testIds);
        if (selectedTests.size() != testIds.size()) {
            throw new RuntimeException("One or more selected tests not found");
        }

        // Validate all tests are active
        boolean hasInactiveTest = selectedTests.stream().anyMatch(test -> !test.getIsActive());
        if (hasInactiveTest) {
            throw new RuntimeException("One or more selected tests are not available");
        }

        // Calculate total price
        BigDecimal totalPrice = labTestRepository.calculateTotalPriceByIds(testIds);

        // Determine prescribedBy
        String prescribedBy;
        if (currentDoctor != null) {
            prescribedBy = "Doctor " + currentDoctor.getFirstName() + " " + currentDoctor.getLastName();
        } else {
            prescribedBy = "Self";
        }

        // Create booking entity
        Booking booking = new Booking();
        booking.setPatient(patient);
        booking.setSelectedTests(selectedTests);
        booking.setTotalPrice(totalPrice);
        if (currentDoctor != null) {
            booking.setDoctor(currentDoctor);
        }
        booking.setPrescribedBy(prescribedBy);
        booking.setBookingDate(request.getBookingDate() != null ? request.getBookingDate() : LocalDateTime.now());
        booking.setNotes(request.getNotes());
        // Status is set to PENDING by default in entity

        // Save booking
        booking = bookingRepository.save(booking);

        return convertToBookingResponse(booking);
    }

    /**
     * Get all bookings for a specific patient
     */
    public List<BookingResponse> getBookingsByUserId(Long userId) {
        Patient patient = patientRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + userId));

        List<Booking> bookings = bookingRepository.findByPatientIdOrderByBookingDateDesc(userId);
        return bookings.stream()
                .map(this::convertToBookingResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all bookings (for admin/doctor)
     */
    public List<BookingResponse> getAllBookings() {
        List<Booking> bookings = bookingRepository.findAll();
        return bookings.stream()
                .map(this::convertToBookingResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get bookings by status
     */
    public List<BookingResponse> getBookingsByStatus(Booking.BookingStatus status) {
        List<Booking> bookings = bookingRepository.findByStatusOrderByBookingDateDesc(status);
        return bookings.stream()
                .map(this::convertToBookingResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get bookings for current user (patient only)
     */
    public List<BookingResponse> getCurrentUserBookings() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        String currentUsername = authentication.getName();
        Patient currentPatient = patientRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new RuntimeException("Only patients can view their own bookings"));

        List<Booking> bookings = bookingRepository.findByPatientOrderByBookingDateDesc(currentPatient);
        return bookings.stream()
                .map(this::convertToBookingResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update booking status
     */
    public BookingResponse updateBookingStatus(Long bookingId, Booking.BookingStatus status) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found with ID: " + bookingId));

        booking.setStatus(status);
        booking = bookingRepository.save(booking);

        return convertToBookingResponse(booking);
    }

    /**
     * Cancel a booking by setting its status to CANCELLED
     */
    public BookingResponse cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found with ID: " + bookingId));

        // Check if booking is already cancelled
        if (booking.getStatus() == Booking.BookingStatus.CANCELLED) {
            throw new RuntimeException("Booking is already cancelled");
        }

        // Check if booking can be cancelled (not completed)
        if (booking.getStatus() == Booking.BookingStatus.COMPLETED) {
            throw new RuntimeException("Cannot cancel a completed booking");
        }

        booking.setStatus(Booking.BookingStatus.CANCELLED);
        booking.setUpdatedAt(LocalDateTime.now());
        booking = bookingRepository.save(booking);

        return convertToBookingResponse(booking);
    }

    /**
     * Get booking by ID
     */
    public BookingResponse getBookingById(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found with id: " + bookingId));
        return convertToBookingResponse(booking);
    }

    /**
     * Get bookings count by user
     */
    public Long getBookingsCountByUserId(Long userId) {
        return bookingRepository.countByPatientId(userId);
    }

    /**
     * Get bookings within date range
     */
    public List<BookingResponse> getBookingsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        List<Booking> bookings = bookingRepository.findByBookingDateBetween(startDate, endDate);
        return bookings.stream()
                .map(this::convertToBookingResponse)
                .collect(Collectors.toList());
    }

    /**
     * Security helper method: Check if the current user is the specified user
     * Used in @PreAuthorize expressions
     */
    public boolean isCurrentUser(Long userId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return false;
            }

            String currentUsername = authentication.getName();
            Patient currentPatient = patientRepository.findByUsername(currentUsername).orElse(null);

            return currentPatient != null && currentPatient.getId().equals(userId);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Security helper method: Check if the current user owns the specified booking
     * Used in @PreAuthorize expressions
     */
    public boolean isBookingOwner(Long bookingId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return false;
            }

            String currentUsername = authentication.getName();
            Patient currentPatient = patientRepository.findByUsername(currentUsername).orElse(null);

            if (currentPatient == null) {
                return false;
            }

            Booking booking = bookingRepository.findById(bookingId).orElse(null);
            return booking != null && booking.getPatient().getId().equals(currentPatient.getId());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Convert Booking entity to BookingResponse DTO
     */
    private BookingResponse convertToBookingResponse(Booking booking) {
        String patientName = "";

        // Get patient name
        Patient patient = booking.getPatient();
        patientName = patient.getFirstName() + " " + patient.getLastName();

        // Convert selected tests to DTOs
        List<LabTestDto> testDtos = booking.getSelectedTests().stream()
                .map(test -> LabTestDto.builder()
                        .id(test.getId())
                        .testName(test.getTestName())
                        .price(test.getPrice())
                        .description(test.getDescription())
                        .isActive(test.getIsActive())
                        .createdAt(test.getCreatedAt())
                        .updatedAt(test.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());

        BookingResponse response = BookingResponse.builder()
                .id(booking.getId())
                .patientId(booking.getPatient().getId())
                .patientName(patientName)
                .selectedTests(testDtos)
                .totalPrice(booking.getTotalPrice())
                .prescribedBy(booking.getPrescribedBy())
                .bookingDate(booking.getBookingDate())
                .status(booking.getStatus())
                .notes(booking.getNotes())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();

        // Populate lab reports
        List<Document> reports = documentRepository.findByBookingId(booking.getId());
        if (reports != null && !reports.isEmpty()) {
            response.setLabReports(reports.stream()
                    .map(DocumentDto::fromEntity)
                    .collect(Collectors.toList()));
        }

        return response;
    }
}