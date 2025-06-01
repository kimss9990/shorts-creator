package com.shortscreator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortscreator.config.ApiConfig;
import com.shortscreator.model.VideoCreationContent;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIService {

  private final ApiConfig apiConfig;
  private final ResourceLoader resourceLoader;
  private final ObjectMapper objectMapper;
  private final RecentTipsHistoryService recentTipsHistoryService;
  private String masterPromptText; // 원본 마스터 프롬프트 템플릿

  @Value("${openai.master_prompt.filepath:classpath:prompts/master_prompt.txt}")
  private String masterPromptFilePath;

  // 마스터 프롬프트 내 이전 팁 목록을 삽입할 플레이스홀더
  private static final String PREVIOUS_TIPS_PLACEHOLDER = "[INSERT_PREVIOUS_TIPS_HERE]";

  @Value("${openai.api.timeout_seconds:60}")
  private long openaiApiTimeoutSeconds;

  @PostConstruct
  public void init() {
    try {
      Resource resource = resourceLoader.getResource(masterPromptFilePath);
      try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
        masterPromptText = FileCopyUtils.copyToString(reader);
        log.info("Master prompt loaded successfully from: {}", masterPromptFilePath);
        if (!masterPromptText.contains(PREVIOUS_TIPS_PLACEHOLDER)) {
          log.warn(
              "Master prompt does not contain the placeholder '{}'. Previous tips will be prepended if this placeholder is missing.",
              PREVIOUS_TIPS_PLACEHOLDER);
        }
      }
    } catch (IOException e) {
      log.error("Failed to load master prompt from: {}", masterPromptFilePath, e);
      masterPromptText = "Error: Master prompt could not be loaded. Please check the file path and content.";
    }
  }

  @Async // 이 메소드는 비동기적으로 실행됩니다.
  public CompletableFuture<VideoCreationContent> generateVideoContentAndPrompt() {
    if (masterPromptText == null || masterPromptText.startsWith("Error:")) {
      log.error("Master prompt is not loaded correctly. Cannot proceed with OpenAI request.");
      return CompletableFuture.completedFuture(
          new VideoCreationContent("Error", "Master prompt issue.", "Error: Master prompt issue.",
              "Error: Master prompt issue.")
      );
    }

    // 1. 최근 팁 목록 가져오기
    List<String> titlesToAvoid = recentTipsHistoryService.getRecentTipTitles();
    String previousTipsFormattedString = "";

    if (!titlesToAvoid.isEmpty()) {
      previousTipsFormattedString =
          "IMPORTANT: Avoid generating tips that are substantively similar in topic or advice to the following recently generated tips. Focus on providing fresh, distinct advice each time.\nRecently generated tip titles (for your reference to avoid duplication):\n"
              +
              titlesToAvoid.stream().map(title -> "- " + title).collect(Collectors.joining("\n")) +
              "\n(If this list is empty or short, it means fewer tips were generated recently or they were not persisted.)\n";
      log.debug("Previous tips to avoid ({}): {}", titlesToAvoid.size(), titlesToAvoid);
    } else {
      log.debug("No previous tips to avoid.");
      // 플레이스홀더가 있다면, 이전 팁이 없을 경우 빈 문자열로 대체하거나,
      // "No previous tips generated yet." 같은 메시지로 대체할 수 있습니다.
      previousTipsFormattedString = "No previous tips have been generated recently. Feel free to generate any relevant tip.\n";
    }

    // 2. 마스터 프롬프트에 이전 팁 정보 삽입
    String currentFullPrompt;
    if (masterPromptText.contains(PREVIOUS_TIPS_PLACEHOLDER)) {
      currentFullPrompt = masterPromptText.replace(PREVIOUS_TIPS_PLACEHOLDER, previousTipsFormattedString);
      log.debug("Placeholder '{}' replaced with previous tips information.", PREVIOUS_TIPS_PLACEHOLDER);
    } else {
      // 플레이스홀더가 없다면, 프롬프트 시작 부분에 이전 팁 정보를 추가 (이전 방식)
      currentFullPrompt = previousTipsFormattedString + "\n" + masterPromptText;
      log.debug("No placeholder found. Previous tips information prepended to master prompt.");
    }
    // log.trace("Current full prompt for OpenAI:\n{}", currentFullPrompt); // 매우 긴 로그가 될 수 있으므로 TRACE 레벨

    // 3. OpenAI API 요청 준비
    String model = apiConfig.getOpenaiModel();
    log.info("비동기 작업 시작: OpenAI 모델 ({})을 사용하여 비디오 콘텐츠 및 프롬프트 생성 중...", model);
    OpenAiService service = new OpenAiService(apiConfig.getOpenaiApiKey(), Duration.ofSeconds(openaiApiTimeoutSeconds));

    List<ChatMessage> messages = new ArrayList<>();
    messages.add(new ChatMessage("user", currentFullPrompt)); // 최종적으로 구성된 프롬프트 사용

    ChatCompletionRequest request = ChatCompletionRequest.builder()
        .model(model) // 예: "gpt-4o", "gpt-4-turbo"
        .messages(messages)
        .temperature(0.8) // 창의성 조절 (0.0 ~ 2.0)
        .maxTokens(3500)  // 응답 최대 길이. JSON 응답이 길 수 있으므로 충분히 설정
        .build();

    // GPT-3.5 Turbo 등에서는 response_format을 지원하지 않을 수 있으므로, 조건부로 설정하거나
    // 프롬프트 내에서 "응답은 반드시 JSON 객체여야 합니다"라고 강력히 지시하는 것이 중요합니다.
    // 위에서는 일단 responseFormat("json_object")를 추가했습니다. 모델 호환성 확인 필요.

    // 4. OpenAI API 호출 및 응답 처리
    try {
      log.debug("OpenAI API에 요청 전송 중 (모델: {})...", model);
      ChatCompletionResult result = service.createChatCompletion(request);

      if (result != null && result.getChoices() != null && !result.getChoices().isEmpty()) {
        String rawResponse = result.getChoices().get(0).getMessage().getContent();
        log.info("OpenAI로부터 원시 응답 수신 (비동기).");
        log.debug("원시 OpenAI 응답 (JSON 예상):\n{}", rawResponse);

        VideoCreationContent parsedContent = parseOpenAIResponse(rawResponse);

        // 성공적으로 생성되고 파싱된 팁 제목을 히스토리에 추가
//        if (parsedContent != null && parsedContent.getDailyTipTitle() != null &&
//            !parsedContent.getDailyTipTitle().startsWith("Error") &&
//            !parsedContent.getDailyTipTitle().isBlank()) {
//          recentTipsHistoryService.addTipTitle(parsedContent.getDailyTipTitle());
//        }
        return CompletableFuture.completedFuture(parsedContent);
      } else {
        log.error("OpenAI로부터 응답이 없거나 비어있는 선택지를 수신했습니다 (비동기).");
        return CompletableFuture.completedFuture(
            new VideoCreationContent("Error", "No response or empty choices from OpenAI.",
                "Error: OpenAI communication issue.", "Error: OpenAI communication issue.")
        );
      }
    } catch (Exception e) {
      log.error("OpenAI 채팅 생성 중 예외 발생 (비동기): {}", e.getMessage(), e);
      // API 키 오류, 네트워크 문제, 요청 형식 오류 등 다양한 원인이 있을 수 있음
      return CompletableFuture.completedFuture(
          new VideoCreationContent("Error", "Exception during OpenAI API call: " + e.getClass().getSimpleName(),
              "Error: OpenAI API call failed.", "Error: OpenAI API call failed.")
      );
    }
  }

  private VideoCreationContent parseOpenAIResponse(String rawJsonResponse) {
    try {
      log.debug("ObjectMapper로 JSON 응답 파싱 시도..."); // rawJsonResponse 로깅은 이전 단계에서 하므로 여기선 생략 가능
      return objectMapper.readValue(rawJsonResponse, VideoCreationContent.class);
    } catch (JsonProcessingException e) {
      log.error("OpenAI JSON 응답 파싱 중 오류: {}. 원본 응답: {}", e.getMessage(), rawJsonResponse, e);
      // 파싱 실패 시 오류 정보를 포함한 객체 반환하여 원인 파악 용이하게
      return new VideoCreationContent(
          "Error: JSON Parsing Failed",
          "Original Response: " + rawJsonResponse, // 원본 응답을 스크립트 필드 등에 넣어 확인
          "Error: JSON Parsing Failed - Could not parse invideo_ai_prompt. Reason: " + e.getMessage(),
          "Error: JSON Parsing Failed - Could not parse youtube_short_description."
      );
    }
  }
}