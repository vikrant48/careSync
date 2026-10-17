package com.vikrant.careSync.service.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * "Today" for the AI assistant, in the patients' time zone (AI_TIMEZONE, India by default). The server runs on
 * UTC, so without this "tomorrow" would be a day early in India before 05:30.
 */
@Component
public class AiClock {

    private final ZoneId zone;

    public AiClock(@Value("${app.ai.timezone:Asia/Kolkata}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public ZoneId zone() {
        return zone;
    }
}
