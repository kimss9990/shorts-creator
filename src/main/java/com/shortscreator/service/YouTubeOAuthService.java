package com.shortscreator.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.DataStoreCredentialRefreshListener;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeScopes;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class YouTubeOAuthService {

  @Value("${youtube.oauth2.client_id}")
  private String clientId;

  @Value("${youtube.oauth2.client_secret}")
  private String clientSecret;

  @Value("${youtube.oauth2.redirect_uri}")
  private String redirectUri;

  @Value("${youtube.oauth2.application_name}")
  private String applicationName;

  @Value("${youtube.oauth2.credentials_filepath}")
  private String credentialsFilePath;

  @Value("${youtube.oauth2.access_type:offline}")
  private String accessType;

  @Value("${youtube.oauth2.approval_prompt:force}")
  private String approvalPrompt;

  private static final List<String> SCOPES = Arrays.asList(
      YouTubeScopes.YOUTUBE_UPLOAD,
      YouTubeScopes.YOUTUBE
  );

  private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private static final String CREDENTIALS_FOLDER = "credentials";

  @PostConstruct
  public void init() {
    log.info("YouTube OAuth Service 초기화 완료");
    log.info("- Client ID: {}", clientId != null ? clientId.substring(0, Math.min(10, clientId.length())) + "..." : "Not configured");
    log.info("- Application Name: {}", applicationName);
    log.info("- Redirect URI: {}", redirectUri);
    log.info("- Credentials File: {}", credentialsFilePath);
  }

  /**
   * YouTube API 클라이언트를 생성하고 OAuth 2.0 인증을 처리합니다.
   *
   * @return 인증된 YouTube API 클라이언트
   * @throws GeneralSecurityException 보안 관련 예외
   * @throws IOException I/O 관련 예외
   */
  public YouTube getAuthenticatedYouTubeService() throws GeneralSecurityException, IOException {
    log.info("YouTube API 인증 서비스 초기화 시작...");

    Credential credential = authorize();

    return new YouTube.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        JSON_FACTORY,
        credential)
        .setApplicationName(applicationName)
        .build();
  }

  /**
   * OAuth 2.0 인증을 수행하고 Credential 객체를 반환합니다.
   *
   * @return 인증된 Credential 객체
   * @throws IOException I/O 관련 예외
   * @throws GeneralSecurityException 보안 관련 예외
   */
  private Credential authorize() throws IOException, GeneralSecurityException {
    log.info("OAuth 2.0 인증 프로세스 시작...");

    // client_secrets.json 파일을 동적으로 생성
    GoogleClientSecrets clientSecrets = createClientSecrets();

    // 인증 정보 저장소 설정
    FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(
        new File(System.getProperty("user.home"), CREDENTIALS_FOLDER)
    );

    // Google OAuth 2.0 인증 플로우 구성
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        JSON_FACTORY,
        clientSecrets,
        SCOPES)
        .setDataStoreFactory(dataStoreFactory)
        .setAccessType(accessType)
        .setApprovalPrompt(approvalPrompt)
        .addRefreshListener(new DataStoreCredentialRefreshListener("user", dataStoreFactory))
        .build();

    // 로컬 서버 수신기 설정 (리다이렉션 URI에서 포트 추출)
    int port = extractPortFromRedirectUri(redirectUri);
    LocalServerReceiver receiver = new LocalServerReceiver.Builder()
        .setHost("localhost")
        .setPort(port)
        .setCallbackPath("/oauth2/callback")
        .build();

    // 인증 수행
    log.info("브라우저에서 OAuth 2.0 인증을 진행합니다...");
    log.info("인증 완료 후 자동으로 토큰이 저장됩니다.");

    Credential credential = new AuthorizationCodeInstalledApp(flow, receiver)
        .authorize("user");

    log.info("OAuth 2.0 인증 완료!");
    log.info("Access Token: {}...", credential.getAccessToken().substring(0, 20));
    log.info("Refresh Token: {}", credential.getRefreshToken() != null ? "설정됨" : "없음");

    return credential;
  }

  /**
   * GoogleClientSecrets 객체를 동적으로 생성합니다.
   *
   * @return GoogleClientSecrets 객체
   * @throws IOException I/O 관련 예외
   */
  private GoogleClientSecrets createClientSecrets() throws IOException {
    GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
    details.setClientId(clientId);
    details.setClientSecret(clientSecret);
    details.setRedirectUris(Arrays.asList(redirectUri));
    details.setAuthUri("https://accounts.google.com/o/oauth2/auth");
    details.setTokenUri("https://oauth2.googleapis.com/token");

    GoogleClientSecrets clientSecrets = new GoogleClientSecrets();
    clientSecrets.setWeb(details);

    log.debug("Client Secrets 객체 생성 완료");
    return clientSecrets;
  }

  /**
   * 리다이렉션 URI에서 포트 번호를 추출합니다.
   *
   * @param redirectUri 리다이렉션 URI
   * @return 포트 번호
   */
  private int extractPortFromRedirectUri(String redirectUri) {
    try {
      // http://localhost:8080/oauth2/callback -> 8080
      String[] parts = redirectUri.split(":");
      if (parts.length >= 3) {
        String portAndPath = parts[2]; // "8080/oauth2/callback"
        String portStr = portAndPath.split("/")[0]; // "8080"
        return Integer.parseInt(portStr);
      }
    } catch (Exception e) {
      log.warn("리다이렉션 URI에서 포트 추출 실패: {}. 기본 포트 8080 사용", redirectUri);
    }
    return 8080; // 기본값
  }

  /**
   * 저장된 인증 정보가 있는지 확인합니다.
   *
   * @return 인증 정보 존재 여부
   */
  public boolean hasStoredCredentials() {
    try {
      File credentialsDir = new File(System.getProperty("user.home"), CREDENTIALS_FOLDER);
      File dataStoreDir = new File(credentialsDir, "StoredCredential");

      if (dataStoreDir.exists() && dataStoreDir.isDirectory()) {
        File[] files = dataStoreDir.listFiles();
        boolean hasCredentials = files != null && files.length > 0;
        log.info("저장된 인증 정보 확인: {}", hasCredentials ? "있음" : "없음");
        return hasCredentials;
      }

      log.info("인증 정보 저장소 디렉토리가 존재하지 않음");
      return false;
    } catch (Exception e) {
      log.error("저장된 인증 정보 확인 중 오류: {}", e.getMessage());
      return false;
    }
  }

  /**
   * 저장된 인증 정보를 삭제합니다.
   *
   * @return 삭제 성공 여부
   */
  public boolean clearStoredCredentials() {
    try {
      File credentialsDir = new File(System.getProperty("user.home"), CREDENTIALS_FOLDER);
      if (credentialsDir.exists()) {
        deleteDirectory(credentialsDir);
        log.info("저장된 인증 정보가 삭제되었습니다.");
        return true;
      }
      log.info("삭제할 인증 정보가 없습니다.");
      return true;
    } catch (Exception e) {
      log.error("인증 정보 삭제 중 오류: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * 디렉토리를 재귀적으로 삭제합니다.
   *
   * @param directory 삭제할 디렉토리
   */
  private void deleteDirectory(File directory) {
    if (directory.isDirectory()) {
      File[] files = directory.listFiles();
      if (files != null) {
        for (File file : files) {
          deleteDirectory(file);
        }
      }
    }
    directory.delete();
  }

  /**
   * 현재 인증 상태를 확인합니다.
   *
   * @return 인증 상태 정보
   */
  public String getAuthenticationStatus() {
    try {
      if (hasStoredCredentials()) {
        return "✅ 인증 완료 - 저장된 토큰 사용 가능";
      } else {
        return "❌ 인증 필요 - OAuth 2.0 인증을 먼저 수행하세요";
      }
    } catch (Exception e) {
      return "⚠️ 인증 상태 확인 불가 - " + e.getMessage();
    }
  }
}