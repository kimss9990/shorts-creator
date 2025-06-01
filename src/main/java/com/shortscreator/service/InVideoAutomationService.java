package com.shortscreator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
public class InVideoAutomationService {

  @Value("${invideo.login.url}")
  private String invideoLoginUrl;
  @Value("${invideo.login.google_signin_button_xpath}")
  private String invideoGoogleSignInButtonXPath;
  @Value("${invideo.gmail.username.selector:input[type='email']}")
  private String gmailUsernameSelector;
  @Value("${invideo.gmail.username.next.selector:#identifierNext button}")
  private String gmailUsernameNextSelector;
  @Value("${invideo.gmail.password.selector:input[type='password']}")
  private String gmailPasswordSelector;
  @Value("${invideo.gmail.password.next.selector:#passwordNext button}")
  private String gmailPasswordNextSelector;
  @Value("${invideo.login.gmail.mfa_select_prompt_method_xpath}")
  private String mfaSelectPromptMethodXPath;
  @Value("${invideo.login.success.url_starts_with}")
  private String invideoSuccessUrlStartsWith;
  @Value("${invideo.login.mfa.timeout.seconds:120}")
  private int mfaTimeoutSeconds;
  @Value("${invideo.dashboard.loaded_indicator.selector}")
  private String invideoDashboardLoadedIndicatorSelector;
  @Value("${invideo.editor.confirmation_page_indicator_xpath://button[.//div[contains(text(),'Continue')]]}")
  private String invideoConfirmationPageIndicatorXPath;
  @Value("${invideo.editor.audience_married_adults_button_xpath}")
  private String audienceMarriedAdultsButtonXPath;
  @Value("${invideo.editor.visual_style_inspirational_button_xpath}")
  private String visualStyleInspirationalButtonXPath;
  @Value("${invideo.editor.continue_button_xpath}")
  private String settingsPageContinueButtonXPath;

  // --- InVideo AI 영상 생성 페이지 관련 설정 값들 (application.yml에 추가 필요) ---
  @Value("${invideo.editor.prompt_input_selector:textarea[placeholder*='your script or idea here']}") // 예시 Selector
  private String invideoPromptInputSelector;
  @Value("${invideo.editor.generate_button_selector://button[contains(.//text(), 'Generate') and contains(.//text(), 'video')]}")
  // Generate와 video가 포함된 버튼
  private String invideoGenerateButtonSelector;

  @Value("${invideo.access_token_filepath:invideo_access_token.txt}")
  private String accessTokenFilePath;

  @Value("${invideo.editor.settings_page_load_timeout_seconds:120}")
  private int settingsPageLoadTimeoutSeconds;

  // 다운로드 관련 설정 추가
  @Value("${invideo.editor.download_button_xpath}")
  private String downloadButtonXPath;
  @Value("${invideo.editor.download_video_option_xpath}")
  private String downloadVideoOptionXPath;
  @Value("${invideo.editor.download_dialog_xpath}")
  private String downloadDialogXPath;
  @Value("${invideo.editor.download_continue_button_xpath}")
  private String downloadContinueButtonXPath;
  @Value("${invideo.editor.video_generation_timeout_seconds:600}")
  private int videoGenerationTimeoutSeconds;

  private static final String V3_COPILOT_URL_FORMAT = "https://ai.invideo.io/workspace/%s/v30-copilot";
  private static final Pattern WORKSPACE_ID_PATTERN = Pattern.compile(
      "https://ai\\.invideo\\.io/workspace/([a-f0-9\\-]+)/.*");

  private static final String LOCAL_STORAGE_ACCESS_TOKEN_KEY = "access_token";

  private final ObjectMapper objectMapper;

  // WebDriver 옵션 (WebDriverConfig에서 가져오거나 여기서 정의)
  private ChromeOptions getChromeOptions() {
    ChromeOptions options = new ChromeOptions();
    String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"; // 최신 User-Agent로!
    options.addArguments("--user-agent=" + userAgent);
    options.addArguments("--disable-blink-features=AutomationControlled");
    options.setExperimentalOption("excludeSwitches", Arrays.asList("enable-automation", "load-extension"));
    options.setExperimentalOption("useAutomationExtension", false);
    options.addArguments("--no-sandbox");
    options.addArguments("--disable-infobars");
    options.addArguments("--disable-dev-shm-usage");
    options.addArguments("--disable-browser-side-navigation");
    options.addArguments("--disable-gpu");
    options.addArguments("--ignore-certificate-errors");
    // options.addArguments("--start-maximized"); // 필요시
    // options.addArguments("--headless=new"); // 백그라운드 실행 원할 시 (주의: InVideo UI 복잡도에 따라 헤드리스 문제 발생 가능)
    Map<String, Object> prefs = new HashMap<>();
    prefs.put("credentials_enable_service", false);
    prefs.put("profile.password_manager_enabled", false);
    options.setExperimentalOption("prefs", prefs);
    return options;
  }

