package com.part2.monew.repository;

import com.part2.monew.entity.Interest;
import org.springframework.data.jpa.repository.JpaRepository;
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


}
