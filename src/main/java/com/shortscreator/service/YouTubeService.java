package com.shortscreator.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.shortscreator.config.ApiConfig;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeService {

  private final ApiConfig apiConfig;
  private final YouTubeUploadService youTubeUploadService;
  private final YouTubeOAuthService youTubeOAuthService;
  private static final String APPLICATION_NAME = "Shorts Creator";

  /**
   * YouTube 채널 정보를 조회합니다.
   *
   * @param channelId 채널 ID
   * @return 채널 정보
   */
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

  /**
   * YouTube Shorts 영상을 업로드합니다.
   *
   * @param videoFilePath 업로드할 영상 파일 경로
   * @param title         영상 제목
   * @param description   영상 설명
   * @return 업로드 성공 여부
   */
  public boolean uploadShorts(String videoFilePath, String title, String description) {
    try {
      log.info("YouTube Shorts 업로드 요청: {}", videoFilePath);

      // 파일 유효성 검사
      if (!youTubeUploadService.isValidVideoFile(videoFilePath)) {
        log.error("지원되지 않는 영상 파일 형식입니다: {}", videoFilePath);
        return false;
      }

      // 파일 크기 검사
      if (!youTubeUploadService.isValidFileSize(videoFilePath)) {
        log.error("파일 크기가 YouTube 업로드 제한을 초과합니다: {}", videoFilePath);
        return false;
      }

      // 메타데이터 검사
      if (!youTubeUploadService.isValidMetadata(title, description)) {
        log.error("영상 메타데이터가 유효하지 않습니다. 제목: {}, 설명 길이: {}",
            title, description != null ? description.length() : 0);
        return false;
      }

      // 실제 업로드 수행 (기본적으로 비공개로 업로드)
      boolean uploadResult = youTubeUploadService.uploadPrivateShorts(videoFilePath, title, description);

      if (uploadResult) {
        log.info("✅ YouTube Shorts 업로드 성공: {}", videoFilePath);
      } else {
        log.error("❌ YouTube Shorts 업로드 실패: {}", videoFilePath);
      }

      return uploadResult;

    } catch (Exception e) {
      log.error("YouTube Shorts 업로드 중 예상치 못한 오류: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * YouTube Shorts 영상을 공개 상태로 업로드합니다.
   *
   * @param videoFilePath 업로드할 영상 파일 경로
   * @param title         영상 제목
   * @param description   영상 설명
   * @return 업로드 성공 여부
   */
  public boolean uploadPublicShorts(String videoFilePath, String title, String description) {
    try {
      log.info("YouTube Shorts 공개 업로드 요청: {}", videoFilePath);

      if (!validateUploadRequest(videoFilePath, title, description)) {
        return false;
      }

      boolean uploadResult = youTubeUploadService.uploadPublicShorts(videoFilePath, title, description);

      if (uploadResult) {
        log.info("✅ YouTube Shorts 공개 업로드 성공: {}", videoFilePath);
      } else {
        log.error("❌ YouTube Shorts 공개 업로드 실패: {}", videoFilePath);
      }

      return uploadResult;

    } catch (Exception e) {
      log.error("YouTube Shorts 공개 업로드 중 오류: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * YouTube Shorts 영상을 일부 공개 상태로 업로드합니다.
   *
   * @param videoFilePath 업로드할 영상 파일 경로
   * @param title         영상 제목
   * @param description   영상 설명
   * @return 업로드 성공 여부
   */
  public boolean uploadUnlistedShorts(String videoFilePath, String title, String description) {
    try {
      log.info("YouTube Shorts 일부 공개 업로드 요청: {}", videoFilePath);

      if (!validateUploadRequest(videoFilePath, title, description)) {
        return false;
      }

      boolean uploadResult = youTubeUploadService.uploadUnlistedShorts(videoFilePath, title, description);

      if (uploadResult) {
        log.info("✅ YouTube Shorts 일부 공개 업로드 성공: {}", videoFilePath);
      } else {
        log.error("❌ YouTube Shorts 일부 공개 업로드 실패: {}", videoFilePath);
      }

      return uploadResult;

    } catch (Exception e) {
      log.error("YouTube Shorts 일부 공개 업로드 중 오류: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * 업로드 요청의 유효성을 검사합니다.
   *
   * @param videoFilePath 영상 파일 경로
   * @param title         영상 제목
   * @param description   영상 설명
   * @return 유효성 검사 결과
   */
  private boolean validateUploadRequest(String videoFilePath, String title, String description) {
    if (!youTubeUploadService.isValidVideoFile(videoFilePath)) {
      log.error("지원되지 않는 영상 파일 형식입니다: {}", videoFilePath);
      return false;
    }

    if (!youTubeUploadService.isValidFileSize(videoFilePath)) {
      log.error("파일 크기가 YouTube 업로드 제한을 초과합니다: {}", videoFilePath);
      return false;
    }

    if (!youTubeUploadService.isValidMetadata(title, description)) {
      log.error("영상 메타데이터가 유효하지 않습니다. 제목: {}, 설명 길이: {}",
          title, description != null ? description.length() : 0);
      return false;
    }

    return true;
  }

  /**
   * YouTube OAuth 2.0 인증 상태를 확인합니다.
   *
   * @return 인증 상태 정보
   */
  public String getAuthenticationStatus() {
    try {
      return youTubeOAuthService.getAuthenticationStatus();
    } catch (Exception e) {
      log.error("인증 상태 확인 중 오류: {}", e.getMessage());
      return "⚠️ 인증 상태 확인 불가 - " + e.getMessage();
    }
  }

  /**
   * 저장된 OAuth 2.0 인증 정보를 삭제합니다.
   *
   * @return 삭제 성공 여부
   */
  public boolean clearStoredCredentials() {
    try {
      boolean result = youTubeOAuthService.clearStoredCredentials();
      if (result) {
        log.info("YouTube OAuth 2.0 인증 정보가 삭제되었습니다.");
      } else {
        log.warn("YouTube OAuth 2.0 인증 정보 삭제에 실패했습니다.");
      }
      return result;
    } catch (Exception e) {
      log.error("YouTube OAuth 2.0 인증 정보 삭제 중 오류: {}", e.getMessage(), e);
      return false;
    }
  }
}