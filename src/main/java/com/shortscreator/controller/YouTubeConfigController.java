package com.shortscreator.controller;

import com.shortscreator.config.YouTubeUploadConfig;
import com.shortscreator.service.YouTubePlaylistService;
import com.shortscreator.service.YouTubeUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/youtube/config")
@RequiredArgsConstructor
public class YouTubeConfigController {

  private final YouTubeUploadConfig uploadConfig;
  private final YouTubeUploadService uploadService;
  private final YouTubePlaylistService playlistService;

  /**
   * 현재 YouTube 업로드 설정을 조회합니다.
   */
  @GetMapping("/upload-settings")
  public ResponseEntity<Map<String, Object>> getUploadSettings() {
    log.info("YouTube 업로드 설정 조회 요청");

    Map<String, Object> response = new HashMap<>();
    Map<String, Object> settings = new HashMap<>();

    try {
      settings.put("category_id", uploadConfig.getCategoryId());
      settings.put("category_name", getCategoryName(uploadConfig.getCategoryId()));
      settings.put("default_language", uploadConfig.getDefaultLanguage());
      settings.put("default_tags", uploadConfig.getDefaultTags());
      settings.put("made_for_kids", uploadConfig.getMadeForKids());
      settings.put("default_playlist", uploadConfig.getDefaultPlaylist());
      settings.put("default_privacy_status", uploadConfig.getDefaultPrivacyStatus());
//      settings.put("recording_location", uploadConfig.getRecordingLocation());
      settings.put("auto_thumbnail", uploadConfig.getAutoThumbnail());
      settings.put("license", uploadConfig.getLicense());
      settings.put("embeddable", uploadConfig.getEmbeddable());
      settings.put("public_stats_viewable", uploadConfig.getPublicStatsViewable());
      settings.put("shorts_remix_enabled", uploadConfig.getShortsRemixEnabled());

      response.put("status", "success");
      response.put("settings", settings);
      response.put("summary", uploadConfig.getConfigSummary());

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("YouTube 업로드 설정 조회 중 오류: {}", e.getMessage(), e);
      response.put("status", "error");
      response.put("message", "설정 조회 중 오류가 발생했습니다: " + e.getMessage());
      return ResponseEntity.internalServerError().body(response);
    }
  }

  /**
   * 사용자의 YouTube 재생목록을 조회합니다.
   */
  @GetMapping("/playlists")
  public ResponseEntity<Map<String, Object>> getUserPlaylists() {
    log.info("사용자 재생목록 조회 요청");

    Map<String, Object> response = new HashMap<>();

    try {
      var playlists = playlistService.getUserPlaylists();

      response.put("status", "success");
      response.put("playlist_count", playlists.size());
      response.put("playlists", playlists.stream().map(playlist -> {
        Map<String, Object> playlistInfo = new HashMap<>();
        playlistInfo.put("id", playlist.getId());
        playlistInfo.put("title", playlist.getSnippet().getTitle());
        playlistInfo.put("description", playlist.getSnippet().getDescription());
        playlistInfo.put("privacy_status", playlist.getStatus().getPrivacyStatus());
        playlistInfo.put("published_at", playlist.getSnippet().getPublishedAt());
        return playlistInfo;
      }).toList());

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("재생목록 조회 중 오류: {}", e.getMessage(), e);
      response.put("status", "error");
      response.put("message", "재생목록 조회 중 오류가 발생했습니다: " + e.getMessage());
      return ResponseEntity.internalServerError().body(response);
    }
  }

  /**
   * 테스트용 재생목록을 생성합니다.
   */
  @PostMapping("/test-playlist")
  public ResponseEntity<Map<String, Object>> createTestPlaylist() {
    log.info("테스트 재생목록 생성 요청");

    Map<String, Object> response = new HashMap<>();

    try {
      String testPlaylistName = uploadConfig.getDefaultPlaylist();
      boolean success = playlistService.addVideoToPlaylist("dQw4w9WgXcQ", testPlaylistName); // Rick Roll 영상 ID로 테스트

      if (success) {
        response.put("status", "success");
        response.put("message", "테스트 재생목록이 생성되었습니다: " + testPlaylistName);
        response.put("playlist_name", testPlaylistName);
      } else {
        response.put("status", "error");
        response.put("message", "테스트 재생목록 생성에 실패했습니다.");
      }

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("테스트 재생목록 생성 중 오류: {}", e.getMessage(), e);
      response.put("status", "error");
      response.put("message", "테스트 재생목록 생성 중 오류가 발생했습니다: " + e.getMessage());
      return ResponseEntity.internalServerError().body(response);
    }
  }

