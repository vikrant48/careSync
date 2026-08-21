package com.vikrant.careSync.security;

import com.vikrant.careSync.constants.AppConstants;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        Set<GrantedAuthority> authorities = new HashSet<>();

        if (user.getRole() != null) {
            String roleName = user.getRole().name();
            if (!roleName.startsWith("ROLE_")) {
                roleName = "ROLE_" + roleName;
            }
            authorities.add(new SimpleGrantedAuthority(roleName));
        }

        if (doctorRepository.existsByUsername(username)) {
            authorities.add(new SimpleGrantedAuthority(AppConstants.Roles.ROLE_DOCTOR));
        }
        if (patientRepository.existsByUsername(username)) {
            authorities.add(new SimpleGrantedAuthority(AppConstants.Roles.ROLE_PATIENT));
        }

        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority(AppConstants.Roles.ROLE_PATIENT));
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                authorities);
    }
}