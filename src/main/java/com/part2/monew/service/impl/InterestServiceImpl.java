package com.part2.monew.service.impl;

import com.part2.monew.dto.request.InterestRegisterRequestDto;
import com.part2.monew.dto.request.InterestUpdateRequestDto;
import com.part2.monew.dto.response.CursorPageResponse;
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
import com.part2.monew.service.InterestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class InterestServiceImpl implements InterestService {
  private final InterestRepository interestRepository;
  private final KeywordRepository keywordRepository;
  private final InterestMapper interestMapper;
  private final JaroWinklerSimilarity jaroWinklerSimilarity = new JaroWinklerSimilarity();

  @Transactional
  @Override
  public InterestDto registerInterest(InterestRegisterRequestDto requestDto, UUID requestUserId) {
    String newInterestName = requestDto.name();
    if (interestRepository.existsByName(requestDto.name())) {
      throw new BusinessException(ErrorCode.INTEREST_NAME_ALREADY_EXISTS,
          String.format("이미 존재하는 관심사 이름입니다: %s", newInterestName));
    }

    List<String> existingInterestNames = interestRepository.findAllNames();
    for (String existingName : existingInterestNames) {
      double similarityScore = jaroWinklerSimilarity.apply(newInterestName.toLowerCase(),
          existingName.toLowerCase());
      double similarityPercent = similarityScore * 100.0;

      if (similarityPercent >= 80.0) {
        throw new SimilarInterestExistsException(
            String.format("유사한 이름의 관심사 '%s'가(이) 이미 존재합니다 (유사도: %.2f%%). 다른 이름을 사용해주세요", existingName,
                similarityPercent));
      }
    }

    Interest interest = interestMapper.fromRegisterRequestDto(requestDto);
    interest.setSubscriberCount(0);

    List<InterestKeyword> newInterestKeywords = new ArrayList<>();
    for (String keywordName : requestDto.keywords()) {
      Keyword keywordEntity = keywordRepository.findByName(keywordName)
          .orElseGet(() -> {
            Keyword newKeyword = new Keyword();
            newKeyword.setName(keywordName);
            return keywordRepository.save(newKeyword);
          });

      InterestKeyword interestKeyword = new InterestKeyword();
      interestKeyword.setInterest(interest);
      interestKeyword.setKeyword(keywordEntity);

      newInterestKeywords.add(interestKeyword);
    }
    interest.setInterestKeywords(newInterestKeywords);

    Interest savedInterest = interestRepository.save(interest);
    boolean subscribedByMe = false;
    return interestMapper.toDto(savedInterest, subscribedByMe);
  }

  @Transactional
  @Override
  public InterestDto updateInterestKeywords(UUID interestId, InterestUpdateRequestDto requestDto,
      UUID requestId) {
    Interest interestToUpdate = interestRepository.findById(interestId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTEREST_NOT_FOUND,
            String.format("수정할 관심사를 찾을 수 없습니다. ID: %s", interestId)));

    List<Keyword> newKeywordEntities = new ArrayList<>();
    if (requestDto.keywords() != null) {
      for (String keywordName : requestDto.keywords()) {
        Keyword keywordEntity = keywordRepository.findByName(keywordName)
            .orElseGet(() -> {
              Keyword newKeyword = new Keyword();
              newKeyword.setName(keywordName);
              return keywordRepository.save(newKeyword);
            });
        newKeywordEntities.add(keywordEntity);
      }
    }

    interestToUpdate.getInterestKeywords().clear();

    for (Keyword keywordEntity : newKeywordEntities) {
      InterestKeyword newInterestKeyword = new InterestKeyword();
      newInterestKeyword.setInterest(interestToUpdate);
      newInterestKeyword.setKeyword(keywordEntity);
      interestToUpdate.getInterestKeywords().add(newInterestKeyword);
    }

    Interest updatedInterest = interestRepository.save(interestToUpdate);
    boolean subscribedByMe = false;

    return interestMapper.toDto(updatedInterest, subscribedByMe);
  }

  @Transactional(readOnly = true)
  @Override
  public CursorPageResponse<InterestDto> searchInterests(
      String keyword, String orderBy, String direction,
      String cursor, String after, int limit, UUID requestUserId) {

    if (!("name".equalsIgnoreCase(orderBy) || "subscriberCount".equalsIgnoreCase(orderBy))) {
      log.warn("Invalid orderBy parameter: {}", orderBy);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "정렬 기준(orderBy)은 'name' 또는 'subscriberCount'만 가능합니다.");
    }
    if (!("ASC".equalsIgnoreCase(direction) || "DESC".equalsIgnoreCase(direction))) {
      log.warn("Invalid direction parameter: {}", direction);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "정렬 방향(direction)은 'ASC' 또는 'DESC'만 가능합니다.");
    }
    if (limit < 1 || limit > 50) {
      log.warn("Invalid limit parameter: {}", limit);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "페이지 크기(limit)는 1에서 50 사이여야 합니다.");
    }

    return interestRepository.searchInterestsWithQueryDsl(
        keyword,
        orderBy,
        direction,
        cursor,
        after,
        limit,
        requestUserId
    );
  }

  @Transactional
  @Override
  public void deleteInterest(UUID interestId, UUID requestUserId) {
    Interest interestToDelete = interestRepository.findById(interestId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTEREST_NOT_FOUND,
            String.format("삭제할 관심사를 찾을 수 없습니다. ID: %s", interestId)));

    log.info("관심사 삭제 요청 - ID: {}, 요청자 ID: {}", interestId, requestUserId);

    interestRepository.delete(interestToDelete);

    log.info("관심사 삭제 완료 - ID: {}", interestId);
  }
}
