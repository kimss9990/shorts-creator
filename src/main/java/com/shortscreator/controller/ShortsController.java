package com.shortscreator.controller;

import com.shortscreator.service.ShortsAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@RestController
@RequestMapping("/api/shorts")
@RequiredArgsConstructor
public class ShortsController {

    private final ShortsAnalysisService shortsAnalysisService;

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeShorts() {
        log.info("Received request to analyze shorts data");
        shortsAnalysisService.loadShortsData();
        return ResponseEntity.ok(Map.of("status", "Analysis completed"));
    }

    @GetMapping(value = "/generate/title", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<Map<String, String>> generateTitle() {
        log.info("Received request to generate shorts title");
        String title = shortsAnalysisService.generateSimilarTitle();
        return ResponseEntity.ok(Map.of("title", title));
    }

    @GetMapping(value = "/generate/captions", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<Map<String, Object>> generateCaptions() {
        log.info("Received request to generate shorts captions");
        List<String> captions = shortsAnalysisService.generateSimilarCaptions();
        Map<String, Object> response = new HashMap<>();
        response.put("captions", captions);
        response.put("totalLines", captions.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/generate/all", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<Map<String, Object>> generateAll() {
        log.info("Received request to generate complete shorts content");
        Map<String, Object> response = shortsAnalysisService.generateShortsContent();
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/generate/all-shorts", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<List<Map<String, Object>>> getAllShortsContent() {
        log.info("Received request to get all shorts content for AI context");
        List<Map<String, Object>> allShorts = shortsAnalysisService.getAllShortsContent();
        return ResponseEntity.ok(allShorts);
    }
} 