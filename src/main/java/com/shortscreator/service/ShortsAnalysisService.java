package com.shortscreator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortscreator.model.YouTubeShorts;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortsAnalysisService {
    
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final OpenAIService openAIService;
    private List<YouTubeShorts> shortsData;
    private final Random random = new Random();

    @Value("${openai.api.key}")
    private String openaiApiKey;

    public void loadShortsData() {
        try {
            ClassPathResource resource = new ClassPathResource("ymt287_shorts.json");
            shortsData = objectMapper.readValue(resource.getInputStream(), 
                new TypeReference<List<YouTubeShorts>>() {});
            log.info("Loaded {} shorts data entries", shortsData.size());
            
            // Log some statistics
            log.info("Shorts Analysis:");
            log.info("- Average title length: {} characters", 
                calculateAverageTitleLength());
            log.info("- Average captions per video: {}", 
                calculateAverageCaptionsCount());
            log.info("- Most common title patterns: {}", 
                analyzeTitlePatterns());
        } catch (IOException e) {
            log.error("Error loading shorts data", e);
            throw new RuntimeException("Failed to load shorts data", e);
        }
    }

    private double calculateAverageTitleLength() {
        return shortsData.stream()
            .mapToInt(s -> s.getTitle().length())
            .average()
            .orElse(0.0);
    }

    private double calculateAverageCaptionsCount() {
        return shortsData.stream()
            .mapToInt(s -> s.getCaptions().size())
            .average()
            .orElse(0.0);
    }

    private String analyzeTitlePatterns() {
        // Simple pattern analysis - looking for common words or phrases
        return shortsData.stream()
            .map(YouTubeShorts::getTitle)
            .filter(title -> title.contains("이유") || title.contains("진실") || 
                           title.contains("방법") || title.contains("특징"))
            .limit(3)
            .reduce((a, b) -> a + ", " + b)
            .orElse("No common patterns found");
    }

    public YouTubeShorts getRandomShorts() {
        if (shortsData == null || shortsData.isEmpty()) {
            loadShortsData();
        }
        return shortsData.get(random.nextInt(shortsData.size()));
    }

    public String generateSimilarTitle() {
        if (shortsData == null || shortsData.isEmpty()) {
            loadShortsData();
        }
        
        // Get a random shorts entry
        YouTubeShorts randomShorts = getRandomShorts();
        log.info("Generating similar title based on: {}", randomShorts.getTitle());
        
        // For now, just return a random title from the dataset
        // TODO: Implement more sophisticated title generation using NLP
        return randomShorts.getTitle();
    }

    public List<String> generateSimilarCaptions() {
        if (shortsData == null || shortsData.isEmpty()) {
            loadShortsData();
        }
        
        // Get a random shorts entry
        YouTubeShorts randomShorts = getRandomShorts();
        log.info("Generating similar captions based on video: {}", randomShorts.getVideoId());
        
        // For now, just return the captions from a random video
        // TODO: Implement more sophisticated caption generation using NLP
        return randomShorts.getCaptions();
    }

    public Map<String, Object> generateShortsContent() {
        if (shortsData == null || shortsData.isEmpty()) {
            loadShortsData();
        }
        YouTubeShorts randomShorts = getRandomShorts();
        log.info("Generating new shorts content based on video: {}", randomShorts.getVideoId());
        Map<String, Object> result = new HashMap<>();
        result.put("title", randomShorts.getTitle());
        result.put("captions", randomShorts.getCaptions());
        result.put("totalLines", randomShorts.getCaptions().size());
        return result;
    }

    public List<Map<String, Object>> getAllShortsContent() {
        if (shortsData == null || shortsData.isEmpty()) {
            loadShortsData();
        }
        List<Map<String, Object>> allShorts = new java.util.ArrayList<>();
        for (YouTubeShorts shorts : shortsData) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("title", shorts.getTitle());
            entry.put("captions", shorts.getCaptions());
            entry.put("videoId", shorts.getVideoId());
            allShorts.add(entry);
        }
        return allShorts;
    }
} 