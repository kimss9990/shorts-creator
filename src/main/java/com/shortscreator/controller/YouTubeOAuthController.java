package com.shortscreator.controller;

import com.shortscreator.service.YouTubeOAuthService;
import com.shortscreator.service.YouTubeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/youtube/oauth")
@RequiredArgsConstructor
public class YouTubeOAuthController {

  private final YouTubeOAuthService youTubeOAuthService;
  private final YouTubeService youTubeService;

  /**
   * OAuth 2.0 인증 상태를 확인합니다.
   */
  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getAuthStatus() {
    log.info("OAuth 2.0 인증 상태 확인 요청");

    Map<String, Object> response = new HashMap<>();

    try {
      String authStatus = youTubeOAuthService.getAuthenticationStatus();
      boolean hasCredentials = youTubeOAuthService.hasStoredCredentials();

      response.put("status", "success");
      response.put("authenticated", hasCredentials);
      response.put("message", authStatus);
      response.put("timestamp", System.currentTimeMillis());

      if (hasCredentials) {
        response.put("action_required", "none");
        response.put("next_step", "Ready to upload videos");
      } else {
        response.put("action_required", "authentication");
        response.put("next_step", "Call /api/youtube/oauth/initiate to start OAuth flow");
      }

      log.info("OAuth 상태 확인 완료: 인증됨={}", hasCredentials);
      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("OAuth 상태 확인 중 오류: {}", e.getMessage(), e);
      response.put("status", "error");
      response.put("message", "인증 상태 확인 중 오류가 발생했습니다: " + e.getMessage());
      return ResponseEntity.internalServerError().body(response);
    }
  }

  /**
   * OAuth 2.0 인증 프로세스를 시작합니다. (GET 방식 - 브라우저 친화적)
   * 브라우저 주소창에서 직접 접속 가능합니다.
   */
  @GetMapping("/initiate")
  public ResponseEntity<Map<String, Object>> initiateAuthGet() {
    log.info("OAuth 2.0 인증 프로세스 시작 요청 (GET)");
    return performAuthentication();
  }

  /**
   * OAuth 2.0 인증 프로세스를 시작합니다. (POST 방식 - API 호출용)
   * curl이나 API 클라이언트에서 사용합니다.
   */
  @PostMapping("/initiate")
  public ResponseEntity<Map<String, Object>> initiateAuthPost() {
    log.info("OAuth 2.0 인증 프로세스 시작 요청 (POST)");
    return performAuthentication();
  }

  /**
   * 실제 인증 수행 로직 (GET/POST 공통)
   */
  private ResponseEntity<Map<String, Object>> performAuthentication() {
    Map<String, Object> response = new HashMap<>();

    try {
      // 이미 인증되어 있는지 확인
      if (youTubeOAuthService.hasStoredCredentials()) {
        response.put("status", "already_authenticated");
        response.put("message", "이미 인증된 상태입니다. 기존 토큰을 사용합니다.");
        response.put("action", "no_action_required");
        log.info("이미 인증된 상태입니다.");
        return ResponseEntity.ok(response);
      }

      // 새로운 인증 프로세스 시작 (동기적으로 실행)
      log.info("새로운 OAuth 2.0 인증 프로세스를 시작합니다...");

      // 수동 인증 수행
      youTubeOAuthService.performManualAuthentication();

      response.put("status", "completed");
      response.put("message", "🎉 OAuth 2.0 인증이 성공적으로 완료되었습니다!");
      response.put("action", "authentication_completed");
      response.put("next_step", "이제 YouTube 업로드 기능을 사용할 수 있습니다.");
      response.put("test_command", "curl http://localhost:8080/api/youtube/config/playlists");

      log.info("OAuth 인증 프로세스가 완료되었습니다.");
      return ResponseEntity.ok(response);

    } catch (IllegalStateException e) {
      log.warn("OAuth 인증 상태 오류: {}", e.getMessage());
      response.put("status", "authentication_required");
      response.put("message", e.getMessage());
      return ResponseEntity.badRequest().body(response);
    } catch (Exception e) {
      log.error("OAuth 인증 시작 중 오류: {}", e.getMessage(), e);
      response.put("status", "error");
      response.put("message", "OAuth 인증 중 오류가 발생했습니다: " + e.getMessage());
      response.put("help", "문제가 지속되면 기존 토큰을 삭제하고 다시 시도하세요: DELETE /api/youtube/oauth/credentials");
      return ResponseEntity.internalServerError().body(response);
    }
  }

