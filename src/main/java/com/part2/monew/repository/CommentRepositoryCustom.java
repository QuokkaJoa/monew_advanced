package com.part2.monew.repository;

import com.part2.monew.entity.CommentsManagement;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public interface CommentRepositoryCustom {
    List<CommentsManagement> findCommentsByArticleId(UUID articleId, Timestamp after, int limit, UUID userId);
    Long totalCount(UUID articleId);
    List<CommentsManagement> findTop10RecentCommentsByUserId(UUID userId);

    List<CommentsManagement> findCommentsPage(UUID articleId, Timestamp after, int limit);
}
