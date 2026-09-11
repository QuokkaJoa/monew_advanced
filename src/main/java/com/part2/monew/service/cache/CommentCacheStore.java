package com.part2.monew.service.cache;

import com.part2.monew.dto.cache.CachedComment;
import com.part2.monew.dto.cache.CachedCommentPage;
import com.part2.monew.dto.request.CommentRequest;
import com.part2.monew.global.annotation.DistributedCache;
import com.part2.monew.global.event.ArticleCommentsChanged;
import com.part2.monew.repository.CommentRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class CommentCacheStore {

  public static final int DEFAULT_LIMIT = 10;
  public static final String CACHE_NAME = "comments";
  public static final String KEY_PREFIX = "comments:first:";

  private final CommentRepository commentRepository;
  private final CacheManager cacheManager;

  public boolean isCacheable(CommentRequest req) {
    return req.after() == null
        && req.cursor() == null
        && "DESC".equalsIgnoreCase(directionOf(req))
        && limitOf(req) == DEFAULT_LIMIT;
  }

  @DistributedCache(cacheName = CACHE_NAME, key = "'comments:first:' + #articleId")
  public CachedCommentPage getFirstPage(UUID articleId) {
    return loadPage(articleId, null, DEFAULT_LIMIT);
  }

  public CachedCommentPage loadPage(UUID articleId, Timestamp after, int limit) {
    List<CachedComment> comments = commentRepository
        .findCommentsPage(articleId, after, limit)
        .stream()
        .map(CachedComment::from)
        .toList();

    return new CachedCommentPage(comments, commentRepository.totalCount(articleId));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void evict(ArticleCommentsChanged event) {
    Cache cache = cacheManager.getCache(CACHE_NAME);
    if (cache != null) {
      cache.evict(KEY_PREFIX + event.articleId());
    }
  }

  public static int limitOf(CommentRequest req) {
    return req.limit() == null ? DEFAULT_LIMIT : req.limit();
  }

  private static String directionOf(CommentRequest req) {
    return req.direction() == null ? "DESC" : req.direction();
  }

}
