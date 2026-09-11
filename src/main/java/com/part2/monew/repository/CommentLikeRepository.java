package com.part2.monew.repository;

import com.part2.monew.entity.CommentLike;
import com.part2.monew.entity.CommentsManagement;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentLikeRepository extends JpaRepository<CommentLike, UUID> {
    List<CommentLike> findAllByCommentsManagement(CommentsManagement commentsManagement);
    Optional<CommentLike> findByCommentsManagement_IdAndUser_Id(UUID id, UUID userId);
    List<CommentLike> findTop10ByUser_IdOrderByCreatedAtDesc(UUID userId);

    @Query("select cl.commentsManagement.id from CommentLike cl "
        + "where cl.user.id = :userId and cl.commentsManagement.id in :commentIds")
    List<UUID> findLikedCommentIds(@Param("userId") UUID userId,
        @Param("commentIds") Collection<UUID> commentIds);
}