  @Async("taskExecutor")
  public CompletableFuture<String> createVideoInInVideoAI(String gmailUsername, String gmailPassword,
      String invideoAiPromptForVideo) {
    WebDriver driver = null;
    log.info("InVideo AI 영상 생성 자동화 시작...");

    try {
      log.debug("WebDriverManager를 사용하여 ChromeDriver 설정 중...");
      WebDriverManager.chromedriver().setup();
      ChromeOptions options = getChromeOptions();
      driver = new ChromeDriver(options);
      log.info("WebDriver (Chrome) 인스턴스 생성 완료.");
      driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));

      boolean sessionRestoredViaToken = loadAndSetAccessToken(driver);

      if (!sessionRestoredViaToken) {
        log.info("Access Token을 통한 세션 복원 실패 또는 토큰 없음. 일반 로그인을 시도합니다.");
        // 토큰 없거나 유효하지 않으면 일반 로그인 수행
        boolean loggedInManually = loginToInVideo(driver, gmailUsername, gmailPassword);
        if (!loggedInManually) {
          log.error("InVideo AI 수동 로그인 실패. 영상 생성을 진행할 수 없습니다.");
          return CompletableFuture.completedFuture("❌ 로그인 실패");
        }
        log.info("InVideo AI 수동 로그인 성공.");
      } else {
        log.info("Access Token을 통해 성공적으로 세션이 복원되고 v3.0 페이지로 이동 완료. 현재 URL: {}", getCurrentUrlSafe(driver));
      }

      log.info("프롬프트 입력 단계로 진행합니다. 현재 URL: {}", getCurrentUrlSafe(driver));

      WebDriverWait interactionWait = new WebDriverWait(driver, Duration.ofSeconds(45));
      JavascriptExecutor js = (JavascriptExecutor) driver;

      log.info("InVideo AI 프롬프트 입력 필드({}) 대기 중...", invideoPromptInputSelector);
      WebElement promptInput = interactionWait.until(
          ExpectedConditions.visibilityOfElementLocated(By.xpath(invideoPromptInputSelector)));
      interactionWait.until(ExpectedConditions.elementToBeClickable(promptInput));
      promptInput.click(); // 포커스
      Thread.sleep(300);
      promptInput.clear();
      Thread.sleep(200);
      js.executeScript(
          "arguments[0].value = arguments[1]; arguments[0].dispatchEvent(new Event('input', { bubbles: true })); arguments[0].dispatchEvent(new Event('change', { bubbles: true }));",
          promptInput, invideoAiPromptForVideo);
      log.info("InVideo AI에 프롬프트 입력 완료.");
      Thread.sleep(2000);

      log.info("InVideo AI 영상 생성 시작 버튼({}) 대기 중...", invideoGenerateButtonSelector);
      WebElement generateButton = interactionWait.until(
          ExpectedConditions.elementToBeClickable(By.xpath(invideoGenerateButtonSelector)));
      js.executeScript("arguments[0].scrollIntoView(true);", generateButton);
      Thread.sleep(500);
      generateButton.click();
      log.info("InVideo AI 영상 생성 시작 버튼 클릭 완료.");

      // --- "Generate my video" 클릭 후 설정 페이지 로드 확인 ---
      log.info("영상 생성 설정 페이지 로딩 대기 중... (지표 요소: {}, 대기 시간: {}초)",
          invideoConfirmationPageIndicatorXPath, settingsPageLoadTimeoutSeconds);
      WebDriverWait settingsPageWait = new WebDriverWait(driver, Duration.ofSeconds(settingsPageLoadTimeoutSeconds));

      settingsPageWait.until(
          ExpectedConditions.visibilityOfElementLocated(By.xpath(invideoConfirmationPageIndicatorXPath))
      );
      log.info("영상 생성 설정 페이지로 성공적으로 이동 확인됨. 현재 URL: {}", getCurrentUrlSafe(driver));
      Thread.sleep(1000); // 페이지 안정화 대기

      // --- Audience 설정: "Married adults" 클릭 (없으면 랜덤 선택) ---
      try {
        log.info("'Audiences: Married adults' 버튼({}) 클릭 시도...", audienceMarriedAdultsButtonXPath);
        WebElement audienceButton = null;

        try {
          audienceButton = settingsPageWait.until(
              ExpectedConditions.elementToBeClickable(By.xpath(audienceMarriedAdultsButtonXPath)));
          log.info("'Married adults' 버튼을 찾았습니다.");
        } catch (Exception e) {
          log.info("'Married adults' 버튼을 찾을 수 없습니다. 사용 가능한 Audience 옵션 중 랜덤 선택을 시도합니다.");
          audienceButton = selectRandomAudienceOption(driver, settingsPageWait);
        }

        if (audienceButton != null) {
          if (!audienceButton.getAttribute("class").contains("selected-true")) {
            js.executeScript("arguments[0].scrollIntoView(true);", audienceButton);
            Thread.sleep(200);
            audienceButton.click();
            log.info("Audience 버튼 클릭 완료.");
            Thread.sleep(500);
          } else {
            log.info("선택된 Audience는 이미 선택되어 있습니다.");
          }
        }
      } catch (Exception e) {
        log.warn("Audience 설정 중 오류 발생 (무시하고 진행 가능성 있음): {}", e.getMessage());
      }

