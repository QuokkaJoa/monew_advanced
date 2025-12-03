package com.part2.monew.repository;

import com.part2.monew.entity.Interest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface InterestRepository extends JpaRepository<Interest, UUID>,
    InterestRepositoryCustom {

  boolean existsByName(String name);

  @Query("SELECT i.name FROM Interest i")
  List<String> findAllNames();

  @Modifying(clearAutomatically = true)
  @Query("UPDATE Interest i SET i.subscriberCount = i.subscriberCount + 1 WHERE i.id = :id")
  void incrementSubscriberCount(UUID interestId);

  @Modifying(clearAutomatically = true)
  @Query("UPDATE Interest i SET i.subscriberCount = i.subscriberCount -1 WHERE i.id = :id AND i.subscriberCount > 0")
  void decrementSubscriberCount(UUID interestId);
}