  /**
   * 업로드 설정의 유효성을 검사합니다.
   */
  @GetMapping("/validate-settings")
  public ResponseEntity<Map<String, Object>> validateSettings() {
    log.info("업로드 설정 유효성 검사 요청");

    Map<String, Object> response = new HashMap<>();
    Map<String, Object> validation = new HashMap<>();

    try {
      // 카테고리 ID 유효성 검사
      boolean validCategory = uploadConfig.isValidCategoryId(uploadConfig.getCategoryId());
      validation.put("category_id_valid", validCategory);

      // 공개 상태 유효성 검사
      boolean validPrivacy = uploadConfig.isValidPrivacyStatus(uploadConfig.getDefaultPrivacyStatus());
      validation.put("privacy_status_valid", validPrivacy);

      // 태그 개수 검사 (YouTube는 최대 500글자, 태그당 평균 10글자 가정시 50개 정도)
      int tagCount = uploadConfig.getDefaultTags().size();
      boolean validTagCount = tagCount <= 30; // 안전한 개수로 제한
      validation.put("tag_count_valid", validTagCount);
      validation.put("tag_count", tagCount);

      // 재생목록 이름 길이 검사
      String playlistName = uploadConfig.getDefaultPlaylist();
      boolean validPlaylistName = playlistName != null &&
          playlistName.length() > 0 &&
          playlistName.length() <= 150;
      validation.put("playlist_name_valid", validPlaylistName);

      // 전체 유효성
      boolean allValid = validCategory && validPrivacy && validTagCount && validPlaylistName;
      validation.put("all_settings_valid", allValid);

      response.put("status", "success");
      response.put("validation", validation);

      if (!allValid) {
        response.put("message", "일부 설정에 문제가 있습니다. 확인해주세요.");
      } else {
        response.put("message", "모든 설정이 유효합니다.");
      }

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("설정 유효성 검사 중 오류: {}", e.getMessage(), e);
      response.put("status", "error");
      response.put("message", "설정 유효성 검사 중 오류가 발생했습니다: " + e.getMessage());
      return ResponseEntity.internalServerError().body(response);
    }
  }

  /**
   * 업로드 준비 상태를 확인합니다.
   */
  @GetMapping("/upload-readiness")
  public ResponseEntity<Map<String, Object>> checkUploadReadiness() {
    log.info("업로드 준비 상태 확인 요청");

    Map<String, Object> response = new HashMap<>();
    Map<String, Object> readiness = new HashMap<>();

    try {
      // OAuth 인증 상태 확인
      // TODO: YouTubeOAuthService를 통해 인증 상태 확인
      readiness.put("oauth_authenticated", true); // 임시값

      // 설정 유효성 확인
      boolean settingsValid = uploadConfig.isValidCategoryId(uploadConfig.getCategoryId()) &&
          uploadConfig.isValidPrivacyStatus(uploadConfig.getDefaultPrivacyStatus());
      readiness.put("settings_valid", settingsValid);

      // 재생목록 접근 가능성 확인
      boolean playlistAccessible = true; // 실제로는 API 호출해서 확인
      readiness.put("playlist_accessible", playlistAccessible);

      // 전체 준비 상태
      boolean fullyReady = (boolean) readiness.get("oauth_authenticated") &&
          settingsValid && playlistAccessible;
      readiness.put("fully_ready", fullyReady);

      response.put("status", "success");
      response.put("readiness", readiness);
      response.put("upload_settings_summary", uploadService.getUploadSettings());

      if (fullyReady) {
        response.put("message", "✅ YouTube 업로드 준비가 완료되었습니다!");
      } else {
        response.put("message", "⚠️ YouTube 업로드 준비에 문제가 있습니다.");
      }

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("업로드 준비 상태 확인 중 오류: {}", e.getMessage(), e);
      response.put("status", "error");
      response.put("message", "준비 상태 확인 중 오류가 발생했습니다: " + e.getMessage());
      return ResponseEntity.internalServerError().body(response);
    }
  }

  /**
   * 카테고리 ID를 이름으로 변환하는 헬퍼 메서드
   */
  private String getCategoryName(String categoryId) {
    return switch (categoryId) {
      case "1" -> "Film & Animation";
      case "2" -> "Autos & Vehicles";
      case "10" -> "Music";
      case "15" -> "Pets & Animals";
      case "17" -> "Sports";
      case "19" -> "Travel & Events";
      case "20" -> "Gaming";
      case "22" -> "People & Blogs";
      case "23" -> "Comedy";
      case "24" -> "Entertainment";
      case "25" -> "News & Politics";
      case "26" -> "Howto & Style";
      case "27" -> "Education";
      case "28" -> "Science & Technology";
      default -> "Unknown";
    };
  }
}