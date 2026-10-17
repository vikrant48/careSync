package com.vikrant.careSync.service.ai;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The user used up their AI allowance for this minute or day. Answered as HTTP 429 with a readable reason. */
public class AiQuotaExceededException extends ResponseStatusException {

    public AiQuotaExceededException(String reason) {
        super(HttpStatus.TOO_MANY_REQUESTS, reason);
    }

    /** Text safe to show the user */
    public String userMessage() {
        return getReason();
    }
}
