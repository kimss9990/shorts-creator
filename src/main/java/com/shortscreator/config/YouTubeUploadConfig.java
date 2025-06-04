package com.shortscreator.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Getter
@Component
@ConfigurationProperties(prefix = "youtube.upload")
public class YouTubeUploadConfig {

  // 기본 메타데이터
  private String categoryId = "22"; // People & Blogs
  private String defaultLanguage = "en"; // English

  // 기본 태그들
  private List<String> defaultTags = List.of(
      "shorts", "marriage", "relationships", "advice", "couples", "intimacy", "communication"
  );

  // 🔧 중요: 시청자 설정 - 명시적으로 false
  private Boolean madeForKids = false;

  // 재생목록 설정
  private String defaultPlaylist = "Sexless Marriage Advice";

  // 공개 설정
  private String defaultPrivacyStatus = "private"; // private, public, unlisted

  // 위치 정보
  private String recordingLocation = "United States";

  // 썸네일 설정
  private Boolean autoThumbnail = true;

  // 라이선스 및 권한
  private String license = "youtube"; // youtube 또는 creativeCommon
  private Boolean embeddable = true;
  private Boolean publicStatsViewable = true;

  // Shorts 특화 설정
  private Boolean shortsRemixEnabled = true;

  // 🔧 새로 추가: 콘텐츠 등급 관련 설정
  private ContentRating contentRating = new ContentRating();

  // 🔧 새로 추가: 지역 설정
  private Region region = new Region();

  // Setter methods for Spring Boot configuration binding
  public void setCategoryId(String categoryId) {
    this.categoryId = categoryId;
  }

  public void setDefaultLanguage(String defaultLanguage) {
    this.defaultLanguage = defaultLanguage;
  }

  public void setDefaultTags(List<String> defaultTags) {
    this.defaultTags = defaultTags;
  }

  public void setMadeForKids(Boolean madeForKids) {
    this.madeForKids = madeForKids;
  }

  public void setDefaultPlaylist(String defaultPlaylist) {
    this.defaultPlaylist = defaultPlaylist;
  }

  public void setDefaultPrivacyStatus(String defaultPrivacyStatus) {
    this.defaultPrivacyStatus = defaultPrivacyStatus;
  }

  public void setRecordingLocation(String recordingLocation) {
    this.recordingLocation = recordingLocation;
  }

  public void setAutoThumbnail(Boolean autoThumbnail) {
    this.autoThumbnail = autoThumbnail;
  }

  public void setLicense(String license) {
    this.license = license;
  }

  public void setEmbeddable(Boolean embeddable) {
    this.embeddable = embeddable;
  }

  public void setPublicStatsViewable(Boolean publicStatsViewable) {
    this.publicStatsViewable = publicStatsViewable;
  }

  public void setShortsRemixEnabled(Boolean shortsRemixEnabled) {
    this.shortsRemixEnabled = shortsRemixEnabled;
  }

  // 🔧 새로 추가된 setter들
  public void setContentRating(ContentRating contentRating) {
    this.contentRating = contentRating;
  }

  public void setRegion(Region region) {
    this.region = region;
  }

  @PostConstruct
  public void logConfiguration() {
    log.info("YouTube 업로드 설정 로드됨:");
    log.info("- 카테고리: {} ({})", categoryId, getCategoryName(categoryId));
    log.info("- 언어: {}", defaultLanguage);
    log.info("- 기본 태그: {}", defaultTags);
    log.info("- 🔧 아동용 콘텐츠: {} (중요 설정)", madeForKids);
    log.info("- 기본 재생목록: {}", defaultPlaylist);
    log.info("- 기본 공개 설정: {}", defaultPrivacyStatus);
    log.info("- 🔧 촬영 위치: {} (중요 설정)", recordingLocation);
    log.info("- 자동 썸네일: {}", autoThumbnail);
    log.info("- 라이선스: {}", license);
    log.info("- 임베드 가능: {}", embeddable);
    log.info("- 통계 공개: {}", publicStatsViewable);
    log.info("- Shorts 리믹스: {}", shortsRemixEnabled);
    log.info("- 🔧 자체 신고 아동용 콘텐츠: {}", contentRating.selfDeclaredMadeForKids);
    log.info("- 🔧 허용된 국가 수: {}", region.allowedCountries.size());
    log.info("- 🔧 차단된 국가 수: {}", region.blockedCountries.size());
  }

  /**
   * 카테고리 ID를 이름으로 변환
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

  /**
   * 공개 상태 유효성 검사
   */
  public boolean isValidPrivacyStatus(String privacyStatus) {
    return List.of("private", "public", "unlisted").contains(privacyStatus.toLowerCase());
  }

  /**
   * 카테고리 ID 유효성 검사
   */
  public boolean isValidCategoryId(String categoryId) {
    List<String> validCategories = List.of(
        "1", "2", "10", "15", "17", "19", "20", "22", "23", "24", "25", "26", "27", "28"
    );
    return validCategories.contains(categoryId);
  }

  /**
   * 설정 요약 정보 반환
   */
  public String getConfigSummary() {
    return String.format(
        "카테고리: %s, 언어: %s, 태그: %d개, 재생목록: %s, 공개설정: %s, 아동용: %s, 위치: %s",
        getCategoryName(categoryId), defaultLanguage, defaultTags.size(),
        defaultPlaylist, defaultPrivacyStatus, madeForKids, recordingLocation
    );
  }

  // 🔧 새로 추가: 내부 클래스들
  @Getter
  public static class ContentRating {
    private Boolean selfDeclaredMadeForKids = false;
    private Boolean hasCustomThumbnail = true;

    public void setSelfDeclaredMadeForKids(Boolean selfDeclaredMadeForKids) {
      this.selfDeclaredMadeForKids = selfDeclaredMadeForKids;
    }

    public void setHasCustomThumbnail(Boolean hasCustomThumbnail) {
      this.hasCustomThumbnail = hasCustomThumbnail;
    }
  }

  @Getter
  public static class Region {
    private List<String> allowedCountries = List.of(); // 빈 리스트 = 모든 국가 허용
    private List<String> blockedCountries = List.of(); // 차단할 국가 없음

    public void setAllowedCountries(List<String> allowedCountries) {
      this.allowedCountries = allowedCountries;
    }

    public void setBlockedCountries(List<String> blockedCountries) {
      this.blockedCountries = blockedCountries;
    }
  }

  /**
   * 아동용 콘텐츠 설정이 올바른지 확인
   */
  public boolean isProperlyConfiguredForAdults() {
    boolean adultContent = !madeForKids && !contentRating.selfDeclaredMadeForKids;
    if (adultContent) {
      log.info("✅ 성인 콘텐츠로 올바르게 설정됨");
    } else {
      log.warn("⚠️ 아동용 콘텐츠 설정 확인 필요: madeForKids={}, selfDeclared={}",
          madeForKids, contentRating.selfDeclaredMadeForKids);
    }
    return adultContent;
  }

  /**
   * 지역 설정이 올바른지 확인
   */
  public boolean isProperlyConfiguredForGlobal() {
    boolean globalAccess = region.blockedCountries.isEmpty();
    if (globalAccess) {
      log.info("✅ 전 세계 접근 가능으로 설정됨");
    } else {
      log.warn("⚠️ 일부 국가에서 차단됨: {}", region.blockedCountries);
    }
    return globalAccess;
  }
}