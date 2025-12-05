package com.part2.monew.repository;

import com.part2.monew.dto.response.CursorPageResponse;
import com.part2.monew.dto.response.InterestDto;
import com.part2.monew.entity.Interest;
import com.part2.monew.entity.QInterest;
import com.part2.monew.entity.QInterestKeyword;
import com.part2.monew.entity.QKeyword;
import com.part2.monew.entity.QUserSubscriber;
import com.part2.monew.mapper.InterestMapper;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class InterestRepositoryCustomImpl implements InterestRepositoryCustom {

  private final JPAQueryFactory queryFactory;
  private final InterestMapper interestMapper;

  private static final QInterest interest = QInterest.interest;
  private static final QKeyword keyword = QKeyword.keyword;
  private static final QInterestKeyword interestKeyword = QInterestKeyword.interestKeyword;
  private static final QUserSubscriber userSubscriber = QUserSubscriber.userSubscriber;

  public InterestRepositoryCustomImpl(JPAQueryFactory queryFactory, InterestMapper interestMapper) {
    this.queryFactory = queryFactory;
    this.interestMapper = interestMapper;
  }

  @Override
  public CursorPageResponse<InterestDto> searchInterestsWithQueryDsl(
      String keywordSearchTerm, String orderByField, String direction,
      String primaryCursorValue, String idCursorValue, int limit, UUID requestUserId) {

    List<OrderSpecifier<?>> orderSpecifiers = buildOrderByClause(orderByField, direction);
    BooleanExpression predicate = buildWhereClause(keywordSearchTerm, orderByField, direction, primaryCursorValue, idCursorValue);

    JPAQuery<UUID> idQuery = queryFactory
        .select(interest.id)
        .from(interest)
        .where(predicate);

    if (keywordSearchTerm != null && !keywordSearchTerm.isEmpty()) {
      idQuery.leftJoin(interest.interestKeywords, interestKeyword)
          .leftJoin(interestKeyword.keyword, keyword);
    }

    List<UUID> interestIds = idQuery
        .orderBy(orderSpecifiers.toArray(new OrderSpecifier[0]))
        .limit(limit + 1)
        .fetch()
        .stream().distinct().collect(Collectors.toList());

    boolean hasNext = interestIds.size() > limit;
    List<UUID> contentInterestIds = hasNext ? interestIds.subList(0, limit) : interestIds;

    if (contentInterestIds.isEmpty()) {
      long totalElements = countTotalElements(keywordSearchTerm);
      return CursorPageResponse.of(Collections.emptyList(), null, null, totalElements, false);
    }

    List<Tuple> results = queryFactory
        .select(
            interest,
            JPAExpressions.selectOne()
                .from(userSubscriber)
                .where(userSubscriber.user.id.eq(requestUserId)
                    .and(userSubscriber.interest.id.eq(interest.id)))
                .exists()
        )
        .from(interest)
        .leftJoin(interest.interestKeywords, interestKeyword).fetchJoin()
        .leftJoin(interestKeyword.keyword, keyword).fetchJoin()
        .where(interest.id.in(contentInterestIds))
        .orderBy(orderSpecifiers.toArray(new OrderSpecifier[0]))
        .distinct()
        .fetch();

    Map<UUID, Tuple> resultMap = results.stream()
        .collect(Collectors.toMap(
            t -> Objects.requireNonNull(t.get(interest)).getId(),
            t -> t,
            (t1, t2) -> t1
        ));

    List<InterestDto> interestDtos = contentInterestIds.stream()
        .map(id -> {
          Tuple tuple = resultMap.get(id);
          if (tuple == null) return null;
          Interest fetchedInterest = tuple.get(interest);
          Boolean isSubscribed = tuple.get(1, Boolean.class);
          return interestMapper.toDto(fetchedInterest, Boolean.TRUE.equals(isSubscribed));
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    String nextPrimaryCursor = null;
    String nextIdCursor = null;
    if (hasNext && !interestDtos.isEmpty()) {
      InterestDto lastDto = interestDtos.get(interestDtos.size() - 1);
      if ("name".equalsIgnoreCase(orderByField)) {
        nextPrimaryCursor = lastDto.name();
      } else {
        nextPrimaryCursor = lastDto.subscriberCount().toString();
      }
      nextIdCursor = lastDto.id().toString();
    }

    long totalElements = countTotalElements(keywordSearchTerm);

    return CursorPageResponse.of(
        interestDtos,
        nextPrimaryCursor,
        nextIdCursor,
        totalElements,
        hasNext
    );
  }

  private Long countTotalElements(String keywordSearchTerm) {
    BooleanExpression countPredicate = buildWhereClause(keywordSearchTerm, null, null, null, null);
    JPAQuery<Long> countBaseQuery = queryFactory.select(interest.countDistinct()).from(interest);
    if (keywordSearchTerm != null && !keywordSearchTerm.isEmpty()) {
      countBaseQuery.leftJoin(interest.interestKeywords, interestKeyword)
          .leftJoin(interestKeyword.keyword, keyword);
    }
    Long total = countBaseQuery.where(countPredicate).fetchOne();
    return total == null ? 0L : total;
  }

  private BooleanExpression buildWhereClause(String keywordSearchTerm, String orderByField, String direction,
      String primaryCursorValue, String idCursorValue) {
    BooleanExpression predicate = null;

    if (keywordSearchTerm != null && !keywordSearchTerm.isEmpty()) {
      predicate = interest.name.containsIgnoreCase(keywordSearchTerm)
          .or(interest.interestKeywords.any().keyword.name.containsIgnoreCase(keywordSearchTerm));
    }

    if (orderByField != null && primaryCursorValue != null && !primaryCursorValue.isEmpty()) {
      boolean isAsc = "ASC".equalsIgnoreCase(direction);

      UUID idCursor = null;
      if (idCursorValue != null && !idCursorValue.isEmpty()) {
        try {
          idCursor = UUID.fromString(idCursorValue);
        } catch (Exception e) {
          log.warn("ID 커서 파싱 오류: {}", idCursorValue, e);
        }
      }
      if (idCursor == null) {
        return predicate;
      }

      BooleanExpression cursorCondition = null;
      if ("name".equalsIgnoreCase(orderByField)) {
        if (isAsc) {
          cursorCondition = interest.name.gt(primaryCursorValue)
              .or(interest.name.eq(primaryCursorValue).and(interest.id.gt(idCursor)));
        } else {
          cursorCondition = interest.name.lt(primaryCursorValue)
              .or(interest.name.eq(primaryCursorValue).and(interest.id.lt(idCursor)));
        }
      } else if ("subscriberCount".equalsIgnoreCase(orderByField)) {
        try {
          Integer countCursor = Integer.parseInt(primaryCursorValue);
          if (isAsc) {
            cursorCondition = interest.subscriberCount.gt(countCursor)
                .or(interest.subscriberCount.eq(countCursor).and(interest.id.gt(idCursor)));
          } else {
            cursorCondition = interest.subscriberCount.lt(countCursor)
                .or(interest.subscriberCount.eq(countCursor).and(interest.id.lt(idCursor)));
          }
        } catch (NumberFormatException e) {
          log.warn("구독자 수 커서 파싱 오류: {}", primaryCursorValue, e);
        }
      }
      predicate = (predicate == null) ? cursorCondition : predicate.and(cursorCondition);
    }
    return predicate;
  }

  private List<OrderSpecifier<?>> buildOrderByClause(String orderByField, String direction) {
    List<OrderSpecifier<?>> orders = new ArrayList<>();
    boolean isAsc = "ASC".equalsIgnoreCase(direction);
    Order orderDirection = isAsc ? Order.ASC : Order.DESC;

    if ("name".equalsIgnoreCase(orderByField)) {
      orders.add(new OrderSpecifier<>(orderDirection, interest.name));
    } else if ("subscriberCount".equalsIgnoreCase(orderByField)) {
      orders.add(new OrderSpecifier<>(orderDirection, interest.subscriberCount));
    } else {
      orders.add(new OrderSpecifier<>(Order.DESC, interest.createdAt));
    }

    orders.add(new OrderSpecifier<>(orderDirection, interest.id));
    return orders;
  }
}
