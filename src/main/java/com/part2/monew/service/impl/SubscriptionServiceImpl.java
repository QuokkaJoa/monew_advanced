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
import java.util.Optional;
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

//  @Transactional
//  public SubscriptionResponse subscribeToInterest(UUID interestId, UUID requestUserId) {
//    User user = userRepository.findById(requestUserId)
//        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
//
//    Interest interest = interestRepository.findById(interestId)
//        .orElseThrow(() -> new BusinessException(ErrorCode.INTEREST_NOT_FOUND));
//
//    // 1. 중복 체크 (동시 요청 시 뚫릴 수 있음)
//    if (userSubscriberRepository.existsByUser_IdAndInterest_Id(requestUserId, interestId)) {
//      throw new BusinessException(ErrorCode.ALREADY_SUBSCRIBED_INTEREST);
//    }
//
//    UserSubscriber newSubscription = new UserSubscriber();
//    newSubscription.setUser(user);
//    newSubscription.setInterest(interest);
//    UserSubscriber savedSubscription = userSubscriberRepository.save(newSubscription);
//
//    // 2. 카운트 증가 (Lost Update 발생 지점!)
//    // DB에서 읽어온 값(100)에 1을 더해서 저장(101).
//    // 동시에 여러 명이 읽으면 모두 100을 읽어서 101로 저장함.
//    interest.setSubscriberCount(interest.getSubscriberCount() + 1);
//    // (Dirty Checking에 의해 트랜잭션 종료 시 UPDATE 쿼리 나감)
//
//    return subscriptionMapper.toSubscriptionResponse(savedSubscription, interest);
//  }
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

//  @Transactional
//  public void unsubscribeFromInterest(UUID interestId, UUID requestUserId) {
//    Interest interest = interestRepository.findById(interestId).orElse(null);
//    Optional<UserSubscriber> subscriptionOpt = userSubscriberRepository.findByUser_IdAndInterest_Id(requestUserId, interestId);
//
//    if (subscriptionOpt.isPresent()) {
//      userSubscriberRepository.delete(subscriptionOpt.get());
//
//      if (interest != null) {
//        // 3. 카운트 감소 (Lost Update 발생 지점!)
//        int currentCount = interest.getSubscriberCount();
//        interest.setSubscriberCount(Math.max(0, currentCount - 1));
//      }
//    }
//  }
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
