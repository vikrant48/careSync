package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.PaymentRequestDto;
import com.vikrant.careSync.dto.PaymentResponseDto;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Booking;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.Payment;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.BookingRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Demo payments: server-side price and ownership, no card secrets kept, last used method remembered. */
class PaymentServiceDemoModeTest {

    private PaymentRepository paymentRepository;
    private BookingRepository bookingRepository;
    private AppointmentRepository appointmentRepository;
    private PaymentPreferenceService preferenceService;
    private PaymentService service;
    private Patient patientA;
    private Doctor doctorX;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        PatientRepository patientRepository = mock(PatientRepository.class);
        bookingRepository = mock(BookingRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        preferenceService = mock(PaymentPreferenceService.class);
        RazorpayService razorpayService = mock(RazorpayService.class);
        AfterCommitTaskDispatcher dispatcher = mock(AfterCommitTaskDispatcher.class);
        // Run the after-commit task straight away
        doAnswer(inv -> {
            inv.getArgument(1, Runnable.class).run();
            return null;
        }).when(dispatcher).submitAfterCommit(anyString(), any(Runnable.class));

        service = new PaymentService(paymentRepository, patientRepository, bookingRepository, razorpayService,
                appointmentRepository, preferenceService, dispatcher);
        ReflectionTestUtils.setField(service, "demoMode", true);
        ReflectionTestUtils.setField(service, "paymentCurrency", "INR");

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(razorpayService.createOrder(any(), any(), any())).thenReturn("order_mock_1");

        patientA = Patient.builder().id(201L).firstName("Pat").lastName("A").build();
        when(patientRepository.findById(201L)).thenReturn(Optional.of(patientA));
        doctorX = Doctor.builder().id(101L).firstName("Doc").lastName("X")
                .consultationFees(new BigDecimal("500.00")).build();
    }

    private Appointment appointment(Long id, Patient patient) {
        Appointment appointment = Appointment.builder().id(id).doctor(doctorX).patient(patient)
                .appointmentDateTime(LocalDateTime.now().plusDays(1)).status(Appointment.Status.BOOKED).build();
        when(appointmentRepository.findByIdForUpdate(id)).thenReturn(Optional.of(appointment));
        return appointment;
    }

    private PaymentRequestDto upiRequest(String amount) {
        PaymentRequestDto request = new PaymentRequestDto();
        request.setAmount(new BigDecimal(amount));
        request.setPaymentType(Payment.PaymentType.APPOINTMENT);
        request.setPaymentMethod(Payment.PaymentMethod.UPI);
        request.setPatientId(201L);
        request.setUpiId("pat.a@okbank");
        return request;
    }

    private PaymentRequestDto cardRequest(String amount) {
        PaymentRequestDto request = upiRequest(amount);
        request.setPaymentMethod(Payment.PaymentMethod.CARD);
        request.setUpiId(null);
        request.setCardDetails(new PaymentRequestDto.CardDetailsDto("4111111111111111", "12", "30", "123", "Pat A"));
        return request;
    }

    @Test
    void appointmentAmountMustMatchDoctorsFee() {
        appointment(500L, patientA);
        PaymentRequestDto request = upiRequest("1.00");
        request.setAppointmentId(500L);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.initiatePayment(request));
        assertTrue(error.getMessage().contains("consultation fee"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void fullFeeIsAcceptedAndLabelledAsDemo() {
        appointment(500L, patientA);
        PaymentRequestDto request = upiRequest("500");
        request.setAppointmentId(500L);

        PaymentResponseDto response = service.initiatePayment(request);

        assertEquals(Payment.PaymentStatus.SUCCESS, response.getPaymentStatus());
        assertTrue(response.isDemoMode());
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeastOnce()).save(saved.capture());
        assertEquals(PaymentService.DEMO_GATEWAY_NOTE, saved.getValue().getGatewayResponse());
        assertEquals("INR", saved.getValue().getCurrency());
    }

    @Test
    void doctorWithoutFeeMeansFreeConsultation() {
        doctorX.setConsultationFees(null);
        appointment(500L, patientA);
        PaymentRequestDto request = upiRequest("0");
        request.setAppointmentId(500L);

        assertEquals(Payment.PaymentStatus.SUCCESS, service.initiatePayment(request).getPaymentStatus());
    }

    @Test
    void cannotPayForAnotherPatientsAppointment() {
        appointment(500L, Patient.builder().id(999L).firstName("Other").lastName("P").build());
        PaymentRequestDto request = upiRequest("500");
        request.setAppointmentId(500L);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.initiatePayment(request));
        assertTrue(error.getMessage().contains("does not belong"));
    }

    @Test
    void cancelledAppointmentCannotBePaid() {
        appointment(500L, patientA).setStatus(Appointment.Status.CANCELLED_BY_DOCTOR);
        PaymentRequestDto request = upiRequest("500");
        request.setAppointmentId(500L);

        assertThrows(RuntimeException.class, () -> service.initiatePayment(request));
    }

