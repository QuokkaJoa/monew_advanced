package com.part2.monew.mapper;

import com.part2.monew.dto.response.SubscriptionResponse;
import com.part2.monew.entity.Interest;
import com.part2.monew.entity.InterestKeyword;
import com.part2.monew.entity.Keyword;
import com.part2.monew.entity.UserSubscriber;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

  @Mapping(source = "subscription.id", target = "id")
  @Mapping(source = "subscription.createdAt", target = "createdAt")

  @Mapping(source = "interest.id", target = "interestId")
  @Mapping(source = "interest.name", target = "interestName")
  @Mapping(source = "interest.interestKeywords", target = "interestKeywords", qualifiedByName = "mapKeywordsToStringList")
  @Mapping(source = "interest.subscriberCount", target = "interestSubscriberCount")
  SubscriptionResponse toSubscriptionResponse(UserSubscriber subscription, Interest interest);

  @Named("mapKeywordsToStringList")
  default List<String> mapKeywordsToStringList(List<InterestKeyword> interestKeywords) {
    if (interestKeywords == null || interestKeywords.isEmpty()) {
      return Collections.emptyList();
    }
    return interestKeywords.stream()
        .map(InterestKeyword::getKeyword)
        .map(Keyword::getName)
        .collect(Collectors.toList());
  }
}
