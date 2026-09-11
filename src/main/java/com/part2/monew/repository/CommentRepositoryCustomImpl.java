package com.part2.monew.repository;

import com.part2.monew.entity.CommentLike;
import com.part2.monew.entity.CommentsManagement;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.part2.monew.entity.QCommentLike.commentLike;
import static com.part2.monew.entity.QCommentsManagement.commentsManagement;
import static com.part2.monew.entity.QNewsArticle.newsArticle;
import static com.part2.monew.entity.QUser.user;

public class CommentRepositoryCustomImpl implements CommentRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    public CommentRepositoryCustomImpl(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    @Override
    public List<CommentsManagement> findCommentsByArticleId(UUID articleId, Timestamp after, int limit, UUID userId) {
        // 1) 댓글 + 유저·기사 정보만 가져오기
        List<CommentsManagement> comments = queryFactory
                .selectFrom(commentsManagement)
                .join(commentsManagement.user, user).fetchJoin()
                .join(commentsManagement.newsArticle, newsArticle).fetchJoin()
                .where(
                        commentsManagement.newsArticle.id.eq(articleId),
                        commentsManagement.active.isTrue(),
                        ltCreatedAt(after)
                )
                .orderBy(
                        commentsManagement.createdAt.desc(),
                        commentsManagement.likeCount.desc()
                )
                .limit(limit + 1)
                .fetch();

        if (comments.isEmpty()) {
            return comments;
        }

        // 2) 이 유저가 누른 좋아요가 달린 댓글 ID만 뽑아오기
        List<UUID> likedIds = queryFactory
                .select(commentLike.commentsManagement.id)
                .from(commentLike)
                .where(
                        commentLike.user.id.eq(userId),
                        commentLike.commentsManagement.newsArticle.id.eq(articleId),
                        commentLike.commentsManagement.active.isTrue(),
                        ltCreatedAt(after)
                )
                .fetch();
        Set<UUID> likedSet = new HashSet<>(likedIds);

        // 3) 각 CommentsManagement 의 commentLikes 컬렉션을
        //    “내가 눌렀던 것만” 남기도록 필터링
        comments.forEach(c -> {
            List<CommentLike> filtered = c.getCommentLikes().stream()
                    .filter(cl -> likedSet.contains(cl.getCommentsManagement().getId()))
                    .toList();

            List<CommentLike> likes = c.getCommentLikes();
            likes.clear();
            likes.addAll(filtered);
        });

        return comments;
    }

    @Override
    public Long totalCount(UUID articleId) {
        return queryFactory
                .selectFrom(commentsManagement)
                .where(
                        commentsManagement.newsArticle.id.eq(articleId),
                        commentsManagement.active.isTrue()
                ).fetchCount();
    }

    private BooleanExpression ltCreatedAt(Timestamp after) {
        return after != null ? commentsManagement.createdAt.lt(after) : null;
    }

    @Override
    public List<CommentsManagement> findTop10RecentCommentsByUserId(UUID userId) {
        return queryFactory
            .selectFrom(commentsManagement)
            .join(commentsManagement.user, user).fetchJoin()
            .join(commentsManagement.newsArticle, newsArticle).fetchJoin()
            .where(
                commentsManagement.user.id.eq(userId),
                commentsManagement.active.isTrue()
            )
            .orderBy(commentsManagement.createdAt.desc())
            .limit(10)
            .fetch();
    }

    @Override
    public List<CommentsManagement> findCommentsPage(UUID articleId, Timestamp after, int limit) {
        return queryFactory
            .selectFrom(commentsManagement)
            .join(commentsManagement.user, user).fetchJoin()
            .join(commentsManagement.newsArticle, newsArticle).fetchJoin()
            .where(
                commentsManagement.newsArticle.id.eq(articleId),
                commentsManagement.active.isTrue(),
                ltCreatedAt(after)
            )
            .orderBy(
                commentsManagement.createdAt.desc(),
                commentsManagement.id.desc()
            )
            .limit(limit + 1)
            .fetch();
    }
}