    @Test
    void secondPaymentForSameAppointmentIsRefused() {
        appointment(500L, patientA);
        when(paymentRepository.countActivePaymentsByAppointmentId(500L)).thenReturn(1L);
        PaymentRequestDto request = upiRequest("500");
        request.setAppointmentId(500L);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.initiatePayment(request));
        assertTrue(error.getMessage().contains("already exists"));
    }

    @Test
    void paymentMustBeLinkedToAppointmentOrBooking() {
        RuntimeException error = assertThrows(RuntimeException.class, () -> service.initiatePayment(upiRequest("10")));
        assertTrue(error.getMessage().contains("linked"));
    }

    @Test
    void bookingAmountAndOwnerAreChecked() {
        Booking booking = new Booking();
        booking.setId(700L);
        booking.setPatient(patientA);
        booking.setTotalPrice(new BigDecimal("1200.00"));
        booking.setStatus(Booking.BookingStatus.PENDING);
        when(bookingRepository.findByIdForUpdate(700L)).thenReturn(Optional.of(booking));

        PaymentRequestDto wrongAmount = upiRequest("1");
        wrongAmount.setBookingId(700L);
        assertThrows(RuntimeException.class, () -> service.initiatePayment(wrongAmount));

        booking.setPatient(Patient.builder().id(999L).build());
        PaymentRequestDto otherPatient = upiRequest("1200");
        otherPatient.setBookingId(700L);
        assertThrows(RuntimeException.class, () -> service.initiatePayment(otherPatient));
    }

    @Test
    void cardPaymentKeepsOnlyLastFourAndBrand() {
        appointment(500L, patientA);
        PaymentRequestDto request = cardRequest("500");
        request.setAppointmentId(500L);

        service.initiatePayment(request);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeastOnce()).save(saved.capture());
        assertEquals("1111", saved.getValue().getCardLastFour());
        assertEquals("VISA", saved.getValue().getCardType());
        // Nothing sensitive is left on the request after the payment
        assertNull(request.getCardDetails().getCardNumber());
        assertNull(request.getCardDetails().getCvv());
        assertFalse(request.getCardDetails().toString().contains("4111"));

        ArgumentCaptor<PaymentPreferenceService.SavedDetails> remembered =
                ArgumentCaptor.forClass(PaymentPreferenceService.SavedDetails.class);
        verify(preferenceService).remember(eq(201L), remembered.capture());
        assertEquals(Payment.PaymentMethod.CARD, remembered.getValue().paymentMethod());
        assertEquals("1111", remembered.getValue().cardLastFour());
        assertEquals("VISA", remembered.getValue().cardBrand());
        assertEquals("Pat A", remembered.getValue().cardholderName());
        assertNull(remembered.getValue().upiId());
    }

    @Test
    void cardDetailsNeverPrintedInLogsOrToString() {
        PaymentRequestDto request = cardRequest("500");
        String printed = request.toString();
        assertFalse(printed.contains("4111111111111111"));
        assertFalse(printed.contains("cvv"));
    }

    @Test
    void upiPaymentRemembersUpiIdOnly() {
        appointment(500L, patientA);
        PaymentRequestDto request = upiRequest("500");
        request.setAppointmentId(500L);

        service.initiatePayment(request);

        verify(preferenceService).remember(eq(201L), argThat(details -> details.paymentMethod() == Payment.PaymentMethod.UPI
                && "pat.a@okbank".equals(details.upiId()) && details.cardLastFour() == null
                && details.cardholderName() == null));
    }

    @Test
    void methodsNeedTheirDetails() {
        PaymentRequestDto upi = upiRequest("500");
        upi.setAppointmentId(500L);
        upi.setUpiId(" ");
        assertThrows(RuntimeException.class, () -> service.initiatePayment(upi));

        PaymentRequestDto card = cardRequest("500");
        card.setAppointmentId(500L);
        card.getCardDetails().setCardNumber("1234");
        assertThrows(RuntimeException.class, () -> service.initiatePayment(card));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void failedValidationStillNeverSavesPreference() {
        PaymentRequestDto request = upiRequest("1");
        request.setAppointmentId(500L);
        appointment(500L, patientA);

        assertThrows(RuntimeException.class, () -> service.initiatePayment(request));
        verifyNoInteractions(preferenceService);
    }

    @Test
    void outsideDemoModeEveryPaymentIsRefused() {
        ReflectionTestUtils.setField(service, "demoMode", false);
        appointment(500L, patientA);
        PaymentRequestDto request = upiRequest("500");
        request.setAppointmentId(500L);

        assertThrows(IllegalStateException.class, () -> service.initiatePayment(request));
        assertThrows(IllegalStateException.class, () -> service.payForBooking(700L, upiRequest("500")));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void savedDetailsRefuseAFullCardNumber() {
        assertThrows(IllegalArgumentException.class, () -> new PaymentPreferenceService.SavedDetails(
                Payment.PaymentMethod.CARD, null, "4111111111111111", "VISA", "Pat A"));
    }
}