  /**
   * 저장된 OAuth 2.0 인증 정보를 삭제합니다.
   */
  @DeleteMapping("/credentials")
  public ResponseEntity<Map<String, Object>> clearCredentials() {
    log.info("OAuth 2.0 인증 정보 삭제 요청");

    Map<String, Object> response = new HashMap<>();

    try {
      boolean cleared = youTubeOAuthService.clearStoredCredentials();

      if (cleared) {
        response.put("status", "success");
        response.put("message", "저장된 OAuth 2.0 인증 정보가 삭제되었습니다.");
        response.put("action", "credentials_cleared");
        response.put("next_step", "Call /api/youtube/oauth/initiate to re-authenticate");
        log.info("OAuth 인증 정보가 성공적으로 삭제되었습니다.");
      } else {
        response.put("status", "warning");
        response.put("message", "삭제할 인증 정보가 없거나 삭제에 실패했습니다.");
        response.put("action", "no_action_taken");
      }

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("OAuth 인증 정보 삭제 중 오류: {}", e.getMessage(), e);
      response.put("status", "error");
      response.put("message", "인증 정보 삭제 중 오류가 발생했습니다: " + e.getMessage());
      return ResponseEntity.internalServerError().body(response);
    }
  }

  /**
   * OAuth 2.0 설정 정보를 확인합니다. (민감한 정보는 마스킹)
   */
  @GetMapping("/config")
  public ResponseEntity<Map<String, Object>> getOAuthConfig() {
    log.info("OAuth 2.0 설정 정보 확인 요청");

    Map<String, Object> response = new HashMap<>();
    Map<String, Object> config = new HashMap<>();

    try {
      // 민감하지 않은 설정 정보만 반환
      config.put("application_name", "YouTube Shorts Creator");
      config.put("redirect_uri", "http://localhost:8080/oauth2/callback");
      config.put("scopes", new String[]{
          "https://www.googleapis.com/auth/youtube.upload",
          "https://www.googleapis.com/auth/youtube"
      });
      config.put("access_type", "offline");
      config.put("approval_prompt", "force");

      // Client ID는 일부만 표시 (보안상)
      String maskedClientId = youTubeOAuthService.getClass().getDeclaredField("clientId") != null ?
          "설정됨 (***...***)" : "설정되지 않음";
      config.put("client_id_status", maskedClientId);

      response.put("status", "success");
      response.put("config", config);
      response.put("message", "OAuth 2.0 설정 정보를 조회했습니다.");

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("OAuth 설정 정보 조회 중 오류: {}", e.getMessage(), e);
      response.put("status", "error");
      response.put("message", "설정 정보 조회 중 오류가 발생했습니다: " + e.getMessage());
      return ResponseEntity.internalServerError().body(response);
    }
  }

  /**
   * 테스트용 영상 업로드를 시도합니다. (실제로는 업로드하지 않고 권한만 확인)
   */
  @PostMapping("/test-upload")
  public ResponseEntity<Map<String, Object>> testUploadPermissions() {
    log.info("YouTube 업로드 권한 테스트 요청");

    Map<String, Object> response = new HashMap<>();

    try {
      // 인증 상태 확인
      if (!youTubeOAuthService.hasStoredCredentials()) {
        response.put("status", "authentication_required");
        response.put("message", "OAuth 2.0 인증이 필요합니다. 먼저 /api/youtube/oauth/initiate를 호출하세요.");
        return ResponseEntity.badRequest().body(response);
      }

      // YouTube 서비스 생성 시도 (권한 확인)
      youTubeOAuthService.getAuthenticatedYouTubeService();

      response.put("status", "success");
      response.put("message", "✅ YouTube 업로드 권한이 정상적으로 확인되었습니다!");
      response.put("permissions", new String[]{
          "youtube.upload", "youtube.readonly"
      });
      response.put("ready_for_upload", true);

      log.info("YouTube 업로드 권한 테스트 성공");
      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("YouTube 업로드 권한 테스트 실패: {}", e.getMessage(), e);
      response.put("status", "error");
      response.put("message", "YouTube 업로드 권한 확인 실패: " + e.getMessage());
      response.put("ready_for_upload", false);

      // 인증 만료 등의 경우 재인증 안내
      if (e.getMessage().contains("unauthorized") || e.getMessage().contains("invalid_token")) {
        response.put("action_required", "re_authentication");
        response.put("next_step", "Clear credentials and re-authenticate");
      }

      return ResponseEntity.internalServerError().body(response);
    }
  }

  /**
   * OAuth 2.0 토큰 갱신을 시도합니다.
   */
  @PostMapping("/refresh")
  public ResponseEntity<Map<String, Object>> refreshToken() {
    log.info("OAuth 2.0 토큰 갱신 요청");

    Map<String, Object> response = new HashMap<>();

    try {
      if (!youTubeOAuthService.hasStoredCredentials()) {
        response.put("status", "no_credentials");
        response.put("message", "갱신할 인증 정보가 없습니다. 새로 인증하세요.");
        return ResponseEntity.badRequest().body(response);
      }

      // YouTube 서비스 생성 (자동으로 토큰 갱신 시도)
      youTubeOAuthService.getAuthenticatedYouTubeService();

      response.put("status", "success");
      response.put("message", "토큰이 성공적으로 갱신되었습니다.");
      response.put("action", "token_refreshed");

      log.info("OAuth 토큰 갱신 성공");
      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("OAuth 토큰 갱신 실패: {}", e.getMessage(), e);
      response.put("status", "error");
      response.put("message", "토큰 갱신 실패: " + e.getMessage());
      response.put("action_required", "re_authentication");
      return ResponseEntity.internalServerError().body(response);
    }
  }
}