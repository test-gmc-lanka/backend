package com.gmc.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ddl-auto=update adds new columns but never relaxes an existing NOT NULL, so databases
 * created before custom-design maintenance tasks still require a product. The statement
 * is idempotent and safe to run on every start.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaFixRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE maintenance MODIFY product_id BIGINT NULL");
        } catch (DataAccessException e) {
            log.warn("Could not make maintenance.product_id nullable: {}", e.getMessage());
        }
    }
}
