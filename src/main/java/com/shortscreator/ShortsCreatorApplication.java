package com.shortscreator;

// import com.shortscreator.model.VideoCreationContent; // 필요시 유지 또는 제거
// import com.shortscreator.service.InVideoLoginService; // 필요시 유지 또는 제거
// import com.shortscreator.service.OpenAIService; // 필요시 유지 또는 제거
import lombok.extern.slf4j.Slf4j;
// import org.springframework.beans.factory.annotation.Autowired; // 필요시 유지 또는 제거
// import org.springframework.beans.factory.annotation.Value; // 필요시 유지 또는 제거
// import org.springframework.boot.CommandLineRunner; // 제거 또는 주석 처리
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync; // @EnableAsync 추가

@Slf4j
@SpringBootApplication
@EnableAsync // 비동기 처리를 위한 어노테이션 추가
public class ShortsCreatorApplication /* implements CommandLineRunner */ { // CommandLineRunner 제거 또는 주석 처리

    // CommandLineRunner 관련 필드 및 run 메소드는 제거하거나 주석 처리합니다.
    /*
    @Autowired(required = false)
    private InVideoLoginService inVideoLoginService;

    @Value("${invideo.account.username:#{null}}")
    private String invideoGmailUsername;

    @Value("${invideo.account.password:#{null}}")
    private String invideoGmailPassword;

    @Autowired
    private OpenAIService openAIService;

    @Autowired
    private ConfigurableApplicationContext context;
    */

    public static void main(String[] args) {
        SpringApplication.run(ShortsCreatorApplication.class, args);
        log.info("ShortsCreatorApplication 시작됨. Telegram 봇 리스닝 준비 중...");
    }

    /*
    @Override
    public void run(String... args) throws Exception {
        log.info("ShortsCreator 애플리케이션 시작 (CommandLineRunner)...");
        // 기존 CommandLineRunner 로직은 주석 처리 또는 삭제
        // ...
        log.info("CommandLineRunner 작업 완료.");
    }
    */
}