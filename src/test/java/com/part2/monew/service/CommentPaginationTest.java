package com.part2.monew.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.part2.monew.config.RedisTestContainerConfig;
import com.part2.monew.dto.request.CommentRequest;
import com.part2.monew.dto.response.CursorResponse;
import com.part2.monew.entity.CommentsManagement;
import com.part2.monew.entity.NewsArticle;
import com.part2.monew.entity.User;
import com.part2.monew.repository.CommentLikeRepository;
import com.part2.monew.repository.CommentRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
public class CommentPaginationTest extends RedisTestContainerConfig {

  @Autowired
  private CommentService commentService;

  @Autowired
  @Qualifier("commentsCacheManager")
  private CacheManager cacheManager;

  @MockitoBean
  private CommentRepository commentRepository;
  @MockitoBean
  private CommentLikeRepository commentLikeRepository;

  @BeforeEach
  void clearCommentsCache() {
    Objects.requireNonNull(cacheManager.getCache("comments")).clear();
  }

  @Test
  @DisplayName("limit 10으로 요청하면 content도 10개여야 한다")
  void respectsRequestedLimit() {
    UUID articleId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    List<CommentsManagement> eleven = comments(articleId, 11);

    given(commentRepository.findCommentsPage(articleId, null, 10)).willReturn(eleven);
    given(commentRepository.totalCount(articleId)).willReturn(12L);
    given(commentLikeRepository.findLikedCommentIds(eq(userId), anyList()))
        .willReturn(List.of());

    CommentRequest request = CommentRequest.builder()
        .articleId(articleId)
        .limit(10)
        .direction("DESC")
        .after(null)
        .cursor(null)
        .build();

    CursorResponse response = commentService.findCommentsByArticleId(request, userId);

    assertThat(response.getContent()).hasSize(10);
    assertThat(response.getSize()).isEqualTo(10);
    assertThat(response.getHasNext()).isTrue();
    assertThat(response.getTotalElements()).isEqualTo(12L);
  }

  private List<CommentsManagement> comments(UUID articleId, int count) {
    User author = new User();
    author.setId(UUID.randomUUID());
    author.setNickname("작성자");

    NewsArticle article = new NewsArticle();
    article.setId(articleId);

    Instant base = Instant.parse("2026-01-01T00:00:00Z");
    List<CommentsManagement> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      CommentsManagement c = new CommentsManagement();
      c.setId(UUID.randomUUID());
      c.setUser(author);
      c.setNewsArticle(article);
      c.setContent("댓글 " + i);
      c.setLikeCount(0);
      c.setCreatedAt(Timestamp.from(base.minusSeconds(i)));
      c.setActive(true);
      list.add(c);
    }
    return list;
  }
}
