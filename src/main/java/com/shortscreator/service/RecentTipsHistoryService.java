package com.shortscreator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Slf4j
@Service
public class RecentTipsHistoryService {

  private final ObjectMapper objectMapper;
  private final String recentTipsFilePath;

  private final LinkedList<String> recentTipTitles = new LinkedList<>();
  private static final int MAX_RECENT_TIPS_TO_REMEMBER = 10; // 기억할 최대 팁 개수

  public RecentTipsHistoryService(ObjectMapper objectMapper,
      @Value("${openai.recent_tips_filepath:recent_tip_titles.json}") String recentTipsFilePath) {
    this.objectMapper = objectMapper;
    this.recentTipsFilePath = recentTipsFilePath;
  }

  @PostConstruct
  public void initialize() {
    loadFromFile();
  }

  private synchronized void loadFromFile() {
    File tipsFile = new File(recentTipsFilePath);
    if (tipsFile.exists() && tipsFile.length() > 0) {
      try {
        List<String> loadedTitles = objectMapper.readValue(tipsFile, new TypeReference<LinkedList<String>>() {});
        recentTipTitles.clear();
        int startIndex = Math.max(0, loadedTitles.size() - MAX_RECENT_TIPS_TO_REMEMBER);
        for (int i = startIndex; i < loadedTitles.size(); i++) {
          recentTipTitles.add(loadedTitles.get(i));
        }
        log.info("파일에서 최근 팁 제목 {}개를 로드했습니다. 경로: {}", recentTipTitles.size(), recentTipsFilePath);
      } catch (IOException e) {
        log.error("최근 팁 목록 파일({}) 로드 중 오류 발생: {}", recentTipsFilePath, e.getMessage(), e);
      }
    } else {
      log.info("최근 팁 목록 파일({})이 없거나 비어있습니다. 새 목록으로 시작합니다.", recentTipsFilePath);
    }
  }

  private synchronized void saveToFile() {
    try {
      File tipsFile = new File(recentTipsFilePath);
      File parentDir = tipsFile.getParentFile();
      if (parentDir != null && !parentDir.exists()) {
        if (parentDir.mkdirs()) {
          log.info("저장 경로 디렉토리 생성: {}", parentDir.getAbsolutePath());
        } else {
          log.error("저장 경로 디렉토리 생성 실패: {}", parentDir.getAbsolutePath());
        }
      }
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(tipsFile, recentTipTitles);
      log.info("최근 팁 제목 {}개를 파일에 저장했습니다. 경로: {}", recentTipTitles.size(), recentTipsFilePath);
    } catch (IOException e) {
      log.error("최근 팁 목록 파일({}) 저장 중 오류 발생: {}", recentTipsFilePath, e.getMessage(), e);
    }
  }

  public synchronized void addTipTitle(String title) {
    if (title == null || title.trim().isEmpty()) return;
    if (recentTipTitles.contains(title)) {
      log.debug("팁 제목 '{}'은(는) 이미 최근 목록에 있습니다. 추가하지 않습니다.", title);
      return;
    }
    if (recentTipTitles.size() >= MAX_RECENT_TIPS_TO_REMEMBER) {
      recentTipTitles.removeFirst();
    }
    recentTipTitles.addLast(title);
    log.info("새로운 팁 제목 추가됨: '{}'. 현재 기억 중인 팁 개수: {}. 목록: {}", title, recentTipTitles.size(), recentTipTitles);
    saveToFile();
  }

  public synchronized List<String> getRecentTipTitles() {
    // 외부에서 수정할 수 없도록 불변 또는 복사본 리스트 반환
    return Collections.unmodifiableList(new ArrayList<>(recentTipTitles));
  }

  // 테스트나 관리 목적으로 모든 팁을 지우는 기능 (선택적)
  public synchronized void clearHistory() {
    recentTipTitles.clear();
    saveToFile();
    log.info("최근 팁 히스토리가 모두 삭제되었습니다.");
  }
}