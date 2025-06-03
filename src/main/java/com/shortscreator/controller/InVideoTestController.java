package com.shortscreator.controller;

import com.shortscreator.service.InVideoTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/invideo/test")
@RequiredArgsConstructor
public class InVideoTestController {

  private final InVideoTestService inVideoTestService;

  /**
   * InVideo 영상 다운로드 및 YouTube 업로드 테스트
   *
   * @param videoUrl InVideo 영상 URL
   * @param title 영상 제목 (선택사항)
   * @param description 영상 설명 (선택사항)
   * @return 비동기 작업 시작 응답
   */
  @PostMapping("/download-and-upload")
  public ResponseEntity<Map<String, Object>> downloadAndUpload(
      @RequestParam String videoUrl,
      @RequestParam(required = false, defaultValue = "Test Video from InVideo") String title,
      @RequestParam(required = false, defaultValue = "This is a test video downloaded from InVideo and uploaded to YouTube Shorts") String description) {

    log.info("InVideo 다운로드 및 YouTube 업로드 테스트 요청");
    log.info("- URL: {}", videoUrl);
    log.info("- 제목: {}", title);
    log.info("- 설명: {}", description);

    Map<String, Object> response = new HashMap<>();

    try {
      // 비동기로 다운로드 및 업로드 실행
      CompletableFuture<String> futureResult = inVideoTestService.downloadAndUploadVideo(
          videoUrl, title, description);

      // 비동기 작업이 완료되면 결과 처리
      futureResult.thenAcceptAsync(result -> {
        log.info("InVideo 테스트 작업 완료: {}", result);
      }).exceptionally(ex -> {
        log.error("InVideo 테스트 작업 중 오류: {}", ex.getMessage(), ex);
        return null;
      });

      response.put("status", "started");
      response.put("message", "InVideo 다운로드 및 YouTube 업로드 작업이 시작되었습니다.");
      response.put("video_url", videoUrl);
      response.put("title", title);
      response.put("description", description);
      response.put("estimated_time", "5-10분");

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("InVideo 테스트 작업 시작 중 오류: {}", e.getMessage(), e);
      response.put("status", "error");
      response.put("message", "작업 시작 중 오류가 발생했습니다: " + e.getMessage());
      return ResponseEntity.internalServerError().body(response);
    }
  }

  /**
   * 간단한 GUI 테스트 페이지 제공
   */
  @GetMapping("/page")
  public String getTestPage() {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>InVideo Download & Upload Test</title>
            <style>
                body { font-family: Arial, sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; }
                .form-group { margin: 15px 0; }
                label { display: block; margin-bottom: 5px; font-weight: bold; }
                input, textarea { width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; }
                button { background: #007bff; color: white; padding: 10px 20px; border: none; border-radius: 4px; cursor: pointer; }
                button:hover { background: #0056b3; }
                .result { margin-top: 20px; padding: 10px; border-radius: 4px; }
                .success { background: #d4edda; border: 1px solid #c3e6cb; color: #155724; }
                .error { background: #f8d7da; border: 1px solid #f5c6cb; color: #721c24; }
                .info { background: #d1ecf1; border: 1px solid #bee5eb; color: #0c5460; }
            </style>
        </head>
        <body>
            <h1>🎬 InVideo 다운로드 & YouTube 업로드 테스트</h1>
            
            <form id="testForm">
                <div class="form-group">
                    <label for="videoUrl">InVideo 영상 URL:</label>
                    <input type="url" id="videoUrl" name="videoUrl" 
                           value="https://ai.invideo.io/workspace/10a4c8af-5f19-44d0-8281-c9c2b4ee7c3b/v30-copilot/6ea53da1-55ed-45e4-bacc-93ae6eb96d56"
                           required>
                </div>
                
                <div class="form-group">
                    <label for="title">영상 제목:</label>
                    <input type="text" id="title" name="title" 
                           value="Test Shorts from InVideo">
                </div>
                
                <div class="form-group">
                    <label for="description">영상 설명:</label>
                    <textarea id="description" name="description" rows="3">This is a test YouTube Shorts video downloaded from InVideo AI and automatically uploaded using our automation system.</textarea>
                </div>
                
                <button type="submit">🚀 다운로드 & 업로드 시작</button>
            </form>
            
            <div id="result"></div>
            
            <div style="margin-top: 30px; padding: 15px; background: #f8f9fa; border-radius: 4px;">
                <h3>📋 사전 준비사항:</h3>
                <ul>
                    <li>✅ YouTube OAuth 2.0 인증 완료</li>
                    <li>✅ InVideo 계정 로그인 정보 설정</li>
                    <li>✅ 다운로드 폴더 경로 설정</li>
                </ul>
                
                <h3>🔧 API 엔드포인트:</h3>
                <code>POST /api/invideo/test/download-and-upload</code>
                <br><br>
                
                <h3>📊 확인 방법:</h3>
                <ul>
                    <li>애플리케이션 로그 확인</li>
                    <li>다운로드 폴더에서 파일 확인</li>
                    <li>YouTube Studio에서 업로드된 영상 확인</li>
                </ul>
            </div>
            
            <script>
                document.getElementById('testForm').addEventListener('submit', function(e) {
                    e.preventDefault();
                    
                    const formData = new FormData(this);
                    const resultDiv = document.getElementById('result');
                    
                    // 로딩 표시
                    resultDiv.innerHTML = '<div class="info">⏳ 작업을 시작하는 중...</div>';
                    
                    // API 호출
                    fetch('/api/invideo/test/download-and-upload', {
                        method: 'POST',
                        body: formData
                    })
                    .then(response => response.json())
                    .then(data => {
                        if (data.status === 'started') {
                            resultDiv.innerHTML = `
                                <div class="success">
                                    <h4>✅ 작업 시작됨</h4>
                                    <p><strong>메시지:</strong> ${data.message}</p>
                                    <p><strong>예상 소요 시간:</strong> ${data.estimated_time}</p>
                                    <p><strong>처리할 URL:</strong> ${data.video_url}</p>
                                    <p>📊 진행 상황은 애플리케이션 로그를 확인하세요.</p>
                                </div>
                            `;
                        } else {
                            resultDiv.innerHTML = `
                                <div class="error">
                                    <h4>❌ 오류 발생</h4>
                                    <p>${data.message}</p>
                                </div>
                            `;
                        }
                    })
                    .catch(error => {
                        resultDiv.innerHTML = `
                            <div class="error">
                                <h4>❌ 네트워크 오류</h4>
                                <p>${error.message}</p>
                            </div>
                        `;
                    });
                });
            </script>
        </body>
        </html>
        """;
  }
}