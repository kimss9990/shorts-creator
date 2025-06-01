package com.shortscreator.controller;

import com.shortscreator.config.ApiConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class ConfigTestController {

    private final ApiConfig apiConfig;

    @GetMapping("/config")
    public String testConfig() {
        return String.format("""
            Configuration Test:
            OpenAI API Key: %s
            YouTube API Key: %s
            """, 
            maskApiKey(apiConfig.getOpenaiApiKey()),
            maskApiKey(apiConfig.getYoutubeApiKey())
        );
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "Not configured";
        }
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }
} 