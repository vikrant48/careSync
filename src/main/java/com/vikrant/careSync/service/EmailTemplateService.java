package com.vikrant.careSync.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    public String render(String templatePath, Map<String, String> model) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/" + templatePath);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String html = reader.lines().collect(Collectors.joining("\n"));
                if (model != null) {
                    for (Map.Entry<String, String> e : model.entrySet()) {
                        String key = e.getKey();
                        String val = e.getValue() == null ? "" : e.getValue();
                        html = html.replace("{{" + key + "}}", escape(val));
                    }
                }
                return html;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to render email template: " + templatePath, e);
        }
    }

    private String escape(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}