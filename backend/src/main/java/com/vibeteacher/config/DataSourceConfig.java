package com.vibeteacher.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.vibeteacher.service.KeyVaultService;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DataSourceConfig {
    private static final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(KeyVaultService keyVaultService,
                                 @Value("${spring.datasource.url:}") String configuredUrl,
                                 @Value("${spring.datasource.username:}") String configuredUsername,
                                 @Value("${spring.datasource.password:}") String configuredPassword,
                                 @Value("${spring.datasource.driver-class-name:}") String configuredDriver) {
        String mysqlDb = safeTrim(keyVaultService.getMysqlDb());
        String mysqlHost = safeTrim(keyVaultService.getMysqlHost());
        String mysqlUsername = safeTrim(keyVaultService.getMysqlUsername());
        String mysqlPassword = safeTrim(keyVaultService.getMysqlPassword());
        String mysqlPort = safeTrim(keyVaultService.getMysqlPort());

        boolean hasMySqlSecrets = isPresent(mysqlDb)
            && isPresent(mysqlHost)
            && isPresent(mysqlUsername)
            && isPresent(mysqlPassword)
            && isPresent(mysqlPort);

        String jdbcUrl;
        String username;
        String password;
        String driverClassName;

        if (hasMySqlSecrets) {
            jdbcUrl = buildMySqlJdbcUrl(mysqlHost, mysqlPort, mysqlDb);
            username = mysqlUsername;
            password = mysqlPassword;
            driverClassName = "com.mysql.cj.jdbc.Driver";
            logger.info("Datasource source=KeyVault, dbType=mysql, host={}, port={}, db={}", mysqlHost, mysqlPort, mysqlDb);
        } else {
            jdbcUrl = configuredUrl;
            username = configuredUsername;
            password = configuredPassword;
            driverClassName = configuredDriver;
            logger.warn("Datasource source=application/env fallback. Missing one or more MYSQL-* KeyVault secrets.");
        }

        driverClassName = normalizeDriver(driverClassName, jdbcUrl);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setMaximumPoolSize(8);
        dataSource.setMinimumIdle(1);
        dataSource.setPoolName("vibeteacher-hikari");

        logger.info("Datasource configured. driver={}, url={}", driverClassName, maskJdbcUrl(jdbcUrl));
        return dataSource;
    }

    private String buildMySqlJdbcUrl(String host, String port, String db) {
        return "jdbc:mysql://" + host + ":" + port + "/" + db
            + "?useUnicode=true&characterEncoding=utf8&useSSL=true&requireSSL=true"
            + "&verifyServerCertificate=true&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    }

    private String maskJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return "null";
        }
        int q = jdbcUrl.indexOf('?');
        return q < 0 ? jdbcUrl : jdbcUrl.substring(0, q) + "?...";
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeDriver(String candidateDriver, String jdbcUrl) {
        if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:h2:")) {
            return "org.h2.Driver";
        }
        if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        return isPresent(candidateDriver) ? candidateDriver : "com.mysql.cj.jdbc.Driver";
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
