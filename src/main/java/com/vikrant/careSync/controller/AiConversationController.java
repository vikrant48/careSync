package com.vikrant.careSync.controller;

import com.vikrant.careSync.dto.AiConversationDtos;
import com.vikrant.careSync.service.ai.AiChatHistoryService;
import com.vikrant.careSync.service.ai.AiConversationMemoryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * The logged-in user's saved AI chats: list, open, rename, delete one, delete all. Every call acts on the caller's
 * own chats only; another user's chat id answers 404.
 */
@RestController
@RequestMapping("/api/ai/conversations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
public class AiConversationController {

    private final AiChatHistoryService historyService;
    private final AiConversationMemoryService memoryService;

    @Operation(summary = "My AI chats", description = "Newest first, up to 50")
    @GetMapping
    public ResponseEntity<List<AiConversationDtos.Summary>> list() {
        return ResponseEntity.ok(historyService.list());
    }

    @Operation(summary = "Open one AI chat", description = "All messages with their cards and emergency notices")
    @GetMapping("/{conversationId}")
    public ResponseEntity<AiConversationDtos.Conversation> get(@PathVariable String conversationId) {
        return ResponseEntity.ok(historyService.get(conversationId));
    }

    @Operation(summary = "Rename an AI chat")
    @PatchMapping("/{conversationId}")
    public ResponseEntity<AiConversationDtos.Summary> rename(@PathVariable String conversationId,
            @Valid @RequestBody AiConversationDtos.RenameRequest request) {
        return ResponseEntity.ok(historyService.rename(conversationId, request.title()));
    }

    @Operation(summary = "Delete an AI chat", description = "Also clears the assistant's short-term memory of it")
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> delete(@PathVariable String conversationId) {
        historyService.delete(conversationId);
        memoryService.forget(conversationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete all my AI chats")
    @DeleteMapping
    public ResponseEntity<Map<String, Integer>> deleteAll() {
        return ResponseEntity.ok(Map.of("deleted", historyService.deleteAll()));
    }
}
