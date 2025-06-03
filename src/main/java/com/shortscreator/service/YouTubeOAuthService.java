package com.shortscreator.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.DataStoreCredentialRefreshListener;
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
import java.net.ServerSocket;
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

  @Value("${youtube.oauth2.callback_port:8888}")
  private int oauthCallbackPort;

  private static final List<String> SCOPES = Arrays.asList(
      YouTubeScopes.YOUTUBE_UPLOAD,
      YouTubeScopes.YOUTUBE
  );

  private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private static final String CREDENTIALS_FOLDER = "credentials";

  // 캐시된 인증 플로우와 자격 증명
  private GoogleAuthorizationCodeFlow flow;
  private FileDataStoreFactory dataStoreFactory;

  @PostConstruct
  public void init() {
    try {
      // 인증 플로우를 미리 초기화
      initializeFlow();
      log.info("YouTube OAuth Service 초기화 완료");
      log.info("- Client ID: {}", clientId != null ? clientId.substring(0, Math.min(10, clientId.length())) + "..." : "Not configured");
      log.info("- Application Name: {}", applicationName);
      log.info("- OAuth Callback Port: {}", oauthCallbackPort);
      log.info("- 저장된 인증 정보: {}", hasStoredCredentials() ? "있음" : "없음");
    } catch (Exception e) {
      log.error("YouTube OAuth Service 초기화 실패: {}", e.getMessage(), e);
    }
  }

  /**
   * OAuth 플로우를 초기화합니다.
   */
  private void initializeFlow() throws IOException, GeneralSecurityException {
    if (this.flow == null) {
      GoogleClientSecrets clientSecrets = createClientSecrets();

      this.dataStoreFactory = new FileDataStoreFactory(
          new File(System.getProperty("user.home"), CREDENTIALS_FOLDER)
      );

      this.flow = new GoogleAuthorizationCodeFlow.Builder(
          GoogleNetHttpTransport.newTrustedTransport(),
          JSON_FACTORY,
          clientSecrets,
          SCOPES)
          .setDataStoreFactory(dataStoreFactory)
          .setAccessType(accessType)
          .setApprovalPrompt(approvalPrompt)
          .addRefreshListener(new DataStoreCredentialRefreshListener("user", dataStoreFactory))
          .build();

      log.debug("OAuth 플로우 초기화 완료");
    }
  }

  /**
   * YouTube API 클라이언트를 생성하고 OAuth 2.0 인증을 처리합니다.
   */
  public YouTube getAuthenticatedYouTubeService() throws GeneralSecurityException, IOException {
    log.debug("YouTube API 인증 서비스 요청...");

    Credential credential = getStoredOrNewCredential();

    return new YouTube.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        JSON_FACTORY,
        credential)
        .setApplicationName(applicationName)
        .build();
  }

  /**
   * 저장된 자격 증명을 가져오거나 새로 인증합니다.
   */
  private Credential getStoredOrNewCredential() throws IOException, GeneralSecurityException {
    initializeFlow();

    // 먼저 저장된 자격 증명 확인
    Credential credential = flow.loadCredential("user");

    if (credential != null) {
      log.debug("저장된 인증 정보 발견");

      // 토큰이 만료되었는지 확인하고 갱신
      if (credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() <= 60) {
        log.info("액세스 토큰이 만료됨. 갱신 시도...");
        boolean refreshed = credential.refreshToken();
        if (refreshed) {
          log.info("✅ 토큰 갱신 성공");
        } else {
          log.warn("⚠️ 토큰 갱신 실패. 새로운 인증이 필요할 수 있습니다.");
        }
      }

      return credential;
    } else {
      log.info("저장된 인증 정보가 없습니다. 새로운 인증이 필요합니다.");
      throw new IllegalStateException("OAuth 2.0 인증이 필요합니다. 먼저 /api/youtube/oauth/initiate를 호출하여 인증을 완료하세요.");
    }
  }

  /**
   * 새로운 OAuth 2.0 인증을 수행합니다 (수동 인증용).
   */
  public Credential performManualAuthentication() throws IOException, GeneralSecurityException {
    log.info("수동 OAuth 2.0 인증 프로세스 시작...");

    initializeFlow();

    // 사용 가능한 포트 찾기
    int availablePort = findAvailablePort(oauthCallbackPort);
    log.info("OAuth 콜백을 위한 사용 가능한 포트: {}", availablePort);

    // 동적 리다이렉션 URI로 클라이언트 시크릿 업데이트
    GoogleClientSecrets clientSecrets = createClientSecretsWithPort(availablePort);

    // 새로운 플로우 생성 (동적 포트 포함)
    GoogleAuthorizationCodeFlow dynamicFlow = new GoogleAuthorizationCodeFlow.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        JSON_FACTORY,
        clientSecrets,
        SCOPES)
        .setDataStoreFactory(dataStoreFactory)
        .setAccessType(accessType)
        .setApprovalPrompt(approvalPrompt)
        .addRefreshListener(new DataStoreCredentialRefreshListener("user", dataStoreFactory))
        .build();

    // 로컬 서버 수신기 설정
    LocalServerReceiver receiver = new LocalServerReceiver.Builder()
        .setHost("localhost")
        .setPort(availablePort)
        .setCallbackPath("/oauth2/callback")
        .build();

    log.info("OAuth 콜백 URL: http://localhost:{}/oauth2/callback", availablePort);
    log.info("브라우저에서 OAuth 2.0 인증을 진행합니다...");

    Credential credential = new AuthorizationCodeInstalledApp(dynamicFlow, receiver)
        .authorize("user");

    log.info("✅ OAuth 2.0 인증 완료!");
    log.info("Access Token: {}...", credential.getAccessToken() != null ? credential.getAccessToken().substring(0, 20) : "null");
    log.info("Refresh Token: {}", credential.getRefreshToken() != null ? "설정됨" : "없음");

    // 메인 플로우 업데이트
    this.flow = dynamicFlow;

    return credential;
  }

  /**
   * 포트가 포함된 클라이언트 시크릿을 생성합니다.
   */
  private GoogleClientSecrets createClientSecretsWithPort(int port) throws IOException {
    GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
    details.setClientId(clientId);
    details.setClientSecret(clientSecret);

    String dynamicRedirectUri = String.format("http://localhost:%d/oauth2/callback", port);
    details.setRedirectUris(Arrays.asList(dynamicRedirectUri));

    details.setAuthUri("https://accounts.google.com/o/oauth2/auth");
    details.setTokenUri("https://oauth2.googleapis.com/token");

    GoogleClientSecrets clientSecrets = new GoogleClientSecrets();
    clientSecrets.setWeb(details);

    log.debug("동적 포트 클라이언트 시크릿 생성 완료 (포트: {})", port);
    return clientSecrets;
  }

  /**
   * 사용 가능한 포트를 찾습니다.
   */
  private int findAvailablePort(int preferredPort) {
    for (int port = preferredPort; port <= preferredPort + 100; port++) {
      if (isPortAvailable(port)) {
        return port;
      }
    }

    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      log.warn("시스템 할당 포트도 찾을 수 없음. 기본 포트 9999 사용");
      return 9999;
    }
  }

  /**
   * 포트가 사용 가능한지 확인합니다.
   */
  private boolean isPortAvailable(int port) {
    try (ServerSocket socket = new ServerSocket(port)) {
      socket.setReuseAddress(true);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * GoogleClientSecrets 객체를 생성합니다.
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

    log.debug("기본 클라이언트 시크릿 생성 완료");
    return clientSecrets;
  }

  /**
   * 저장된 인증 정보가 있는지 확인합니다.
   */
  public boolean hasStoredCredentials() {
    try {
      File credentialsDir = new File(System.getProperty("user.home"), CREDENTIALS_FOLDER);
      File dataStoreDir = new File(credentialsDir, "StoredCredential");

      if (dataStoreDir.exists() && dataStoreDir.isDirectory()) {
        File[] files = dataStoreDir.listFiles();
        boolean hasCredentials = files != null && files.length > 0;
        log.debug("저장된 인증 정보 확인: {}", hasCredentials ? "있음" : "없음");
        return hasCredentials;
      }

      log.debug("인증 정보 저장소 디렉토리가 존재하지 않음");
      return false;
    } catch (Exception e) {
      log.error("저장된 인증 정보 확인 중 오류: {}", e.getMessage());
      return false;
    }
  }

  /**
   * 저장된 인증 정보를 삭제합니다.
   */
  public boolean clearStoredCredentials() {
    try {
      File credentialsDir = new File(System.getProperty("user.home"), CREDENTIALS_FOLDER);
      if (credentialsDir.exists()) {
        deleteDirectory(credentialsDir);
        log.info("저장된 인증 정보가 삭제되었습니다.");
        // 플로우도 다시 초기화
        this.flow = null;
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
   */
  public String getAuthenticationStatus() {
    try {
      if (hasStoredCredentials()) {
        // 실제로 토큰이 유효한지 확인
        try {
          Credential credential = getStoredOrNewCredential();
          return "✅ 인증 완료 - 저장된 토큰 사용 가능";
        } catch (Exception e) {
          return "⚠️ 저장된 토큰이 유효하지 않음 - 재인증 필요";
        }
      } else {
        return "❌ 인증 필요 - OAuth 2.0 인증을 먼저 수행하세요";
      }
    } catch (Exception e) {
      return "⚠️ 인증 상태 확인 불가 - " + e.getMessage();
    }
  }
}