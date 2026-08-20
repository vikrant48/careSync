package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummaryDto {
    private Long userId;
    private String username;
    private String role;
    private Boolean isActive;

    public UserSummaryDto(User user) {
        if (user != null) {
            this.userId = user.getId();
            this.username = user.getUsername();
            this.role = user.getRole() != null ? user.getRole().name() : null;
            this.isActive = user.getIsActive() != null ? user.getIsActive() : true;
        }
    }
}
