package com.vikrant.careSync.security;

import com.vikrant.careSync.dto.AiBookingSuggestion;
import com.vikrant.careSync.dto.AiChatResponse;
import com.vikrant.careSync.dto.AiEmergencyInfo;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.AiConversationRepository;
import com.vikrant.careSync.repository.UserRepository;
import com.vikrant.careSync.service.ai.AiChatHistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saved AI chats: stored encrypted, only ever visible to their owner, renamable, deletable, purged when old.
 * Not @Transactional: history is saved in its own transaction and must see the committed test users.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiChatHistoryEndpointTest {

    private static final String URL = "/api/ai/conversations";
    private static final String CHAT = "chat-aaaa-1111";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AiChatHistoryService history;
    @Autowired
    private AiConversationRepository conversationRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        userRepository.save(User.builder().id(9301L).username("chatPatientA").password("x")
                .email("chatPatientA@test.local").role(User.Role.PATIENT).build());
        userRepository.save(User.builder().id(9302L).username("chatPatientB").password("x")
                .email("chatPatientB@test.local").role(User.Role.PATIENT).build());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM ai_messages");
        jdbc.update("DELETE FROM ai_conversations");
        userRepository.deleteAllById(List.of(9301L, 9302L));
    }

    private static AiChatResponse reply(String text) {
        return AiChatResponse.builder().response(text).success(true).conversationId(CHAT)
                .suggestion(AiBookingSuggestion.builder().type(AiBookingSuggestion.SuggestionType.SPECIALIZATIONS)
                        .specializations(List.of("Dermatology")).build())
                .build();
    }

    @Test
    @WithMockUser(username = "chatPatientA", roles = "PATIENT")
    void savedChatsAreListedAndReopenedWithTheirCards() throws Exception {
        assertEquals(CHAT, history.record(CHAT, "I have an itchy rash on my arm", reply("A dermatologist can help.")));
        history.record(CHAT, "ACTION_GET_SPECIALIZATIONS", reply("Please select a specialization:"));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(CHAT))
                .andExpect(jsonPath("$[0].title").value("I have an itchy rash on my arm"))
                .andExpect(jsonPath("$[0].messageCount").value(3));

        mvc.perform(get(URL + "/" + CHAT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(3)) // button click not saved as user text
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.messages[1].suggestion.specializations[0]").value("Dermatology"));
    }

    @Test
    @WithMockUser(username = "chatPatientA", roles = "PATIENT")
    void chatsAreStoredEncrypted() {
        AiChatResponse withNotice = reply("Please call 112 now.");
        withNotice.setEmergency(AiEmergencyInfo.builder().level("EMERGENCY").title("Emergency").build());
        history.record(CHAT, "I have crushing chest pain", withNotice);

        List<String> stored = jdbc.queryForList(
                "SELECT content FROM ai_messages UNION ALL SELECT title FROM ai_conversations "
                        + "UNION ALL SELECT emergency_json FROM ai_messages WHERE emergency_json IS NOT NULL",
                String.class);
        assertFalse(stored.isEmpty());
        assertTrue(stored.stream().noneMatch(v -> v.contains("chest") || v.contains("112") || v.contains("EMERGENCY")),
                "health details are never stored as plain text");
    }

    @Test
    @WithMockUser(username = "chatPatientB", roles = "PATIENT")
    void someoneElsesChatIsInvisible() throws Exception {
        chatOwnedByA();

        mvc.perform(get(URL)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(URL + "/" + CHAT)).andExpect(status().isNotFound());
        mvc.perform(patch(URL + "/" + CHAT).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"mine\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(URL + "/" + CHAT)).andExpect(status().isNotFound());

        // Writing to A's chat id starts a separate chat for B instead
        String id = asPatient("chatPatientB", () -> history.record(CHAT, "hello", reply("Hi")));
        assertNotEquals(CHAT, id);
        assertEquals(2, conversationRepository.findByPublicId(CHAT).orElseThrow().getMessageCount(),
                "A's chat is untouched");
    }

    private void chatOwnedByA() {
        asPatient("chatPatientA", () -> history.record(CHAT, "my private question", reply("answer")));
    }

    /**
     * Runs a direct service call as the given patient. Needed because the test's own login does not survive:
     * each mvc.perform clears the login on this thread when it finishes, and CurrentUserService caches the
     * account on the test's shared mock request. So the call runs with its own login and outside that request,
     * and both are put back afterwards.
     */
    private <T> T asPatient(String username, java.util.function.Supplier<T> call) {
        org.springframework.security.core.context.SecurityContext previous =
                org.springframework.security.core.context.SecurityContextHolder.getContext();
        org.springframework.web.context.request.RequestAttributes request =
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        org.springframework.security.core.context.SecurityContext context =
                org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                username, "x",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PATIENT"))));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        try {
            return call.get();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.setContext(previous);
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(request);
        }
    }

    @Test
    @WithMockUser(username = "chatPatientA", roles = "PATIENT")
    void renameDeleteAndDeleteAll() throws Exception {
        history.record(CHAT, "first", reply("one"));
        history.record("chat-bbbb-2222", "second", reply("two"));

        mvc.perform(patch(URL + "/" + CHAT).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Skin rash\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Skin rash"));
        mvc.perform(patch(URL + "/" + CHAT).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete(URL + "/" + CHAT)).andExpect(status().isNoContent());
        mvc.perform(get(URL)).andExpect(jsonPath("$.length()").value(1));

        mvc.perform(delete(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(1));
        mvc.perform(get(URL)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(username = "chatPatientA", roles = "PATIENT")
    void retentionDeletesChatsNotUsedForAWhile() {
        history.record(CHAT, "old question", reply("old answer"));
        jdbc.update("UPDATE ai_conversations SET updated_at = ? WHERE public_id = ?",
                LocalDateTime.now().minusDays(120), CHAT);
        history.record("chat-cccc-3333", "recent question", reply("recent answer"));

        assertEquals(1, history.purgeNotUsedSince(LocalDateTime.now().minusDays(90)));
        assertTrue(conversationRepository.findByPublicId(CHAT).isEmpty());
        assertTrue(conversationRepository.findByPublicId("chat-cccc-3333").isPresent());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ai_messages", Integer.class),
                "the old chat's messages went with it; the recent chat's two remain");
    }
}
