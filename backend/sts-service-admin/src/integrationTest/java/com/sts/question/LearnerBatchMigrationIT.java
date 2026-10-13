package com.sts.question;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

/** 在容器独立库从旧题目状态升级，比较本批migration与最新init的真实列定义。 */
class LearnerBatchMigrationIT extends AbstractMysqlIntegrationTest {
  @Test void threeMigrationsMatchFullInit() throws Exception {
    var original = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    try (var migratedMysql = new org.testcontainers.containers.MySQLContainer<>("mysql:8.0.45")) {
      migratedMysql.start();
      var ds = new DriverManagerDataSource(migratedMysql.getJdbcUrl(),migratedMysql.getUsername(),migratedMysql.getPassword());
      var jdbc = new JdbcTemplate(ds);
      String full = new String(new ClassPathResource("schema/schema-question.sql").getInputStream().readAllBytes(),StandardCharsets.UTF_8);
      var match = Pattern.compile("(?s)CREATE TABLE `sts_question_user_state`.*?;").matcher(full); assertThat(match.find()).isTrue();
      String previous = match.group().replaceAll("(?m)^.*`(?:handled_wrong_key|wrong_disposition)`.*\n", "");
      new ResourceDatabasePopulator(new ByteArrayResource(previous.getBytes(StandardCharsets.UTF_8))).execute(ds);
      Flyway.configure().dataSource(ds).baselineVersion("20260930180000").load().baseline();
      var result = Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
      assertThat(result.migrationsExecuted).isEqualTo(3);
      for (String table : List.of("sts_question_user_state", "sts_learner_practice_session", "sts_learner_practice_item", "sts_course_favorite", "sts_teacher_follow")) {
        String sql = "SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? ORDER BY ORDINAL_POSITION";
        // 新列在旧表升级后位于末尾，按名称比较以忽略无业务含义的列位置。
        var migrated = jdbc.queryForList(sql,migratedMysql.getDatabaseName(),table);
        var init = original.queryForList(sql,MYSQL.getDatabaseName(),table);
        assertThat(migrated).containsExactlyInAnyOrderElementsOf(init);
      }
      assertThat(Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate().migrationsExecuted).isZero();
    }
  }
}
