package com.part2.monew.service;

import com.part2.monew.dto.request.InterestRegisterRequestDto;
import com.part2.monew.dto.request.InterestUpdateRequestDto;
import com.part2.monew.dto.response.InterestDto;
import com.part2.monew.entity.Interest;
import com.part2.monew.entity.InterestKeyword;
import com.part2.monew.entity.Keyword;
import com.part2.monew.global.exception.BusinessException;
import com.part2.monew.global.exception.ErrorCode;
import com.part2.monew.global.exception.interest.SimilarInterestExistsException;
import com.part2.monew.mapper.InterestMapper;
import com.part2.monew.repository.InterestRepository;
import com.part2.monew.repository.KeywordRepository;
import com.part2.monew.service.impl.InterestServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class InterestServiceImplTest {

  @Mock
  private InterestRepository interestRepository;

  @Mock
  private KeywordRepository keywordRepository;

  @Mock
  private InterestMapper interestMapper;

  @InjectMocks
  private InterestServiceImpl interestService;

  private UUID requestUserId;

  @BeforeEach
  void commonSetUp() {
    requestUserId = UUID.randomUUID();
  }

  @Test
  @DisplayName("[등록] 관심사 등록 성공 - 모든 키워드가 신규")
  void registerInterest_success_allNewKeywords() {
    InterestRegisterRequestDto registerRequestDto = new InterestRegisterRequestDto("새 관심사", Arrays.asList("키워드1", "키워드2"));

    Interest interestFromMapper = new Interest();
    interestFromMapper.setName(registerRequestDto.name());

    Interest savedInterest = new Interest();
    savedInterest.setId(UUID.randomUUID());
    savedInterest.setName(registerRequestDto.name());
    savedInterest.setSubscriberCount(0);

    InterestDto expectedResponseDto = new InterestDto(
        savedInterest.getId(),
        savedInterest.getName(),
        Arrays.asList("키워드1", "키워드2"),
        0L,
        false
    );

    given(interestRepository.existsByName(registerRequestDto.name())).willReturn(false);
    given(interestRepository.findAllNames()).willReturn(Collections.emptyList());
    given(interestMapper.fromRegisterRequestDto(registerRequestDto)).willReturn(interestFromMapper);

    Keyword keyword1Entity = new Keyword(); keyword1Entity.setId(UUID.randomUUID()); keyword1Entity.setName("키워드1");
    Keyword keyword2Entity = new Keyword(); keyword2Entity.setId(UUID.randomUUID()); keyword2Entity.setName("키워드2");

    given(keywordRepository.findByName("키워드1")).willReturn(Optional.empty());
    given(keywordRepository.findByName("키워드2")).willReturn(Optional.empty());

    given(keywordRepository.save(any(Keyword.class)))
        .willReturn(keyword1Entity)
        .willReturn(keyword2Entity);

    ArgumentCaptor<Interest> interestCaptorForSave = ArgumentCaptor.forClass(Interest.class);
    given(interestRepository.save(interestCaptorForSave.capture())).willReturn(savedInterest);

    given(interestMapper.toDto(savedInterest, false)).willReturn(expectedResponseDto);

    InterestDto actualDto = interestService.registerInterest(registerRequestDto, requestUserId);

    assertThat(actualDto).isNotNull();
    assertThat(actualDto.id()).isEqualTo(expectedResponseDto.id());
    assertThat(actualDto.name()).isEqualTo(expectedResponseDto.name());
    assertThat(actualDto.keywords()).containsExactlyInAnyOrderElementsOf(expectedResponseDto.keywords());

    Interest capturedForSave = interestCaptorForSave.getValue();
    assertThat(capturedForSave.getName()).isEqualTo(registerRequestDto.name());
    assertThat(capturedForSave.getSubscriberCount()).isZero();
    assertThat(capturedForSave.getInterestKeywords()).hasSize(2);
    List<String> savedKeywordNames = capturedForSave.getInterestKeywords().stream()
        .map(ik -> ik.getKeyword().getName())
        .collect(Collectors.toList());
    assertThat(savedKeywordNames).containsExactlyInAnyOrder("키워드1", "키워드2");

    verify(interestRepository).existsByName(registerRequestDto.name());
    verify(interestRepository).findAllNames();
    verify(interestMapper).fromRegisterRequestDto(registerRequestDto);
    verify(keywordRepository).findByName("키워드1");
    verify(keywordRepository).findByName("키워드2");

    ArgumentCaptor<Keyword> keywordSaveCaptor = ArgumentCaptor.forClass(Keyword.class);
    verify(keywordRepository, times(2)).save(keywordSaveCaptor.capture());
    List<Keyword> capturedSavedKeywords = keywordSaveCaptor.getAllValues();
    assertThat(capturedSavedKeywords.get(0).getName()).isEqualTo("키워드1");
    assertThat(capturedSavedKeywords.get(1).getName()).isEqualTo("키워드2");

    verify(interestRepository).save(any(Interest.class));
    verify(interestMapper).toDto(savedInterest, false);
  }

  @Test
  @DisplayName("[등록] 관심사 등록 성공 - 일부 기존 키워드 사용")
  void registerInterest_success_withMixedKeywords() {
    InterestRegisterRequestDto registerRequestDto = new InterestRegisterRequestDto("혼합 관심사", Arrays.asList("기존키워드", "신규키워드"));

    Interest interestFromMapper = new Interest();
    interestFromMapper.setName(registerRequestDto.name());

    Interest savedInterest = new Interest();
    savedInterest.setId(UUID.randomUUID());
    savedInterest.setName(registerRequestDto.name());
    savedInterest.setSubscriberCount(0);

    InterestDto expectedResponseDto = new InterestDto(
        savedInterest.getId(),
        savedInterest.getName(),
        registerRequestDto.keywords(),
        0L,
        false
    );

    given(interestRepository.existsByName(registerRequestDto.name())).willReturn(false);
    given(interestRepository.findAllNames()).willReturn(Collections.emptyList());
    given(interestMapper.fromRegisterRequestDto(registerRequestDto)).willReturn(interestFromMapper);

    Keyword existingKeyword = new Keyword(); existingKeyword.setId(UUID.randomUUID()); existingKeyword.setName("기존키워드");
    Keyword newKeyword = new Keyword(); newKeyword.setId(UUID.randomUUID()); newKeyword.setName("신규키워드");

    given(keywordRepository.findByName("기존키워드")).willReturn(Optional.of(existingKeyword));
    given(keywordRepository.findByName("신규키워드")).willReturn(Optional.empty());
    given(keywordRepository.save(argThat(k -> k.getName().equals("신규키워드")))).willReturn(newKeyword);

    given(interestRepository.save(any(Interest.class))).willReturn(savedInterest);
    given(interestMapper.toDto(savedInterest, false)).willReturn(expectedResponseDto);

    InterestDto actualDto = interestService.registerInterest(registerRequestDto, requestUserId);

    assertThat(actualDto).isNotNull();
    assertThat(actualDto.name()).isEqualTo(registerRequestDto.name());

    verify(keywordRepository).findByName("기존키워드");
    verify(keywordRepository).findByName("신규키워드");
    verify(keywordRepository, times(1)).save(argThat(k -> k.getName().equals("신규키워드"))); // 신규키워드만 저장
    verify(interestRepository).save(any(Interest.class));
  }


  @Test
  @DisplayName("[등록] 관심사 등록 실패 - 이미 존재하는 이름")
  void registerInterest_fail_exactNameExists() {
    InterestRegisterRequestDto registerRequestDto = new InterestRegisterRequestDto("중복된 관심사", List.of("키워드"));
    given(interestRepository.existsByName(registerRequestDto.name())).willReturn(true);

    BusinessException exception = assertThrows(BusinessException.class, () -> {
      interestService.registerInterest(registerRequestDto, requestUserId);
    });
    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTEREST_NAME_ALREADY_EXISTS);
    assertThat(exception.getDetailMessage()).isEqualTo(String.format("이미 존재하는 관심사 이름입니다: %s", registerRequestDto.name()));
  }

  @Test
  @DisplayName("[등록] 관심사 등록 실패 - 유사한 이름 존재 (80% 이상)")
  void registerInterest_fail_similarNameExists() {
    String newName = "매우비슷한이름";
    String existingSimilarName = "매우비슷한이름이다";
    InterestRegisterRequestDto similarRequestDto = new InterestRegisterRequestDto(newName, Arrays.asList("키워드A"));

    given(interestRepository.existsByName(newName)).willReturn(false);
    given(interestRepository.findAllNames()).willReturn(List.of(existingSimilarName, "완전다른이름1", "완전다른이름2"));

    SimilarInterestExistsException exception = assertThrows(SimilarInterestExistsException.class, () -> {
      interestService.registerInterest(similarRequestDto, requestUserId);
    });

    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SIMILAR_INTEREST_EXISTS);
    assertThat(exception.getDetailMessage()).contains(existingSimilarName);
    assertThat(exception.getDetailMessage()).contains("유사한 이름의 관심사");
  }

  @Test
  @DisplayName("[수정] 관심사 키워드 수정 성공 - 모든 키워드가 신규, 기존 키워드는 삭제")
  void updateInterestKeywords_success_allNewKeywords_oldRemoved() {
    UUID existingInterestId = UUID.randomUUID();
    Interest existingInterest = new Interest();
    existingInterest.setId(existingInterestId);
    existingInterest.setName("기존 관심사");
    existingInterest.setSubscriberCount(5);
    Keyword oldKeywordEntity = new Keyword(); oldKeywordEntity.setName("옛날키워드"); oldKeywordEntity.setId(UUID.randomUUID());
    InterestKeyword oldIk = new InterestKeyword(); oldIk.setInterest(existingInterest); oldIk.setKeyword(oldKeywordEntity);
    existingInterest.setInterestKeywords(new ArrayList<>(List.of(oldIk)));

    InterestUpdateRequestDto updateRequestDto = new InterestUpdateRequestDto(Arrays.asList("새키워드A", "새키워드B"));

    InterestDto expectedUpdatedDto = new InterestDto(
        existingInterestId,
        existingInterest.getName(),
        updateRequestDto.keywords(),
        (long) existingInterest.getSubscriberCount(),
        false
    );

    given(interestRepository.findById(existingInterestId)).willReturn(Optional.of(existingInterest));

    Keyword newKeywordA = new Keyword(); newKeywordA.setId(UUID.randomUUID()); newKeywordA.setName("새키워드A");
    Keyword newKeywordB = new Keyword(); newKeywordB.setId(UUID.randomUUID()); newKeywordB.setName("새키워드B");

    given(keywordRepository.findByName("새키워드A")).willReturn(Optional.empty());
    given(keywordRepository.findByName("새키워드B")).willReturn(Optional.empty());

    given(keywordRepository.save(any(Keyword.class)))
        .willReturn(newKeywordA)
        .willReturn(newKeywordB);

    ArgumentCaptor<Interest> interestCaptor = ArgumentCaptor.forClass(Interest.class);
    given(interestRepository.save(interestCaptor.capture())).willAnswer(invocation -> invocation.getArgument(0));

    given(interestMapper.toDto(any(Interest.class), eq(false))).willReturn(expectedUpdatedDto);

    InterestDto actualUpdatedDto = interestService.updateInterestKeywords(existingInterestId, updateRequestDto, requestUserId);

    assertThat(actualUpdatedDto).isNotNull();
    assertThat(actualUpdatedDto.id()).isEqualTo(existingInterestId);
    assertThat(actualUpdatedDto.keywords()).containsExactlyInAnyOrder("새키워드A", "새키워드B");

    Interest capturedInterest = interestCaptor.getValue();
    assertThat(capturedInterest.getInterestKeywords()).hasSize(2);
    Set<String> capturedKeywordNames = capturedInterest.getInterestKeywords().stream()
        .map(ik -> ik.getKeyword().getName())
        .collect(Collectors.toSet());
    assertThat(capturedKeywordNames).containsExactlyInAnyOrder("새키워드A", "새키워드B");

    verify(interestRepository).findById(existingInterestId);

    ArgumentCaptor<Keyword> keywordSaveCaptor = ArgumentCaptor.forClass(Keyword.class);
    verify(keywordRepository, times(2)).save(keywordSaveCaptor.capture());
    List<Keyword> capturedSavedKeywords = keywordSaveCaptor.getAllValues();
    assertThat(capturedSavedKeywords.get(0).getName()).isEqualTo("새키워드A");
    assertThat(capturedSavedKeywords.get(1).getName()).isEqualTo("새키워드B");

    verify(interestRepository).save(any(Interest.class));
    verify(interestMapper).toDto(any(Interest.class), eq(false));
  }

  @Test
  @DisplayName("[수정] 관심사 키워드 수정 실패 - 존재하지 않는 관심사 ID")
  void updateInterestKeywords_fail_interestNotFound() {
    UUID nonExistentInterestId = UUID.randomUUID();
    InterestUpdateRequestDto updateRequestDto = new InterestUpdateRequestDto(List.of("어떤키워드"));
    given(interestRepository.findById(nonExistentInterestId)).willReturn(Optional.empty());

    BusinessException exception = assertThrows(BusinessException.class, () -> {
      interestService.updateInterestKeywords(nonExistentInterestId, updateRequestDto, requestUserId);
    });
    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTEREST_NOT_FOUND);
    assertThat(exception.getDetailMessage()).isEqualTo(String.format("수정할 관심사를 찾을 수 없습니다. ID: %s", nonExistentInterestId));

    verify(keywordRepository, never()).findByName(anyString());
  }
}
