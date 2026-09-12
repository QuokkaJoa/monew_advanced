package com.part2.monew.controller;

import com.part2.monew.dto.request.CommentRequest;
import com.part2.monew.dto.request.CreateCommentRequest;
import com.part2.monew.dto.request.UpdateCommentRequest;
import com.part2.monew.dto.response.CommentLikeResponse;
import com.part2.monew.dto.response.CommentResponse;
import com.part2.monew.support.ControllerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommentController.class)
@ActiveProfiles("test")
class CommentControllerTest extends ControllerTestSupport {
    @DisplayName("댓글 목록을 조회한다.")
    @Test
    void findCommentsByArticleId() throws Exception {
        UUID userId    = UUID.randomUUID();

        // given
        CommentRequest commentRequest = CommentRequest.builder()
            .articleId(UUID.randomUUID())
            .orderBy("createdAt")
            .direction("DESC")
            .limit(10)
            .build();
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.get("/api/comments")
                        .param("articleId", commentRequest.articleId().toString())
                        .param("orderBy", commentRequest.orderBy())
                        .param("direction", commentRequest.direction())
                        .param("limit", String.valueOf(commentRequest.limit()))
                        .header("Monew-Request-User-ID", userId))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());
    }


    @DisplayName("limit이 허용 범위를 벗어나면 400을 반환한다.")
    @Test
    void rejectsOutOfRangeLimit() throws Exception {
        UUID userId = UUID.randomUUID();

        // when // then
        mockMvc.perform(MockMvcRequestBuilders.get("/api/comments")
                        .param("articleId", UUID.randomUUID().toString())
                        .param("orderBy", "createdAt")
                        .param("direction", "DESC")
                        .param("limit", "0")
                        .header("Monew-Request-User-ID", userId))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest());
    }


    @DisplayName("댓글을 생성한다.")
    @Test
    void createComment() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        String content = "테스트 댓글 내용";

        CreateCommentRequest request = CreateCommentRequest.create(userId, articleId, content);

        // when // then
        mockMvc.perform(MockMvcRequestBuilders.post("/api/comments")
                .content(objectMapper.writeValueAsString(request))
                .contentType((MediaType.APPLICATION_JSON))
        )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());

    }

    @DisplayName("댓글을 업데이트한다.")
    @Test
    void updateComment() throws Exception {
        // given
        UUID commentId = UUID.randomUUID();
        String content = "업데이트";

        UpdateCommentRequest request = new UpdateCommentRequest(content);

        CommentResponse fakeResponse = CommentResponse.builder()
                .id(commentId)
                .userId(UUID.randomUUID())
                .userNickname("tester")
                .articleId(UUID.randomUUID())
                .content(content)
                .likeCount(0)
                .likedByMe(false)
                .createdAt(Timestamp.from(Instant.now()))
                .build();

        when(commentService.update(commentId, content)).thenReturn(fakeResponse);

        // when // then
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/comments/{commentId}", commentId)
                        .content(objectMapper.writeValueAsString(request))
                        .contentType((MediaType.APPLICATION_JSON))
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$.id").value(commentId.toString()))
                .andExpect(jsonPath("$.content").value(fakeResponse.getContent()));

    }

    @DisplayName("댓글 좋아요를 성공적으로 처리한다")
    @Test
    void likeComment_success() throws Exception {
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CommentLikeResponse fakeResponse = CommentLikeResponse.builder()
                .id(UUID.randomUUID())                       // 좋아요 엔티티 ID
                .likeBy(userId)                              // 좋아요 누른 사용자 ID
                .createdAt(Timestamp.from(Instant.now()))    // 좋아요 생성된 시각
                .commentId(commentId)                        // 좋아요가 달린 댓글 ID
                .articleId(UUID.randomUUID())                // 댓글이 달린 기사 ID
                .commentUserId(UUID.randomUUID())            // 댓글 작성자 ID
                .commentUserNickname("tester")               // 댓글 작성자 닉네임
                .content("댓글 내용")                         // 댓글 본문
                .likeCount(5)                               // 최종 좋아요 개수
                .commentCreatedAt(Timestamp.from(Instant.now().minusSeconds(3600)))
                .build();

        // commentService.likeComment(댓글ID, 사용자ID) 호출 시 fakeResponse를 반환하도록 stubbing
        when(commentService.likeComment(ArgumentMatchers.eq(commentId), ArgumentMatchers.eq(userId)))
                .thenReturn(fakeResponse);

        //--- when & then ---
        mockMvc.perform(MockMvcRequestBuilders.post("/api/comments/{commentId}/comment-likes", commentId)
                        .header("Monew-Request-User-ID", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                // 반환된 JSON에서 id, likeBy, commentId, likeCount 등이 정확히 담겨있는지 확인
                .andExpect(jsonPath("$.id").value(fakeResponse.getId().toString()))
                .andExpect(jsonPath("$.likeBy").value(userId.toString()))
                .andExpect(jsonPath("$.commentId").value(commentId.toString()))
                .andExpect(jsonPath("$.likeCount").value((int) (long) fakeResponse.getLikeCount()))
                .andExpect(jsonPath("$.commentUserNickname").value("tester"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
