package com.part2.monew.service.impl;

import com.part2.monew.dto.response.SubscriptionResponse;
import com.part2.monew.entity.Interest;
import com.part2.monew.entity.User;
import com.part2.monew.entity.UserSubscriber;
import com.part2.monew.global.exception.BusinessException;
import com.part2.monew.global.exception.ErrorCode;
import com.part2.monew.mapper.SubscriptionMapper;
import com.part2.monew.repository.InterestRepository;
import com.part2.monew.repository.UserRepository;
import com.part2.monew.repository.UserSubscriberRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SubscriptionServiceImpl {

  private final UserRepository userRepository;
  private final UserSubscriberRepository userSubscriberRepository;
  private final InterestRepository interestRepository;
  private final SubscriptionMapper subscriptionMapper;

  @Transactional
  public SubscriptionResponse subscribeToInterest(UUID interestId, UUID requestUserId) {
    if (!interestRepository.existsById(interestId)) {
      throw new BusinessException(ErrorCode.INTEREST_NOT_FOUND);
    }

    User userRef = userRepository.getReferenceById(requestUserId);
    Interest interestRef = interestRepository.getReferenceById(interestId);

    UserSubscriber newSubscription = UserSubscriber.builder()
        .user(userRef)
        .interest(interestRef)
        .build();

    try {
      userSubscriberRepository.save(newSubscription);
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.ALREADY_SUBSCRIBED_INTEREST);
    }
    userSubscriberRepository.incrementSubscriberCount(interestId);

    Interest updatedInterest = interestRepository.findById(interestId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTEREST_NOT_FOUND));

    return subscriptionMapper.toSubscriptionResponse(newSubscription, updatedInterest);
  }

  @Transactional
  public void unsubscribeFromInterest(UUID interestId, UUID requestUserId) {
    int deletedCount = userSubscriberRepository.deleteByUserIdAndInterestId(requestUserId,
        interestId);

    if (deletedCount > 0) {
      userSubscriberRepository.decrementSubscriberCount(interestId);
    } else {
      log.info("구독 정보 없음 (이미 취소됨): User(ID:{}), Interest(ID:{})", requestUserId, interestId);
    }
  }
}
