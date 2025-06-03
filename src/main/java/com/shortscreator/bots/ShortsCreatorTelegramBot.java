package com.shortscreator.bots;

import com.shortscreator.model.VideoCreationContent;
import com.shortscreator.service.InVideoAutomationService;
import com.shortscreator.service.OpenAIService;
import com.shortscreator.service.YouTubeService;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
public class ShortsCreatorTelegramBot extends TelegramLongPollingBot {

  private final OpenAIService openAIService;
  private final InVideoAutomationService inVideoAutomationService;
  private final YouTubeService youTubeService;
  private final String botUsername;
  private static final String CALLBACK_CREATE_VIDEO_PREFIX = "create_video_";

  public ShortsCreatorTelegramBot(
      @Value("${telegram.bot.username}") String botUsername,
      @Value("${telegram.bot.token}") String botToken,
      OpenAIService openAIService,
      InVideoAutomationService inVideoAutomationService,
      YouTubeService youTubeService) {
    super(botToken);
    this.botUsername = botUsername;
    this.openAIService = openAIService;
    this.inVideoAutomationService = inVideoAutomationService;
    this.youTubeService = youTubeService;
    log.info("ShortsCreatorTelegramBot 초기화 완료. Username: {}", this.botUsername);
  }

  // 생성된 콘텐츠 임시 저장용
  private final Map<String, VideoCreationContent> pendingVideoTasks = new HashMap<>();

  // Gmail 계정 정보 (application.yml에서 주입)
  @Value("${invideo.account.username}")
  private String invideoGmailUsername;
  @Value("${invideo.account.password}")
  private String invideoGmailPassword;

  @Override
  public String getBotUsername() {
    return this.botUsername;
  }

  @Override
  public void onUpdateReceived(Update update) {
    if (update.hasMessage() && update.getMessage().hasText()) {
      // 일반 텍스트 메시지 처리
      Message receivedMessage = update.getMessage();
      String messageText = receivedMessage.getText().trim();
      long chatId = receivedMessage.getChatId();
      log.info("Telegram으로부터 텍스트 메시지 수신 (Chat ID: {}): {}", chatId, messageText);

      if ("/generate_tip".equalsIgnoreCase(messageText)) {
        handleGenerateTipCommand(chatId);
      } else if ("/youtube_auth".equalsIgnoreCase(messageText)) {
        handleYouTubeAuthCommand(chatId);
      } else if (messageText.toLowerCase().startsWith("/create_video ")) {
        String taskId = messageText.substring("/create_video ".length()).trim();
        handleCreateVideoCommand(chatId, taskId);
      } else if ("/help".equalsIgnoreCase(messageText) || "/start".equalsIgnoreCase(messageText)) {
        handleHelpCommand(chatId);
      } else {
        sendTelegramMessage(chatId,
            "알 수 없는 명령어입니다\\. 사용 가능한 명령어:\n\n" +
                "• `/generate_tip` \\- 새 팁 콘텐츠 생성\n" +
                "• `/youtube_auth` \\- YouTube 인증 상태 확인\n" +
                "• `/help` \\- 도움말 보기", true);
      }
    } else if (update.hasCallbackQuery()) {
      // 인라인 키보드 버튼 클릭(CallbackQuery) 처리
      CallbackQuery callbackQuery = update.getCallbackQuery();
      String callbackData = callbackQuery.getData();
      long chatId = callbackQuery.getMessage().getChatId();

      log.info("Telegram으로부터 콜백 데이터 수신 (Chat ID: {}): {}", chatId, callbackData);

      // 사용자에게 버튼 클릭에 대한 즉각적인 피드백
      AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
          .callbackQueryId(callbackQuery.getId())
          .text("요청 처리 중...")
          .build();
      try {
        execute(answer);
      } catch (TelegramApiException e) {
        log.error("AnswerCallbackQuery 전송 실패: {}", e.getMessage());
      }

      // 콜백 데이터 파싱 및 해당 작업 실행
      if (callbackData.startsWith(CALLBACK_CREATE_VIDEO_PREFIX)) {
        String taskId = callbackData.substring(CALLBACK_CREATE_VIDEO_PREFIX.length());
        handleCreateVideoCommand(chatId, taskId);
      } else {
        log.warn("알 수 없는 콜백 데이터 수신: {}", callbackData);
        sendTelegramMessage(chatId, "알 수 없는 버튼 액션입니다.", false);
      }
    }
  }

