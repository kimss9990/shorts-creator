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
  @Value("${invideo.editor.look_inspirational_button_xpath}")
  private String lookInspirationalButtonXPath;
  @Value("${invideo.editor.continue_button_xpath}")
  private String settingsPageContinueButtonXPath;

  // --- InVideo AI 영상 생성 페이지 관련 설정 값들 (application.yml에 추가 필요) ---
  @Value("${invideo.editor.prompt_input_selector:textarea[placeholder*='your script or idea here']}") // 예시 Selector
  private String invideoPromptInputSelector;
  @Value("${invideo.editor.generate_button_selector:button[data-testid='generate-video-button']}") // 예시 Selector
  private String invideoGenerateButtonSelector;

  @Value("${invideo.access_token_filepath:invideo_access_token.txt}")
  private String accessTokenFilePath;

  @Value("${invideo.editor.settings_page_load_timeout_seconds:120}")
  private int settingsPageLoadTimeoutSeconds;

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
  public CompletableFuture<Boolean> createVideoInInVideoAI(String gmailUsername, String gmailPassword,
      String invideoAiPromptForVideo) {
    WebDriver driver = null;
    log.info("InVideo AI 영상 생성 자동화 시작...");

    try {
      // ... (WebDriver 생성 및 로그인, 프롬프트 입력, "Generate my video" 버튼 클릭까지의 로직은 이전과 동일) ...
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
          return CompletableFuture.completedFuture(false);
        }
        log.info("InVideo AI 수동 로그인 성공.");
        // 로그인 성공 후 Access Token 저장 (loginToInVideo 내부에서 호출되도록 수정했었음)
        // saveAccessToken(driver); // loginToInVideo 내부에서 호출되므로 여기서는 중복 호출 방지
      } else {
        log.info("Access Token을 통해 성공적으로 세션이 복원된 것으로 보입니다. 현재 URL: {}", getCurrentUrlSafe(driver));
      }

      log.info("프롬프트 입력 단계로 진행합니다. 현재 URL: {}", getCurrentUrlSafe(driver));
      // (필요시 에디터 페이지로의 명시적 이동 로직)

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
      // WebDriverWait 생성 시 설정된 타임아웃 값 사용
      WebDriverWait settingsPageWait = new WebDriverWait(driver, Duration.ofSeconds(settingsPageLoadTimeoutSeconds));

      settingsPageWait.until(
          ExpectedConditions.visibilityOfElementLocated(By.xpath(invideoConfirmationPageIndicatorXPath))
      );
      log.info("영상 생성 설정 페이지로 성공적으로 이동 확인됨. 현재 URL: {}", getCurrentUrlSafe(driver));
      Thread.sleep(1000); // 페이지 안정화 대기

      // --- Audience 설정: "Married adults" 클릭 ---
      try {
        log.info("'Audiences: Married adults' 버튼({}) 클릭 시도...", audienceMarriedAdultsButtonXPath);
        WebElement audienceButton = settingsPageWait.until(
            ExpectedConditions.elementToBeClickable(By.xpath(audienceMarriedAdultsButtonXPath)));
        // 이미 선택되어 있을 수 있지만, 확실히 하기 위해 클릭 (또는 isSelected/class 확인 후 조건부 클릭)
        if (!audienceButton.getAttribute("class").contains("selected-true")) { // 예시: 선택 여부 확인
          js.executeScript("arguments[0].scrollIntoView(true);", audienceButton);
          Thread.sleep(200);
          audienceButton.click();
          log.info("'Married adults' 버튼 클릭 완료.");
          Thread.sleep(500); // 클릭 후 반영 대기
        } else {
          log.info("'Married adults'는 이미 선택되어 있습니다.");
        }
      } catch (Exception e) {
        log.warn("'Audiences: Married adults' 버튼 클릭 중 오류 발생 (무시하고 진행 가능성 있음): {}", e.getMessage());
      }

      // --- Look and feel 설정: "Inspirational" 클릭 ---
      try {
        log.info("'Look and feel: Inspirational' 버튼({}) 클릭 시도...", lookInspirationalButtonXPath);
        WebElement lookButton = settingsPageWait.until(
            ExpectedConditions.elementToBeClickable(By.xpath(lookInspirationalButtonXPath)));
        if (!lookButton.getAttribute("class").contains("selected-true")) { // 예시: 선택 여부 확인
          js.executeScript("arguments[0].scrollIntoView(true);", lookButton);
          Thread.sleep(200);
          lookButton.click();
          log.info("'Inspirational' 버튼 클릭 완료.");
          Thread.sleep(500); // 클릭 후 반영 대기
        } else {
          log.info("'Inspirational'은 이미 선택되어 있습니다.");
        }
      } catch (Exception e) {
        log.warn("'Look and feel: Inspirational' 버튼 클릭 중 오류 발생 (무시하고 진행 가능성 있음): {}", e.getMessage());
      }

      // (Platform은 YouTube Shorts가 기본 선택되어 있을 것으로 가정하고 일단 생략)

      // --- 최종 "Continue" 버튼 클릭 ---
      log.info("설정 페이지의 'Continue' 버튼({}) 클릭 시도...", settingsPageContinueButtonXPath);
      WebElement continueButton = settingsPageWait.until(
          ExpectedConditions.elementToBeClickable(By.xpath(settingsPageContinueButtonXPath)));
      js.executeScript("arguments[0].scrollIntoView(true);", continueButton);
      Thread.sleep(200);
      continueButton.click();
      log.info("설정 페이지 'Continue' 버튼 클릭 완료. 실제 영상 생성 프로세스가 시작될 것으로 예상됩니다.");

      log.info("InVideo AI 영상 생성이 다음 단계로 진행되었습니다. (실제 완료까지는 시간이 소요될 수 있음)");
      // TODO: 이 페이지 이후의 실제 영상 생성 진행률 추적, 완료 감지, 다운로드 등의 로직은 추가 구현 필요.

      return CompletableFuture.completedFuture(true);

    } catch (Exception e) {
      log.error("InVideo AI 영상 생성 자동화 중 오류 발생: {}", e.getMessage(), e);
      if (driver != null) {
        log.error("오류 발생 시점 URL: {}", getCurrentUrlSafe(driver));
      }
      return CompletableFuture.completedFuture(false);
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
      driver.get(invideoLoginUrl); // 또는 invideoSuccessUrlStartsWith의 기본 도메인
      Thread.sleep(1000); // 페이지 로드 대기

      JavascriptExecutor js = (JavascriptExecutor) driver;
      js.executeScript(
          String.format("window.localStorage.setItem('%s', '%s');", LOCAL_STORAGE_ACCESS_TOKEN_KEY, accessToken));
      log.info("Access Token을 Local Storage에 설정 완료 (키: {}).", LOCAL_STORAGE_ACCESS_TOKEN_KEY);

      // 토큰 적용을 위해 페이지 새로고침 또는 대시보드로 이동
      driver.navigate().refresh(); // 또는 driver.get(invideoSuccessUrlStartsWith);
      log.debug("Local Storage에 토큰 설정 후 페이지 새로고침/이동 시도. 목표 URL: {}", invideoSuccessUrlStartsWith);
      Thread.sleep(3000); // 애플리케이션이 토큰을 읽고 상태를 변경할 시간

      // 로그인된 상태인지 확인 (예: 대시보드 특정 요소 존재 여부)
      // 이 확인은 invideoSuccessUrlStartsWith URL로 이동한 후에 수행되어야 함.
      if (!driver.getCurrentUrl().startsWith(invideoSuccessUrlStartsWith)) {
        driver.get(invideoSuccessUrlStartsWith); // 명시적으로 대시보드 URL로 이동
        Thread.sleep(2000); // 이동 후 대기
      }

      WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
      WebElement dashboardIndicator = wait.until(
          ExpectedConditions.visibilityOfElementLocated(By.xpath(invideoDashboardLoadedIndicatorSelector)));
      if (dashboardIndicator.isDisplayed()) {
        log.info("Access Token을 통한 세션 복원 성공. 대시보드 요소 확인됨.");
        return true;
      } else {
        log.warn("Access Token을 Local Storage에 설정했으나, 로그인된 상태로 확인되지 않음.");
        // 토큰이 유효하지 않을 수 있으므로 삭제 고려
        js.executeScript(String.format("window.localStorage.removeItem('%s');", LOCAL_STORAGE_ACCESS_TOKEN_KEY));
        if (tokenFile.exists()) {
          tokenFile.delete();
        }
        return false;
      }
    } catch (Exception e) {
      log.warn("Access Token 로드 및 설정 중 오류 발생: {}. 일반 로그인을 진행합니다.", e.getMessage(), e);
      if (tokenFile.exists()) {
        tokenFile.delete(); // 오류 시 파일 삭제
      }
    }
    return false;
  }

  // 로그인 로직 (기존 InVideoLoginService 내용을 가져오거나 수정하여 사용)
  private boolean loginToInVideo(WebDriver driver, String gmailUsername, String gmailPassword) {
    // 이 메소드는 이전 InVideoLoginService의 loginToInVideoWithGmailMfa 내용을 기반으로,
    // WebDriver를 인자로 받아 사용하도록 수정합니다.
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

      if (googleLoginWindowHandle != null) { // 팝업이 있었다면 원래 창으로 돌아오는지 확인
        // 일부 시스템에서는 팝업 닫힘 후 자동으로 원래 창으로 돌아오지 않을 수 있음
        // 또는 원래 창의 URL이 변경되는 것을 기다려야 함.
        // 여기서는 원래 창으로 먼저 전환하고 URL 변경을 기다림.
        driver.switchTo().window(originalWindowHandle);
        log.info("MFA 승인 후, 원래 창({})으로 포커스 전환.", originalWindowHandle);
      }

      WebDriverWait pageLoadWait = new WebDriverWait(driver, Duration.ofSeconds(60)); // 대기 시간은 적절히 조절

// 1. v4.0 워크스페이스 URL로 이동 확인 (기존 로직)
      log.info("InVideo 워크스페이스 URL('{}'로 시작) 로딩 대기 중...", invideoSuccessUrlStartsWith);
      pageLoadWait.until(ExpectedConditions.urlMatches("^" + Pattern.quote(invideoSuccessUrlStartsWith) + ".*"));
      String currentV40Url = getCurrentUrlSafe(driver);
      log.info("InVideo v4.0 워크스페이스 URL로 리디렉션 확인됨: {}", currentV40Url);

      // 2. 페이지 완전 로드 대기 (기존 로직)
      log.info("페이지 완전 로드 대기 중 (document.readyState == 'complete')...");
      pageLoadWait.until(webDriver -> ((JavascriptExecutor) webDriver)
          .executeScript("return document.readyState").equals("complete"));
      log.info("페이지 document.readyState 'complete' 확인.");

      // 3. 대시보드/워크스페이스 v4.0의 특정 요소 확인 (기존 로직)
      log.info("InVideo v4.0 대시보드 특정 요소({}) 표시 대기 중...", invideoDashboardLoadedIndicatorSelector);
      WebElement dashboardElement = pageLoadWait.until(
          ExpectedConditions.visibilityOfElementLocated(By.xpath(invideoDashboardLoadedIndicatorSelector)));
      log.info("InVideo v4.0 대시보드 특정 요소 확인됨.");

      // --- v3.0 Copilot 페이지로 이동 ---
      Matcher matcher = WORKSPACE_ID_PATTERN.matcher(currentV40Url);
      if (matcher.matches()) {
        String workspaceId = matcher.group(1);
        String v30CopilotUrl = String.format(V3_COPILOT_URL_FORMAT, workspaceId);
        log.info("추출된 워크스페이스 ID: {}. v3.0 copilot URL로 이동 시도: {}", workspaceId, v30CopilotUrl);
        driver.get(v30CopilotUrl);

        // v3.0 페이지가 로드되었는지 확인 (예: v3.0 페이지에만 있는 특정 요소 대기)
        // 이 Selector는 application.yml에 새로 정의하거나, 여기에 직접 작성해야 합니다.
        // 예시: String v30PageIndicatorSelector = "//h1[contains(text(),'Welcome to invideo AI v3')]";
        String v30PageIndicatorSelector = invideoPromptInputSelector; // 프롬프트 입력창이 v3에도 있다면 그것으로 확인
        log.info("v3.0 copilot 페이지 로드 및 특정 요소({}) 대기 중...", v30PageIndicatorSelector);
        pageLoadWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(v30PageIndicatorSelector)));
        log.info("v3.0 copilot 페이지로 성공적으로 이동 및 확인 완료: {}", getCurrentUrlSafe(driver));
      } else {
        log.warn("현재 URL({})에서 워크스페이스 ID를 추출하지 못했습니다. v3.0 페이지로 이동할 수 없습니다.", currentV40Url);
        // 이 경우 로그인 실패로 처리하거나, v4.0에서 계속 진행할지 결정해야 합니다.
        // 여기서는 일단 실패로 간주하지 않고 로그만 남깁니다 (v4.0으로라도 진행 가능하게).
        // 만약 v3.0 이동이 필수라면 여기서 false를 반환해야 합니다.
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