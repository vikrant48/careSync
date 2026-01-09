package com.vikrant.careSync.dto;

import com.vikrant.careSync.security.entity.BlockedIP;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BlockedIPDto {
    private Long id;
    private String ipAddress;
    private String reason;
    private Instant blockedAt;
    private Instant expiresAt;
    private boolean active;

    public BlockedIPDto(BlockedIP blockedIP) {
        this.id = blockedIP.getId();
        this.ipAddress = blockedIP.getIpAddress();
        this.reason = blockedIP.getReason();
        this.blockedAt = blockedIP.getBlockedAt();
        this.expiresAt = blockedIP.getExpiresAt();
        this.active = blockedIP.isActive();
    }
}
