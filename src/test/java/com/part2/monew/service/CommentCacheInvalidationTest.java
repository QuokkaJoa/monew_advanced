package com.part2.monew.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.part2.monew.config.RedisTestContainerConfig;
import com.part2.monew.dto.request.CommentRequest;
import com.part2.monew.dto.request.CreateCommentRequest;
import com.part2.monew.dto.response.CommentResponse;
import com.part2.monew.dto.response.CursorResponse;
import com.part2.monew.entity.CommentLike;
import com.part2.monew.entity.CommentsManagement;
import com.part2.monew.entity.NewsArticle;
import com.part2.monew.entity.User;
import com.part2.monew.repository.CommentLikeRepository;
import com.part2.monew.repository.CommentRepository;
import com.part2.monew.repository.NewsArticleRepository;
import com.part2.monew.repository.UserRepository;
import com.part2.monew.service.cache.CommentCacheStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
class CommentCacheInvalidationTest extends RedisTestContainerConfig {

  @Autowired
  private CommentService commentService;

  @Autowired
  @Qualifier("commentsCacheManager")
  private CacheManager cacheManager;

  @MockitoBean
  private CommentRepository commentRepository;

  @MockitoBean
  private CommentLikeRepository commentLikeRepository;

  @MockitoBean
  private UserRepository userRepository;

  @MockitoBean
  private NewsArticleRepository articleRepository;

  private final UUID articleId = UUID.randomUUID();
  private final UUID readerId = UUID.randomUUID();
  private final UUID authorId = UUID.randomUUID();

  private User author;
  private NewsArticle article;

  @BeforeEach
  void setUp() {
    Objects.requireNonNull(cacheManager.getCache(CommentCacheStore.CACHE_NAME)).clear();

    author = new User();
    author.setId(authorId);
    author.setNickname("작성자");

    article = new NewsArticle();
    article.setId(articleId);

    given(userRepository.findById(authorId)).willReturn(Optional.of(author));
    given(articleRepository.findById(articleId)).willReturn(Optional.of(article));
    given(commentLikeRepository.findLikedCommentIds(any(), any())).willReturn(List.of());
  }

  @Test
  @DisplayName("댓글을 생성하면 첫 페이지 캐시가 무효화되어 새 댓글이 조회된다")
  void evictsFirstPageCacheAfterCreate() {
    CommentsManagement c1 = comment("댓글1", "2026-01-01T00:00:03Z");
    CommentsManagement c2 = comment("댓글2", "2026-01-01T00:00:02Z");
    CommentsManagement c3 = comment("댓글3", "2026-01-01T00:00:01Z");

    givenRepositoryReturns(List.of(c1,c2,c3));

    CursorResponse before = commentService.findCommentsByArticleId(pageRequest(), readerId);
    commentService.findCommentsByArticleId(pageRequest(), readerId);

    assertThat(before.getContent()).hasSize(3);
    verify(commentRepository, times(1)).findCommentsPage(
        articleId, null, null, "DESC", CommentCacheStore.DEFAULT_LIMIT);

    CommentsManagement c4 = comment("새 댓글", "2026-01-01T00:00:04Z");
    givenRepositoryReturns(List.of(c4,c1,c2,c3));

    given(commentRepository.saveAndFlush(any())).willReturn(c4);
    commentService.create(CreateCommentRequest.create(articleId, authorId, "새 댓글"));

    CursorResponse after = commentService.findCommentsByArticleId(pageRequest(), readerId);

    assertThat(after.getContent()).hasSize(4);
    assertThat(after.getContent())
        .extracting(CommentResponse::getContent)
        .contains("새 댓글");

    verify(commentRepository, times(2)).findCommentsPage(
        articleId, null, null, "DESC", CommentCacheStore.DEFAULT_LIMIT);
  }

  @Test
  @DisplayName("댓글을 수정하면 첫 페이지 캐시가 무효화되어 바뀐 내용이 조회된다")
  void evictsFirstPageCacheAfterUpdate() {
    CommentsManagement c1 = comment("댓글1", "2026-01-01T00:00:03Z");
    CommentsManagement c2 = comment("댓글2", "2026-01-01T00:00:02Z");
    CommentsManagement c3 = comment("댓글3", "2026-01-01T00:00:01Z");

    givenRepositoryReturns(List.of(c1, c2, c3));

    CursorResponse before = commentService.findCommentsByArticleId(pageRequest(), readerId);
    assertThat(before.getContent())
        .extracting(CommentResponse::getContent)
        .containsExactly("댓글1", "댓글2", "댓글3");

    given(commentRepository.findById(c1.getId())).willReturn(Optional.of(c1));
    commentService.update(c1.getId(), "수정된 내용");

    CursorResponse after = commentService.findCommentsByArticleId(pageRequest(), readerId);

    assertThat(after.getContent())
        .extracting(CommentResponse::getContent)
        .containsExactly("수정된 내용", "댓글2", "댓글3");
  }