      // --- Visual style 설정: "Inspirational" 클릭 (없으면 랜덤 선택) ---
      try {
        log.info("'Visual style: Inspirational' 버튼({}) 클릭 시도...", visualStyleInspirationalButtonXPath);
        WebElement visualStyleButton = null;

        try {
          visualStyleButton = settingsPageWait.until(
              ExpectedConditions.elementToBeClickable(By.xpath(visualStyleInspirationalButtonXPath)));
          log.info("'Inspirational' 버튼을 찾았습니다.");
        } catch (Exception e) {
          log.info("'Inspirational' 버튼을 찾을 수 없습니다. 사용 가능한 Visual style 옵션 중 랜덤 선택을 시도합니다.");
          visualStyleButton = selectRandomVisualStyleOption(driver, settingsPageWait);
        }

        if (visualStyleButton != null) {
          if (!visualStyleButton.getAttribute("class").contains("selected-true")) {
            js.executeScript("arguments[0].scrollIntoView(true);", visualStyleButton);
            Thread.sleep(200);
            visualStyleButton.click();
            log.info("Visual style 버튼 클릭 완료.");
            Thread.sleep(500);
          } else {
            log.info("선택된 Visual style은 이미 선택되어 있습니다.");
          }
        }
      } catch (Exception e) {
        log.warn("Visual style 설정 중 오류 발생 (무시하고 진행 가능성 있음): {}", e.getMessage());
      }

      // (Platform은 YouTube Shorts가 기본 선택되어 있을 것으로 가정하고 일단 생략)

      // --- 선택된 옵션들 확인 및 Telegram으로 전송 ---
      String selectedOptionsMessage = "";
      try {
        selectedOptionsMessage = getSelectedOptions(driver);
        log.info("선택된 옵션들: {}", selectedOptionsMessage);
      } catch (Exception e) {
        log.warn("선택된 옵션 확인 중 오류 발생 (무시하고 진행): {}", e.getMessage());
        selectedOptionsMessage = "⚠️ 옵션 확인 중 오류가 발생했습니다.";
      }

      // --- 최종 "Continue" 버튼 클릭 ---
      log.info("설정 페이지의 'Continue' 버튼({}) 클릭 시도...", settingsPageContinueButtonXPath);
      WebElement continueButton = settingsPageWait.until(
          ExpectedConditions.elementToBeClickable(By.xpath(settingsPageContinueButtonXPath)));
      js.executeScript("arguments[0].scrollIntoView(true);", continueButton);
      Thread.sleep(200);
      continueButton.click();
      log.info("설정 페이지 'Continue' 버튼 클릭 완료. 실제 영상 생성 프로세스가 시작될 것으로 예상됩니다.");

      // --- 영상 생성 완료 대기 및 다운로드 시작 ---
      boolean downloadStarted = waitForVideoCompletionAndStartDownload(driver, settingsPageWait);
      if (downloadStarted) {
        selectedOptionsMessage += "\n\n🎬 영상 생성 완료 및 다운로드 시작됨";
        log.info("InVideo AI 영상 생성 및 다운로드 프로세스가 완료되었습니다.");
      } else {
        selectedOptionsMessage += "\n\n⚠️ 영상 생성은 진행되었으나 다운로드 시작 확인 실패";
        log.warn("영상 생성 후 다운로드 시작을 확인하지 못했습니다.");
      }

