package com.part2.monew.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.part2.monew.config.RedisTestContainerConfig;
import com.part2.monew.dto.request.CommentRequest;
import com.part2.monew.repository.CommentRepository;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
public class CommentServiceConcurrencyTest extends RedisTestContainerConfig {

  private static final Logger log = LoggerFactory.getLogger(CommentServiceConcurrencyTest.class);

  @Autowired
  private CommentService commentService;

  @MockitoBean
  private CommentRepository commentRepository;

  @Test
  @DisplayName("동시성 테스트: 동시에 10명이 조회해도 DB 조회는 1번만 발생해야 한다 (분산 락 검증)")
  void findComments_ConcurrencyTset() throws InterruptedException {
    // given
    int threadCount = 10;
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    AtomicInteger errorCount = new AtomicInteger(0);

    UUID articleId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    int limit = 10;

    CommentRequest request = CommentRequest.builder()
        .articleId(articleId)
        .limit(10)
        .after(null)
        .build();

    given(commentRepository.findCommentsByArticleId(
        eq(articleId),
        eq(null),
        eq(limit),
        eq(userId)
    )).willAnswer(invocation -> {
      // 이 람다식은 Repository가 호출될 때 실행됩니다.
      log.info(" Repository 접근 (DB에 Connect)");

      // 실제 리턴값 반환
      return Collections.emptyList();
    });

    given(commentRepository.totalCount(eq(articleId))).willReturn(0L);


    // when
    System.out.println("=== 동시 요청 시작 ===");

    for (int i = 0; i < threadCount; i++) {
      executorService.submit(() -> {
        try {
          commentService.findCommentsByArticleId(request, userId);
        } catch (Exception e) {
          log.error("테스트 중 예외 발생", e);
          errorCount.incrementAndGet(); // 에러 카운트 증가
        } finally {
          latch.countDown(); // 작업 완료 신호
        }
      });
    }

    latch.await(); // 모든 스레드가 끝날 때까지 메인 스레드 대기
    log.info("=== 동시 요청 종료 ===");

    // then
    // 1. 실행 중 예외가 하나도 없었어야 함
    assertThat(errorCount.get()).as("테스트 실행 중 예외가 발생했습니다.").isEqualTo(0);

    // 2. 분산 락이 제대로 작동했다면, DB 조회는 단 1번이어야 함.
    verify(commentRepository, times(1)).findCommentsByArticleId(
        eq(articleId),
        eq(null),
        eq(limit),
        eq(userId)
    );
  }
}
