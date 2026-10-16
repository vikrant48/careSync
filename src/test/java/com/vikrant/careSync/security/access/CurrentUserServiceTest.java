package com.vikrant.careSync.security.access;

import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserServiceTest {

    private UserRepository userRepository;
    private PatientRepository patientRepository;
    private CurrentUserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        patientRepository = mock(PatientRepository.class);
        service = new CurrentUserService(userRepository, patientRepository, mock(DoctorRepository.class));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, AuthorityUtils.createAuthorityList("ROLE_PATIENT")));
    }

    @Test
    void anonymousCallerHasNoCurrentUser() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertTrue(service.current().isEmpty());
        RuntimeException ex = assertThrows(RuntimeException.class, service::require);
        assertEquals("User not authenticated", ex.getMessage());
        verifyNoInteractions(userRepository);
    }

    @Test
    void resolvesUserOncePerRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        authenticateAs("patientA");
        when(userRepository.findByUsername("patientA")).thenReturn(Optional.of(
                User.builder().id(201L).username("patientA").role(User.Role.PATIENT).build()));

        AuthenticatedUser first = service.require();
        AuthenticatedUser second = service.require();

        assertEquals(new AuthenticatedUser(201L, "patientA", User.Role.PATIENT), first);
        assertSame(first, second);
        verify(userRepository, times(1)).findByUsername("patientA");
    }

    @Test
    void requirePatientKeepsOriginalErrorMessage() {
        authenticateAs("ghost");
        when(patientRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, service::requirePatient);
        assertEquals("Patient not found with username: ghost", ex.getMessage());
    }
}
