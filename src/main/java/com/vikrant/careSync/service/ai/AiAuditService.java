package com.vikrant.careSync.service.ai;

import com.vikrant.careSync.entity.AiAuditLog;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.AiAuditLogRepository;
import com.vikrant.careSync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiAuditService {

    private final AiAuditLogRepository aiAuditLogRepository;
    private final UserRepository userRepository;

    public void logInteraction(String promptType, String selectedModel, long latencyMs, boolean success,
            Integer promptTokens, Integer completionTokens, Integer totalTokens,
            String bookingActionProduced, String errorMessage, String userPrompt, String aiResponse) {
        try {
            Long userId = null;
            String username = "ANONYMOUS";

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                username = auth.getName();
                Optional<User> userOpt = userRepository.findByEmail(username);
                if (userOpt.isEmpty()) {
                    userOpt = userRepository.findByUsername(username);
                }
                if (userOpt.isEmpty()) {
                    try {
                        Long parsedId = Long.parseLong(username);
                        userOpt = userRepository.findById(parsedId);
                    } catch (NumberFormatException ignored) {
                    }
                }

                if (userOpt.isPresent()) {
                    User u = userOpt.get();
                    userId = u.getId();
                    username = u.getEmail() != null ? u.getEmail() : u.getUsername();
                }
            }

            AiAuditLog auditLog = AiAuditLog.builder()
                    .userId(userId)
                    .username(username)
                    .promptType(promptType != null ? promptType : "CHAT")
                    .selectedModel(selectedModel != null ? selectedModel : "grok-beta")
                    .latencyMs(latencyMs)
                    .success(success)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .bookingActionProduced(bookingActionProduced != null ? bookingActionProduced : "NONE")
                    .errorMessage(errorMessage)
                    .userPrompt(userPrompt)
                    .aiResponse(aiResponse)
                    .createdAt(LocalDateTime.now())
                    .build();

            aiAuditLogRepository.save(auditLog);
            log.info("AI Audit Log saved [Type: {}, Model: {}, Latency: {}ms, Success: {}, Action: {}]",
                    promptType, selectedModel, latencyMs, success, bookingActionProduced);
        } catch (Exception e) {
            log.error("Failed to save AI Audit Log: {}", e.getMessage(), e);
        }
    }

    public void logInteraction(String promptType, String selectedModel, long latencyMs, boolean success,
            Integer promptTokens, Integer completionTokens, Integer totalTokens,
            String bookingActionProduced, String errorMessage) {
        logInteraction(promptType, selectedModel, latencyMs, success, promptTokens, completionTokens, totalTokens,
                bookingActionProduced, errorMessage, null, null);
    }
}
