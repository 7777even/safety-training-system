package com.sts.init.service;

import com.sts.init.config.InitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

/**
 * 在模块化完整 SQL 建库后登记 Flyway 基线，供 Admin 后续只执行增量迁移。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlywayBaselineInitializer {

  private final DataSource dataSource;
  private final InitProperties initProperties;

  public void baseline() {
    String schemaVersion = initProperties.getSchemaVersion();
    if (schemaVersion == null || !schemaVersion.matches("\\d{14}")) {
      throw new IllegalStateException("sts.init.schema-version 必须是 14 位数字版本");
    }

    Flyway.configure()
      .dataSource(dataSource)
      .baselineVersion(MigrationVersion.fromVersion(schemaVersion))
      .baselineDescription("admin-init modular full schema")
      .baselineOnMigrate(false)
      .cleanDisabled(true)
      .load()
      .baseline();
    log.info("Flyway baseline {} registered successfully.", schemaVersion);
  }
}
