package com.shortscreator.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InVideoTestService {

  private final YouTubeService youTubeService;

  @Value("${invideo.account.username}")
  private String invideoGmailUsername;

  @Value("${invideo.account.password}")
  private String invideoGmailPassword;

  @Value("${invideo.download.folder_path:#{systemProperties['user.home']}/Downloads}")
  private String downloadFolderPath;

  @Value("${invideo.download.wait_timeout_seconds:300}")
  private int downloadWaitTimeoutSeconds;

  @Value("${invideo.access_token_filepath:invideo_access_token.txt}")
  private String accessTokenFilePath;

  // InVideo 페이지 요소 선택자들
  @Value("${invideo.editor.download_button_xpath://button[contains(.//text(), 'Download')]}")
  private String downloadButtonXPath;

  @Value("${invideo.editor.download_video_option_xpath://div[@role='menuitem'][.//div[contains(text(), 'Download video')]]}")
  private String downloadVideoOptionXPath;

  @Value("${invideo.editor.download_dialog_xpath://div[@role='dialog'][.//div[contains(text(), 'Download Settings')]]}")
  private String downloadDialogXPath;

  @Value("${invideo.editor.download_continue_button_xpath://div[@role='dialog']//button[.//div[contains(text(), 'Continue')]]}")
  private String downloadContinueButtonXPath;

  private static final String LOCAL_STORAGE_ACCESS_TOKEN_KEY = "access_token";

  /**
   * InVideo 영상을 다운로드하고 YouTube에 업로드합니다.
   */
  @Async("taskExecutor")
  public CompletableFuture<String> downloadAndUploadVideo(String videoUrl, String title, String description) {
    WebDriver driver = null;
    log.info("InVideo 테스트: 영상 다운로드 및 YouTube 업로드 시작");
    log.info("- URL: {}", videoUrl);
    log.info("- 제목: {}", title);
    log.info("- 설명: {}", description);

    try {
      // WebDriver 설정
      WebDriverManager.chromedriver().setup();
      ChromeOptions options = getChromeOptions();
      driver = new ChromeDriver(options);
      driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));

      WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

      // Access Token 로드 및 설정 또는 로그인
      boolean authenticated = authenticateInVideo(driver, videoUrl);
      if (!authenticated) {
        log.error("InVideo 인증 실패");
        return CompletableFuture.completedFuture("❌ InVideo 인증 실패 - 로그인 정보를 확인하세요");
      }

      log.info("InVideo 페이지 로드 완료. 다운로드 프로세스 시작...");

      // 다운로드 전 기존 파일 목록 확인
      Set<String> beforeDownloadFiles = getFilesInDownloadFolder();
      log.info("다운로드 폴더 기존 파일 개수: {}", beforeDownloadFiles.size());

      // Download 버튼 클릭
      log.info("Download 버튼 대기 중...");
      WebElement downloadButton = wait.until(
          ExpectedConditions.elementToBeClickable(By.xpath(downloadButtonXPath)));

      JavascriptExecutor js = (JavascriptExecutor) driver;
      js.executeScript("arguments[0].scrollIntoView(true);", downloadButton);
      Thread.sleep(500);
      downloadButton.click();
      log.info("Download 버튼 클릭 완료");

      // Download video 옵션 선택
      log.info("Download video 옵션 대기 중...");
      WebElement downloadVideoOption = wait.until(
          ExpectedConditions.elementToBeClickable(By.xpath(downloadVideoOptionXPath)));
      downloadVideoOption.click();
      log.info("Download video 옵션 클릭 완료");

      Thread.sleep(1000);

      // Download Settings 다이얼로그 대기
      log.info("Download Settings 다이얼로그 대기 중...");
      WebElement downloadDialog = wait.until(
          ExpectedConditions.visibilityOfElementLocated(By.xpath(downloadDialogXPath)));
      log.info("Download Settings 다이얼로그 확인됨");

      // Continue 버튼 클릭
      log.info("Continue 버튼 대기 중...");
      WebElement continueButton = wait.until(
          ExpectedConditions.elementToBeClickable(By.xpath(downloadContinueButtonXPath)));
      js.executeScript("arguments[0].scrollIntoView(true);", continueButton);
      Thread.sleep(500);
      continueButton.click();
      log.info("Continue 버튼 클릭 완료. 다운로드 시작...");

      // 다운로드 완료 대기
      String downloadedFilePath = waitForDownloadCompletion(beforeDownloadFiles);
      if (downloadedFilePath == null) {
        log.error("다운로드 완료를 확인하지 못했습니다.");
        return CompletableFuture.completedFuture("❌ 다운로드 실패");
      }

      log.info("✅ 다운로드 완료: {}", downloadedFilePath);

      // YouTube 업로드
      log.info("YouTube Shorts 업로드 시작...");
      boolean uploadSuccess = youTubeService.uploadShorts(downloadedFilePath, title, description);

      if (uploadSuccess) {
        log.info("✅ YouTube Shorts 업로드 성공");

        // 로컬 파일 삭제
        boolean deleteSuccess = deleteLocalFile(downloadedFilePath);
        String result = "✅ 테스트 완료 - InVideo 다운로드 및 YouTube 업로드 성공";
        if (deleteSuccess) {
          result += "\n🗑️ 로컬 파일 삭제 완료";
        } else {
          result += "\n⚠️ 로컬 파일 삭제 실패";
        }
        return CompletableFuture.completedFuture(result);
      } else {
        log.error("❌ YouTube Shorts 업로드 실패");
        return CompletableFuture.completedFuture("❌ YouTube 업로드 실패 - OAuth 인증 상태를 확인하세요");
      }

    } catch (Exception e) {
      log.error("InVideo 테스트 중 오류 발생: {}", e.getMessage(), e);
      return CompletableFuture.completedFuture("❌ 오류 발생: " + e.getMessage());
    } finally {
      if (driver != null) {
        try {
          log.info("작업 완료 확인을 위해 5초 대기 후 WebDriver 종료...");
          Thread.sleep(5000);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        } finally {
          driver.quit();
          log.info("WebDriver 종료 완료");
        }
      }
    }
  }

  /**
   * Chrome 옵션 설정 (GPU 가속 및 성능 최적화 포함)
   */
  private ChromeOptions getChromeOptions() {
    ChromeOptions options = new ChromeOptions();

    // User Agent 설정
    String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
    options.addArguments("--user-agent=" + userAgent);

    // 자동화 감지 방지
    options.addArguments("--disable-blink-features=AutomationControlled");
    options.setExperimentalOption("excludeSwitches", Arrays.asList("enable-automation", "load-extension"));
    options.setExperimentalOption("useAutomationExtension", false);

    // 보안 및 샌드박스 설정
    options.addArguments("--no-sandbox");
    options.addArguments("--disable-dev-shm-usage");
    options.addArguments("--disable-infobars");
    options.addArguments("--ignore-certificate-errors");
    options.addArguments("--ignore-ssl-errors");
    options.addArguments("--ignore-certificate-errors-spki-list");

    // ===== GPU 가속 및 성능 최적화 =====
    // GPU 가속 활성화 (기존의 --disable-gpu 제거)
    options.addArguments("--enable-gpu");
    options.addArguments("--use-gl=desktop");
    options.addArguments("--enable-accelerated-2d-canvas");
    options.addArguments("--enable-accelerated-jpeg-decoding");
    options.addArguments("--enable-accelerated-mjpeg-decode");
    options.addArguments("--enable-accelerated-video-decode");

    // 하드웨어 가속 활성화
    options.addArguments("--enable-features=VaapiVideoDecoder");
    options.addArguments("--use-gpu-in-tests");

    // 메모리 및 CPU 최적화
    options.addArguments("--max_old_space_size=4096");
    options.addArguments("--memory-pressure-off");
    options.addArguments("--disable-background-timer-throttling");
    options.addArguments("--disable-backgrounding-occluded-windows");
    options.addArguments("--disable-renderer-backgrounding");

    // 네트워크 최적화
    options.addArguments("--aggressive-cache-discard");
    options.addArguments("--enable-tcp-fast-open");

    // 브라우저 UI 최적화 (필요시)
    options.addArguments("--headless"); // 헤드리스 모드 (UI 없음)
    options.addArguments("--disable-extensions");
    options.addArguments("--disable-plugins");
    options.addArguments("--disable-images"); // 이미지 로딩 비활성화로 속도 향상

    // 렌더링 최적화
    options.addArguments("--disable-background-mode");
    options.addArguments("--disable-default-apps");
    options.addArguments("--disable-sync");

    // 윈도우 크기 설정 (성능에 영향)
    options.addArguments("--window-size=1920,1080");
    options.addArguments("--start-maximized");

    // 다운로드 설정
    Map<String, Object> prefs = new HashMap<>();
    prefs.put("credentials_enable_service", false);
    prefs.put("profile.password_manager_enabled", false);
    prefs.put("download.default_directory", downloadFolderPath);
    prefs.put("download.prompt_for_download", false);
    prefs.put("download.directory_upgrade", true);
    prefs.put("safebrowsing.enabled", true);

    // 성능 관련 설정
    prefs.put("profile.default_content_setting_values.notifications", 2); // 알림 차단
    prefs.put("profile.default_content_settings.popups", 0); // 팝업 차단
    prefs.put("profile.managed_default_content_settings.images", 2); // 이미지 차단 (속도 향상)

    options.setExperimentalOption("prefs", prefs);

    // 로그 레벨 설정 (성능에 약간 도움)
    options.addArguments("--log-level=3"); // ERROR 레벨만
    options.addArguments("--silent");

    log.info("Chrome 옵션 설정 완료 - GPU 가속 활성화 및 성능 최적화 적용");
    return options;
  }

  /**
   * InVideo 로그인 또는 세션 복원을 수행합니다.
   */
  private boolean authenticateInVideo(WebDriver driver, String targetUrl) {
    try {
      // 1. 먼저 Access Token으로 세션 복원 시도
      boolean sessionRestored = loadAndSetAccessToken(driver, targetUrl);
      if (sessionRestored) {
        log.info("Access Token으로 세션 복원 성공");
        return true;
      }

      // 2. Access Token 복원 실패 시 수동 로그인 수행
      log.info("Access Token 세션 복원 실패. 수동 로그인을 시도합니다.");
      boolean loginSuccess = performInVideoLogin(driver);
      if (!loginSuccess) {
        log.error("InVideo 수동 로그인 실패");
        return false;
      }

      // 3. 로그인 성공 후 목표 URL로 이동
      log.info("로그인 성공. 목표 URL로 이동: {}", targetUrl);
      driver.get(targetUrl);
      Thread.sleep(3000);

      // 4. Access Token 저장
      saveAccessToken(driver);

      return true;

    } catch (Exception e) {
      log.error("InVideo 인증 중 오류: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * Access Token을 로드하고 설정합니다.
   */
  private boolean loadAndSetAccessToken(WebDriver driver, String targetUrl) {
    try {
      // Access Token 파일에서 로드
      File tokenFile = new File(accessTokenFilePath);
      if (!tokenFile.exists() || tokenFile.length() == 0) {
        log.info("Access Token 파일이 없습니다: {}", accessTokenFilePath);
        return false;
      }

      String accessToken = Files.readString(Paths.get(accessTokenFilePath));
      if (accessToken.isEmpty()) {
        log.info("Access Token이 비어있습니다.");
        return false;
      }

      log.info("Access Token 로드 완료");

      // InVideo 도메인으로 이동하여 토큰 설정
      driver.get("https://ai.invideo.io");
      Thread.sleep(2000);

      JavascriptExecutor js = (JavascriptExecutor) driver;
      js.executeScript(
          String.format("window.localStorage.setItem('%s', '%s');", LOCAL_STORAGE_ACCESS_TOKEN_KEY, accessToken));
      log.info("Local Storage에 Access Token 설정 완료");

      // 목표 URL로 이동
      driver.get(targetUrl);
      log.info("목표 URL로 이동: {}", targetUrl);
      Thread.sleep(3000);

      // 페이지가 제대로 로드되었는지 확인
      String currentUrl = driver.getCurrentUrl();
      if (currentUrl.contains("invideo.io") && !currentUrl.contains("login")) {
        log.info("InVideo 페이지 로드 성공: {}", currentUrl);

        // 다운로드 버튼이 있는지 확인하여 로그인 상태 검증
        try {
          WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));
          shortWait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(downloadButtonXPath)));
          log.info("다운로드 버튼 확인됨. 로그인 상태 정상");
          return true;
        } catch (Exception e) {
          log.warn("다운로드 버튼을 찾을 수 없음. 토큰이 유효하지 않을 수 있음");
          return false;
        }
      } else {
        log.warn("로그인 페이지로 리디렉션됨. 토큰이 만료된 것으로 보임: {}", currentUrl);
        return false;
      }

    } catch (Exception e) {
      log.error("Access Token 설정 중 오류: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * InVideo 수동 로그인을 수행합니다.
   */
  private boolean performInVideoLogin(WebDriver driver) {
    String originalWindowHandle = driver.getWindowHandle();
    String googleLoginWindowHandle = null;

    try {
      log.info("InVideo 수동 로그인 시작. 사용자: {}", invideoGmailUsername);

      // InVideo 로그인 페이지로 이동
      driver.get("https://invideo.io/login");
      WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

      // Google 로그인 버튼 클릭
      log.info("Google 로그인 버튼 대기 중...");
      WebElement joinWithGoogleButton = wait.until(
          ExpectedConditions.elementToBeClickable(By.xpath("//button[.//p[text()='Join with Google']]")));
      joinWithGoogleButton.click();
      log.info("Google 로그인 버튼 클릭 완료");

      // 새 창/탭 핸들링
      Set<String> allWindowHandles = driver.getWindowHandles();
      if (allWindowHandles.size() > 1) {
        for (String handle : allWindowHandles) {
          if (!handle.equals(originalWindowHandle)) {
            googleLoginWindowHandle = handle;
            driver.switchTo().window(googleLoginWindowHandle);
            log.info("Google 로그인 창으로 전환됨");
            break;
          }
        }
      }

      // Google 로그인 URL 확인
      wait.until(ExpectedConditions.urlContains("accounts.google.com"));
      log.info("Google 로그인 페이지 확인됨");

      // 이메일 입력
      log.info("이메일 입력 중...");
      WebElement emailField = wait.until(
          ExpectedConditions.visibilityOfElementLocated(By.cssSelector("input[type='email']")));
      emailField.sendKeys(invideoGmailUsername);

      WebElement nextButtonEmail = wait.until(
          ExpectedConditions.elementToBeClickable(By.cssSelector("#identifierNext button")));
      nextButtonEmail.click();
      log.info("이메일 입력 완료");

      // 비밀번호 입력
      log.info("비밀번호 입력 중...");
      WebElement passwordField = wait.until(
          ExpectedConditions.visibilityOfElementLocated(By.cssSelector("input[type='password']")));
      passwordField.sendKeys(invideoGmailPassword);

      WebElement nextButtonPassword = wait.until(
          ExpectedConditions.elementToBeClickable(By.cssSelector("#passwordNext button")));
      nextButtonPassword.click();
      log.info("비밀번호 입력 완료");

      // 2단계 인증 처리 (있는 경우)
      try {
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//h1[contains(., '2단계 인증')]")));
        log.info("2단계 인증 페이지 감지됨");

        WebElement mfaPromptButton = wait.until(
            ExpectedConditions.elementToBeClickable(
                By.xpath("//div[@data-challengetype='39'][.//div[contains(text(), '휴대전화나 태블릿에서')]]")));
        mfaPromptButton.click();
        log.info("MFA 프롬프트 방식 선택 완료. 스마트폰에서 승인해주세요...");

        // MFA 승인 대기
        WebDriverWait mfaWait = new WebDriverWait(driver, Duration.ofSeconds(120));
        if (googleLoginWindowHandle != null) {
          driver.switchTo().window(originalWindowHandle);
        }

        // InVideo 워크스페이스 URL로 리디렉션 대기
        mfaWait.until(ExpectedConditions.urlMatches("^https://ai\\.invideo\\.io/workspace/.*"));
        log.info("MFA 승인 완료 및 InVideo 워크스페이스로 리디렉션 확인");

      } catch (Exception mfaException) {
        log.info("2단계 인증이 필요하지 않거나 이미 완료됨");

        // 원래 창으로 전환하고 InVideo 페이지 로드 대기
        if (googleLoginWindowHandle != null) {
          driver.switchTo().window(originalWindowHandle);
        }

        WebDriverWait loginWait = new WebDriverWait(driver, Duration.ofSeconds(60));
        loginWait.until(ExpectedConditions.urlMatches("^https://ai\\.invideo\\.io/workspace/.*"));
        log.info("InVideo 워크스페이스로 리디렉션 확인");
      }

      log.info("InVideo 로그인 성공");
      return true;

    } catch (Exception e) {
      log.error("InVideo 수동 로그인 중 오류: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * Access Token을 저장합니다.
   */
  private void saveAccessToken(WebDriver driver) {
    try {
      JavascriptExecutor js = (JavascriptExecutor) driver;
      String accessToken = (String) js.executeScript(
          String.format("return window.localStorage.getItem('%s');", LOCAL_STORAGE_ACCESS_TOKEN_KEY));

      if (accessToken != null && !accessToken.isEmpty()) {
        File tokenFile = new File(accessTokenFilePath);
        File parentDir = tokenFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
          parentDir.mkdirs();
        }
        Files.writeString(Paths.get(accessTokenFilePath), accessToken);
        log.info("Access Token 저장 완료: {}", accessTokenFilePath);
      } else {
        log.warn("저장할 Access Token을 찾을 수 없음");
      }
    } catch (Exception e) {
      log.error("Access Token 저장 중 오류: {}", e.getMessage(), e);
    }
  }

  /**
   * 다운로드 폴더의 파일 목록을 가져옵니다.
   */
  private Set<String> getFilesInDownloadFolder() {
    try {
      File downloadDir = new File(downloadFolderPath);
      if (!downloadDir.exists()) {
        downloadDir.mkdirs();
        return new HashSet<>();
      }

      File[] files = downloadDir.listFiles();
      if (files == null) {
        return new HashSet<>();
      }

      return Arrays.stream(files)
          .map(File::getName)
          .collect(Collectors.toSet());
    } catch (Exception e) {
      log.error("다운로드 폴더 파일 목록 확인 중 오류: {}", e.getMessage());
      return new HashSet<>();
    }
  }

  /**
   * 다운로드 완료를 대기하고 새로 다운로드된 파일 경로를 반환합니다.
   */
  private String waitForDownloadCompletion(Set<String> beforeFiles) {
    try {
      log.info("다운로드 완료 대기 중... (최대 {}초)", downloadWaitTimeoutSeconds);

      long startTime = System.currentTimeMillis();
      long timeoutMillis = downloadWaitTimeoutSeconds * 1000L;

      while (System.currentTimeMillis() - startTime < timeoutMillis) {
        Set<String> currentFiles = getFilesInDownloadFolder();

        // 새로 추가된 파일 찾기
        for (String fileName : currentFiles) {
          if (!beforeFiles.contains(fileName) && isVideoFile(fileName) && !fileName.endsWith(".crdownload")) {
            String fullPath = new File(downloadFolderPath, fileName).getAbsolutePath();
            log.info("새로 다운로드된 파일 감지: {}", fileName);

            // 파일이 완전히 다운로드되었는지 확인 (크기 안정화 체크)
            if (isFileStable(fullPath)) {
              return fullPath;
            }
          }
        }

        Thread.sleep(2000); // 2초마다 확인
      }

      log.error("다운로드 완료 대기 시간 초과");
      return null;
    } catch (Exception e) {
      log.error("다운로드 완료 대기 중 오류: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 파일이 비디오 파일인지 확인합니다.
   */
  private boolean isVideoFile(String fileName) {
    String[] videoExtensions = {".mp4", ".avi", ".mov", ".wmv", ".flv", ".webm", ".mkv"};
    String lowerFileName = fileName.toLowerCase();
    return Arrays.stream(videoExtensions).anyMatch(lowerFileName::endsWith);
  }

  /**
   * 파일 크기가 안정화되었는지 확인합니다 (다운로드 완료 여부).
   */
  private boolean isFileStable(String filePath) {
    try {
      File file = new File(filePath);
      if (!file.exists()) {
        return false;
      }

      long size1 = file.length();
      Thread.sleep(2000); // 2초 대기
      long size2 = file.length();

      boolean stable = size1 == size2 && size1 > 0;
      log.debug("파일 안정성 체크: {} (크기: {} -> {}, 안정: {})",
          file.getName(), size1, size2, stable);
      return stable;
    } catch (Exception e) {
      log.warn("파일 안정성 체크 중 오류: {}", e.getMessage());
      return false;
    }
  }

  /**
   * 로컬 파일을 삭제합니다.
   */
  private boolean deleteLocalFile(String filePath) {
    try {
      File file = new File(filePath);
      if (file.exists()) {
        boolean deleted = file.delete();
        if (deleted) {
          log.info("로컬 파일 삭제 완료: {}", filePath);
          return true;
        } else {
          log.error("로컬 파일 삭제 실패: {}", filePath);
          return false;
        }
      } else {
        log.warn("삭제할 파일이 존재하지 않음: {}", filePath);
        return false;
      }
    } catch (Exception e) {
      log.error("로컬 파일 삭제 중 오류 발생: {}", e.getMessage(), e);
      return false;
    }
  }
}