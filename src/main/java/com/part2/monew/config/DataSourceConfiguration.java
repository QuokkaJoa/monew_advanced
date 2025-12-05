package com.part2.monew.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class DataSourceConfiguration {
    public static final String MAIN_DATA_SOURCE = "MAIN_DATA_SOURCE";
    public static final String STANDBY_DATA_SOURCE = "STANDBY_DATA_SOURCE";

    @Bean(MAIN_DATA_SOURCE)
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public DataSource masterDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(STANDBY_DATA_SOURCE)
    @ConfigurationProperties(prefix = "spring.datasource.replica")
    public DataSource replicaDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }
}
