package com.part2.monew.service;


import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.part2.monew.dto.request.CommentRequest;
import com.part2.monew.repository.CommentRepository;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
public class CommentServiceTest {

  @Autowired
  private CommentService commentService;

  @MockitoBean
  private CommentRepository commentRepository;

  @Test
  @DisplayName("캐싱 적용 확인: 두 번째 조회부터는 DB를 거치지 않아야 한다")
  void findComments_CachingTest() {
    // given
    UUID articleId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    int limit = 10;

    // Record Builder를 사용하여 요청 객체 생성
    CommentRequest request = CommentRequest.builder()
        .articleId(articleId)
        .limit(limit)
        .after(null) // 첫 페이지 조회 가정
        .build();

    // Mocking 1: 댓글 목록 조회 (빈 리스트 반환)
    given(commentRepository.findCommentsByArticleId(
        eq(articleId),
        eq(null), // req.after()가 null이므로
        eq(limit),
        eq(userId)
    )).willReturn(Collections.emptyList());

    // Mocking 2: 전체 카운트 조회 (0 반환)
    given(commentRepository.totalCount(eq(articleId)))
        .willReturn(0L);

    // when
    System.out.println("=== 1차 호출 (Cache Miss -> DB 조회) ===");
    commentService.findCommentsByArticleId(request, userId);

    System.out.println("=== 2차 호출 (Cache Hit -> DB 조회 X) ===");
    commentService.findCommentsByArticleId(request, userId);

    System.out.println("=== 3차 호출 (Cache Hit -> DB 조회 X) ===");
    commentService.findCommentsByArticleId(request, userId);

    // then
    // findCommentsByArticleId 메서드가 정확히 '1번'만 호출되었는지 검증
    verify(commentRepository, times(1)).findCommentsByArticleId(
        eq(articleId),
        eq(null),
        eq(limit),
        eq(userId)
    );

    // totalCount도 캐싱 범위 안에 포함되어 있다면 1번, 아니라면 3번 호출될 수 있음.
    // (코드상 totalCount는 캐싱 로직 밖인 것 같지만, 확인 필요)
  }

}
