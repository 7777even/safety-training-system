package com.sts.init;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
  classes = AdminInitApplication.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AdminInitFlywayIT {

  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.45");

  static {
    MYSQL.start();
  }

  @Autowired
  private JdbcTemplate jdbcTemplate;

  /** 当前 init 快照的基线版本：取自 sts.init.schema-version，避免升级快照时断言漏改。 */
  @Value("${sts.init.schema-version}")
  private String schemaVersion;

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
  }

  @Test
  void shouldInitializeSchemaWithFlywayBeforeWritingSeedData() {
    assertThat(jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM flyway_schema_history "
        + "WHERE type = 'BASELINE' AND version = ? AND success = 1",
      Long.class, schemaVersion)).isEqualTo(1L);
    assertThat(jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM us_sys_user WHERE deleted = 0",
      Long.class)).isPositive();

  }
}
