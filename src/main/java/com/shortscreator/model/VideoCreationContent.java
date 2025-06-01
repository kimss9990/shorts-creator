package com.shortscreator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoCreationContent {

  @JsonProperty("daily_tip_title")
  private String dailyTipTitle;

  @JsonProperty("daily_tip_script")
  private String dailyTipScript;

  @JsonProperty("invideo_ai_prompt")
  private String invideoPrompt;

  @JsonProperty("youtube_short_description")
  private String youtubeShortDescription;
}