package com.shortscreator.service;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException; // TimeoutException import
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern; // Pattern import

@Slf4j
public class InVideoLoginService {

  private final WebDriver driver;

  // ... (기존 @Value 필드 선언은 동일하게 유지) ...
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


  @Autowired
  public InVideoLoginService(WebDriver driver) {
    this.driver = driver;
  }

  public boolean loginToInVideoWithGmailMfa(String gmailUsername, String gmailPassword) {
    String originalWindowHandle = driver.getWindowHandle();
    String googleLoginWindowHandle = null;

    try {
      log.info("InVideo AI Gmail MFA 로그인 시도. 사용자: {}", gmailUsername);
      driver.get(invideoLoginUrl);
      log.debug("InVideo 로그인 페이지({}) 로드 완료", invideoLoginUrl);

      WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

      log.debug("'Join with Google' 버튼({}) 대기 중...", invideoGoogleSignInButtonXPath);
      WebElement joinWithGoogleButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(invideoGoogleSignInButtonXPath)));
      joinWithGoogleButton.click();
      log.info("'Join with Google' 버튼 클릭 완료.");

      // 새 창/탭 핸들링
      Set<String> allWindowHandles = driver.getWindowHandles();
      if (allWindowHandles.size() > 1) {
        for (String handle : allWindowHandles) {
          if (!handle.equals(originalWindowHandle)) {
            googleLoginWindowHandle = handle;
            driver.switchTo().window(googleLoginWindowHandle);
            log.info("새로운 Google 로그인 창/탭({})으로 전환됨.", googleLoginWindowHandle);
            break;
          }
        }
      } else {
        log.warn("Google 로그인 창/탭이 새로 열리지 않았습니다. 현재 창에서 진행합니다.");
        // 이 경우 googleLoginWindowHandle은 null로 유지됩니다.
      }

      // 현재 창(Google 로그인 창이어야 함)에서 URL 확인
      wait.until(ExpectedConditions.urlContains("accounts.google.com"));
      log.debug("Google 로그인 페이지 URL({}) 확인.", getCurrentUrlSafe());


