package com.sts.learner;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

/** 定向核验增量与完整init一致，重复迁移不重复建表或授予角色。 */
class LearnerHistoryMigrationIT {
  @Test void historyUpgradeAndInitDefineSameTableAndNeverGrantRoles() {
    try(var mysql=new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start(); var ds=new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()); var jdbc=new JdbcTemplate(ds);
      new ResourceDatabasePopulator(new ClassPathResource("schema/schema-system.sql"),new ClassPathResource("schema/schema-archive.sql")).execute(ds);
      String expected=jdbc.queryForList("SHOW COLUMNS FROM sts_archive_history_correction").toString();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM us_sys_menu WHERE perms IN ('archive:history:correct','archive:history:correction:list')",Integer.class)).isEqualTo(2);
      jdbc.execute("DROP TABLE sts_archive_history_correction"); jdbc.update("DELETE FROM us_sys_menu WHERE menu_id IN (9722,9723)");
      var flyway=Flyway.configure().dataSource(ds).locations("classpath:db/migration").baselineVersion("20261008180000").cleanDisabled(true).baselineOnMigrate(false).load();
      flyway.baseline(); assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1); flyway.validate();
      assertThat(flyway.migrate().migrationsExecuted).isZero();
      assertThat(jdbc.queryForList("SHOW COLUMNS FROM sts_archive_history_correction").toString()).isEqualTo(expected);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM us_sys_role_menu WHERE menu_id IN (9722,9723)",Integer.class)).isZero();
    }
  }
}
