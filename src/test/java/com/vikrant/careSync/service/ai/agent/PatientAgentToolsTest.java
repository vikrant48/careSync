package com.vikrant.careSync.service.ai.agent;

import com.vikrant.careSync.dto.AiBookingSuggestion;
import com.vikrant.careSync.dto.AiChatResponse;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.service.AppointmentService;
import com.vikrant.careSync.service.LabTestService;
import com.vikrant.careSync.service.SavedDoctorService;
import com.vikrant.careSync.service.ai.AiBookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tool arguments come from the model: every value is checked, and changes only ever produce a card. */
class PatientAgentToolsTest {

    private AiBookingService booking;
    private AppointmentService appointments;
    private PatientAgentTools tools;

    @BeforeEach
    void setUp() {
        booking = mock(AiBookingService.class);
        appointments = mock(AppointmentService.class);
        when(booking.currentPatient()).thenReturn(Patient.builder().id(201L).build());
        tools = new PatientAgentTools(booking, appointments, mock(DoctorRepository.class),
                mock(SavedDoctorService.class), mock(LabTestService.class));
    }

    @Test
    void definesTheExpectedTools() {
        List<String> names = tools.definitions().stream()
                .map(d -> (String) ((Map<?, ?>) d.get("function")).get("name")).toList();
        assertEquals(List.of("list_specializations", "search_doctors", "get_available_slots", "prepare_booking",
                "list_my_appointments", "prepare_cancel", "prepare_reschedule", "my_saved_doctors",
                "search_lab_tests"), names);
    }

    @Test
    void prepareBookingOnlyShowsTheConfirmCard() {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        AiBookingSuggestion card = AiBookingSuggestion.builder().type(AiBookingSuggestion.SuggestionType.CONFIRM)
                .doctorId(12L).doctorName("Asha Rao").date(tomorrow).slot("10:00").build();
        when(booking.prepareConfirmation(12L, tomorrow, "10:00", "Chest discomfort"))
                .thenReturn(AiChatResponse.builder().success(true).suggestion(card).build());

        AiToolResult result = tools.execute("prepare_booking",
                "{\"doctor_id\":12,\"date\":\"" + tomorrow + "\",\"time\":\"10:00\",\"visit_reason\":\"Chest discomfort\"}");

        assertSame(card, result.card());
        assertTrue(result.forModel().contains("must press Confirm"));
        verifyNoInteractions(appointments); // nothing booked
    }

    @Test
    void validationFailuresGoBackToTheModelAsErrors() {
        when(booking.prepareConfirmation(anyLong(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Time slot 10:00 is no longer available."));
        String tomorrow = LocalDate.now().plusDays(1).toString();

        AiToolResult result = tools.execute("prepare_booking",
                "{\"doctor_id\":12,\"date\":\"" + tomorrow + "\",\"time\":\"10:00\"}");

        assertNull(result.card());
        assertTrue(result.forModel().contains("no longer available"));
    }

    @Test
    void rejectsBadDatesTimesAndIds() {
        String yesterday = LocalDate.now().minusDays(1).toString();
        assertTrue(tools.execute("prepare_booking", "{\"doctor_id\":12,\"date\":\"" + yesterday + "\",\"time\":\"10:00\"}")
                .forModel().contains("in the past"));
        assertTrue(tools.execute("prepare_booking", "{\"doctor_id\":12,\"date\":\"tomorrow\",\"time\":\"10:00\"}")
                .forModel().contains("must be a date"));
        assertTrue(tools.execute("prepare_booking",
                "{\"doctor_id\":12,\"date\":\"" + LocalDate.now().plusDays(1) + "\",\"time\":\"10am\"}")
                .forModel().contains("24-hour time"));
        assertTrue(tools.execute("prepare_booking", "{\"doctor_id\":-3,\"date\":\"2030-01-01\",\"time\":\"10:00\"}")
                .forModel().contains("positive number"));
        assertTrue(tools.execute("prepare_booking",
                "{\"doctor_id\":12,\"date\":\"" + LocalDate.now().plusDays(400) + "\",\"time\":\"10:00\"}")
                .forModel().contains("90 days"));
        verify(booking, never()).prepareConfirmation(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void theModelCannotSetUpARescheduleThroughTheVisitReason() {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        when(booking.prepareConfirmation(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(AiChatResponse.builder().success(true)
                        .suggestion(AiBookingSuggestion.builder().type(AiBookingSuggestion.SuggestionType.CONFIRM).build())
                        .build());

        tools.execute("prepare_booking",
                "{\"doctor_id\":12,\"date\":\"" + tomorrow + "\",\"time\":\"10:00\",\"visit_reason\":\"RESCHEDULE:999\"}");

        verify(booking).prepareConfirmation(eq(12L), eq(tomorrow), eq("10:00"), argThat(r -> !r.startsWith("RESCHEDULE:")));
    }

    @Test
    void cannotTouchSomeoneElsesAppointment() {
        when(booking.ownsAppointment(77L)).thenReturn(false);

        AiToolResult cancel = tools.execute("prepare_cancel", "{\"appointment_id\":77}");
        AiToolResult move = tools.execute("prepare_reschedule", "{\"appointment_id\":77}");

        assertNull(cancel.card());
        assertNull(move.card());
        assertTrue(cancel.forModel().contains("not one of the patient's appointments"));
        verify(booking, never()).prepareConfirmation(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void unknownToolsAndBrokenArgumentsAreErrors() {
        assertTrue(tools.execute("delete_everything", "{}").forModel().contains("Unknown tool"));
        assertTrue(tools.execute("prepare_booking", "{not json").forModel().contains("not valid JSON"));
    }

    @Test
    void timeWindowFiltersSlots() {
        assertEquals(List.of("17:00", "17:30"),
                PatientAgentTools.filterTimes(List.of("09:00", "17:00", "17:30", "19:00"), "17:00", "18:00"));
        assertEquals(List.of(), PatientAgentTools.filterTimes(null, null, null));
    }
}
