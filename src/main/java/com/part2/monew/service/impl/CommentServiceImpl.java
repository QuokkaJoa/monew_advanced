package com.part2.monew.service.impl;

import com.part2.monew.dto.cache.CachedComment;
import com.part2.monew.dto.cache.CachedCommentPage;
import com.part2.monew.dto.request.CommentRequest;
import com.part2.monew.dto.request.CreateCommentRequest;
import com.part2.monew.dto.response.CommentLikeResponse;
import com.part2.monew.dto.response.CommentResponse;
import com.part2.monew.dto.response.CursorResponse;
import com.part2.monew.entity.CommentLike;
import com.part2.monew.entity.CommentsManagement;
import com.part2.monew.entity.NewsArticle;
import com.part2.monew.entity.User;
import com.part2.monew.global.exception.article.ArticleNotFoundException;
import com.part2.monew.global.exception.comment.CommentIsActiveException;
import com.part2.monew.global.exception.comment.CommentLikeDuplication;
import com.part2.monew.global.exception.comment.CommentNotFoundException;
import com.part2.monew.global.exception.comment.CommentUnlikeDuplication;
import com.part2.monew.global.exception.user.UserNotFoundException;
import com.part2.monew.repository.CommentLikeRepository;
import com.part2.monew.repository.CommentRepository;
import com.part2.monew.repository.NewsArticleRepository;
import com.part2.monew.repository.UserRepository;
import com.part2.monew.service.CommentService;
import com.part2.monew.service.NotificationService;
import com.part2.monew.service.cache.CommentCacheStore;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final UserRepository userRepository;
    private final NewsArticleRepository articleRepository;
    private final NotificationService notificationService;
    private final DataSource dataSource;
    private final CommentCacheStore cacheStore;

    @Override
    public CursorResponse findCommentsByArticleId(CommentRequest req, UUID userId) {
        CachedCommentPage page = cacheStore.isCacheable(req)
            ? cacheStore.getFirstPage(req.articleId())
            : cacheStore.loadPage(req.articleId(), req.after(), CommentCacheStore.limitOf(req));

        return assemble(page, userId);
    }

    @Override
    @Transactional
    public CommentResponse create(CreateCommentRequest request) {
        printLogo("Comment Create");

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(UserNotFoundException::new);


        NewsArticle article = articleRepository.findById(request.getArticleId())
                .orElseThrow(ArticleNotFoundException::new);

        CommentsManagement comment = CommentsManagement.create(user, article, request.getContent(), 0);

        CommentsManagement saveComment = commentRepository.saveAndFlush(comment);

        // 뉴스 기사 댓글 수 증가
        article.incrementCommentCount();
        articleRepository.save(article);

        return CommentResponse.of(saveComment);

    }

    @Override
    @Transactional
    public CommentResponse update(UUID id, String content) {
        printLogo("Comment update");


        CommentsManagement commentsManagement = commentRepository.findById(id)
                .orElseThrow(CommentNotFoundException::new);

        commentsManagement.update(content);

        return CommentResponse.of(commentsManagement);
    }

    @Override
    @Transactional
    public CommentLikeResponse likeComment(UUID id, UUID userId) {
        printLogo("Comment likeComment");


        Optional<CommentLike> existingLikeOpt = commentLikeRepository.findByCommentsManagement_IdAndUser_Id(id, userId);

        existingLikeOpt.ifPresent(cl -> {
            throw new CommentLikeDuplication();
        });


        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        CommentsManagement commentsManagement = commentRepository.findById(id)
                .orElseThrow(CommentNotFoundException::new);


        CommentLike commentLike = CommentLike.create(user, commentsManagement);

        CommentLike saveComment = commentLikeRepository.saveAndFlush(commentLike);

        int totalLike = commentTotalLike(commentsManagement);

        commentsManagement.updateTotalCount(totalLike);

        User commentOwner = commentsManagement.getUser();
        if (!commentOwner.getId().equals(user.getId())) {
            String content = (user.getNickname()+"님이 나의 댓글을 좋아합니다.");
            notificationService.createNotification(
                    commentOwner,
                    content,
                    "COMMENT",
                    commentsManagement.getId()
            );
        }

        return CommentLikeResponse.of(commentsManagement, saveComment);
    }

    @Override
    @Transactional
    public void unlikeComment(UUID id, UUID userId) {
        printLogo("Comment unlikeComment");


        CommentLike commentLike = commentLikeRepository.findByCommentsManagement_IdAndUser_Id(id, userId)
                .orElseThrow(CommentUnlikeDuplication::new);

        CommentsManagement commentsManagement = commentRepository.findById(id)
                .orElseThrow(CommentNotFoundException::new);


        commentLikeRepository.deleteById(commentLike.getId());

        int totalLike = commentTotalLike(commentsManagement);

        commentsManagement.updateTotalCount(totalLike);
    }

    @Override
    @Transactional
    public void deleteComment(UUID id) {
        printLogo("Comment deleteComment");


        CommentsManagement commentsManagement = commentRepository.findById(id)
                .orElseThrow(CommentNotFoundException::new);

        // 뉴스 기사 댓글 수 감소
        NewsArticle article = commentsManagement.getNewsArticle();
        article.decrementCommentCount();
        articleRepository.save(article);

        commentsManagement.delete();

    }

    @Override
    @Transactional
    public void hardDeleteComment(UUID id) {
        printLogo("Comment hardDeleteComment");


        CommentsManagement commentsManagement = commentRepository.findById(id)
                .orElseThrow(CommentNotFoundException::new);

        if(commentsManagement.isActive()){
            throw new CommentIsActiveException();
        }

        commentRepository.deleteById(id);
    }

    private int commentTotalLike(CommentsManagement commentsManagement) {
        return commentLikeRepository.findAllByCommentsManagement(commentsManagement).size();
    }


    private void printLogo(String callName){
        try(Connection connection = dataSource.getConnection()) {
            log.info("[DB URL Check {}] : {} ", callName ,connection.getMetaData().getURL());
        } catch (Exception e) {
            log.error("Failed to check DB URL", e);
        }
    }

    private CursorResponse assemble(CachedCommentPage page, UUID userId) {
        List<UUID> commentIds = page.comments().stream()
            .map(CachedComment::id)
            .toList();

        Set<UUID> likedIds = commentIds.isEmpty()
            ? Set.of()
            : new HashSet<>(commentLikeRepository.findLikedCommentIds(userId, commentIds));

        List<CommentResponse> content = page.comments().stream()
            .map(c -> CommentResponse.of(c, likedIds.contains(c.id())))
            .toList();

        return CursorResponse.of(content, page.totalElements());
    }
}
