package com.shortscreator.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.CaptionListResponse;
import com.google.api.services.youtube.model.Caption;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.shortscreator.config.ApiConfig;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeService {
    
    private final ApiConfig apiConfig;
    private static final String APPLICATION_NAME = "Shorts Creator";
    
    public Channel getChannelInfo(String channelId) {
        try {
            log.info("Fetching YouTube channel info for channel ID: {}", channelId);
            
            YouTube youtube = new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                null)
                .setApplicationName(APPLICATION_NAME)
                .build();

            YouTube.Channels.List request = youtube.channels()
                .list(Collections.singletonList("snippet,statistics"))
                .setKey(apiConfig.getYoutubeApiKey())
                .setId(Collections.singletonList(channelId));

            ChannelListResponse response = request.execute();
            log.info("Successfully retrieved channel info");
            
            if (response.getItems().isEmpty()) {
                log.warn("No channel found with ID: {}", channelId);
                return null;
            }
            
            return response.getItems().get(0);
        } catch (Exception e) {
            log.error("Error fetching YouTube channel info", e);
            throw new RuntimeException("Failed to fetch YouTube channel info", e);
        }
    }
} 