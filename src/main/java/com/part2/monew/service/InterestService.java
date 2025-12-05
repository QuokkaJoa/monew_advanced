package com.part2.monew.service;

import com.part2.monew.dto.request.InterestRegisterRequestDto;
import com.part2.monew.dto.request.InterestUpdateRequestDto;
import com.part2.monew.dto.response.CursorPageResponse;
import com.part2.monew.dto.response.InterestDto;
import java.util.UUID;

public interface InterestService {

  InterestDto registerInterest(InterestRegisterRequestDto requestDto, UUID requestUserId);

  InterestDto updateInterestKeywords(UUID interestId, InterestUpdateRequestDto requestDto, UUID requestId);

  CursorPageResponse<InterestDto> searchInterests(String keyword, String orderBy, String direction,
      String cursor, String after, int limit, UUID requestUserId);

  void deleteInterest(UUID interestId, UUID requestUserId);
}