      // --- 이메일, 비밀번호 입력, MFA 방식 선택 클릭 로직 (동일) ---
      log.debug("Gmail 이메일 입력 필드({}) 대기 중...", gmailUsernameSelector);
      WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(gmailUsernameSelector)));
      emailField.sendKeys(gmailUsername);
      log.info("Gmail 이메일 입력 완료.");

      log.debug("이메일 '다음' 버튼({}) 클릭 대기 중...", gmailUsernameNextSelector);
      WebElement nextButtonEmail = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(gmailUsernameNextSelector)));
      nextButtonEmail.click();
      log.info("이메일 입력 후 '다음' 버튼 클릭 완료.");

      log.debug("Gmail 비밀번호 입력 필드({}) 대기 중...", gmailPasswordSelector);
      WebElement passwordField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(gmailPasswordSelector)));
      passwordField.sendKeys(gmailPassword);
      log.info("Gmail 비밀번호 입력 완료.");

      log.debug("비밀번호 '다음' 버튼({}) 클릭 대기 중...", gmailPasswordNextSelector);
      WebElement nextButtonPassword = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(gmailPasswordNextSelector)));
      nextButtonPassword.click();
      log.info("비밀번호 입력 후 '다음' 버튼 클릭 완료.");

      wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//h1[contains(., '2단계 인증')]")));
      log.info("2단계 인증 방법 선택 페이지 로드됨.");

      log.debug("MFA 방식 선택 버튼('휴대전화/태블릿에서 예') 대기 중... XPath: {}", mfaSelectPromptMethodXPath);
      WebElement mfaPromptButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(mfaSelectPromptMethodXPath)));
      mfaPromptButton.click();
      log.info("'휴대전화나 태블릿에서 예를 탭합니다.' 옵션 클릭 완료.");
      // --- 여기까지 Google 로그인 창/탭에서 작업 ---

      log.info("스마트폰에서 MFA(2단계 인증)를 승인해주세요...");
      log.info("MFA 승인 대기 중... (최대 {}초).", mfaTimeoutSeconds);

      // === MFA 승인 후 원래 창으로 돌아와서 URL 및 요소 확인 ===
      WebDriverWait mfaWait = new WebDriverWait(driver, Duration.ofSeconds(mfaTimeoutSeconds));

      try {
        // 1. Google 로그인 창이 닫히고 원래 창으로 돌아올 때까지 기다림
        //    또는 원래 창의 URL이 변경될 때까지 기다림.
        //    여기서는 원래 창으로 먼저 전환하고, 그 창의 URL 변경을 기다립니다.
        if (googleLoginWindowHandle != null && driver.getWindowHandles().contains(googleLoginWindowHandle)) {
          // 만약 Google 로그인 창이 아직 열려있다면, 사용자가 MFA를 누르면 이 창이 닫히거나
          // 원래 창의 URL이 바뀔 것입니다.
          // 여기서는 Google 창이 닫히고 원래 창의 URL이 바뀌는 것을 동시에 기다리기 어렵기 때문에,
          // '원래 창의 URL이 InVideo 워크스페이스로 시작하는가'를 주된 조건으로 삼습니다.
          // 그 전에 원래 창으로 포커스를 옮겨줍니다.
          log.debug("MFA 승인 후, 원래 창({})으로 포커스 전환 시도.", originalWindowHandle);
          driver.switchTo().window(originalWindowHandle);
        } else if (googleLoginWindowHandle == null && driver.getWindowHandles().size() > 1) {
          // Google 창이 특정되지 않았지만 창이 여러 개면 원래 창으로 시도
          log.debug("MFA 승인 후, Google 창이 특정되지 않았으나 창이 여러개이므로 원래 창({})으로 포커스 전환 시도.", originalWindowHandle);
          driver.switchTo().window(originalWindowHandle);
        } else {
          // 이미 원래 창이거나, 창이 하나만 남은 경우 (자동으로 포커스 전환됨)
          log.debug("MFA 승인 후, 현재 창({})에서 URL 변경 대기.", driver.getWindowHandle());
        }


        // 2. 현재 활성화된 창(원래 InVideo 창이어야 함)의 URL이 InVideo 워크스페이스로 시작하는지 확인
        log.info("InVideo 워크스페이스 URL('{}'로 시작) 로딩 대기 중...", invideoSuccessUrlStartsWith);
        mfaWait.until(ExpectedConditions.urlMatches("^" + Pattern.quote(invideoSuccessUrlStartsWith) + ".*"));
        log.info("InVideo 워크스페이스 URL로 리디렉션 확인됨: {}", getCurrentUrlSafe());

        // 3. InVideo 대시보드 페이지가 완전히 로드되었는지 특정 요소로 확인 (안정성 확보)
        log.info("InVideo 대시보드 페이지 로딩 및 특정 요소({}) 대기 중...", invideoDashboardLoadedIndicatorSelector);
        WebElement dashboardElement = new WebDriverWait(driver, Duration.ofSeconds(20)) // 대시보드 요소 대기 시간 증가
            .until(ExpectedConditions.visibilityOfElementLocated(By.xpath(invideoDashboardLoadedIndicatorSelector)));
        log.info("InVideo 대시보드 특정 요소 확인됨. 페이지 로드 안정된 것으로 판단.");
        log.info("최종적으로 확인된 InVideo 페이지 URL: {}", getCurrentUrlSafe());
        return true; // 성공

      } catch (TimeoutException e) {
        log.warn("MFA 승인 후 InVideo 페이지 로드 타임아웃 (URL: '{}', 요소: '{}'). 현재 URL: {}",
            invideoSuccessUrlStartsWith, invideoDashboardLoadedIndicatorSelector, getCurrentUrlSafe(), e);
        return false;
      } catch (Exception e) {
        log.error("MFA 후 InVideo 페이지 확인 중 예외 발생. 현재 URL: {}. 오류: {}",
            getCurrentUrlSafe(), e.getMessage(), e);
        return false;
      }

    } catch (Exception e) {
      log.error("InVideo AI 로그인 중 전체 프로세스에서 예외 발생 (사용자: {}): {}", gmailUsername, e.getMessage(), e);
      return false;
    } finally {
      // `finally` 블록은 WebDriver를 여기서 닫지 않도록 주의 (WebDriverConfig에서 관리)
      // 다만, 로그인 프로세스에서 사용된 특정 창들을 정리할 수는 있습니다.
      // 하지만 현재 로직에서는 주 창(originalWindowHandle)을 계속 사용해야 하므로 섣불리 닫으면 안 됩니다.
      log.debug("로그인 시도 완료. 원래 창 핸들: {}", originalWindowHandle);
      if (driver != null && originalWindowHandle != null && driver.getWindowHandles().contains(originalWindowHandle)) {
        // 다른 작업이 없다면 WebDriver는 @PreDestroy에서 닫힙니다.
        // 특정 팝업만 닫고 싶다면 여기서 처리 가능.
        // 현재는 특별히 할 작업 없음.
      }
    }
  }

  private String getCurrentUrlSafe() {
    try {
      // 현재 활성화된 창의 URL을 가져오려고 시도
      if (driver != null && driver.getWindowHandles() != null && !driver.getWindowHandles().isEmpty()) {
        // 현재 focus된 창의 URL을 가져오는 것이 일반적
        return driver.getCurrentUrl();
      }
      return "N/A - WebDriver 또는 WindowHandles 없음";
    } catch (Exception e) {
      log.warn("getCurrentUrlSafe() 호출 중 오류: {}", e.getMessage());
      return "N/A - URL 가져오기 중 오류 발생 (" + e.getClass().getSimpleName() + ")";
    }
  }
}