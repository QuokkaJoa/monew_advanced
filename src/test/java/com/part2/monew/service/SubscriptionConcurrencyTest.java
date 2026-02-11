package com.part2.monew.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.part2.monew.entity.Interest;
import com.part2.monew.entity.User;
import com.part2.monew.repository.InterestRepository;
import com.part2.monew.repository.UserRepository;
import com.part2.monew.service.impl.SubscriptionServiceImpl;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
public class SubscriptionConcurrencyTest {

  @Autowired
  private SubscriptionServiceImpl subscriptionService;

  @Autowired
  private InterestRepository interestRepository;

  @Autowired
  private UserRepository userRepository;

  @MockitoBean
  private com.part2.monew.repository.NewsArticleRepository newsArticleRepository;

  @Test
  @DisplayName("동시에 100명이 구독 요청을 보내면 구독자 수가 정확히 100명 증가해야 한다.")
  void subscribeConcurrencyTest() throws InterruptedException {
    // 1. 테스트 데이터 준비
    Interest interest = interestRepository.save(Interest.builder()
        .name("테스트관심사")
        .subscriberCount(0)
        .build());
    UUID interestId = interest.getId();

    int threadCount = 100;
    UUID[] userIds = new UUID[threadCount];
    for (int i = 0; i < threadCount; i++) {
      User user = userRepository.save(User.builder()
          .email("test" + i + "@test.com")
          .password("password")
          .nickname("user" + i)
          .active(true)
          .build());
      userIds[i] = user.getId();
    }

    // 2. 멀티스레드 실행
    ExecutorService executorService = Executors.newFixedThreadPool(32);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      int index = i;
      executorService.submit(() -> {
        try {
          subscriptionService.subscribeToInterest(interestId, userIds[index]);
        } catch (Exception e) {
          System.out.println("Error: " + e.getMessage());
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await();

    // 3. 검증
    Interest updatedInterest = interestRepository.findById(interestId).orElseThrow();
    System.out.println("최종 구독자 수: " + updatedInterest.getSubscriberCount());

    assertThat(updatedInterest.getSubscriberCount()).isEqualTo(100);
  }
}