  private void handleHelpCommand(long chatId) {
    String helpMessage = "🤖 *YouTube Shorts Creator Bot*\n\n" +
        "*사용 가능한 명령어:*\n\n" +
        "📝 `/generate_tip` \\- AI로 새로운 팁 콘텐츠를 생성합니다\n" +
        "🔐 `/youtube_auth` \\- YouTube 업로드 인증 상태를 확인합니다\n" +
        "❓ `/help` \\- 이 도움말을 보여줍니다\n\n" +
        "*사용 방법:*\n" +
        "1\\. `/generate_tip` 명령으로 콘텐츠 생성\n" +
        "2\\. 생성된 콘텐츠 확인 후 '영상 만들기' 버튼 클릭\n" +
        "3\\. 자동으로 InVideo AI에서 영상 제작\n" +
        "4\\. YouTube에 자동 업로드\n\n" +
        "*주의사항:*\n" +
        "• 처음 사용 시 YouTube 인증이 필요합니다\n" +
        "• 영상 생성에는 5\\-10분 정도 소요됩니다\n" +
        "• 생성된 영상은 기본적으로 비공개로 업로드됩니다";

    sendTelegramMessage(chatId, helpMessage, true);
  }

  private void handleGenerateTipCommand(long chatId) {
    sendTelegramMessage(chatId, "콘텐츠 생성 요청을 받았습니다\\. OpenAI로부터 팁과 프롬프트를 생성 중입니다\\.\\.\\. 🧘", true);
    CompletableFuture<VideoCreationContent> futureContent = openAIService.generateVideoContentAndPrompt();

    futureContent.thenAcceptAsync(videoContent -> {
      if (videoContent != null && videoContent.getInvideoPrompt() != null && !videoContent.getInvideoPrompt()
          .startsWith("Error:")) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        pendingVideoTasks.put(taskId, videoContent);

        log.info("OpenAI 콘텐츠 생성 완료 (Chat ID: {}, Task ID: {})", chatId, taskId);
        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("🎉 콘텐츠 생성이 완료되었습니다\\! \\(Task ID: `").append(taskId).append("`\\)\n\n");

        responseBuilder.append("*✨ 일일 팁 제목 ✨*\n");
        responseBuilder.append(escapeMarkdownV2(videoContent.getDailyTipTitle())).append("\n\n");

        responseBuilder.append("*📝 일일 팁 스크립트 📝*\n");
        responseBuilder.append("```\n").append(videoContent.getDailyTipScript()).append("\n```\n\n");

        responseBuilder.append("*🎬 InVideo AI용 프롬프트 🎬*\n");
        responseBuilder.append("```\n").append(videoContent.getInvideoPrompt()).append("\n```\n\n");

        responseBuilder.append("*📄 YouTube Short 설명 📄*\n");
        responseBuilder.append("```\n").append(videoContent.getYoutubeShortDescription()).append("\n```");

        // 인라인 키보드 버튼 생성
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton createVideoButton = new InlineKeyboardButton();
        createVideoButton.setText("🎬 이 내용으로 영상 만들기");
        createVideoButton.setCallbackData(CALLBACK_CREATE_VIDEO_PREFIX + taskId);

        List<InlineKeyboardButton> rowInline = Collections.singletonList(createVideoButton);
        List<List<InlineKeyboardButton>> rowsInline = Collections.singletonList(rowInline);

        inlineKeyboardMarkup.setKeyboard(rowsInline);

        sendTelegramMessageWithKeyboard(chatId, responseBuilder.toString(), inlineKeyboardMarkup);

      } else {
        log.error("OpenAI 콘텐츠 생성 실패 또는 유효하지 않은 결과 (Chat ID: {})", chatId);
        String errorMessage = "콘텐츠 생성에 실패했습니다\\. ";
        if (videoContent != null && videoContent.getInvideoPrompt() != null) {
          errorMessage += "오류: " + escapeMarkdownV2(videoContent.getInvideoPrompt());
        } else {
          errorMessage += "OpenAI 서비스에서 문제가 발생한 것 같습니다\\. 잠시 후 다시 시도해주세요\\.";
        }
        sendTelegramMessage(chatId, errorMessage, true);
      }
    }).exceptionally(ex -> {
      log.error("OpenAI 콘텐츠 생성 중 예외 발생 (Chat ID: {}): {}", chatId, ex.getMessage(), ex);
      sendTelegramMessage(chatId, "콘텐츠 생성 중 오류가 발생했습니다: " + escapeMarkdownV2(ex.getMessage()), true);
      return null;
    });
  }

  private void handleYouTubeAuthCommand(long chatId) {
    try {
      log.info("YouTube 인증 상태 확인 요청 (Chat ID: {})", chatId);
      String authStatus = youTubeService.getAuthenticationStatus();

      String statusMessage = "🔐 *YouTube OAuth 2\\.0 인증 상태*\n\n" +
          escapeMarkdownV2(authStatus) + "\n\n";

      if (authStatus.contains("✅")) {
        statusMessage += "✅ 인증 완료\\! YouTube 업로드가 가능합니다\\.";
      } else {
        statusMessage += "❌ 인증 필요\\!\n\n" +
            "💡 *인증 방법:*\n" +
            "1\\. 브라우저에서 접속: `http://localhost:8080/api/youtube/oauth/initiate`\n" +
            "2\\. Google 계정으로 로그인\n" +
            "3\\. YouTube 업로드 권한 승인\n\n" +
            "인증 완료 후 다시 `/youtube_auth` 명령으로 확인하세요\\.";
      }

      sendTelegramMessage(chatId, statusMessage, true);

    } catch (Exception e) {
      log.error("YouTube 인증 상태 확인 중 오류 (Chat ID: {}): {}", chatId, e.getMessage(), e);
      sendTelegramMessage(chatId,
          "YouTube 인증 상태 확인 중 오류가 발생했습니다: " + escapeMarkdownV2(e.getMessage()), true);
    }
  }

  private void handleCreateVideoCommand(long chatId, String taskId) {
    VideoCreationContent taskContent = pendingVideoTasks.get(taskId);
    if (taskContent == null) {
      sendTelegramMessage(chatId,
          "잘못된 작업 ID이거나 해당 작업 내용을 찾을 수 없습니다\\. `/generate_tip` 명령으로 먼저 콘텐츠를 생성해주세요\\.", true);
      return;
    }

    if (invideoGmailUsername == null || invideoGmailUsername.isEmpty() ||
        invideoGmailPassword == null || invideoGmailPassword.isEmpty()) {
      sendTelegramMessage(chatId,
          "InVideo AI 로그인 정보\\(사용자명/비밀번호\\)가 설정되지 않았습니다\\. 관리자에게 문의하세요\\.", true);
      return;
    }

    // YouTube OAuth 2.0 인증 상태 확인
    try {
      String authStatus = youTubeService.getAuthenticationStatus();
      if (!authStatus.contains("✅")) {
        String oauthWarning = "⚠️ *YouTube OAuth 2\\.0 인증이 필요합니다*\n\n" +
            "현재 상태: " + escapeMarkdownV2(authStatus) + "\n\n" +
            "💡 *해결방법:*\n" +
            "1\\. 브라우저에서 `http://localhost:8080/api/youtube/oauth/status` 접속\n" +
            "2\\. 인증 상태 확인 후 필요시 `/initiate` 엔드포인트 호출\n" +
            "3\\. Google 계정으로 YouTube 업로드 권한 승인\n\n" +
            "*영상 생성은 계속 진행되지만, YouTube 업로드는 인증 후 가능합니다*\\.";

        sendTelegramMessage(chatId, oauthWarning, true);
      }
    } catch (Exception e) {
      log.warn("YouTube 인증 상태 확인 중 오류 (Chat ID: {}): {}", chatId, e.getMessage());
    }

    sendTelegramMessage(chatId,
        "영상 생성 요청\\(Task ID: `" + taskId + "`\\)을 받았습니다\\. InVideo AI 작업을 시작합니다\\. 브라우저가 실행될 수 있습니다\\.\\.\\. 🎬", true);

    // 영상 제목과 설명을 별도로 전달
    inVideoAutomationService.createVideoInInVideoAI(
            invideoGmailUsername,
            invideoGmailPassword,
            taskContent.getInvideoPrompt(),
            taskContent.getDailyTipTitle(),  // 영상 제목
            taskContent.getYoutubeShortDescription()  // 영상 설명
        )
        .thenAcceptAsync(resultMessage -> {
          if (resultMessage.startsWith("✅")) {
            String finalMessage = resultMessage + "\n\n\\(Task ID: `" + taskId + "`\\)";

            // YouTube 업로드 성공 여부에 따른 추가 안내
            if (resultMessage.contains("YouTube 업로드 실패")) {
              finalMessage += "\n\n💡 *YouTube 업로드 문제 해결:*\n" +
                  "• 브라우저에서 `http://localhost:8080/api/youtube/oauth/status` 접속\n" +
                  "• OAuth 2\\.0 인증 상태 확인 및 재인증\n" +
                  "• `/youtube_auth` 명령으로 상태 재확인";
            } else if (resultMessage.contains("YouTube Shorts 업로드 완료")) {
              finalMessage += "\n\n🎉 *업로드 완료\\!*\n" +
                  "YouTube Studio에서 업로드된 영상을 확인하세요\\.\n" +
                  "초기에는 비공개 상태로 업로드됩니다\\.";
            }

            sendTelegramMessage(chatId, escapeMarkdownV2(finalMessage), true);
            pendingVideoTasks.remove(taskId); // 작업 완료 후 제거

            // 성공적인 경우에만 히스토리에 추가 (필요시 활성화)
            if (resultMessage.contains("YouTube Shorts 업로드 완료")) {
              log.info("YouTube 업로드 성공. 팁 제목: {}", taskContent.getDailyTipTitle());
              // recentTipsHistoryService.addTipTitle(taskContent.getDailyTipTitle());
            }

          } else {
            String finalMessage = resultMessage + "\n\n\\(Task ID: `" + taskId + "`\\)";

            // 오류 발생 시 추가 도움말 제공
            if (resultMessage.contains("로그인 실패")) {
              finalMessage += "\n\n💡 *InVideo 로그인 문제:*\n" +
                  "• Gmail 계정 정보를 확인하세요\n" +
                  "• 2단계 인증이 활성화되어 있는지 확인하세요\n" +
                  "• 네트워크 연결을 확인하세요";
            }

            sendTelegramMessage(chatId, escapeMarkdownV2(finalMessage), true);
          }
        }).exceptionally(ex -> {
          log.error("InVideo AI 영상 생성 작업 중 예외 발생 (Chat ID: {}, Task ID: {}): {}",
              chatId, taskId, ex.getMessage(), ex);
          String errorMessage = "InVideo AI 영상 생성 작업 중 오류가 발생했습니다\\.\n\n" +
              "오류 내용: " + escapeMarkdownV2(ex.getMessage()) + "\n\n" +
              "\\(Task ID: `" + taskId + "`\\)";
          sendTelegramMessage(chatId, errorMessage, true);
          return null;
        });
  }

  private String escapeMarkdownV2(String text) {
    if (text == null) {
      return "";
    }

    // MarkdownV2에서 이스케이프해야 하는 모든 특수 문자들
    return text
        .replace("\\", "\\\\")  // 백슬래시를 먼저 처리
        .replace("_", "\\_")
        .replace("*", "\\*")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace("~", "\\~")
        .replace("`", "\\`")
        .replace(">", "\\>")
        .replace("#", "\\#")
        .replace("+", "\\+")
        .replace("-", "\\-")
        .replace("=", "\\=")
        .replace("|", "\\|")
        .replace("{", "\\{")
        .replace("}", "\\}")
        .replace(".", "\\.")
        .replace("!", "\\!");
  }

  private void sendTelegramMessage(long chatId, String text, boolean enableMarkdown) {
    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText(text);
    if (enableMarkdown) {
      message.setParseMode(ParseMode.MARKDOWNV2);
    }

    try {
      execute(message);
      log.info("응답 메시지 전송 완료 (Chat ID: {})", chatId);
    } catch (TelegramApiException e) {
      log.error("Telegram 메시지 전송 중 오류 발생 (Chat ID: {}): {}", chatId, e.getMessage(), e);

      // Markdown 파싱 오류 시 일반 텍스트로 재시도
      if (enableMarkdown && e.getMessage() != null && e.getMessage().contains("can't parse entities")) {
        log.warn("MarkdownV2 파싱 오류로 인해 일반 텍스트로 재전송 시도 (Chat ID: {})", chatId);

        SendMessage fallbackMessage = new SendMessage();
        fallbackMessage.setChatId(String.valueOf(chatId));
        // 특수문자를 제거하고 간단한 텍스트로 변환
        String cleanText = text
            .replaceAll("\\\\[\\*_\\[\\]\\(\\)~`>#+=|{}.!-]", "") // 이스케이프된 특수문자 제거
            .replaceAll("[\\*_\\[\\]\\(\\)~`>#+=|{}.!-]", "");    // 남은 특수문자 제거
        fallbackMessage.setText("⚠️ 메시지 포맷팅 오류가 발생하여 단순 텍스트로 전송:\n\n" + cleanText);

        try {
          execute(fallbackMessage);
          log.info("일반 텍스트 재전송 성공 (Chat ID: {})", chatId);
        } catch (TelegramApiException exFallback) {
          log.error("일반 텍스트 재전송도 실패 (Chat ID: {}): {}", chatId, exFallback.getMessage());
        }
      }
    }
  }

  private void sendTelegramMessageWithKeyboard(long chatId, String text, InlineKeyboardMarkup keyboardMarkup) {
    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText(text);
    message.setParseMode(ParseMode.MARKDOWNV2);
    message.setReplyMarkup(keyboardMarkup);

    try {
      execute(message);
      log.info("인라인 키보드와 함께 응답 메시지 전송 완료 (Chat ID: {})", chatId);
    } catch (TelegramApiException e) {
      log.error("Telegram 메시지(키보드 포함) 전송 중 오류 발생 (Chat ID: {}): {}", chatId, e.getMessage(), e);

      // Markdown 파싱 오류 시 대체 로직
      if (e.getMessage() != null && e.getMessage().contains("can't parse entities")) {
        String taskId = extractTaskIdFromFailedMessage(text);
        String fallbackText = "⚠️ 메시지 포맷팅 중 오류가 발생했습니다.\n\n" +
            "Task ID: " + taskId + "\n" +
            "다음 명령으로 수동 실행: /create_video " + taskId;
        sendTelegramMessage(chatId, fallbackText, false);
      }
    }
  }

  private String extractTaskIdFromFailedMessage(String text) {
    try {
      Pattern pattern = Pattern.compile("Task ID: `([a-zA-Z0-9-]+)`");
      Matcher matcher = pattern.matcher(text);
      if (matcher.find()) {
        return matcher.group(1);
      }
    } catch (Exception e) {
      log.warn("Task ID 추출 중 오류: {}", e.getMessage());
    }
    return "불명확함";
  }
}