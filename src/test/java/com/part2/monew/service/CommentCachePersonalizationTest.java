package com.part2.monew.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.part2.monew.config.RedisTestContainerConfig;
import com.part2.monew.dto.request.CommentRequest;
import com.part2.monew.dto.response.CursorResponse;
import com.part2.monew.entity.CommentsManagement;
import com.part2.monew.entity.NewsArticle;
import com.part2.monew.entity.User;
import com.part2.monew.repository.CommentLikeRepository;
import com.part2.monew.repository.CommentRepository;
import com.part2.monew.service.cache.CommentCacheStore;
import java.sql.Timestamp;
import java.time.Instant;
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
class CommentCachePersonalizationTest extends RedisTestContainerConfig {

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
  @DisplayName("같은 댓글 목록을 조회해도 likedByMe는 사용자별로 달라야 한다")
  void doesNotReuseLikedByMeAcrossUsers() {
    // given
    UUID articleId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    UUID user1Id = UUID.randomUUID();
    UUID user2Id = UUID.randomUUID();

    User author = new User();
    author.setId(UUID.randomUUID());
    author.setNickname("작성자");

    NewsArticle article = new NewsArticle();
    article.setId(articleId);

    Timestamp createdAt = Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"));

    CommentsManagement view = commentView(commentId, author, article, createdAt);

    CommentRequest request = CommentRequest.builder()
        .articleId(articleId)
        .limit(CommentCacheStore.DEFAULT_LIMIT)
        .direction("DESC")
        .after(null)
        .cursor(null)
        .build();

    given(commentRepository.findCommentsPage(articleId, null, CommentCacheStore.DEFAULT_LIMIT))
        .willReturn(List.of(view));
    given(commentRepository.totalCount(articleId)).willReturn(1L);

    given(commentLikeRepository.findLikedCommentIds(user1Id, List.of(commentId)))
        .willReturn(List.of(commentId));
    given(commentLikeRepository.findLikedCommentIds(user2Id, List.of(commentId)))
        .willReturn(List.of());

    CursorResponse r1 = commentService.findCommentsByArticleId(request, user1Id);
    CursorResponse r2 = commentService.findCommentsByArticleId(request, user2Id);

    assertThat(r1.getContent().get(0).getLikedByMe()).isTrue();
    assertThat(r2.getContent().get(0).getLikedByMe()).isFalse();

    verify(commentRepository, times(1))
        .findCommentsPage(articleId, null, CommentCacheStore.DEFAULT_LIMIT);
  }

  private CommentsManagement commentView(
      UUID commentId,
      User author,
      NewsArticle article,
      Timestamp createdAt
  ) {
    CommentsManagement comment = new CommentsManagement();
    comment.setId(commentId);
    comment.setUser(author);
    comment.setNewsArticle(article);
    comment.setContent("테스트 댓글");
    comment.setLikeCount(1);
    comment.setCreatedAt(createdAt);
    comment.setActive(true);
    return comment;
  }
}
