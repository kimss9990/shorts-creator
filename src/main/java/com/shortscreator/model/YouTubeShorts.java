package com.shortscreator.model;

import lombok.Data;
import java.util.List;

@Data
public class YouTubeShorts {
    private String channelHandle;
    private String channelName;
    private boolean isShorts;
    private String dateText;
    private String relativeDateText;
    private String datePublished;
    private String videoId;
    private String title;
    private List<String> captions;
} 