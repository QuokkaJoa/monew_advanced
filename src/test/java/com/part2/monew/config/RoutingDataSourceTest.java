package com.part2.monew.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.part2.monew.entity.DataSourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class RoutingDataSourceTest {

  private RoutingDataSource routingDataSource;

  @BeforeEach
  void setUp() {
    routingDataSource = new RoutingDataSource();
    TransactionSynchronizationManager.initSynchronization();
  }

  @AfterEach
  void tearDown() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  @Test
  @DisplayName("ReadOnly 트랜잭션(조회)일 경우 STANDBY DataSource를 선택해야 한다")
  void determineCurrentLookupKey_ReadOnly() {
    // given
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

    // when
    Object lookupKey = routingDataSource.determineCurrentLookupKey();

    // then
    assertThat(lookupKey).isEqualTo(DataSourceType.STANDBY);
  }

  @Test
  @DisplayName("일반 트랜잭션(쓰기)일 경우 MAIN DatatSource를 선택해야 한다")
  void determineCurrentLookupKey_Write() {
    // given
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

    // when
    Object lookupKey = routingDataSource.determineCurrentLookupKey();

    //then
    assertThat(lookupKey).isEqualTo(DataSourceType.MAIN);
  }

  @Test
  @DisplayName("트랜잭션 설정이 없을 경우 기본적으로 MAIN DataSource를 선택해야 한다")
  void determineCurrentLookupKey_Default() {
    // given
    // readOnly 설정을 아예 안 했을 때 (null 상태 등)

    // when
    Object lookupKey = routingDataSource.determineCurrentLookupKey();

    //then
    assertThat(lookupKey).isEqualTo(DataSourceType.MAIN);
  }
}