  @Test
  @DisplayName("댓글을 논리 삭제하면 첫 페이지 캐시가 무효화되어 목록에서 빠진다")
  void evictsFirstPageCacheAfterDelete() {
    CommentsManagement c1 = comment("댓글1", "2026-01-01T00:00:03Z");
    CommentsManagement c2 = comment("댓글2", "2026-01-01T00:00:02Z");
    CommentsManagement c3 = comment("댓글3", "2026-01-01T00:00:01Z");

    givenRepositoryReturns(List.of(c1, c2, c3));

    CursorResponse before = commentService.findCommentsByArticleId(pageRequest(), readerId);
    assertThat(before.getContent())
        .extracting(CommentResponse::getContent)
        .containsExactly("댓글1", "댓글2", "댓글3");

    given(commentRepository.findById(c1.getId())).willReturn(Optional.of(c1));
    givenRepositoryReturns(List.of(c2, c3));
    commentService.deleteComment(c1.getId());

    CursorResponse after = commentService.findCommentsByArticleId(pageRequest(), readerId);

    assertThat(after.getContent())
        .extracting(CommentResponse::getContent)
        .containsExactly("댓글2", "댓글3");
    assertThat(after.getTotalElements()).isEqualTo(2L);
  }

  @Test
  @DisplayName("좋아요를 누르면 첫 페이지 캐시가 무효화되어 늘어난 likeCount가 조회된다")
  void evictsFirstPageCacheAfterLike() {
    CommentsManagement c1 = comment("댓글1", "2026-01-01T00:00:02Z");
    CommentsManagement c2 = comment("댓글2", "2026-01-01T00:00:01Z");

    givenRepositoryReturns(List.of(c1, c2));

    CursorResponse before = commentService.findCommentsByArticleId(pageRequest(), readerId);
    assertThat(before.getContent())
        .extracting(CommentResponse::getLikeCount)
        .containsExactly(0, 0);

    CommentLike like = CommentLike.create(author, c1);
    given(commentLikeRepository.findByCommentsManagement_IdAndUser_Id(c1.getId(), authorId))
        .willReturn(Optional.empty());
    given(commentRepository.findById(c1.getId())).willReturn(Optional.of(c1));
    given(commentLikeRepository.saveAndFlush(any())).willReturn(like);
    given(commentLikeRepository.findAllByCommentsManagement(c1)).willReturn(List.of(like));

    commentService.likeComment(c1.getId(), authorId);

    CursorResponse after = commentService.findCommentsByArticleId(pageRequest(), readerId);

    assertThat(after.getContent())
        .extracting(CommentResponse::getLikeCount)
        .containsExactly(1, 0);
  }

  @Test
  @DisplayName("좋아요를 취소하면 첫 페이지 캐시가 무효화되어 줄어든 likeCount가 조회된다")
  void evictsFirstPageCacheAfterUnlike() {
    CommentsManagement c1 = comment("댓글1", "2026-01-01T00:00:02Z");
    CommentsManagement c2 = comment("댓글2", "2026-01-01T00:00:01Z");
    c1.setLikeCount(1);

    givenRepositoryReturns(List.of(c1, c2));

    CursorResponse before = commentService.findCommentsByArticleId(pageRequest(), readerId);
    assertThat(before.getContent())
        .extracting(CommentResponse::getLikeCount)
        .containsExactly(1, 0);

    CommentLike like = CommentLike.create(author, c1);
    given(commentLikeRepository.findByCommentsManagement_IdAndUser_Id(c1.getId(), authorId))
        .willReturn(Optional.of(like));
    given(commentRepository.findById(c1.getId())).willReturn(Optional.of(c1));
    given(commentLikeRepository.findAllByCommentsManagement(c1)).willReturn(List.of());

    commentService.unlikeComment(c1.getId(), authorId);
    
    CursorResponse after = commentService.findCommentsByArticleId(pageRequest(), readerId);

    assertThat(after.getContent())
        .extracting(CommentResponse::getLikeCount)
        .containsExactly(0, 0);
  }

  private void givenRepositoryReturns(List<CommentsManagement> comments) {
    given(commentRepository.findCommentsPage(
        articleId, null, null, "DESC", CommentCacheStore.DEFAULT_LIMIT))
        .willReturn(comments);
    given(commentRepository.totalCount(articleId)).willReturn((long) comments.size());
  }

  private CommentRequest pageRequest() {
    return CommentRequest.builder()
        .articleId(articleId)
        .limit(CommentCacheStore.DEFAULT_LIMIT)
        .direction("DESC")
        .after(null)
        .cursor(null)
        .build();
  }

  private CommentsManagement comment(String content, String isoTime) {
    CommentsManagement c = new CommentsManagement();
    c.setId(UUID.randomUUID());
    c.setUser(author);
    c.setNewsArticle(article);
    c.setContent(content);
    c.setLikeCount(0);
    c.setCreatedAt(Timestamp.from(Instant.parse(isoTime)));
    c.setActive(true);
    return c;
  }
}
