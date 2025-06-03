package com.shortscreator.controller;

import com.shortscreator.service.YouTubeOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/youtube")
@RequiredArgsConstructor
public class YouTubeWebController {

  private final YouTubeOAuthService youTubeOAuthService;

  /**
   * YouTube OAuth 설정 페이지를 표시합니다.
   */
  @GetMapping("/setup")
  public String showSetupPage(Model model) {
    log.info("YouTube OAuth 설정 페이지 요청");

    boolean isAuthenticated = youTubeOAuthService.hasStoredCredentials();
    String authStatus = youTubeOAuthService.getAuthenticationStatus();

    model.addAttribute("isAuthenticated", isAuthenticated);
    model.addAttribute("authStatus", authStatus);
    model.addAttribute("initiateUrl", "/api/youtube/oauth/initiate");
    model.addAttribute("statusUrl", "/api/youtube/oauth/status");
    model.addAttribute("testUrl", "/api/youtube/config/playlists");

    return "youtube-setup"; // youtube-setup.html 템플릿
  }
}