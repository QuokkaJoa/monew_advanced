package com.part2.monew.config;

import com.part2.monew.entity.DataSourceType;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import static com.part2.monew.config.DataSourceConfiguration.MAIN_DATA_SOURCE;
import static com.part2.monew.config.DataSourceConfiguration.STANDBY_DATA_SOURCE;


@EnableJpaRepositories(
        basePackages = {"com.part2.monew.repository"},
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager"
)
@Profile("!test")
@Configuration
public class RoutingDataSourceConfiguration {


    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String ddlAuto;
    private final String ROUTING_DATA_SOURCE = "ROUTING_DATA_SOURCE";
    private final String DATA_SOURCE = "DATA_SOURCE";

    @Bean(DATA_SOURCE)
    @Primary
    public DataSource dataSource(@Qualifier(ROUTING_DATA_SOURCE) DataSource routingDataSource) {
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    @Bean(ROUTING_DATA_SOURCE)
    public DataSource routingDataSource(
            @Qualifier(MAIN_DATA_SOURCE) final DataSource mainDataSource,
            @Qualifier(STANDBY_DATA_SOURCE) final DataSource standbyDataSource) {

        RoutingDataSource routingDataSource = new RoutingDataSource();

        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put(DataSourceType.MAIN, mainDataSource);
        dataSourceMap.put(DataSourceType.STANDBY, standbyDataSource);

        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(mainDataSource);

        return routingDataSource;
    }

    @Bean("entityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            @Qualifier(DATA_SOURCE) DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean entityManagerFactory
                = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactory.setDataSource(dataSource);
        entityManagerFactory.setPackagesToScan("com.part2.monew.entity");
        entityManagerFactory.setJpaVendorAdapter(this.jpaVendorAdapter());
        entityManagerFactory.setPersistenceUnitName("entityManager");
        entityManagerFactory.setJpaVendorAdapter(this.jpaVendorAdapter());

        // DDL 실행을 보장하기 위해 프로퍼티를 명시적으로 설정
        Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.hbm2ddl.auto", ddlAuto);
        entityManagerFactory.setJpaProperties(jpaProperties);
        return entityManagerFactory;
    }

    private JpaVendorAdapter jpaVendorAdapter() {
        HibernateJpaVendorAdapter hibernateJpaVendorAdapter = new HibernateJpaVendorAdapter();
        hibernateJpaVendorAdapter.setGenerateDdl(false);
        hibernateJpaVendorAdapter.setShowSql(false);
        hibernateJpaVendorAdapter.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");
        return hibernateJpaVendorAdapter;
    }

    @Bean("transactionManager")
    public PlatformTransactionManager platformTransactionManager(
            @Qualifier("entityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        JpaTransactionManager jpaTransactionManager = new JpaTransactionManager();
        jpaTransactionManager.setEntityManagerFactory(entityManagerFactory.getObject());
        return jpaTransactionManager;
    }

}
