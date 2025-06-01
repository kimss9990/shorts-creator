package com.shortscreator.config;

import com.shortscreator.bots.ShortsCreatorTelegramBot;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotInitializer {

  private final ShortsCreatorTelegramBot shortsCreatorTelegramBot;

  // @PostConstruct // 또는 @EventListener(ContextRefreshedEvent.class) 사용 가능
  // public void init() throws TelegramApiException {
  //    log.info("BotInitializer: 봇 등록 시도...");
  //    TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
  //    try {
  //        botsApi.registerBot(shortsCreatorTelegramBot);
  //        log.info("Telegram 봇이 성공적으로 등록되었습니다. 봇 이름: {}", shortsCreatorTelegramBot.getBotUsername());
  //    } catch (TelegramApiException e) {
  //        log.error("Telegram 봇 등록 중 오류 발생: {}", e.getMessage(), e);
  //    }
  // }

  // ApplicationReadyEvent는 모든 빈이 초기화되고 애플리케이션이 완전히 실행 준비가 되었을 때 발생
  @EventListener(ContextRefreshedEvent.class) // ContextRefreshedEvent를 사용하면 모든 빈이 초기화 된 후 실행됨
  public void onApplicationEvent(ContextRefreshedEvent event) {
    log.info("BotInitializer (ContextRefreshedEvent): 봇 등록 시도...");
    try {
      TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
      botsApi.registerBot(shortsCreatorTelegramBot);
      log.info("Telegram 봇이 성공적으로 등록되었습니다. 봇 이름: {}", shortsCreatorTelegramBot.getBotUsername());
    } catch (TelegramApiException e) {
      log.error("Telegram 봇 등록 중 오류 발생: {}", e.getMessage(), e);
    }
  }
}