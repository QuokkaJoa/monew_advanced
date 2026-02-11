package com.part2.monew.controller;

import com.part2.monew.dto.request.InterestRegisterRequestDto;
import com.part2.monew.dto.request.InterestUpdateRequestDto;
import com.part2.monew.dto.response.CursorPageResponse;
import com.part2.monew.dto.response.InterestDto;
import com.part2.monew.global.exception.BusinessException;
import com.part2.monew.global.exception.ErrorCode;
import com.part2.monew.global.exception.ErrorResponse;
import com.part2.monew.global.exception.interest.SimilarInterestExistsException;
import com.part2.monew.service.impl.SubscriptionServiceImpl;
import com.part2.monew.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InterestController.class)
@ActiveProfiles("test")
class InterestControllerTest extends ControllerTestSupport {

  @MockitoBean
  private SubscriptionServiceImpl subscriptionServiceImpl;

  private final String BASE_URL = "/api/interests";
  private UUID requestUserId;

  @BeforeEach
  void setUp() {
    requestUserId = UUID.randomUUID();
  }

  @Test
  @DisplayName("관심사 등록 성공")
  void registerInterest_success() throws Exception {
    UUID requestUserId = UUID.randomUUID();
    InterestRegisterRequestDto requestDto = new InterestRegisterRequestDto("스포츠", Arrays.asList("야구", "축구"));
    InterestDto mockResponseDto = new InterestDto(UUID.randomUUID(), "스포츠", Arrays.asList("야구", "축구"), 0L, false);

    given(interestService.registerInterest(any(InterestRegisterRequestDto.class), eq(requestUserId)))
        .willReturn(mockResponseDto);

    ResultActions resultActions = mockMvc.perform(post(BASE_URL)
            .header("Monew-Request-User-ID", requestUserId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(requestDto)))
        .andDo(print());

    resultActions
        .andExpect(status().isCreated())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(mockResponseDto.id().toString()))
        .andExpect(jsonPath("$.name").value(mockResponseDto.name()))
        .andExpect(jsonPath("$.keywords[0]").value(mockResponseDto.keywords().get(0)))
        .andExpect(jsonPath("$.subscriberCount").value(mockResponseDto.subscriberCount()))
        .andExpect(jsonPath("$.subscribedByMe").value(mockResponseDto.subscribedByMe()));

