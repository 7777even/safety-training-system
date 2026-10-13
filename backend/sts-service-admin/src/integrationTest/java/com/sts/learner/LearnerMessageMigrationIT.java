package com.sts.learner;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

/** 本批消息表真实MySQL升级及完整init接管，不依赖历史菜单夹具。 */
class LearnerMessageMigrationIT {
  @Test void messageUpgradeAndFreshInitAreBothIdempotent() throws Exception {
    try (var mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      var dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
      new ResourceDatabasePopulator(new ClassPathResource("schema/schema-system.sql")).execute(dataSource);
      try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
        statement.execute("DROP TABLE sts_common_message_recipient"); statement.execute("DROP TABLE sts_common_message");
      }
      var flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
        .baselineVersion("20261008140000").target("20261008180000").load();
      flyway.baseline(); assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
      assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
      assertThat(flyway.migrate().migrationsExecuted).isZero();
      try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
           var result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('sts_common_message','sts_common_message_recipient')")) {
        result.next(); assertThat(result.getInt(1)).isEqualTo(2);
      }
    }
  }
}
