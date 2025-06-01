package com.shortscreator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
@Getter
public class ApiConfig {
    
    @Value("${openai.api.key}")
    private String openaiApiKey;
    
    @Value("${openai.api.model}")
    private String openaiModel;
    
    @Value("${youtube.api.key}")
    private String youtubeApiKey;

    @PostConstruct
    public void logConfiguration() {
        log.info("OpenAI API Configuration:");
        log.info("- Model: {}", openaiModel);
        log.info("- API Key: {}", openaiApiKey != null ? "Configured" : "Not configured");
        log.info("YouTube API Configuration:");
        log.info("- API Key: {}", youtubeApiKey != null ? "Configured" : "Not configured");
    }
} 