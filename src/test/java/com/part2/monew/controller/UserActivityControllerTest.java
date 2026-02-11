package com.part2.monew.controller;

import com.part2.monew.dto.response.*;
import com.part2.monew.global.exception.ErrorCode;
import com.part2.monew.global.exception.ErrorResponse;
import com.part2.monew.global.exception.user.UserNotFoundException;
import com.part2.monew.service.impl.SubscriptionServiceImpl;
import com.part2.monew.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserActivityController.class)
@ActiveProfiles("test")
class UserActivityControllerTest extends ControllerTestSupport {

  @MockitoBean
  private SubscriptionServiceImpl subscriptionServiceImpl;
  
  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
  }

  @Test
  @DisplayName("사용자 활동 내역 조회 성공")
  void getUserActivity_success() throws Exception {
    String email = "user_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    // given
    UserActivityResponse response = UserActivityResponse.builder()
        .id(userId)
        .email(email)
        .nickname("tester")
        .createdAt(Timestamp.from(Instant.now()))
        .subscriptions(List.of(
            UserSubscriptionActivityResponse.builder()
                .interestName("날씨")
                .interestKeywords(List.of("기온", "강수량"))
                .build()
        ))
        .comments(List.of(
            UserCommentActivityDto.builder()
                .content("맑음입니다")
                .likeCount(3)
                .build()
        ))
        .commentLikes(List.of(
            UserCommentLikeActivityDto.builder()
                .commentContent("맑음입니다")
                .build()
        ))
        .articleViews(List.of(
            UserArticleViewsActivityDto.builder()
                .articleTitle("날씨 기사")
                .build()
        ))
        .build();

    given(userActivityService.getUserActivity(userId)).willReturn(response);

    // when & then
    mockMvc.perform(get("/api/user-activities/{userId}", userId))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.nickname").value("tester"))
        .andExpect(jsonPath("$.subscriptions[0].interestName").value("날씨"))
        .andExpect(jsonPath("$.subscriptions[0].interestKeywords[0]").value("기온"))
        .andExpect(jsonPath("$.comments[0].content").value("맑음입니다"))
        .andExpect(jsonPath("$.commentLikes[0].commentContent").value("맑음입니다"))
        .andExpect(jsonPath("$.articleViews[0].articleTitle").value("날씨 기사"));

    verify(userActivityService).getUserActivity(userId);
  }

  @Test
  @DisplayName("사용자 활동 내역 조회 실패 - 존재하지 않는 사용자")
  void getUserActivity_userNotFound() throws Exception {
    // given
    String errorMessage = "해당 사용자를 찾을 수 없습니다.";
    given(userActivityService.getUserActivity(userId))
        .willThrow(new UserNotFoundException(errorMessage));

    // when
    String path = "/api/user-activities/" + userId;
    String responseBody = mockMvc.perform(get(path))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()))
        .andExpect(jsonPath("$.message").value(errorMessage))
        .andExpect(jsonPath("$.status").value(ErrorCode.USER_NOT_FOUND.getStatus().value()))
        .andExpect(jsonPath("$.path").value(path))
        .andReturn()
        .getResponse()
        .getContentAsString();

    // then
    ErrorResponse parsed = objectMapper.readValue(responseBody, ErrorResponse.class);
    assertThat(parsed.code()).isEqualTo(ErrorCode.USER_NOT_FOUND.getCode());
    assertThat(parsed.message()).isEqualTo(errorMessage);
  }
}
