package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.EmailVerificationOtp;
import com.vikrant.careSync.repository.EmailVerificationOtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationOtpRepository otpRepository;
    private final EmailService emailService;

    private static final SecureRandom RANDOM = new SecureRandom();

    public void startVerification(String name, String email, String mobileNumber) {
        String otp = generateOtp();

        EmailVerificationOtp record = EmailVerificationOtp.builder()
                .email(email)
                .otp(otp)
                .name(name)
                .mobileNumber(mobileNumber)
                .createdAt(LocalDateTime.now())
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .verified(false)
                .used(false)
                .build();

        otpRepository.save(record);

        Map<String, String> model = new HashMap<>();
        model.put("name", name != null ? name : "User");
        model.put("otp", otp);

        emailService.sendTemplateEmail(
                email,
                "Verify your email",
                "email/verification.html",
                model
        );
    }

    public boolean verifyOtp(String email, String otp) {
        Optional<EmailVerificationOtp> valid = otpRepository.findValidOtp(email, otp, LocalDateTime.now());
        if (valid.isPresent()) {
            EmailVerificationOtp rec = valid.get();
            rec.setVerified(true);
            rec.setUsed(true);
            otpRepository.save(rec);
            return true;
        }
        return false;
    }

    public boolean isVerified(String email) {
        return otpRepository.findTopByEmailAndVerifiedTrueOrderByCreatedAtDesc(email).isPresent();
    }

    public void attachUserIdIfVerified(String email, Long userId) {
        // Attach created userId to latest verified record for traceability
        otpRepository.findTopByEmailAndVerifiedTrueOrderByCreatedAtDesc(email)
                .ifPresent(rec -> {
                    rec.setUserId(userId);
                    otpRepository.save(rec);
                });
    }

    private String generateOtp() {
        int n = RANDOM.nextInt(1_000_000); // 0..999999
        return String.format("%06d", n);
    }
}