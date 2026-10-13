package com.sts.admin.support;

import com.sts.admin.tenant.TenantContext;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 集成测试基类：提供一个整个测试 JVM 共享的临时 MySQL 容器（单例，退出时由 Testcontainers Ryuk 清理）。
 * <p>
 * 容器启动后执行与 init 相同的模块化完整 SQL，登记 Flyway baseline，再让 Admin Flyway
 * 从 {@code classpath:db/migration} 接管增量。
 * 子类继承即可获得指向该容器的 {@code spring.datasource}。
 * <p>
 * 镜像 {@code mysql:8.0.45} 与线上一致；本机已有该镜像时 Testcontainers 直接复用、不重复拉取。
 * <p>
 * <b>租户上下文</b>：每个用例默认绑定示例企业租户 {@code DEMO}，使租户插件按真实路径注入与填充
 * {@code organization_id}。需要验证跨租户行为的用例自行调用
 * {@link TenantContext#callWithTenant} 或 {@link TenantContext#setTenantId} 切换。
 */
public abstract class AbstractMysqlIntegrationTest {

  /**
   * 必须与 {@code sts-service-admin-init} 的 {@code sts.init.schema-version} 以及
   * classpath 内最新的 {@code db/migration} 版本保持一致：init 快照已吸收全部增量，
   * 基线落后会让已被快照吸收的 DDL（如归档版本镜像文件列）重复执行而失败。
   */
  private static final String INIT_SCHEMA_VERSION = "20261012100000";
  private static final String[] INIT_SCHEMA_SCRIPTS = {
    "schema/schema-system.sql",
    "schema/schema-org.sql",
    "schema/schema-course.sql",
    "schema/schema-asset.sql",
    "schema/schema-question.sql",
    "schema/schema-exam.sql",
    "schema/schema-training.sql",
    "schema/schema-evaluation.sql",
    "schema/schema-survey.sql",
    "schema/schema-archive.sql",
    "schema/schema-certificate.sql",
    "schema/schema-ops.sql",
    "schema/schema-regulation.sql"
  };

  /**
   * 开发与集成测试使用的示例企业租户编码。
   */
  public static final String DEMO_ORG_CODE = "DEMO";

  protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.45");

    static {
        // 单例容器：整个测试 JVM 只起一次，所有 IT 子类共用
        MYSQL.start();
      DriverManagerDataSource dataSource = new DriverManagerDataSource(
        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
      ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
      for (String script : INIT_SCHEMA_SCRIPTS) {
        populator.addScript(new ClassPathResource(script));
      }
      populator.execute(dataSource);

      Flyway.configure()
        .dataSource(dataSource)
        .baselineVersion(MigrationVersion.fromVersion(INIT_SCHEMA_VERSION))
        .baselineDescription("admin-init modular full schema")
        .load()
        .baseline();
      Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .failOnMissingLocations(true)
        .validateMigrationNaming(true)
        .validateOnMigrate(true)
        .baselineOnMigrate(false)
        .outOfOrder(false)
        .cleanDisabled(true)
        .load()
        .migrate();
    }

  @Autowired(required = false)
  private JdbcTemplate tenantSupportJdbcTemplate;

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

  @BeforeEach
  void bindDemoTenant() {
    Long demoTenantId = demoTenantId();
    if (demoTenantId != null) {
      TenantContext.setTenantId(demoTenantId);
    }
  }

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  /**
   * 示例企业租户 ID；未注入 {@code JdbcTemplate} 的切片返回 {@code null}。
   */
  protected Long demoTenantId() {
    if (tenantSupportJdbcTemplate == null) {
      return null;
    }
    return tenantSupportJdbcTemplate.query(
      "SELECT organization_id FROM sts_org_organization WHERE org_code = ? AND deleted = 0",
      rs -> rs.next() ? rs.getLong("organization_id") : null,
      DEMO_ORG_CODE);
    }
}
