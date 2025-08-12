package com.vikrant.careSync.security.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LogoutResponse {
    private String message;
    private boolean success;
} 