      return CompletableFuture.completedFuture("✅ 영상 생성 시작 완료\\n\\n" + selectedOptionsMessage);

    } catch (Exception e) {
      log.error("InVideo AI 영상 생성 자동화 중 오류 발생: {}", e.getMessage(), e);
      if (driver != null) {
        log.error("오류 발생 시점 URL: {}", getCurrentUrlSafe(driver));
      }
      return CompletableFuture.completedFuture("❌ 오류 발생: " + e.getMessage());
    } finally {
      if (driver != null) {
        try {
          log.info("작업 확인을 위해 10초 대기 후 WebDriver 종료...");
          Thread.sleep(10000);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        } finally {
          log.info("WebDriver 종료 시도...");
          driver.quit();
          log.info("WebDriver 종료 완료.");
        }
      }
    }
  }

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
        Files.writeString(Paths.get(accessTokenFilePath), accessToken, StandardCharsets.UTF_8);
        log.info("Access Token을 파일({})에 성공적으로 저장했습니다.", accessTokenFilePath);
      } else {
        log.warn("Local Storage에서 Access Token을 찾을 수 없거나 비어있습니다. (키: {})", LOCAL_STORAGE_ACCESS_TOKEN_KEY);
      }
    } catch (Exception e) {
      log.error("Access Token 저장 중 오류 발생: {}", e.getMessage(), e);
    }
  }

  private boolean loadAndSetAccessToken(WebDriver driver) {
    File tokenFile = new File(accessTokenFilePath);
    if (!tokenFile.exists() || tokenFile.length() == 0) {
      log.info("Access Token 파일({})이 없거나 비어있습니다.", accessTokenFilePath);
      return false;
    }

    try {
      String accessToken = Files.readString(Paths.get(accessTokenFilePath), StandardCharsets.UTF_8);
      if (accessToken.isEmpty()) {
        log.info("Access Token 파일은 존재하지만, 내용이 비어있습니다.");
        return false;
      }
      log.info("파일에서 Access Token 로드 완료.");

      // 토큰 설정을 위해 먼저 해당 도메인의 페이지로 이동해야 함
      driver.get(invideoLoginUrl);
      Thread.sleep(1000);

      JavascriptExecutor js = (JavascriptExecutor) driver;
      js.executeScript(
          String.format("window.localStorage.setItem('%s', '%s');", LOCAL_STORAGE_ACCESS_TOKEN_KEY, accessToken));
      log.info("Access Token을 Local Storage에 설정 완료 (키: {}).", LOCAL_STORAGE_ACCESS_TOKEN_KEY);

      // 토큰 적용을 위해 워크스페이스 페이지로 이동
      driver.get(invideoSuccessUrlStartsWith);
      log.debug("Local Storage에 토큰 설정 후 워크스페이스 페이지로 이동. 목표 URL: {}", invideoSuccessUrlStartsWith);
      Thread.sleep(3000);

      // 로그인된 상태인지 확인 및 v4.0에서 v3.0으로 리다이렉트
      WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
      try {
        WebElement dashboardIndicator = wait.until(
            ExpectedConditions.visibilityOfElementLocated(By.xpath(invideoDashboardLoadedIndicatorSelector)));

        if (dashboardIndicator.isDisplayed()) {
          log.info("Access Token을 통한 세션 복원 성공. 대시보드 요소 확인됨.");

          // **v4.0에서 v3.0으로 리다이렉트 처리 추가**
          String currentV40Url = getCurrentUrlSafe(driver);
          log.info("현재 v4.0 워크스페이스 URL: {}", currentV40Url);

          // v3.0 Copilot 페이지로 이동
          boolean redirected = redirectToV30Copilot(driver, currentV40Url, wait);
          if (!redirected) {
            log.warn("v3.0 페이지로의 리다이렉트가 실패했지만, 토큰 세션 복원은 성공했습니다.");
            // 토큰이 유효하지 않을 수 있으므로 삭제 고려
            js.executeScript(String.format("window.localStorage.removeItem('%s');", LOCAL_STORAGE_ACCESS_TOKEN_KEY));
            if (tokenFile.exists()) {
              tokenFile.delete();
            }
            return false;
          }

          return true;
        } else {
          log.warn("Access Token을 Local Storage에 설정했으나, 로그인된 상태로 확인되지 않음.");
          // 토큰이 유효하지 않을 수 있으므로 삭제
          js.executeScript(String.format("window.localStorage.removeItem('%s');", LOCAL_STORAGE_ACCESS_TOKEN_KEY));
          if (tokenFile.exists()) {
            tokenFile.delete();
          }
          return false;
        }
      } catch (Exception e) {
        log.warn("대시보드 요소 확인 중 오류 발생: {}. 토큰이 유효하지 않을 수 있습니다.", e.getMessage());
        js.executeScript(String.format("window.localStorage.removeItem('%s');", LOCAL_STORAGE_ACCESS_TOKEN_KEY));
        if (tokenFile.exists()) {
          tokenFile.delete();
        }
        return false;
      }
    } catch (Exception e) {
      log.warn("Access Token 로드 및 설정 중 오류 발생: {}. 일반 로그인을 진행합니다.", e.getMessage(), e);
      if (tokenFile.exists()) {
        tokenFile.delete();
      }
    }
    return false;
  }

  /**
   * v4.0 워크스페이스 URL에서 워크스페이스 ID를 추출하여 v3.0 Copilot 페이지로 리다이렉트
   *
   * @param driver     WebDriver 인스턴스
   * @param currentUrl 현재 v4.0 워크스페이스 URL
   * @param wait       WebDriverWait 인스턴스
   * @return 리다이렉트 성공 여부
   */
  private boolean redirectToV30Copilot(WebDriver driver, String currentUrl, WebDriverWait wait) {
    try {
      Matcher matcher = WORKSPACE_ID_PATTERN.matcher(currentUrl);
      if (matcher.matches()) {
        String workspaceId = matcher.group(1);
        String v30CopilotUrl = String.format(V3_COPILOT_URL_FORMAT, workspaceId);
        log.info("추출된 워크스페이스 ID: {}. v3.0 copilot URL로 이동 시도: {}", workspaceId, v30CopilotUrl);

        driver.get(v30CopilotUrl);
        Thread.sleep(2000); // 페이지 로드 대기

        // v3.0 페이지가 로드되었는지 확인
        log.info("v3.0 copilot 페이지 로드 및 특정 요소({}) 대기 중...", invideoPromptInputSelector);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(invideoPromptInputSelector)));
        log.info("v3.0 copilot 페이지로 성공적으로 이동 및 확인 완료: {}", getCurrentUrlSafe(driver));

        return true;
      } else {
        log.warn("현재 URL({})에서 워크스페이스 ID를 추출하지 못했습니다. v3.0 페이지로 이동할 수 없습니다.", currentUrl);
        return false;
      }
    } catch (Exception e) {
      log.error("v3.0 페이지로의 리다이렉트 중 오류 발생: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * 사용 가능한 Audience 옵션 중 랜덤으로 하나를 선택
   *
   * @param driver WebDriver 인스턴스
   * @param wait   WebDriverWait 인스턴스
   * @return 선택된 WebElement 또는 null
   */
  private WebElement selectRandomAudienceOption(WebDriver driver, WebDriverWait wait) {
    try {
      // Audience 섹션의 모든 버튼을 찾음 (일반적인 패턴으로 찾기)
      List<WebElement> audienceButtons = driver.findElements(
          By.xpath("//div[contains(text(), 'Audience') or contains(text(), 'audience')]/..//button[@value]"));

      if (audienceButtons.isEmpty()) {
        // 다른 패턴으로 시도
        audienceButtons = driver.findElements(
            By.xpath("//button[contains(@class, 'audience') or contains(@data-testid, 'audience')]"));
      }

      if (!audienceButtons.isEmpty()) {
        WebElement randomButton = audienceButtons.get((int) (Math.random() * audienceButtons.size()));
        String buttonText = randomButton.getAttribute("value");
        if (buttonText == null || buttonText.isEmpty()) {
          buttonText = randomButton.getText();
        }
        log.info("랜덤으로 선택된 Audience: {}", buttonText);
        return randomButton;
      } else {
        log.warn("사용 가능한 Audience 옵션을 찾을 수 없습니다.");
        return null;
      }
    } catch (Exception e) {
      log.error("랜덤 Audience 선택 중 오류 발생: {}", e.getMessage(), e);
      return null;
    }
  }

  /**
   * 사용 가능한 Visual Style 옵션 중 랜덤으로 하나를 선택
   *
   * @param driver WebDriver 인스턴스
   * @param wait   WebDriverWait 인스턴스
   * @return 선택된 WebElement 또는 null
   */
  private WebElement selectRandomVisualStyleOption(WebDriver driver, WebDriverWait wait) {
    try {
      // Visual Style 섹션의 모든 버튼을 찾음
      List<WebElement> styleButtons = driver.findElements(
          By.xpath("//div[contains(text(), 'Visual style') or contains(text(), 'Look and feel')]/..//button[@value]"));

      if (styleButtons.isEmpty()) {
        // 다른 패턴으로 시도
        styleButtons = driver.findElements(
            By.xpath("//button[contains(@class, 'style') or contains(@data-testid, 'style')]"));
      }

      if (!styleButtons.isEmpty()) {
        WebElement randomButton = styleButtons.get((int) (Math.random() * styleButtons.size()));
        String buttonText = randomButton.getAttribute("value");
        if (buttonText == null || buttonText.isEmpty()) {
          buttonText = randomButton.getText();
        }
        log.info("랜덤으로 선택된 Visual Style: {}", buttonText);
        return randomButton;
      } else {
        log.warn("사용 가능한 Visual Style 옵션을 찾을 수 없습니다.");
        return null;
      }
    } catch (Exception e) {
      log.error("랜덤 Visual Style 선택 중 오류 발생: {}", e.getMessage(), e);
      return null;
    }
  }

  /**
   * 현재 선택된 모든 옵션들을 확인하고 문자열로 반환
   *
   * @param driver WebDriver 인스턴스
   * @return 선택된 옵션들의 요약 문자열 (MarkdownV2 이스케이프 처리됨)
   */
  private String getSelectedOptions(WebDriver driver) {
    StringBuilder selectedOptions = new StringBuilder();
    selectedOptions.append("🎬 InVideo AI 설정 선택 완료\\n\\n");

    try {
      // Visual Style 확인
      String visualStyle = getSelectedOption(driver, "Visual style");
      selectedOptions.append("🎨 Visual Style: ").append(escapeForMarkdown(visualStyle)).append("\\n");

      // Audience 확인
      String audience = getSelectedOption(driver, "Audiences");
      selectedOptions.append("👥 Audience: ").append(escapeForMarkdown(audience)).append("\\n");

      // Platform 확인
      String platform = getSelectedOption(driver, "Platform");
      selectedOptions.append("📱 Platform: ").append(escapeForMarkdown(platform)).append("\\n");

    } catch (Exception e) {
      log.error("선택된 옵션 확인 중 오류: {}", e.getMessage(), e);
      selectedOptions.append("⚠️ 일부 옵션 확인 중 오류가 발생했습니다\\.");
    }

    return selectedOptions.toString();
  }

  /**
   * 영상 생성 완료를 대기하고 다운로드를 시작하는 메서드
   *
   * @param driver WebDriver 인스턴스
   * @param wait   WebDriverWait 인스턴스
   * @return 다운로드 시작 성공 여부
   */
  private boolean waitForVideoCompletionAndStartDownload(WebDriver driver, WebDriverWait wait) {
    try {
      log.info("영상 생성 완료 대기 중... (최대 {}분)", videoGenerationTimeoutSeconds / 60);

      // 영상 생성 완료 표시를 기다림 (Download 버튼이 활성화될 때까지)
      WebDriverWait longWait = new WebDriverWait(driver, Duration.ofSeconds(videoGenerationTimeoutSeconds));

      // Download 버튼이 나타날 때까지 대기
      WebElement downloadButton = longWait.until(
          ExpectedConditions.elementToBeClickable(By.xpath(downloadButtonXPath)));

      log.info("Download 버튼이 활성화되었습니다. 영상 생성이 완료된 것으로 보입니다.");
      Thread.sleep(2000); // 안정화 대기

      // Download 버튼 클릭
      JavascriptExecutor js = (JavascriptExecutor) driver;
      js.executeScript("arguments[0].scrollIntoView(true);", downloadButton);
      Thread.sleep(500);
      downloadButton.click();
      log.info("Download 버튼 클릭 완료.");

      // 드롭다운 메뉴에서 "Download video" 옵션 클릭 대기
      WebElement downloadVideoOption = wait.until(
          ExpectedConditions.elementToBeClickable(By.xpath(downloadVideoOptionXPath)));

      downloadVideoOption.click();
      log.info("'Download video' 옵션 클릭 완료.");
      Thread.sleep(1000);

      // Download Settings 다이얼로그가 나타날 때까지 대기
      WebElement downloadDialog = wait.until(
          ExpectedConditions.visibilityOfElementLocated(By.xpath(downloadDialogXPath)));

      log.info("Download Settings 다이얼로그가 나타났습니다.");

      // 다이얼로그에서 선택된 설정들 확인 및 로깅
      String downloadSettings = getDownloadSettings(driver);
      log.info("다운로드 설정: {}", downloadSettings);

      // Continue 버튼 클릭
      WebElement continueButton = wait.until(
          ExpectedConditions.elementToBeClickable(By.xpath(downloadContinueButtonXPath)));

      js.executeScript("arguments[0].scrollIntoView(true);", continueButton);
      Thread.sleep(500);
      continueButton.click();
      log.info("Download Settings의 Continue 버튼 클릭 완료. 다운로드가 시작될 것입니다.");

      return true;

    } catch (Exception e) {
      log.error("영상 생성 완료 대기 또는 다운로드 시작 중 오류 발생: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * Download Settings 다이얼로그에서 현재 선택된 설정들을 확인
   *
   * @param driver WebDriver 인스턴스
   * @return 다운로드 설정 요약 문자열
   */
  private String getDownloadSettings(WebDriver driver) {
    StringBuilder settings = new StringBuilder();

    try {
      // Type of watermarks 확인
      String watermarks = getSelectedDownloadOption(driver, "Type of watermarks");
      settings.append("워터마크: ").append(watermarks).append(", ");

      // invideo AI branding 확인
      String branding = getSelectedDownloadOption(driver, "invideo AI branding");
      settings.append("브랜딩: ").append(branding).append(", ");

      // Download resolution 확인
      String resolution = getSelectedDownloadOption(driver, "Download resolution");
      settings.append("해상도: ").append(resolution);

    } catch (Exception e) {
      log.warn("다운로드 설정 확인 중 오류: {}", e.getMessage());
      return "설정 확인 실패";
    }

    return settings.toString();
  }

  /**
   * Download Settings 다이얼로그에서 특정 섹션의 선택된 옵션을 확인
   */
  private String getSelectedDownloadOption(WebDriver driver, String sectionName) {
    try {
      List<WebElement> selectedButtons = driver.findElements(
          By.xpath(String.format("//div[contains(text(), '%s')]/..//button[contains(@class, 'hWMCax-selected-true')]",
              sectionName)));

      if (!selectedButtons.isEmpty()) {
        WebElement selectedButton = selectedButtons.get(0);
        String value = selectedButton.getAttribute("value");
        if (value != null && !value.isEmpty()) {
          return value;
        } else {
          // value가 없으면 텍스트에서 추출
          WebElement textDiv = selectedButton.findElement(By.xpath(".//div[contains(@class, 'c-cURRIC')]"));
          return textDiv.getText();
        }
      }

      return "선택되지 않음";
    } catch (Exception e) {
      log.warn("'{}' 섹션의 선택된 다운로드 옵션 확인 중 오류: {}", sectionName, e.getMessage());
      return "확인 실패";
    }
  }

  private void logAllButtonStates(WebDriver driver) {
    try {
      String[] sections = {"Visual style", "Audiences", "Platform"};

      for (String section : sections) {
        log.info("=== {} 섹션 버튼 상태 ===", section);

        List<WebElement> allButtons = driver.findElements(
            By.xpath(String.format("//div[contains(text(), '%s')]/..//button[@value]", section)));

        for (int i = 0; i < allButtons.size(); i++) {
          WebElement button = allButtons.get(i);
          String value = button.getAttribute("value");
          String classAttr = button.getAttribute("class");
          String text = button.getText();

          boolean isSelected = classAttr != null &&
              (classAttr.contains("selected-true") || classAttr.contains("hWMCax-selected-true"));

          log.info("  버튼 {}: value='{}', text='{}', selected={}, class='{}'",
              i + 1, value, text, isSelected, classAttr);
        }
      }
    } catch (Exception e) {
      log.warn("버튼 상태 로깅 중 오류: {}", e.getMessage());
    }
  }

  private String escapeForMarkdown(String text) {
    if (text == null) {
      return "";
    }
    return text
        .replace("_", "\\_")
        .replace("*", "\\*")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace(".", "\\.")
        .replace("!", "\\!")
        .replace("-", "\\-");
  }

  /**
   * 특정 섹션에서 선택된 옵션을 찾아서 반환
   *
   * @param driver      WebDriver 인스턴스
   * @param sectionName 섹션 이름 (예: "Visual style", "Audiences", "Platform")
   * @return 선택된 옵션의 텍스트
   */
  private String getSelectedOption(WebDriver driver, String sectionName) {
    try {
      // 여러 패턴으로 선택된 버튼 찾기
      String[] selectedPatterns = {
          // 패턴 1: selected-true 클래스
          String.format("//div[contains(text(), '%s')]/..//button[contains(@class, 'selected-true')]", sectionName),
          // 패턴 2: hWMCax-selected-true 클래스 (실제 HTML 구조 기반)
          String.format("//div[contains(text(), '%s')]/..//button[contains(@class, 'hWMCax-selected-true')]",
              sectionName),
          // 패턴 3: 첫 번째 버튼 (기본 선택된 경우가 많음)
          String.format("//div[contains(text(), '%s')]/..//button[1]", sectionName),
          // 패턴 4: value 속성이 있는 모든 버튼 중 첫 번째
          String.format("//div[contains(text(), '%s')]/..//button[@value][1]", sectionName)
      };

      for (String pattern : selectedPatterns) {
        try {
          List<WebElement> buttons = driver.findElements(By.xpath(pattern));
          if (!buttons.isEmpty()) {
            WebElement selectedButton = buttons.get(0);

            // 버튼이 실제로 선택된 상태인지 확인
            String classAttribute = selectedButton.getAttribute("class");
            boolean isSelected = classAttribute != null &&
                (classAttribute.contains("selected-true") ||
                    classAttribute.contains("hWMCax-selected-true"));

            String value = selectedButton.getAttribute("value");
            String text = (value != null && !value.isEmpty()) ? value : selectedButton.getText();

            if (isSelected) {
              log.debug("'{}' 섹션에서 명시적으로 선택된 옵션 발견: {}", sectionName, text);
              return text + " ✅";
            } else if (pattern.contains("[1]")) {
              // 첫 번째 버튼인 경우 (기본 선택)
              log.debug("'{}' 섹션에서 기본 선택된 옵션으로 추정: {}", sectionName, text);
              return text + " (기본선택)";
            }
          }
        } catch (Exception e) {
          log.debug("패턴 '{}' 시도 중 오류: {}", pattern, e.getMessage());
          continue;
        }
      }

      // 모든 패턴이 실패한 경우, 해당 섹션의 모든 버튼을 찾아서 첫 번째 반환
      try {
        List<WebElement> allButtons = driver.findElements(
            By.xpath(String.format("//div[contains(text(), '%s')]/..//button[@value]", sectionName)));

        if (!allButtons.isEmpty()) {
          WebElement firstButton = allButtons.get(0);
          String value = firstButton.getAttribute("value");
          String text = (value != null && !value.isEmpty()) ? value : firstButton.getText();
          log.debug("'{}' 섹션에서 첫 번째 사용 가능한 옵션 반환: {}", sectionName, text);
          return text + " (감지됨)";
        }
      } catch (Exception e) {
        log.warn("'{}' 섹션의 모든 버튼 탐색 중 오류: {}", sectionName, e.getMessage());
      }

      return "선택 감지 실패";
    } catch (Exception e) {
      log.warn("'{}' 섹션의 선택된 옵션 확인 중 오류: {}", sectionName, e.getMessage());
      return "확인 실패";
    }
  }

  private boolean loginToInVideo(WebDriver driver, String gmailUsername, String gmailPassword) {
    String originalWindowHandle = driver.getWindowHandle();
    String googleLoginWindowHandle = null;
    try {
      log.info("InVideo AI Gmail MFA 로그인 시도 (WebDriver 전달받음). 사용자: {}", gmailUsername);
      driver.get(invideoLoginUrl);
      WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

      WebElement joinWithGoogleButton = wait.until(
          ExpectedConditions.elementToBeClickable(By.xpath(invideoGoogleSignInButtonXPath)));
      joinWithGoogleButton.click();

      Set<String> allWindowHandles = driver.getWindowHandles();
      if (allWindowHandles.size() > 1) {
        for (String handle : allWindowHandles) {
          if (!handle.equals(originalWindowHandle)) {
            googleLoginWindowHandle = handle;
            driver.switchTo().window(googleLoginWindowHandle);
            break;
          }
        }
      }
      wait.until(ExpectedConditions.urlContains("accounts.google.com"));

      WebElement emailField = wait.until(
          ExpectedConditions.visibilityOfElementLocated(By.cssSelector(gmailUsernameSelector)));
      emailField.sendKeys(gmailUsername);
      WebElement nextButtonEmail = wait.until(
          ExpectedConditions.elementToBeClickable(By.cssSelector(gmailUsernameNextSelector)));
      nextButtonEmail.click();

      WebElement passwordField = wait.until(
          ExpectedConditions.visibilityOfElementLocated(By.cssSelector(gmailPasswordSelector)));
      passwordField.sendKeys(gmailPassword);
      WebElement nextButtonPassword = wait.until(
          ExpectedConditions.elementToBeClickable(By.cssSelector(gmailPasswordNextSelector)));
      nextButtonPassword.click();

      wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//h1[contains(., '2단계 인증')]")));
      WebElement mfaPromptButton = wait.until(
          ExpectedConditions.elementToBeClickable(By.xpath(mfaSelectPromptMethodXPath)));
      mfaPromptButton.click();
      log.info("'휴대전화나 태블릿에서 예를 탭합니다.' 옵션 클릭 완료. 스마트폰 알림을 확인하세요.");

      log.info("스마트폰에서 MFA(2단계 인증)를 승인해주세요...");
      WebDriverWait mfaWait = new WebDriverWait(driver, Duration.ofSeconds(mfaTimeoutSeconds));

      if (googleLoginWindowHandle != null) {
        driver.switchTo().window(originalWindowHandle);
        log.info("MFA 승인 후, 원래 창({})으로 포커스 전환.", originalWindowHandle);
      }

      WebDriverWait pageLoadWait = new WebDriverWait(driver, Duration.ofSeconds(60));

      // 1. v4.0 워크스페이스 URL로 이동 확인
      log.info("InVideo 워크스페이스 URL('{}'로 시작) 로딩 대기 중...", invideoSuccessUrlStartsWith);
      pageLoadWait.until(ExpectedConditions.urlMatches("^" + Pattern.quote(invideoSuccessUrlStartsWith) + ".*"));
      String currentV40Url = getCurrentUrlSafe(driver);
      log.info("InVideo v4.0 워크스페이스 URL로 리디렉션 확인됨: {}", currentV40Url);

      // 2. 페이지 완전 로드 대기
      log.info("페이지 완전 로드 대기 중 (document.readyState == 'complete')...");
      pageLoadWait.until(webDriver -> ((JavascriptExecutor) webDriver)
          .executeScript("return document.readyState").equals("complete"));
      log.info("페이지 document.readyState 'complete' 확인.");

      // 3. 대시보드/워크스페이스 v4.0의 특정 요소 확인
      log.info("InVideo v4.0 대시보드 특정 요소({}) 표시 대기 중...", invideoDashboardLoadedIndicatorSelector);
      WebElement dashboardElement = pageLoadWait.until(
          ExpectedConditions.visibilityOfElementLocated(By.xpath(invideoDashboardLoadedIndicatorSelector)));
      log.info("InVideo v4.0 대시보드 특정 요소 확인됨.");

      // --- v3.0 Copilot 페이지로 이동 ---
      boolean redirected = redirectToV30Copilot(driver, currentV40Url, pageLoadWait);
      if (!redirected) {
        log.warn("v3.0 페이지로의 리다이렉트가 실패했습니다.");
        return false;
      }

      saveAccessToken(driver);
      return true;

    } catch (Exception e) {
      log.error("InVideo AI 로그인 또는 v3.0 페이지 이동 중 오류 발생: {}", e.getMessage(), e);
      return false;
    }
  }

  private String getCurrentUrlSafe(WebDriver currentActiveDriver) {
    try {
      if (currentActiveDriver != null && currentActiveDriver.getWindowHandles() != null
          && !currentActiveDriver.getWindowHandles().isEmpty()) {
        return currentActiveDriver.getCurrentUrl();
      }
      return "N/A - WebDriver 또는 WindowHandles 없음";
    } catch (Exception e) {
      log.warn("getCurrentUrlSafe(WebDriver) 호출 중 오류: {}", e.getMessage());
      return "N/A - URL 가져오기 중 오류 발생 (" + e.getClass().getSimpleName() + ")";
    }
  }
}