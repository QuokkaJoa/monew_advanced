package com.part2.monew.dto.cache;

import com.part2.monew.entity.CommentsManagement;
import java.sql.Timestamp;
import java.util.UUID;

public record CachedComment(
    UUID id,
    UUID articleId,
    UUID userId,
    String userNickname,
    String content,
    int likeCount,
    Timestamp createdAt
) {

  public static CachedComment from(CommentsManagement c) {
    return new CachedComment(
        c.getId(),
        c.getNewsArticle().getId(),
        c.getUser().getId(),
        c.getUser().getNickname(),
        c.getContent(),
        c.getLikeCount(),
        c.getCreatedAt()
    );
  }

}
