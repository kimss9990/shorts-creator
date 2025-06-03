package com.shortscreator.service;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubePlaylistService {

  private final YouTubeOAuthService youTubeOAuthService;

  /**
   * 재생목록을 생성하거나 기존 재생목록을 찾아서 영상을 추가합니다.
   *
   * @param videoId 추가할 영상 ID
   * @param playlistName 재생목록 이름
   * @return 성공 여부
   */
  public boolean addVideoToPlaylist(String videoId, String playlistName) {
    try {
      log.info("재생목록에 영상 추가 시도: {} -> {}", videoId, playlistName);

      YouTube youtube = youTubeOAuthService.getAuthenticatedYouTubeService();

      // 기존 재생목록 검색
      String playlistId = findPlaylistByName(youtube, playlistName);

      // 재생목록이 없으면 새로 생성
      if (playlistId == null) {
        playlistId = createPlaylist(youtube, playlistName);
        if (playlistId == null) {
          log.error("재생목록 생성 실패: {}", playlistName);
          return false;
        }
        log.info("새 재생목록 생성됨: {} (ID: {})", playlistName, playlistId);
      } else {
        log.info("기존 재생목록 발견: {} (ID: {})", playlistName, playlistId);
      }

      // 영상을 재생목록에 추가
      return addVideoToExistingPlaylist(youtube, videoId, playlistId);

    } catch (Exception e) {
      log.error("재생목록에 영상 추가 중 오류: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * 재생목록 이름으로 재생목록 ID를 찾습니다.
   *
   * @param youtube YouTube 서비스 인스턴스
   * @param playlistName 찾을 재생목록 이름
   * @return 재생목록 ID (없으면 null)
   */
  private String findPlaylistByName(YouTube youtube, String playlistName) {
    try {
      log.debug("재생목록 검색: {}", playlistName);

      YouTube.Playlists.List request = youtube.playlists()
          .list(Arrays.asList("snippet"))
          .setMine(true)
          .setMaxResults(50L);

      PlaylistListResponse response = request.execute();
      List<Playlist> playlists = response.getItems();

      for (Playlist playlist : playlists) {
        if (playlistName.equals(playlist.getSnippet().getTitle())) {
          log.debug("재생목록 발견: {} (ID: {})", playlistName, playlist.getId());
          return playlist.getId();
        }
      }

      log.debug("재생목록을 찾을 수 없음: {}", playlistName);
      return null;

    } catch (Exception e) {
      log.error("재생목록 검색 중 오류: {}", e.getMessage(), e);
      return null;
    }
  }

  /**
   * 새로운 재생목록을 생성합니다.
   *
   * @param youtube YouTube 서비스 인스턴스
   * @param playlistName 생성할 재생목록 이름
   * @return 생성된 재생목록 ID (실패 시 null)
   */
  private String createPlaylist(YouTube youtube, String playlistName) {
    try {
      log.info("새 재생목록 생성: {}", playlistName);

      // 재생목록 스니펫 설정
      PlaylistSnippet playlistSnippet = new PlaylistSnippet();
      playlistSnippet.setTitle(playlistName);
      playlistSnippet.setDescription("AI로 생성된 YouTube Shorts 모음");
      playlistSnippet.setDefaultLanguage("en");

      // 재생목록 상태 설정 (공개 여부)
      PlaylistStatus playlistStatus = new PlaylistStatus();
      playlistStatus.setPrivacyStatus("private"); // private, public, unlisted

      // 재생목록 객체 생성
      Playlist playlist = new Playlist();
      playlist.setSnippet(playlistSnippet);
      playlist.setStatus(playlistStatus);

      // 재생목록 생성 요청
      YouTube.Playlists.Insert playlistInsertRequest = youtube.playlists()
          .insert(Arrays.asList("snippet", "status"), playlist);

      Playlist createdPlaylist = playlistInsertRequest.execute();

      if (createdPlaylist != null && createdPlaylist.getId() != null) {
        log.info("✅ 재생목록 생성 성공: {} (ID: {})", playlistName, createdPlaylist.getId());
        return createdPlaylist.getId();
      } else {
        log.error("재생목록 생성 실패: 응답에서 ID를 찾을 수 없음");
        return null;
      }

    } catch (Exception e) {
      log.error("재생목록 생성 중 오류: {}", e.getMessage(), e);
      return null;
    }
  }

  /**
   * 기존 재생목록에 영상을 추가합니다.
   *
   * @param youtube YouTube 서비스 인스턴스
   * @param videoId 추가할 영상 ID
   * @param playlistId 대상 재생목록 ID
   * @return 성공 여부
   */
  private boolean addVideoToExistingPlaylist(YouTube youtube, String videoId, String playlistId) {
    try {
      log.info("재생목록에 영상 추가: {} -> {}", videoId, playlistId);

      // 재생목록 아이템 리소스 ID 설정
      ResourceId resourceId = new ResourceId();
      resourceId.setKind("youtube#video");
      resourceId.setVideoId(videoId);

      // 재생목록 아이템 스니펫 설정
      PlaylistItemSnippet playlistItemSnippet = new PlaylistItemSnippet();
      playlistItemSnippet.setPlaylistId(playlistId);
      playlistItemSnippet.setResourceId(resourceId);

      // 재생목록 아이템 객체 생성
      PlaylistItem playlistItem = new PlaylistItem();
      playlistItem.setSnippet(playlistItemSnippet);

      // 재생목록에 아이템 추가 요청
      YouTube.PlaylistItems.Insert playlistItemsInsertRequest = youtube.playlistItems()
          .insert(Arrays.asList("snippet"), playlistItem);

      PlaylistItem returnedPlaylistItem = playlistItemsInsertRequest.execute();

      if (returnedPlaylistItem != null && returnedPlaylistItem.getId() != null) {
        log.info("✅ 재생목록에 영상 추가 성공: {}", returnedPlaylistItem.getId());
        return true;
      } else {
        log.error("재생목록에 영상 추가 실패: 응답에서 ID를 찾을 수 없음");
        return false;
      }

    } catch (Exception e) {
      log.error("재생목록에 영상 추가 중 오류: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * 사용자의 모든 재생목록을 조회합니다.
   *
   * @return 재생목록 목록
   */
  public List<Playlist> getUserPlaylists() {
    try {
      log.info("사용자 재생목록 조회");

      YouTube youtube = youTubeOAuthService.getAuthenticatedYouTubeService();

      YouTube.Playlists.List request = youtube.playlists()
          .list(Arrays.asList("snippet", "status"))
          .setMine(true)
          .setMaxResults(50L);

      PlaylistListResponse response = request.execute();
      List<Playlist> playlists = response.getItems();

      log.info("사용자 재생목록 {}개 조회됨", playlists.size());
      for (Playlist playlist : playlists) {
        log.debug("- {}: {} ({})",
            playlist.getSnippet().getTitle(),
            playlist.getId(),
            playlist.getStatus().getPrivacyStatus());
      }

      return playlists;

    } catch (Exception e) {
      log.error("재생목록 조회 중 오류: {}", e.getMessage(), e);
      return List.of();
    }
  }

  /**
   * 재생목록 삭제
   *
   * @param playlistName 삭제할 재생목록 이름
   * @return 삭제 성공 여부
   */
  public boolean deletePlaylist(String playlistName) {
    try {
      log.info("재생목록 삭제 시도: {}", playlistName);

      YouTube youtube = youTubeOAuthService.getAuthenticatedYouTubeService();
      String playlistId = findPlaylistByName(youtube, playlistName);

      if (playlistId == null) {
        log.warn("삭제할 재생목록을 찾을 수 없음: {}", playlistName);
        return false;
      }

      YouTube.Playlists.Delete deleteRequest = youtube.playlists().delete(playlistId);
      deleteRequest.execute();

      log.info("✅ 재생목록 삭제 완료: {} (ID: {})", playlistName, playlistId);
      return true;

    } catch (Exception e) {
      log.error("재생목록 삭제 중 오류: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * 재생목록에서 영상 제거
   *
   * @param videoId 제거할 영상 ID
   * @param playlistName 대상 재생목록 이름
   * @return 제거 성공 여부
   */
  public boolean removeVideoFromPlaylist(String videoId, String playlistName) {
    try {
      log.info("재생목록에서 영상 제거: {} <- {}", playlistName, videoId);

      YouTube youtube = youTubeOAuthService.getAuthenticatedYouTubeService();
      String playlistId = findPlaylistByName(youtube, playlistName);

      if (playlistId == null) {
        log.warn("재생목록을 찾을 수 없음: {}", playlistName);
        return false;
      }

      // 재생목록 아이템 검색
      YouTube.PlaylistItems.List request = youtube.playlistItems()
          .list(Arrays.asList("snippet"))
          .setPlaylistId(playlistId)
          .setMaxResults(50L);

      PlaylistItemListResponse response = request.execute();
      List<PlaylistItem> playlistItems = response.getItems();

      // 해당 영상의 재생목록 아이템 ID 찾기
      String playlistItemId = null;
      for (PlaylistItem item : playlistItems) {
        if (videoId.equals(item.getSnippet().getResourceId().getVideoId())) {
          playlistItemId = item.getId();
          break;
        }
      }

      if (playlistItemId == null) {
        log.warn("재생목록에서 해당 영상을 찾을 수 없음: {}", videoId);
        return false;
      }

      // 재생목록 아이템 삭제
      YouTube.PlaylistItems.Delete deleteRequest = youtube.playlistItems().delete(playlistItemId);
      deleteRequest.execute();

      log.info("✅ 재생목록에서 영상 제거 완료: {}", playlistItemId);
      return true;

    } catch (Exception e) {
      log.error("재생목록에서 영상 제거 중 오류: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * 재생목록 정보 업데이트 (제목, 설명, 공개 설정 등)
   *
   * @param playlistName 기존 재생목록 이름
   * @param newTitle 새 제목
   * @param newDescription 새 설명
   * @param newPrivacyStatus 새 공개 설정
   * @return 업데이트 성공 여부
   */
  public boolean updatePlaylist(String playlistName, String newTitle, String newDescription, String newPrivacyStatus) {
    try {
      log.info("재생목록 정보 업데이트: {}", playlistName);

      YouTube youtube = youTubeOAuthService.getAuthenticatedYouTubeService();
      String playlistId = findPlaylistByName(youtube, playlistName);

      if (playlistId == null) {
        log.warn("업데이트할 재생목록을 찾을 수 없음: {}", playlistName);
        return false;
      }

      // 스니펫 업데이트
      PlaylistSnippet snippet = new PlaylistSnippet();
      snippet.setTitle(newTitle);
      snippet.setDescription(newDescription);

      // 상태 업데이트
      PlaylistStatus status = new PlaylistStatus();
      status.setPrivacyStatus(newPrivacyStatus);

      // 재생목록 객체 생성
      Playlist playlist = new Playlist();
      playlist.setId(playlistId);
      playlist.setSnippet(snippet);
      playlist.setStatus(status);

      // 업데이트 요청
      YouTube.Playlists.Update updateRequest = youtube.playlists()
          .update(Arrays.asList("snippet", "status"), playlist);

      Playlist updatedPlaylist = updateRequest.execute();

      if (updatedPlaylist != null) {
        log.info("✅ 재생목록 업데이트 완료: {} -> {}", playlistName, newTitle);
        return true;
      } else {
        log.error("재생목록 업데이트 실패");
        return false;
      }

    } catch (Exception e) {
      log.error("재생목록 업데이트 중 오류: {}", e.getMessage(), e);
      return false;
    }
  }
}