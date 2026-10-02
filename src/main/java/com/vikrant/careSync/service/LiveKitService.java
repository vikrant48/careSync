package com.vikrant.careSync.service;

import io.livekit.server.AccessToken;
import io.livekit.server.RoomServiceClient;
import io.livekit.server.RoomName;
import io.livekit.server.RoomJoin;
import io.livekit.server.CanPublish;
import io.livekit.server.CanSubscribe;
import io.livekit.server.CanPublishData;
import io.livekit.server.RoomAdmin;
import livekit.LivekitModels.ParticipantInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.util.List;

@Service
@Slf4j
public class LiveKitService {

    @Value("${livekit.url}")
    private String livekitUrl;

    @Value("${livekit.api-key}")
    private String apiKey;

    @Value("${livekit.api-secret}")
    private String apiSecret;

    /**
     * Generate a short-lived LiveKit AccessToken JWT for a room participant.
     */
    public String generateToken(String roomName, String userId, String displayName, String role, long durationSeconds) {
        AccessToken token = new AccessToken(apiKey, apiSecret);
        token.setIdentity(userId);
        token.setName(displayName);
        token.setMetadata("{\"role\":\"" + role + "\"}");
        token.setTtl(Math.max(durationSeconds, 600)); // Minimum 10 minutes

        token.addGrants(
                new RoomName(roomName),
                new RoomJoin(true),
                new CanPublish(true),
                new CanSubscribe(true),
                new CanPublishData(true));

        if ("DOCTOR".equalsIgnoreCase(role)) {
            token.addGrants(new RoomAdmin(true));
        }

        return token.toJwt();
    }

    /**
     * Check if a doctor is currently connected to the room.
     */
    public boolean isDoctorInRoom(String roomName) {
        try {
            RoomServiceClient client = RoomServiceClient.create(livekitUrl, apiKey, apiSecret);
            Response<List<ParticipantInfo>> response = client.listParticipants(roomName).execute();
            if (response.isSuccessful() && response.body() != null) {
                List<ParticipantInfo> participants = response.body();
                log.info("RoomServiceClient listParticipants for '{}': {} participant(s) found", roomName, participants.size());
                for (ParticipantInfo p : participants) {
                    String meta = p.getMetadata() != null ? p.getMetadata().toUpperCase() : "";
                    String name = p.getName() != null ? p.getName().toLowerCase() : "";
                    String identity = p.getIdentity() != null ? p.getIdentity().toLowerCase() : "";

                    if (meta.contains("DOCTOR") || name.contains("dr") || name.contains("doctor") || identity.contains("dr") || identity.contains("doc")) {
                        return true;
                    }
                }
                // Fallback: If participants exist in room, consider host present
                if (!participants.isEmpty()) {
                    return true;
                }
            } else {
                log.warn("listParticipants response not successful for room {}: code={}", roomName, response.code());
            }
        } catch (Exception e) {
            log.warn("Could not query room participants for room {}: {}", roomName, e.getMessage());
        }
        return false;
    }

    /**
     * Delete and close a room in LiveKit.
     */
    public void endRoom(String roomName) {
        try {
            RoomServiceClient client = RoomServiceClient.create(livekitUrl, apiKey, apiSecret);
            client.deleteRoom(roomName).execute();
            log.info("Successfully closed LiveKit room: {}", roomName);
        } catch (Exception e) {
            log.error("Failed to delete LiveKit room {}: {}", roomName, e.getMessage(), e);
        }
    }

    public String getLivekitUrl() {
        return livekitUrl;
    }
}