    verify(interestService).registerInterest(any(InterestRegisterRequestDto.class), eq(requestUserId));
  }

  @Test
  @DisplayName("관심사 등록 실패 - 요청 DTO 유효성 검사 (이름 누락)")
  void registerInterest_fail_validation_nameBlank() throws Exception {
    UUID requestUserId = UUID.randomUUID();
    InterestRegisterRequestDto requestDtoWithBlankName = new InterestRegisterRequestDto("", Arrays.asList("야구", "축구"));

    ResultActions resultActions = mockMvc.perform(post(BASE_URL)
            .header("Monew-Request-User-ID", requestUserId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(requestDtoWithBlankName)))
        .andDo(print());

    resultActions
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

    String responseBody = resultActions.andReturn().getResponse().getContentAsString();
    ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);
    assertThat(errorResponse.fieldErrors())
        .anySatisfy(fieldError -> {
          assertThat(fieldError.field()).isEqualTo("name");
          assertThat(fieldError.reason()).isEqualTo("관심사 이름은 필수입니다.");
        });

    assertThat(errorResponse.fieldErrors())
        .anySatisfy(fieldError -> {
          assertThat(fieldError.field()).isEqualTo("name");
          assertThat(fieldError.reason()).isEqualTo("관심사 이름은 1자 이상 50자 이하로 입력해주세요.");
        });

    assertThat(errorResponse.fieldErrors()).hasSize(2);
  }

  @Test
  @DisplayName("관심사 등록 실패 - 요청 DTO 유효성 검사 (키워드 목록 비어있음)")
  void registerInterest_fail_validation_keywordsEmpty() throws Exception {
    UUID requestUserId = UUID.randomUUID();
    InterestRegisterRequestDto requestDtoWithEmptyKeywords = new InterestRegisterRequestDto("게임", List.of());

    ResultActions resultActions = mockMvc.perform(post(BASE_URL)
            .header("Monew-Request-User-ID", requestUserId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(requestDtoWithEmptyKeywords)))
        .andDo(print());

    resultActions
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

    String responseBody = resultActions.andReturn().getResponse().getContentAsString();
    ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);

    assertThat(errorResponse.status()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getStatus().value());
    assertThat(errorResponse.message()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getMessage());
    assertThat(errorResponse.path()).isEqualTo(BASE_URL);

    assertThat(errorResponse.fieldErrors())
        .isNotEmpty()
        .anySatisfy(fieldError -> {
          assertThat(fieldError.field()).isEqualTo("keywords");
          assertThat(fieldError.reason()).isEqualTo("키워드는 최소 1개 이상 등록해야 합니다.");
        });

    assertThat(errorResponse.fieldErrors())
        .anySatisfy(fieldError -> {
          assertThat(fieldError.field()).isEqualTo("keywords");
          assertThat(fieldError.reason()).isEqualTo("키워드는 1개 이상 10개 이하로 등록할 수 있습니다.");
        });

    assertThat(errorResponse.fieldErrors()).hasSize(2);
  }

  @Test
  @DisplayName("관심사 등록 실패 - 이미 존재하는 이름 (정확히 일치)")
  void registerInterest_fail_exactNameExists() throws Exception {
    UUID requestUserId = UUID.randomUUID();
    InterestRegisterRequestDto requestDto = new InterestRegisterRequestDto("스포츠", Arrays.asList("야구", "축구"));
    String errorMessage = String.format("이미 존재하는 관심사 이름입니다: %s", requestDto.name());

    given(interestService.registerInterest(any(InterestRegisterRequestDto.class), eq(requestUserId)))
        .willThrow(new BusinessException(ErrorCode.INTEREST_NAME_ALREADY_EXISTS, errorMessage));

    ResultActions resultActions = mockMvc.perform(post(BASE_URL)
            .header("Monew-Request-User-ID", requestUserId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(requestDto)))
        .andDo(print());

    resultActions
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value(ErrorCode.INTEREST_NAME_ALREADY_EXISTS.getCode()))
        .andExpect(jsonPath("$.message").value(errorMessage))
        .andExpect(jsonPath("$.status").value(ErrorCode.INTEREST_NAME_ALREADY_EXISTS.getStatus().value()));
  }

  @Test
  @DisplayName("관심사 등록 실패 - 유사한 이름 존재")
  void registerInterest_fail_similarNameExists() throws Exception {
    UUID requestUserId = UUID.randomUUID();
    InterestRegisterRequestDto requestDto = new InterestRegisterRequestDto("스포오츠", Arrays.asList("야구", "축구")); // 약간 다른 이름
    String similarExistingName = "스포츠";
    double similarity = 85.00;
    String errorMessage = String.format("유사한 이름의 관심사 '%s'가(이) 이미 존재합니다 (유사도: %.2f%%). 다른 이름을 사용해주세요.", similarExistingName, similarity);

    given(interestService.registerInterest(any(InterestRegisterRequestDto.class), eq(requestUserId)))
        .willThrow(new SimilarInterestExistsException(errorMessage));

    ResultActions resultActions = mockMvc.perform(post(BASE_URL)
            .header("Monew-Request-User-ID", requestUserId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(requestDto)))
        .andDo(print());

    resultActions
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value(ErrorCode.SIMILAR_INTEREST_EXISTS.getCode()))
        .andExpect(jsonPath("$.message").value(errorMessage))
        .andExpect(jsonPath("$.status").value(ErrorCode.SIMILAR_INTEREST_EXISTS.getStatus().value()));
  }

  @Test
  @DisplayName("관심사 키워드 수정 성공")
  void updateInterestKeywords_success() throws Exception {

    UUID interestId = UUID.randomUUID();
    UUID requestUserId = UUID.randomUUID();
    InterestUpdateRequestDto requestDto = new InterestUpdateRequestDto(Arrays.asList("농구", "배구"));

    InterestDto mockUpdatedInterestDto = new InterestDto(
        interestId,
        "스포츠",
        Arrays.asList("농구", "배구"),
        5L,
        true
    );

    given(interestService.updateInterestKeywords(eq(interestId), any(InterestUpdateRequestDto.class), eq(requestUserId)))
        .willReturn(mockUpdatedInterestDto);

    ResultActions resultActions = mockMvc.perform(patch(BASE_URL + "/{interestId}", interestId)
            .header("Monew-Request-User-ID", requestUserId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(requestDto)))
        .andDo(print());

    resultActions
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(interestId.toString()))
        .andExpect(jsonPath("$.name").value(mockUpdatedInterestDto.name()))
        .andExpect(jsonPath("$.keywords[0]").value("농구"))
        .andExpect(jsonPath("$.keywords[1]").value("배구"))
        .andExpect(jsonPath("$.subscriberCount").value(mockUpdatedInterestDto.subscriberCount()))
        .andExpect(jsonPath("$.subscribedByMe").value(mockUpdatedInterestDto.subscribedByMe()));

    verify(interestService).updateInterestKeywords(eq(interestId), any(InterestUpdateRequestDto.class), eq(requestUserId));
  }

  @Test
  @DisplayName("관심사 키워드 수정 실패 - 요청 DTO 유효성 검사 (키워드 목록 비어있음)")
  void updateInterestKeywords_fail_validation_keywordsEmpty() throws Exception {
    // given
    UUID interestId = UUID.randomUUID();
    UUID requestUserId = UUID.randomUUID();
    InterestUpdateRequestDto requestDtoWithEmptyKeywords = new InterestUpdateRequestDto(Collections.emptyList());

    // when
    ResultActions resultActions = mockMvc.perform(patch(BASE_URL + "/{interestId}", interestId)
            .header("Monew-Request-User-ID", requestUserId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(requestDtoWithEmptyKeywords)))
        .andDo(print());

    resultActions
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

    String responseBody = resultActions.andReturn().getResponse().getContentAsString();
    ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);

    assertThat(errorResponse.status()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getStatus().value());
    assertThat(errorResponse.message()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getMessage());
    assertThat(errorResponse.path()).isEqualTo(BASE_URL + "/" + interestId);

    assertThat(errorResponse.fieldErrors())
        .isNotEmpty()
        .anySatisfy(fieldError -> {
          assertThat(fieldError.field()).isEqualTo("keywords");
          assertThat(fieldError.reason()).isEqualTo("키워드는 최소 1개 이상 등록해야 합니다.");
        });

    assertThat(errorResponse.fieldErrors())
        .anySatisfy(fieldError -> {
          assertThat(fieldError.field()).isEqualTo("keywords");
          assertThat(fieldError.reason()).isEqualTo("키워드는 1개이상 10개 이하로 등록 할 수 있습니다.");
        });

    assertThat(errorResponse.fieldErrors()).hasSize(2);
  }

  @Test
  @DisplayName("관심사 키워드 수정 실패 - 존재하지 않는 관심사 ID")
  void updateInterestKeywords_fail_interestNotFound() throws Exception {
    UUID nonExistentInterestId = UUID.randomUUID();
    UUID requestUserId = UUID.randomUUID();
    InterestUpdateRequestDto requestDto = new InterestUpdateRequestDto(Arrays.asList("새키워드"));
    String errorMessage = String.format("수정할 관심사를 찾을 수 없습니다. ID: %s", nonExistentInterestId);

    given(interestService.updateInterestKeywords(eq(nonExistentInterestId), any(
        InterestUpdateRequestDto.class), eq(requestUserId)))
        .willThrow(new BusinessException(ErrorCode.INTEREST_NOT_FOUND, errorMessage));

    ResultActions resultActions = mockMvc.perform(patch(BASE_URL + "/{interestId}", nonExistentInterestId)
            .header("Monew-Request-User-ID", requestUserId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(requestDto)))
        .andDo(print());

    resultActions
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(ErrorCode.INTEREST_NOT_FOUND.getCode()))
        .andExpect(jsonPath("$.message").value(errorMessage))
        .andExpect(jsonPath("$.status").value(ErrorCode.INTEREST_NOT_FOUND.getStatus().value()));
  }

  @Test
  @DisplayName("[목록조회] 관심사 목록 조회 성공 - 모든 파라미터 제공")
  void searchInterests_success_withAllParams() throws Exception {
    String keyword = "Java";
    String orderBy = "name";
    String direction = "ASC";
    String cursor = "PreviousInterestName";
    String after = "2024-01-01T10:00:00Z";
    int limit = 5;

    InterestDto interestDto = new InterestDto(UUID.randomUUID(), "Java World", List.of("Java", "Programming"), 100L, true);
    CursorPageResponse<InterestDto> mockResponse = CursorPageResponse.of(
        List.of(interestDto), "NextInterestName", "2024-01-02T10:00:00Z", 1L, true
    );

    given(interestService.searchInterests(
        eq(keyword), eq(orderBy), eq(direction), eq(cursor), eq(after), eq(limit), eq(requestUserId)
    )).willReturn(mockResponse);

    ResultActions resultActions = mockMvc.perform(get(BASE_URL)
            .param("keyword", keyword)
            .param("orderBy", orderBy)
            .param("direction", direction)
            .param("cursor", cursor)
            .param("after", after)
            .param("limit", String.valueOf(limit))
            .header("Monew-Request-User-ID", requestUserId.toString())
            .accept(MediaType.APPLICATION_JSON))
        .andDo(print());

    resultActions
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Java World"))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.nextCursor").value("NextInterestName"))
        .andExpect(jsonPath("$.totalElements").value(1L));

    verify(interestService).searchInterests(eq(keyword), eq(orderBy), eq(direction), eq(cursor), eq(after), eq(limit), eq(requestUserId));
  }

  @Test
  @DisplayName("[목록조회] 관심사 목록 조회 성공 - limit 파라미터 누락 시 DTO에서 기본값(50) 사용")
  void searchInterests_success_defaultLimit() throws Exception {
    String orderBy = "name";
    String direction = "DESC";
    int expectedDefaultLimit = 50;

    CursorPageResponse<InterestDto> mockResponse = CursorPageResponse.of(
        Collections.emptyList(), null, null, 0L, false
    );

    given(interestService.searchInterests(
        isNull(), eq(orderBy), eq(direction), isNull(), isNull(), eq(expectedDefaultLimit), eq(requestUserId)
    )).willReturn(mockResponse);

    ResultActions resultActions = mockMvc.perform(get(BASE_URL)
            .param("orderBy", orderBy)
            .param("direction", direction)
            .header("Monew-Request-User-ID", requestUserId.toString())
            .accept(MediaType.APPLICATION_JSON))
        .andDo(print());

    resultActions.andExpect(status().isOk());

    verify(interestService).searchInterests(isNull(), eq(orderBy), eq(direction), isNull(), isNull(), eq(expectedDefaultLimit), eq(requestUserId));
  }

  @Test
  @DisplayName("[목록조회] 관심사 목록 조회 실패 - 필수 파라미터(orderBy) 누락 (DTO 유효성 검사)")
  void searchInterests_fail_validation_orderByMissing() throws Exception {
    ResultActions resultActions = mockMvc.perform(get(BASE_URL)
            .param("direction", "ASC")
            .param("limit", "5")
            .header("Monew-Request-User-ID", requestUserId.toString())
            .accept(MediaType.APPLICATION_JSON))
        .andDo(print());

    resultActions
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()))
        .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_INPUT_VALUE.getMessage()))
        .andExpect(jsonPath("$.fieldErrors").isArray())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("orderBy"))
        .andExpect(jsonPath("$.fieldErrors[0].reason").value("정렬 기준(orderBy)은 필수입니다."));
  }

  @Test
  @DisplayName("[목록조회] 관심사 목록 조회 실패 - limit 파라미터 최소값 미만 (DTO 유효성 검사)")
  void searchInterests_fail_validation_limitTooSmall() throws Exception {
    ResultActions resultActions = mockMvc.perform(get(BASE_URL)
            .param("orderBy", "name")
            .param("direction", "ASC")
            .param("limit", "0")
            .header("Monew-Request-User-ID", requestUserId.toString())
            .accept(MediaType.APPLICATION_JSON))
        .andDo(print());

    resultActions
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()))
        .andExpect(jsonPath("$.fieldErrors").isArray())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("limit"))
        .andExpect(jsonPath("$.fieldErrors[0].reason").value("페이지 크기(limit)는 1 이상이어야 합니다."));
  }

  @Test
  @DisplayName("[삭제] 관심사 삭제 성공")
  void deleteInterest_success() throws Exception {
    UUID interestIdToDelete = UUID.randomUUID();

    doNothing().when(interestService).deleteInterest(eq(interestIdToDelete), eq(requestUserId));

    ResultActions resultActions = mockMvc.perform(delete(BASE_URL + "/{interestId}", interestIdToDelete)
            .header("Monew-Request-User-ID", requestUserId.toString())
            .accept(MediaType.APPLICATION_JSON))
        .andDo(print());

    resultActions
        .andExpect(status().isNoContent());

    verify(interestService).deleteInterest(eq(interestIdToDelete), eq(requestUserId));
  }

  @Test
  @DisplayName("[삭제] 관심사 삭제 실패 - 존재하지 않는 관심사 ID")
  void deleteInterest_fail_interestNotFound() throws Exception {
    UUID nonExistentInterestId = UUID.randomUUID();
    String errorMessage = String.format("삭제할 관심사를 찾을 수 없습니다. ID: %s", nonExistentInterestId);

    doThrow(new BusinessException(ErrorCode.INTEREST_NOT_FOUND, errorMessage))
        .when(interestService).deleteInterest(eq(nonExistentInterestId), eq(requestUserId));

    ResultActions resultActions = mockMvc.perform(delete(BASE_URL + "/{interestId}", nonExistentInterestId)
            .header("Monew-Request-User-ID", requestUserId.toString())
            .accept(MediaType.APPLICATION_JSON))
        .andDo(print());

    resultActions
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(ErrorCode.INTEREST_NOT_FOUND.getCode()))
        .andExpect(jsonPath("$.message").value(errorMessage))
        .andExpect(jsonPath("$.status").value(ErrorCode.INTEREST_NOT_FOUND.getStatus().value()));

    verify(interestService).deleteInterest(eq(nonExistentInterestId), eq(requestUserId));
  }
}
