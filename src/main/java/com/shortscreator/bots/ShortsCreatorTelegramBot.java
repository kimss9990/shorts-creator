package com.shortscreator.bots;

import com.shortscreator.model.VideoCreationContent;
import com.shortscreator.service.InVideoAutomationService;
import com.shortscreator.service.OpenAIService;
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
public class ShortsCreatorTelegramBot extends
    TelegramLongPollingBot {

  private final OpenAIService openAIService;
  private final InVideoAutomationService inVideoAutomationService;
  private final String botUsername;
  private static final String CALLBACK_CREATE_VIDEO_PREFIX = "create_video_";

  public ShortsCreatorTelegramBot(
      @Value("${telegram.bot.username}") String botUsername,
      @Value("${telegram.bot.token}") String botToken,
      OpenAIService openAIService, InVideoAutomationService inVideoAutomationService) {
    super(botToken);
    this.botUsername = botUsername;
    this.openAIService = openAIService;
    this.inVideoAutomationService = inVideoAutomationService;
    log.info(
        "ShortsCreatorTelegramBot 초기화 완료. Username: {}",
        this.botUsername);
  }

  // 생성된 콘텐츠 임시 저장용 (간단한 예시, 실제로는 DB나 더 나은 저장소 고려)
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
      // 일반 텍스트 메시지 처리 (기존 로직)
      Message receivedMessage = update.getMessage();
      String messageText = receivedMessage.getText().trim();
      long chatId = receivedMessage.getChatId();
      log.info("Telegram으로부터 텍스트 메시지 수신 (Chat ID: {}): {}", chatId, messageText);

      if ("/generate_tip".equalsIgnoreCase(messageText)) {
        handleGenerateTipCommand(chatId);
      } else if (messageText.toLowerCase().startsWith("/create_video ")) { // 수동 ID 입력 방식 (유지 또는 삭제)
        String taskId = messageText.substring("/create_video ".length()).trim();
        handleCreateVideoCommand(chatId, taskId);
      } else {
        sendTelegramMessage(chatId, "알 수 없는 명령어입니다. '/generate_tip' 명령을 사용해보세요.", false);
      }
    } else if (update.hasCallbackQuery()) {
      // 인라인 키보드 버튼 클릭(CallbackQuery) 처리
      CallbackQuery callbackQuery = update.getCallbackQuery();
      String callbackData = callbackQuery.getData(); // 버튼에 설정했던 callback_data
      long chatId = callbackQuery.getMessage().getChatId(); // 메시지가 원래 보내졌던 채팅 ID
      // int messageId = callbackQuery.getMessage().getMessageId(); // 원본 메시지 ID (버튼 수정/삭제 시 사용)

      log.info("Telegram으로부터 콜백 데이터 수신 (Chat ID: {}): {}", chatId, callbackData);

      // 사용자에게 버튼 클릭에 대한 즉각적인 피드백 (로딩 표시 방지)
      AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
          .callbackQueryId(callbackQuery.getId())
          .text("요청 처리 중...") // 사용자에게 잠깐 보이는 팝업 텍스트
          .build();
      try {
        execute(answer);
      } catch (TelegramApiException e) {
        log.error("AnswerCallbackQuery 전송 실패: {}", e.getMessage());
      }

      // 콜백 데이터 파싱 및 해당 작업 실행
      if (callbackData.startsWith(CALLBACK_CREATE_VIDEO_PREFIX)) {
        String taskId = callbackData.substring(CALLBACK_CREATE_VIDEO_PREFIX.length());
        handleCreateVideoCommand(chatId, taskId); // 기존 영상 생성 시작 로직 호출
      } else {
        log.warn("알 수 없는 콜백 데이터 수신: {}", callbackData);
        sendTelegramMessage(chatId, "알 수 없는 버튼 액션입니다.", false);
      }
    }
  }

  private void handleGenerateTipCommand(long chatId) {
    sendTelegramMessage(chatId, "콘텐츠 생성 요청을 받았습니다. OpenAI로부터 팁과 프롬프트를 생성 중입니다... 🧘", false);
    CompletableFuture<VideoCreationContent> futureContent = openAIService.generateVideoContentAndPrompt();

    futureContent.thenAcceptAsync(videoContent -> {
      if (videoContent != null && videoContent.getInvideoPrompt() != null && !videoContent.getInvideoPrompt()
          .startsWith("Error:")) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        pendingVideoTasks.put(taskId, videoContent); // 생성된 콘텐츠와 taskId 임시 저장

        log.info("OpenAI 콘텐츠 생성 완료 (Chat ID: {}, Task ID: {})", chatId, taskId);
        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("🎉 콘텐츠 생성이 완료되었습니다\\! \\(Task ID: `").append(taskId).append("`\\)\n\n");

        responseBuilder.append("*✨ 일일 팁 제목 ✨*\n");
        responseBuilder.append(escapeMarkdownV2(videoContent.getDailyTipTitle())).append("\n\n"); // 제목 이스케이프

        responseBuilder.append("*📝 일일 팁 스크립트 📝*\n");
        responseBuilder.append("```\n").append(videoContent.getDailyTipScript()).append("\n```\n\n"); // 코드 블록 (이스케이프 불필요)

        responseBuilder.append("*🎬 InVideo AI용 프롬프트 🎬*\n");
        responseBuilder.append("```\n").append(videoContent.getInvideoPrompt()).append("\n```\n\n"); // 코드 블록 (이스케이프 불필요)

        responseBuilder.append("*📄 YouTube Short 설명 📄*\n");
        responseBuilder.append("```\n").append(videoContent.getYoutubeShortDescription()).append("\n```"); // 코드 블록 (이스케이프 불필요)

        // --- 인라인 키보드 버튼 생성 및 추가 ---
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton createVideoButton = new InlineKeyboardButton();
        createVideoButton.setText("🎬 이 내용으로 영상 만들기"); // 버튼에 표시될 텍스트
        // 버튼 클릭 시 봇에게 전달될 데이터: "create_video_" 접두사 + 실제 taskId
        createVideoButton.setCallbackData(CALLBACK_CREATE_VIDEO_PREFIX + taskId);

        // 버튼을 한 줄에 하나씩 배치 (List<InlineKeyboardButton>이 한 줄을 의미)
        List<InlineKeyboardButton> rowInline = Collections.singletonList(createVideoButton);
        // 여러 줄의 버튼을 만들려면 List<List<InlineKeyboardButton>>에 여러 rowInline 리스트를 추가
        List<List<InlineKeyboardButton>> rowsInline = Collections.singletonList(rowInline);

        inlineKeyboardMarkup.setKeyboard(rowsInline); // 완성된 버튼 배열을 키보드 마크업에 설정

        // 수정된 sendTelegramMessageWithKeyboard 메소드 호출
        sendTelegramMessageWithKeyboard(chatId, responseBuilder.toString(), inlineKeyboardMarkup);

      } else {
        // ... (기존 OpenAI 생성 실패 시 오류 처리) ...
        log.error("OpenAI 콘텐츠 생성 실패 또는 유효하지 않은 결과 (Chat ID: {})", chatId);
        String errorMessage = "콘텐츠 생성에 실패했습니다. ";
        if (videoContent != null && videoContent.getInvideoPrompt() != null) {
          errorMessage += "오류: " + videoContent.getInvideoPrompt();
        } else {
          errorMessage += "OpenAI 서비스에서 문제가 발생한 것 같습니다. 잠시 후 다시 시도해주세요.";
        }
        sendTelegramMessage(chatId, errorMessage, false);
      }
    }).exceptionally(ex -> {
      // ... (기존 비동기 작업 예외 처리) ...
      log.error("OpenAI 콘텐츠 생성 중 예외 발생 (Chat ID: {}): {}", chatId, ex.getMessage(), ex);
      sendTelegramMessage(chatId, "콘텐츠 생성 중 오류가 발생했습니다: " + escapeMarkdownV2(ex.getMessage()), true);
      return null;
    });
  }

  private void handleCreateVideoCommand(long chatId, String taskId) {
    VideoCreationContent taskContent = pendingVideoTasks.get(taskId);
    if (taskContent == null) {
      sendTelegramMessage(chatId, "잘못된 작업 ID이거나 해당 작업 내용을 찾을 수 없습니다. '/generate_tip' 명령으로 먼저 콘텐츠를 생성해주세요.", false);
      return;
    }

    if (invideoGmailUsername == null || invideoGmailUsername.isEmpty() || invideoGmailPassword == null
        || invideoGmailPassword.isEmpty()) {
      sendTelegramMessage(chatId, "InVideo AI 로그인 정보(사용자명/비밀번호)가 설정되지 않았습니다. 관리자에게 문의하세요.", false);
      return;
    }

    sendTelegramMessage(chatId,
        "영상 생성 요청(Task ID: `" + taskId + "`)을 받았습니다. InVideo AI 작업을 시작합니다. 브라우저가 실행될 수 있습니다... 🎬", true);

    // 비동기적으로 InVideo AI 작업 실행
    inVideoAutomationService.createVideoInInVideoAI(invideoGmailUsername, invideoGmailPassword,
            taskContent.getInvideoPrompt())
        .thenAcceptAsync(success -> {
          if (success) {
            sendTelegramMessage(chatId, "InVideo AI 영상 생성 작업이 시작되었거나 성공적으로 초기 단계가 완료되었습니다. (Task ID: `" + taskId
                + "`) 실제 영상 생성에는 시간이 걸릴 수 있습니다.", true);
            pendingVideoTasks.remove(taskId); // 작업 시작 후 (또는 성공 후) 임시 저장소에서 제거

            // --- 모든 주요 작업(영상 생성 및 업로드)이 성공했을 때만 히스토리 기록 ---
            // 예시: boolean allStepsSuccess = ... (InVideo 생성 완료 확인 및 YouTube 업로드 결과)
            // if (allStepsSuccess) {
            //    log.info("영상 생성 및 YouTube 업로드 성공. 팁 제목을 히스토리에 추가합니다: {}", taskContent.getDailyTipTitle());
            //    recentTipsHistoryService.addTipTitle(taskContent.getDailyTipTitle()); // <--- 최종 성공 시 여기에 위치!
            // }

          } else {
            sendTelegramMessage(chatId, "InVideo AI 영상 생성 작업 시작에 실패했습니다. (Task ID: `" + taskId + "`) 로그를 확인해주세요.",
                true);
          }
        }).exceptionally(ex -> {
          log.error("InVideo AI 영상 생성 작업 중 예외 발생 (Chat ID: {}, Task ID: {}): {}", chatId, taskId, ex.getMessage(), ex);
          sendTelegramMessage(chatId, "InVideo AI 영상 생성 작업 중 오류가 발생했습니다. (Task ID: `" + taskId + "`)", true);
          return null;
        });
  }

  private String escapeMarkdownV2(String text) {
    if (text == null) {
      return "";
    }
    // MarkdownV2에서 이스케이프해야 하는 문자들 (Telegram Bot API 문서 참조)
    // 순서가 중요할 수 있으므로, 백슬래시를 먼저 처리하지 않도록 주의
    // (하지만 각 문자를 독립적으로 replace하는 경우 순서는 덜 중요)
    // . 과 ! 는 문맥에 따라 다를 수 있으나, 안전하게 모두 이스케이프
    return text
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
    message.setChatId(String.valueOf(chatId)); // Chat ID를 String으로 설정하는 것이 더 안정적일 수 있음
    message.setText(text);
    if (enableMarkdown) {
      message.setParseMode(ParseMode.MARKDOWNV2); // org.telegram.telegrambots.meta.api.methods.ParseMode 사용
    }

    try {
      execute(message);
      log.info("응답 메시지 전송 완료 (Chat ID: {})", chatId);
    } catch (TelegramApiException e) {
      log.error("Telegram 메시지 전송 중 오류 발생 (Chat ID: {}): {}", chatId, e.getMessage(), e);
      // Markdown 파싱 오류 시, Markdown 없이 일반 텍스트로 재시도하는 로직 추가 가능
      if (e.getMessage() != null && e.getMessage().contains("can't parse entities")) {
        log.warn("Markdown 파싱 오류로 인해 일반 텍스트로 재전송 시도 (Chat ID: {})", chatId);
        SendMessage fallbackMessage = new SendMessage();
        fallbackMessage.setChatId(String.valueOf(chatId));
        // 매우 긴급한 경우: 특수문자 제거 또는 매우 단순화된 텍스트
        fallbackMessage.setText("메시지 포맷팅 중 오류가 발생하여 원본 텍스트의 일부를 보여드립니다. (특수문자 문제 가능성)\n" + text.replaceAll(
            "[\\*\\_\\`\\!\\[\\]\\(\\)\\~\\>\\#\\+\\-\\=\\|\\{\\}\\.]", ""));
        try {
          execute(fallbackMessage);
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
    message.setParseMode(ParseMode.MARKDOWNV2); // Markdown 사용
    message.setReplyMarkup(keyboardMarkup);     // 인라인 키보드 설정

    try {
      execute(message);
      log.info("인라인 키보드와 함께 응답 메시지 전송 완료 (Chat ID: {})", chatId);
    } catch (TelegramApiException e) {
      log.error("Telegram 메시지(키보드 포함) 전송 중 오류 발생 (Chat ID: {}): {}", chatId, e.getMessage(), e);
      // Markdown 파싱 오류 시 대체 로직
      if (e.getMessage() != null && e.getMessage().contains("can't parse entities")) {
        sendTelegramMessage(chatId, "메시지 포맷팅 중 오류가 발생하여 일부 내용이 제대로 표시되지 않을 수 있습니다. (이스케이프 필요)\n" +
            "Task ID: " + extractTaskIdFromFailedMessage(text) + "\n" + // 실패 메시지에서 Task ID라도 추출 시도
            "다음 명령으로 수동 실행: /create_video [Task ID]", false);
      }
    }
  }

  private String extractTaskIdFromFailedMessage(String text) {
    Pattern pattern = Pattern.compile("Task ID: `([a-zA-Z0-9-]+)`");
    Matcher matcher = pattern.matcher(text);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return "불명확함";
  }

  // 스크립트 포맷팅 헬퍼 (줄바꿈을 실제 줄바꿈으로)
  private String formatScript(String script) {
    if (script == null) {
      return "N/A";
    }
    // OpenAI 응답의 \n 을 실제 줄바꿈으로 변경할 필요는 없음. Telegram이 알아서 처리.
    // 다만, 너무 길면 잘릴 수 있으니 요약 또는 부분 표시도 고려.
    return script;
  }

  // Telegram 메시지 전송 헬퍼 메소드
  private void sendTelegramMessage(long chatId,
      String text) {
    sendTelegramMessage(chatId, text, false);
  }
}