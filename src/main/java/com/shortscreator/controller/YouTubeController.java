package com.shortscreator.controller;

import com.shortscreator.service.YouTubeService;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.Caption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@RestController
@RequestMapping("/api/youtube")
@RequiredArgsConstructor
public class YouTubeController {

    private final YouTubeService youtubeService;

    @GetMapping("/channel/{channelId}")
    public ResponseEntity<Channel> getChannelInfo(@PathVariable String channelId) {
        log.info("Received request to get channel info for ID: {}", channelId);
        Channel channel = youtubeService.getChannelInfo(channelId);
        if (channel == null) {
            log.warn("No channel found for ID: {}", channelId);
            return ResponseEntity.notFound().build();
        }
        log.info("Successfully retrieved channel info for ID: {}", channelId);
        return ResponseEntity.ok(channel);
    }
}