package com.shortscreator.service;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import com.shortscreator.config.YouTubeUploadConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeUploadService {

  private final YouTubeOAuthService youTubeOAuthService;
  private final YouTubeUploadConfig uploadConfig;
  private final YouTubePlaylistService playlistService;

  /**
   * YouTube Shorts 영상을 기본 설정으로 업로드합니다.
   *
   * @param videoFilePath 업로드할 영상 파일 경로
   * @param title 영상 제목
   * @param description 영상 설명
   * @return 업로드 성공 여부
   */
  public boolean uploadShorts(String videoFilePath, String title, String description) {
    return uploadVideo(videoFilePath, title, description,
        uploadConfig.getDefaultPrivacyStatus(),
        uploadConfig.getDefaultTags());
  }

  /**
   * YouTube에 영상을 업로드합니다 (모든 설정 적용).
   *
   * @param videoFilePath 업로드할 영상 파일 경로
   * @param title 영상 제목
   * @param description 영상 설명
   * @param privacyStatus 공개 상태 (public, private, unlisted)
   * @param customTags 커스텀 태그 목록
   * @return 업로드 성공 여부
   */
  public boolean uploadVideo(String videoFilePath, String title, String description,
      String privacyStatus, List<String> customTags) {
    try {
      log.info("YouTube 영상 업로드 시작: {}", videoFilePath);
      log.info("설정 적용: {}", uploadConfig.getConfigSummary());

      // 파일 존재 확인
      File videoFile = new File(videoFilePath);
      if (!videoFile.exists()) {
        log.error("영상 파일을 찾을 수 없습니다: {}", videoFilePath);
        return false;
      }

      // 파일 크기 확인
      long fileSizeInMB = videoFile.length() / (1024 * 1024);
      log.info("업로드할 파일 크기: {} MB", fileSizeInMB);

      // YouTube API 클라이언트 생성
      YouTube youtube = youTubeOAuthService.getAuthenticatedYouTubeService();

      // 영상 메타데이터 설정
      Video video = new Video();

      // 스니펫 (제목, 설명, 태그 등) 설정
      VideoSnippet snippet = new VideoSnippet();
      snippet.setTitle(title);
      snippet.setDescription(description);

      // 태그 설정: 기본 태그 + 커스텀 태그 조합
      List<String> finalTags = new ArrayList<>(uploadConfig.getDefaultTags());
      if (customTags != null && !customTags.isEmpty()) {
        finalTags.addAll(customTags);
      }
      snippet.setTags(finalTags);

      // 카테고리 및 언어 설정
      snippet.setCategoryId(uploadConfig.getCategoryId());
      snippet.setDefaultLanguage(uploadConfig.getDefaultLanguage());

      // 위치 정보 설정 (선택사항)
      if (uploadConfig.getRecordingLocation() != null && !uploadConfig.getRecordingLocation().isEmpty()) {
        // YouTube API에서 위치 정보는 별도 처리가 필요할 수 있음
        log.debug("촬영 위치 설정: {}", uploadConfig.getRecordingLocation());
      }

      video.setSnippet(snippet);

      // 상태 (공개 설정) 설정
      VideoStatus status = new VideoStatus();
      status.setPrivacyStatus(privacyStatus);
      status.setMadeForKids(uploadConfig.getMadeForKids());
      status.setEmbeddable(uploadConfig.getEmbeddable());
      status.setPublicStatsViewable(uploadConfig.getPublicStatsViewable());
      status.setLicense(uploadConfig.getLicense());
      video.setStatus(status);

      // 파일 입력 스트림 생성
      FileInputStream inputStream = new FileInputStream(videoFile);
      InputStreamContent mediaContent = new InputStreamContent("video/*", inputStream);

      // 파일 크기 설정 (업로드 진행률 표시용)
      mediaContent.setLength(videoFile.length());

      // YouTube 업로드 요청 생성
      YouTube.Videos.Insert videoInsert = youtube.videos()
          .insert(Arrays.asList("snippet", "status"), video, mediaContent);

      // 업로드 진행률 리스너 설정
      MediaHttpUploader uploader = videoInsert.getMediaHttpUploader();
      uploader.setDirectUploadEnabled(false);
      uploader.setProgressListener(new CustomProgressListener());

      // 업로드 실행
      log.info("YouTube 업로드 시작...");
      Video uploadedVideo = videoInsert.execute();

      // 업로드 결과 확인
      if (uploadedVideo != null && uploadedVideo.getId() != null) {
        log.info("✅ YouTube 업로드 성공!");
        log.info("- 영상 ID: {}", uploadedVideo.getId());
        log.info("- 영상 URL: https://www.youtube.com/watch?v={}", uploadedVideo.getId());
        log.info("- 제목: {}", uploadedVideo.getSnippet().getTitle());
        log.info("- 공개 상태: {}", uploadedVideo.getStatus().getPrivacyStatus());
        log.info("- 적용된 태그: {}", finalTags);

        // 재생목록에 추가
        if (uploadConfig.getDefaultPlaylist() != null && !uploadConfig.getDefaultPlaylist().isEmpty()) {
          boolean playlistAdded = playlistService.addVideoToPlaylist(
              uploadedVideo.getId(), uploadConfig.getDefaultPlaylist());
          if (playlistAdded) {
            log.info("- 재생목록 추가 완료: {}", uploadConfig.getDefaultPlaylist());
          } else {
            log.warn("- 재생목록 추가 실패: {}", uploadConfig.getDefaultPlaylist());
          }
        }

        // 입력 스트림 닫기
        inputStream.close();

        return true;
      } else {
        log.error("업로드된 영상 정보를 받을 수 없습니다.");
        inputStream.close();
        return false;
      }

    } catch (GeneralSecurityException e) {
      log.error("OAuth 2.0 인증 보안 오류: {}", e.getMessage(), e);
      return false;
    } catch (IOException e) {
      log.error("YouTube 업로드 중 I/O 오류: {}", e.getMessage(), e);
      return false;
    } catch (Exception e) {
      log.error("YouTube 업로드 중 예상치 못한 오류: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * 업로드 진행률을 모니터링하는 리스너
   */
  private static class CustomProgressListener implements MediaHttpUploaderProgressListener {
    @Override
    public void progressChanged(MediaHttpUploader uploader) throws IOException {
      switch (uploader.getUploadState()) {
        case INITIATION_STARTED:
          log.info("📤 업로드 초기화 중...");
          break;
        case INITIATION_COMPLETE:
          log.info("📤 업로드 초기화 완료. 전송 시작...");
          break;
        case MEDIA_IN_PROGRESS:
          double progress = uploader.getProgress() * 100;
          log.info("📤 업로드 진행중: {:.1f}%", progress);
          break;
        case MEDIA_COMPLETE:
          log.info("📤 업로드 완료! 처리 중...");
          break;
        case NOT_STARTED:
        default:
          log.debug("업로드 상태: {}", uploader.getUploadState());
          break;
      }
    }
  }

  /**
   * YouTube Shorts 영상 업로드 (공개)
   */
  public boolean uploadPublicShorts(String videoFilePath, String title, String description) {
    return uploadVideo(videoFilePath, title, description, "public",
        uploadConfig.getDefaultTags());
  }

  /**
   * YouTube Shorts 영상 업로드 (비공개)
   */
  public boolean uploadPrivateShorts(String videoFilePath, String title, String description) {
    return uploadVideo(videoFilePath, title, description, "private",
        uploadConfig.getDefaultTags());
  }

  /**
   * YouTube Shorts 영상 업로드 (일부 공개)
   */
  public boolean uploadUnlistedShorts(String videoFilePath, String title, String description) {
    return uploadVideo(videoFilePath, title, description, "unlisted",
        uploadConfig.getDefaultTags());
  }

  /**
   * 커스텀 태그와 함께 업로드
   */
  public boolean uploadShortsWithCustomTags(String videoFilePath, String title, String description,
      List<String> additionalTags) {
    List<String> combinedTags = new ArrayList<>(uploadConfig.getDefaultTags());
    if (additionalTags != null) {
      combinedTags.addAll(additionalTags);
    }
    return uploadVideo(videoFilePath, title, description,
        uploadConfig.getDefaultPrivacyStatus(), combinedTags);
  }

  /**
   * 업로드 가능한 파일 형식인지 확인합니다.
   */
  public boolean isValidVideoFile(String filePath) {
    if (filePath == null || filePath.trim().isEmpty()) {
      return false;
    }

    String lowerCasePath = filePath.toLowerCase();
    String[] supportedFormats = {".mp4", ".mov", ".avi", ".wmv", ".flv", ".webm", ".mkv"};

    for (String format : supportedFormats) {
      if (lowerCasePath.endsWith(format)) {
        return true;
      }
    }

    log.warn("지원되지 않는 영상 형식: {}. 지원 형식: {}", filePath, Arrays.toString(supportedFormats));
    return false;
  }

  /**
   * 파일 크기가 YouTube 업로드 제한에 맞는지 확인합니다.
   */
  public boolean isValidFileSize(String filePath) {
    try {
      File file = new File(filePath);
      if (!file.exists()) {
        log.error("파일이 존재하지 않습니다: {}", filePath);
        return false;
      }

      long fileSizeInBytes = file.length();
      long fileSizeInMB = fileSizeInBytes / (1024 * 1024);

      // YouTube 업로드 제한: 128GB (일반 계정)
      long maxSizeInMB = 128 * 1024; // 128GB in MB

      if (fileSizeInMB > maxSizeInMB) {
        log.error("파일 크기가 너무 큽니다: {} MB (최대: {} MB)", fileSizeInMB, maxSizeInMB);
        return false;
      }

      log.info("파일 크기 확인 통과: {} MB", fileSizeInMB);
      return true;

    } catch (Exception e) {
      log.error("파일 크기 확인 중 오류: {}", e.getMessage());
      return false;
    }
  }

  /**
   * 영상 제목과 설명의 유효성을 검사합니다.
   */
  public boolean isValidMetadata(String title, String description) {
    // 제목 검증
    if (title == null || title.trim().isEmpty()) {
      log.error("영상 제목이 비어있습니다.");
      return false;
    }

    if (title.length() > 100) {
      log.error("영상 제목이 너무 깁니다: {} 글자 (최대: 100글자)", title.length());
      return false;
    }

    // 설명 검증
    if (description != null && description.length() > 5000) {
      log.error("영상 설명이 너무 깁니다: {} 글자 (최대: 5000글자)", description.length());
      return false;
    }

    log.debug("메타데이터 유효성 검사 통과");
    return true;
  }

  /**
   * 현재 업로드 설정 정보를 반환합니다.
   */
  public String getUploadSettings() {
    return uploadConfig.getConfigSummary();
  }
}