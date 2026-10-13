package com.sts.admin.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayMigrationIT {

  private static final String INIT_SCHEMA_VERSION = "20261011130000";
  private static final String[] INIT_SCHEMA_SCRIPTS = {
    "schema/schema-system.sql", "schema/schema-org.sql", "schema/schema-course.sql",
    "schema/schema-asset.sql", "schema/schema-question.sql", "schema/schema-exam.sql",
    "schema/schema-training.sql", "schema/schema-evaluation.sql", "schema/schema-survey.sql",
    "schema/schema-archive.sql", "schema/schema-certificate.sql", "schema/schema-ops.sql",
    "schema/schema-regulation.sql"
  };

  /**
   * 迁移前的机构统一课程设置表，用于验证历史值保留和暂时隐藏注释。
   */
  private static final String LEGACY_COURSE_SETTING_DDL = "CREATE TABLE sts_course_setting ("
    + "course_setting_id bigint NOT NULL AUTO_INCREMENT, organization_id bigint NOT NULL, "
    + "default_document_page_seconds int NOT NULL, default_rich_text_seconds int NOT NULL, "
    + "default_link_live_seconds int NOT NULL, default_media_completion_ratio decimal(7, 4) NOT NULL, "
    + "is_category_permission_enabled tinyint NOT NULL, is_resume_prompt_enabled tinyint NOT NULL, "
    + "is_last_page_required tinyint NOT NULL, is_fast_forward_globally_disabled tinyint NOT NULL, "
    + "is_screen_leave_time_paused tinyint NOT NULL, is_transcode_notification_enabled tinyint NOT NULL, "
    + "status tinyint NOT NULL, deleted bigint NOT NULL DEFAULT 0, version int NOT NULL DEFAULT 0, "
    + "PRIMARY KEY (course_setting_id), UNIQUE KEY uk_org_deleted (organization_id, deleted)"
    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";

  /** 迁移前的独立材料规则表，仅用于复现被删除的旧结构。 */
  private static final String LEGACY_MATERIAL_RULE_DDL = "CREATE TABLE sts_course_item_material_rule ("
    + "material_rule_id bigint NOT NULL AUTO_INCREMENT, organization_id bigint NOT NULL, "
    + "course_item_id bigint NOT NULL, duration_mode varchar(16) NOT NULL, required_seconds int NOT NULL, "
    + "is_fast_forward_allowed tinyint NOT NULL, is_speed_play_allowed tinyint NOT NULL, "
    + "watermark_mode varchar(16) NOT NULL, is_screen_leave_paused tinyint NOT NULL, "
    + "deleted bigint NOT NULL DEFAULT 0, version int NOT NULL DEFAULT 0, "
    + "PRIMARY KEY (material_rule_id), UNIQUE KEY uk_course_item_deleted (course_item_id, deleted)"
    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";

  @Test
  void shouldUpgradeBatch6WithoutRewritingHistoricalHours() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      var dataSource = initializeFullSchema(mysql);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        statement.execute("ALTER TABLE sts_course_chapter MODIFY ancestors VARCHAR(1000) NOT NULL");
        statement.execute("ALTER TABLE sts_course_item DROP COLUMN linked_quiz_item_id, DROP COLUMN is_final_assessment, DROP COLUMN pass_score");
        statement.execute("ALTER TABLE sts_course_course DROP COLUMN teacher_user_id");
        statement.execute("ALTER TABLE sts_course_enrollment DROP COLUMN recognized_study_seconds");
        statement.execute("INSERT INTO sts_course_enrollment (organization_id,course_offering_id,user_id,enrollment_source,enrolled_time,learning_status,progress_percent,total_study_seconds,credit_awarded) "
          + "VALUES (1,987699,987699,'SELF',NOW(),'COMPLETED',100,3600,0)");
      }
      Flyway.configure().dataSource(dataSource).baselineVersion(MigrationVersion.fromVersion("20261009140000"))
        .load().baseline();
      var flyway = flyway(mysql, "20261010140000");
      // 20261009140000 之后的五条增量：目录、配题、教师、结业测评及开课权限恢复。
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(5);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM sts_course_enrollment WHERE user_id=987699 AND total_study_seconds=3600 AND recognized_study_seconds IS NULL")).isEqualTo(1);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sts_course_chapter' AND column_name='ancestors' AND data_type='longtext'")).isEqualTo(1);
      }
      assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
  }

  @Test
  void shouldContinueFromAdminInitBaselineAndRemainIdempotent() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      initializeFullSchemaAndBaseline(mysql);
      Flyway flyway = flyway(mysql, INIT_SCHEMA_VERSION);

      assertThat(flyway.migrate().migrationsExecuted).isZero();
      assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
      assertThat(flyway.migrate().migrationsExecuted).isZero();

      try (Connection connection = connection(mysql);
           Statement statement = connection.createStatement()) {
        assertThat(singleLong(statement,
                              "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('us_sys_user', 'sts_course_course', 'sts_exam_exam')"))
          .isEqualTo(3L);
        assertThat(singleLong(statement,
                              "SELECT COUNT(*) FROM flyway_schema_history WHERE type = 'BASELINE' "
                                + "AND success = 1"))
          .isEqualTo(1L);
      }
    }
  }

  @Test
  void shouldAddCertificateRuleEditPermission() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      DriverManagerDataSource dataSource = initializeFullSchema(mysql);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        // 旧库的归档版本表还没有镜像文件列，回退后 pending 迁移才会真正执行该列变更。
        revertArchiveVersionFileColumn(statement);
      }
      Flyway.configure()
        .dataSource(dataSource)
        .baselineVersion(MigrationVersion.fromVersion("20260920210000"))
        .baselineDescription("before certificate rule edit permission")
        .load()
        .baseline();

      Flyway flyway = flyway(mysql);
      // 20260920210000 之后的全部增量：证书规则编辑权限、归档版本文件列、M4 成果菜单、门户我的证书、遗留菜单清理、
      // 奖惩可选字段、资料类型收口、M5 PC 菜单、重复监管菜单清理、机构管理菜单解绑、监管课程管理菜单解绑、
      // 监管标签岗位菜单补齐、监管菜单管理读权限补齐。
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(13);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu "
          + "WHERE menu_id = 8513 AND parent_id = 805 AND perms = 'certificate:rule:edit' "
          + "AND sort_order = 9 AND menu_type = 'F' AND module_id = 800 AND deleted = 0")).isEqualTo(1L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM information_schema.columns "
          + "WHERE table_schema = DATABASE() AND table_name = 'sts_archive_version' "
          + "AND column_name = 'file_info_id'")).isEqualTo(1L);
      }
      assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
  }

  @Test
  void shouldRepairExistingTeacherTaskMenuAndAddLearnerTrainingPermission() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      DriverManagerDataSource dataSource = initializeFullSchema(mysql);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        statement.execute("INSERT INTO us_sys_menu "
                            + "(menu_id, parent_id, menu_name, path, component, perms, icon, sort_order, is_visible, menu_type, module_id) "
                            + "VALUES (9565, 800, '教学任务', 'teacher-tasks', "
                            + "'business-center/training/teacher-tasks/index', NULL, 'Menu', 0, 1, 'C', NULL)");
      }
      Flyway.configure()
        .dataSource(dataSource)
        .baselineVersion(MigrationVersion.fromVersion("20260917170000"))
        .baselineDescription("before teacher task menu synchronization")
        .load()
        .baseline();

      Flyway flyway = Flyway.configure()
        .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
        .locations("classpath:db/migration")
        .target("20260918154500")
        .load();
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        assertThat(singleLong(statement,
                              "SELECT COUNT(*) FROM us_sys_menu WHERE menu_id=9564 AND parent_id=9527 "
                                + "AND perms='training:learner:task' AND menu_type='F' AND module_id=1 AND deleted=0"))
          .isEqualTo(1L);
        assertThat(singleLong(statement,
                              "SELECT COUNT(*) FROM us_sys_menu WHERE menu_id=9565 AND parent_id=800 "
                                + "AND path='teacher-tasks' AND component='business-center/training/teacher-tasks/index' "
                                + "AND perms='training:teacher:task' AND menu_type='C' AND module_id=9530 AND deleted=0"))
          .isEqualTo(1L);
      }
      assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
  }

  @Test
  void shouldCollapseMaterialRuleIntoItemStudySecondsAndRetainHiddenCourseSetting() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      DriverManagerDataSource dataSource = initializeFullSchema(mysql);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        restorePreLearningRuleSimplificationTables(statement);
        statement.execute("INSERT INTO sts_course_setting "
                            + "(organization_id, default_document_page_seconds, default_rich_text_seconds, "
                            + "default_link_live_seconds, default_media_completion_ratio, is_category_permission_enabled, "
                            + "is_resume_prompt_enabled, is_last_page_required, is_fast_forward_globally_disabled, "
                            + "is_screen_leave_time_paused, is_transcode_notification_enabled, status, deleted) VALUES "
                            + "(1, 25, 30, 10, 0.9000, 0, 1, 0, 0, 0, 1, 1, 0),"
                            + "(2, 0, 30, 10, 0.9000, 0, 1, 0, 0, 0, 1, 1, 0)");
        statement.execute("INSERT INTO sts_course_item "
                            + "(course_item_id, organization_id, course_id, chapter_id, item_type, item_title, "
                            + "is_required, minimum_study_seconds, sort_order, is_locked_after_participation, status) VALUES "
                            + "(5001, 1, 1, 1, 'ASSET', '自定义规则优先', 1, 30, 1, 0, 1),"
                            + "(5002, 1, 1, 1, 'ASSET', '内容项自身时长', 1, 45, 2, 0, 1),"
                            + "(5003, 1, 1, 1, 'ASSET', '机构默认兜底', 1, 0, 3, 0, 1),"
                            + "(5004, 2, 1, 1, 'ASSET', '固定兜底 15 秒', 1, 0, 4, 0, 1),"
                            + "(5005, 1, 1, 1, 'ASSET', '已删除规则不参与', 1, 50, 5, 0, 1),"
                            + "(5006, 1, 1, 1, 'ASSET', '零值规则不参与', 1, 0, 6, 0, 1),"
                            + "(5007, 1, 1, 1, 'LIVE', '直播归零', 1, 99, 7, 0, 1),"
                            + "(5008, 1, 1, 1, 'QUIZ', '测验归零', 1, 60, 8, 0, 1)");
        statement.execute("INSERT INTO sts_course_item_material_rule "
                            + "(material_rule_id, organization_id, course_item_id, duration_mode, required_seconds, "
                            + "is_fast_forward_allowed, is_speed_play_allowed, watermark_mode, is_screen_leave_paused, deleted) VALUES "
                            + "(6001, 1, 5001, 'AUTO', 120, 1, 1, 'SYSTEM', 0, 0),"
                            + "(6005, 1, 5005, 'AUTO', 999, 1, 1, 'SYSTEM', 0, 6005),"
                            + "(6006, 1, 5006, 'AUTO', 0, 1, 1, 'SYSTEM', 0, 0)");
        statement.execute("INSERT INTO us_sys_menu "
                            + "(menu_id, parent_id, menu_name, path, component, perms, sort_order, is_visible, menu_type, module_id) VALUES "
                            + "(301, 300, '课程分类', 'categories', 'business-center/course/classify/index', "
                            + "'course:category:list', 1, 1, 'C', 9530),"
                            + "(302, 300, '课程设置', 'settings', 'business-center/course/settings', "
                            + "'course:setting:view', 2, 1, 'C', 9530),"
                            + "(1311, 302, '课程设置查看', NULL, NULL, 'course:setting:view', 1, 1, 'F', 9530),"
                            + "(1312, 302, '课程设置编辑', NULL, NULL, 'course:setting:edit', 2, 1, 'F', 9530)");
        statement.execute("INSERT INTO us_sys_role_menu (role_id, menu_id) VALUES (9001, 302), (9001, 1311), (9001, 1312)");
      }
      Flyway.configure()
        .dataSource(dataSource)
        .baselineVersion(MigrationVersion.fromVersion("20260920180000"))
        .baselineDescription("before course item learning rule simplification")
        .load()
        .baseline();

      Flyway flyway = Flyway.configure()
        .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
        .locations("classpath:db/migration")
        .target("20260920190000")
        .failOnMissingLocations(true)
        .validateMigrationNaming(true)
        .validateOnMigrate(true)
        .baselineOnMigrate(false)
        .outOfOrder(false)
        .cleanDisabled(true)
        .load();
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        // 门槛回填优先级：自定义规则 → 内容项时长 → 机构默认 → 固定 15 秒兜底。
        assertThat(singleLong(statement,
                              "SELECT minimum_study_seconds FROM sts_course_item WHERE course_item_id = 5001"))
          .isEqualTo(120L);
        assertThat(singleLong(statement,
                              "SELECT minimum_study_seconds FROM sts_course_item WHERE course_item_id = 5002"))
          .isEqualTo(45L);
        assertThat(singleLong(statement,
                              "SELECT minimum_study_seconds FROM sts_course_item WHERE course_item_id = 5003"))
          .isEqualTo(25L);
        assertThat(singleLong(statement,
                              "SELECT minimum_study_seconds FROM sts_course_item WHERE course_item_id = 5004"))
          .isEqualTo(15L);
        // 已逻辑删除的规则与零值规则都不覆盖内容项自身的时长。
        assertThat(singleLong(statement,
                              "SELECT minimum_study_seconds FROM sts_course_item WHERE course_item_id = 5005"))
          .isEqualTo(50L);
        assertThat(singleLong(statement,
                              "SELECT minimum_study_seconds FROM sts_course_item WHERE course_item_id = 5006"))
          .isEqualTo(25L);
        // 非素材内容项统一归零。
        assertThat(singleLong(statement,
                              "SELECT COUNT(*) FROM sts_course_item WHERE course_item_id IN (5007, 5008) "
                                + "AND minimum_study_seconds = 0"))
          .isEqualTo(2L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM information_schema.tables "
          + "WHERE table_schema = DATABASE() AND table_name = 'sts_course_item_material_rule'")).isZero();
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM information_schema.tables "
          + "WHERE table_schema = DATABASE() AND table_name = 'sts_course_setting' "
          + "AND table_comment LIKE '【暂时隐藏】原用于：机构统一课程设置%'")).isEqualTo(1L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM information_schema.columns "
          + "WHERE table_schema = DATABASE() AND table_name = 'sts_course_setting' "
          + "AND column_name IN ('default_document_page_seconds','default_rich_text_seconds',"
          + "'default_link_live_seconds','default_media_completion_ratio','is_category_permission_enabled',"
          + "'is_resume_prompt_enabled','is_last_page_required','is_fast_forward_globally_disabled',"
          + "'is_screen_leave_time_paused','is_transcode_notification_enabled') "
          + "AND column_comment LIKE '【暂时隐藏】原用于：%'")).isEqualTo(10L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM sts_course_setting "
          + "WHERE organization_id IN (1, 2) AND deleted = 0")).isEqualTo(2L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu "
          + "WHERE menu_id IN (302, 1311, 1312) AND deleted <> 0 AND is_visible = 0")).isEqualTo(3L);
        assertThat(singleLong(statement,
                              "SELECT COUNT(*) FROM us_sys_role_menu WHERE menu_id IN (302, 1311, 1312)")).isZero();
        assertThat(singleLong(statement,
                              "SELECT COUNT(*) FROM us_sys_menu WHERE menu_id = 301 AND deleted = 0 AND is_visible = 1"))
          .isEqualTo(1L);
      }
      assertThat(flyway.migrate().migrationsExecuted).isZero();
      assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }
  }

  /**
   * 从当前仍保留的增量 V 之前的 init 快照升级到最新。
   * 早于 classpath 中第一份 V 的历史迁移（含 target 20260911152000 的执行归属重建）已并入 admin-init，不再回放。
   */
  @Test
  void shouldApplyPendingMigrationsOntoOlderInitSnapshot() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      DriverManagerDataSource dataSource = initializeFullSchema(mysql);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        statement.execute("ALTER TABLE sts_exam_exam DROP COLUMN draw_rule_json");
        statement.execute("ALTER TABLE sts_exam_policy DROP COLUMN is_anticheat_enabled");
        statement.execute("ALTER TABLE sts_survey_instance DROP COLUMN result_visibility, DROP COLUMN purpose");
        // 最新 init 已吸收 V20260920190000 的结果，旧快照仍需保留被该脚本删除的材料规则表。
        restorePreLearningRuleSimplificationTables(statement);
      }
      Flyway.configure()
        .dataSource(dataSource)
        .baselineVersion(MigrationVersion.fromVersion("20260915103000"))
        .baselineDescription("older admin-init schema before pending V files")
        .load()
        .baseline();

      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        // 已部署库的 admin 角色需要在迁移后自动获得 M4 新菜单。
        statement.execute("INSERT INTO us_sys_role (organization_id, role_name, role_code, role_level) "
          + "VALUES (1, '管理员', 'admin', 'ADMIN')");
        // 同时模拟旧库尚未镜像归档文件列的状态。
        revertArchiveVersionFileColumn(statement);
      }
      // 20260915103000 之后的全部增量：考试抽题、教学任务菜单、防作弊、M4 注释、问卷用途、
      // 课程时长规则、延期注释、证书规则编辑权限、归档版本文件列、M4 成果菜单、门户我的证书、遗留菜单清理、
      // 奖惩可选字段、资料类型收口、M5 PC 菜单、重复监管菜单清理、机构管理菜单解绑、监管课程管理菜单解绑、
      // 监管标签岗位菜单补齐、监管菜单管理读权限补齐。
      assertThat(flyway(mysql).migrate().migrationsExecuted).isEqualTo(21);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM information_schema.columns "
          + "WHERE table_schema = DATABASE() AND table_name = 'sts_exam_exam' "
          + "AND column_name = 'draw_rule_json'")).isEqualTo(1L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM information_schema.columns "
          + "WHERE table_schema = DATABASE() AND table_name = 'sts_exam_policy' "
          + "AND column_name = 'is_anticheat_enabled'")).isEqualTo(1L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM information_schema.columns "
          + "WHERE table_schema = DATABASE() AND table_name = 'sts_survey_instance' "
          + "AND column_name IN ('purpose','result_visibility')")).isEqualTo(2L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM information_schema.columns "
          + "WHERE table_schema = DATABASE() AND table_name = 'sts_exam_policy' "
          + "AND column_name IN ('switch_limit','is_split_screen_force_submit',"
          + "'is_copy_refresh_devtool_disabled','inactivity_seconds','inactivity_countdown_seconds',"
          + "'minimum_answer_seconds','pass_point','fail_point','absent_point','late_forbid_seconds',"
          + "'is_ranking_enabled') AND column_comment LIKE '【暂时隐藏】原用于：%'")).isEqualTo(11L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM information_schema.columns "
          + "WHERE table_schema = DATABASE() AND table_name = 'sts_exam_policy' "
          + "AND column_name IN ('is_anticheat_enabled','is_watermark_enabled','is_pc_allowed',"
          + "'is_app_allowed','is_h5_allowed','is_retake_enabled','answer_detail_mode',"
          + "'is_analysis_visible','is_score_visible') "
          + "AND column_comment LIKE '【暂时隐藏】%'")).isZero();
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu "
          + "WHERE menu_id = 8513 AND parent_id = 805 AND perms = 'certificate:rule:edit' "
          + "AND menu_type = 'F' AND module_id = 800 AND deleted = 0")).isEqualTo(1L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu "
          + "WHERE menu_id IN (9700, 9701, 9702, 9703, 9704, 9705, 9706) "
          + "AND module_id = 9530 AND deleted = 0")).isEqualTo(7L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu "
          + "WHERE role_id = (SELECT role_id FROM us_sys_role WHERE role_code = 'admin' AND deleted = 0) "
          + "AND menu_id BETWEEN 9700 AND 9750")).isEqualTo(25L);
      }
    }
  }

  /**
   * M4 培训成果与门户「我的证书」菜单在已部署库中的升级路径：
   * 新增节点、按角色级别授权、清理遗留重复子树，且重复执行保持幂等。
   */
  @Test
  void shouldAddM4ResultMenusAndGrantAdminRole() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      DriverManagerDataSource dataSource = initializeFullSchema(mysql);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        statement.execute("INSERT INTO us_sys_role (organization_id, role_name, role_code, role_level) VALUES "
          + "(1, '管理员', 'admin', 'ADMIN'), (1, '学员', 'student', 'STUDENT')");
        // 已部署库里存在旧版遗留成果菜单子树（含一条门户证书节点），升级后必须被软删。
        statement.execute("INSERT INTO us_sys_menu "
          + "(menu_id, parent_id, menu_name, path, component, perms, sort_order, is_visible, menu_type, module_id) VALUES "
          + "(9566, 9530, '培训成果(遗留)', '/admin/result', 'Layout', NULL, 9, 1, 'M', 9530),"
          + "(9601, 9566, '我的证书(遗留)', '/portal/certificates', 'portal/certificates/index', NULL, 1, 1, 'C', 1)");
      }
      Flyway.configure()
        .dataSource(dataSource)
        .baselineVersion(MigrationVersion.fromVersion("20260921170000"))
        .baselineDescription("before M4 result menus")
        .load()
        .baseline();

      Flyway flyway = flyway(mysql);
      // 20260921170000 之后的十一条增量：M4 成果菜单、门户我的证书、遗留菜单清理、奖惩可选字段、资料类型收口、
      // M5 PC 菜单、重复监管菜单清理、机构管理菜单解绑、监管课程管理菜单解绑、监管标签岗位菜单补齐、
      // 监管菜单管理读权限补齐。
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(11);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu WHERE menu_id = 9700 "
          + "AND parent_id = 9530 AND path = '/result' AND menu_type = 'M' AND module_id = 9530 "
          + "AND deleted = 0")).isEqualTo(1L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu WHERE menu_id IN (9704, 9705, 9706) "
          + "AND parent_id = 9703 AND menu_type = 'C' AND deleted = 0")).isEqualTo(3L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu WHERE menu_id = 9721 "
          + "AND perms = 'archive:training:create' AND menu_type = 'F' AND deleted = 0")).isEqualTo(1L);
        // 门户学员入口：挂在门户 #1 下，权限沿用学员既有权限，且按角色级别授予学员角色。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu WHERE menu_id = 9751 "
          + "AND parent_id = 1 AND path = 'certificates' AND component = 'portal/certificates/index' "
          + "AND perms = 'training:learner:task' AND menu_type = 'C' AND deleted = 0")).isEqualTo(1L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu "
          + "WHERE role_id = (SELECT role_id FROM us_sys_role WHERE role_code = 'student' AND deleted = 0) "
          + "AND menu_id = 9751")).isEqualTo(1L);
        // 未实现的撤销/重发与无参数成果页不得被迁移带入。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu "
          + "WHERE perms IN ('certificate:cert:issue', 'certificate:cert:revoke', 'certificate:cert:reissue') "
          + "AND deleted = 0")).isZero();
        // 遗留子树按「deleted = 行自身 menu_id」软删，与库内既有失效行语义一致。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu "
          + "WHERE menu_id IN (9566, 9601) AND deleted = menu_id")).isEqualTo(2L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu "
          + "WHERE role_id = (SELECT role_id FROM us_sys_role WHERE role_code = 'admin' AND deleted = 0) "
          + "AND menu_id BETWEEN 9700 AND 9750")).isEqualTo(25L);
      }
      assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
  }

  /** 旧库的两个非空列升级后应与现有可选 API 契约一致。 */
  @Test
  void shouldAllowOptionalTrainingRewardFieldsOnUpgrade() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      DriverManagerDataSource dataSource = initializeFullSchema(mysql);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        statement.execute("ALTER TABLE sts_training_reward_penalty "
          + "MODIFY COLUMN description longtext NOT NULL, MODIFY COLUMN occurred_date date NOT NULL");
      }
      Flyway.configure()
        .dataSource(dataSource)
        .baselineVersion(MigrationVersion.fromVersion("20260923130000"))
        .baselineDescription("before optional reward fields")
        .load()
        .baseline();

      Flyway flyway = flyway(mysql);
      // 20260923130000 之后的八条增量：奖惩可选字段、资料类型收口、M5 PC 菜单、重复监管菜单清理、机构管理菜单解绑、
      // 监管课程管理菜单解绑、监管标签岗位菜单补齐、监管菜单管理读权限补齐。
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(8);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM information_schema.columns "
          + "WHERE table_schema = DATABASE() AND table_name = 'sts_training_reward_penalty' "
          + "AND column_name IN ('description', 'occurred_date') AND is_nullable = 'YES'"))
          .isEqualTo(2L);
      }
      assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
  }

  /** 旧库的「奖惩佐证」资料升级后应改写为一般资料，且列注释同步收口为四类。 */
  @Test
  void shouldNarrowTrainingMaterialTypeOnUpgrade() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      DriverManagerDataSource dataSource = initializeFullSchema(mysql);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        // 还原到收口前：列注释含 REWARD_PENALTY，并造一条该类型的历史资料。
        statement.execute("ALTER TABLE sts_training_attachment MODIFY COLUMN attachment_type varchar(24) NOT NULL "
          + "COMMENT '附件类型（GENERAL/IMAGE/VIDEO/REWARD_PENALTY/EVALUATION）'");
        statement.execute("INSERT INTO sts_training_attachment (organization_id, training_plan_id, attachment_type, "
          + "file_info_id, attachment_name, sort_order, status) "
          + "VALUES (1, 9001, 'REWARD_PENALTY', 7001, '历史奖惩佐证', 1, 1)");
        statement.execute("INSERT INTO sts_training_attachment (organization_id, training_plan_id, attachment_type, "
          + "file_info_id, attachment_name, sort_order, status) "
          + "VALUES (1, 9001, 'IMAGE', 7002, '现场照片', 2, 1)");
      }
      Flyway.configure()
        .dataSource(dataSource)
        .baselineVersion(MigrationVersion.fromVersion("20260924093000"))
        .baselineDescription("before training material type narrowing")
        .load()
        .baseline();

      // 20260924093000 之后的七条增量：资料类型收口、M5 PC 菜单、重复监管菜单清理、机构管理菜单解绑、监管课程管理菜单解绑、
      // 监管标签岗位菜单补齐、监管菜单管理读权限补齐。
      assertThat(flyway(mysql).migrate().migrationsExecuted).isEqualTo(7);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        // 历史奖惩佐证降级为一般资料，记录与文件均保留；非目标类型不受影响。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM sts_training_attachment "
          + "WHERE attachment_type = 'GENERAL' AND attachment_name = '历史奖惩佐证' AND deleted = 0"))
          .isEqualTo(1L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM sts_training_attachment "
          + "WHERE attachment_type = 'REWARD_PENALTY'")).isZero();
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM sts_training_attachment "
          + "WHERE attachment_type = 'IMAGE' AND attachment_name = '现场照片'")).isEqualTo(1L);
        // 列注释同步收口，避免「契约允许、列注释又写着旧类型」的裂缝。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM information_schema.columns "
          + "WHERE table_schema = DATABASE() AND table_name = 'sts_training_attachment' "
          + "AND column_name = 'attachment_type' AND column_comment NOT LIKE '%REWARD_PENALTY%'"))
          .isEqualTo(1L);
      }
    }
  }

  /**
   * 手工预览遗留的重复「园区监管」目录（9752 树）在升级后必须被软删，且其角色绑定清空；
   * 正式 M5 菜单（9810 树）不受影响，重复执行保持幂等。
   */
  @Test
  void shouldRemoveLegacyDuplicatedRegulationMenuOnUpgrade() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      DriverManagerDataSource dataSource = initializeFullSchema(mysql);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        // 复现手工建立状态：module_id 为 NULL、节点可见，并带一条角色绑定。
        statement.execute("INSERT INTO us_sys_menu "
          + "(menu_id, parent_id, menu_name, path, component, perms, sort_order, is_visible, menu_type, module_id) VALUES "
          + "(9752, 0, '园区监管', '/admin/regulation', 'Layout', NULL, 3, 1, 'D', NULL),"
          + "(9753, 9752, '监管工作台', 'workbench', 'regulation/workbench/index', "
          + "'regulation:overview:view', 1, 1, 'C', NULL),"
          + "(9756, 9752, '机构管理', 'institutions', 'system/org/organization/index', "
          + "'system:tenant:list', 4, 1, 'C', NULL)");
        statement.execute("INSERT INTO us_sys_role_menu (role_id, menu_id) VALUES (9001, 9752), (9001, 9753)");
      }
      Flyway.configure()
        .dataSource(dataSource)
        .baselineVersion(MigrationVersion.fromVersion("20260924180000"))
        .baselineDescription("before legacy duplicated regulation menu cleanup")
        .load()
        .baseline();

      Flyway flyway = flyway(mysql);
      // 20260924180000 之后的六条增量：M5 PC 菜单、重复监管菜单清理、机构管理菜单解绑、监管课程管理菜单解绑、
      // 监管标签岗位菜单补齐、监管菜单管理读权限补齐。
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(6);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        // 遗留子树按「deleted = 行自身 menu_id」软删且不可见。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu "
          + "WHERE menu_id BETWEEN 9752 AND 9760 AND deleted = menu_id AND is_visible = 0")).isEqualTo(3L);
        assertThat(singleLong(statement,
                              "SELECT COUNT(*) FROM us_sys_role_menu WHERE menu_id BETWEEN 9752 AND 9760")).isZero();
        // 正式 M5 菜单树不受清理影响：9810 目录仍在且可见。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu "
          + "WHERE menu_id = 9810 AND module_id = 9810 AND deleted = 0 AND is_visible = 1")).isEqualTo(1L);
      }
      assertThat(flyway.migrate().migrationsExecuted).isZero();
      assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }
  }

  /**
   * 误绑在企业与培训机构租户管理员角色上的「机构管理」菜单（9532 及其按钮权限 1381/1382/1383）在升级后必须解绑，
   * 使这两类租户不再出现点击必然 4048 的死入口；菜单节点本身不软删（superAdmin 仍需借此维护租户主数据），
   * 监管租户角色的独立绑定不受影响，重复执行保持幂等。
   */
  @Test
  void shouldUnbindLegacyOrganizationMenuFromTenantRolesOnUpgrade() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      DriverManagerDataSource dataSource = initializeFullSchema(mysql);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        // 复现误绑状态：企业(org 2)、培训机构(org 4)与监管(org 1)的 admin 角色都绑了机构管理。
        statement.execute("INSERT INTO sts_org_organization "
          + "(organization_id, org_code, org_name, org_type, status, description) VALUES "
          + "(4, 'TRAIN', '培训机构', 'TRAINING_INSTITUTION', 1, '培训机构租户')");
        statement.execute("INSERT INTO us_sys_role (role_id, organization_id, role_name, role_code) VALUES "
          + "(1, 2, '管理员', 'admin'), (5, 1, '监管管理员', 'admin'), (6, 4, '管理员', 'admin')");
        statement.execute("INSERT INTO us_sys_menu "
          + "(menu_id, parent_id, menu_name, path, component, perms, sort_order, is_visible, menu_type, module_id) VALUES "
          + "(9532, 500, '机构管理', 'organization', 'system/org/organization/index', "
          + "'system:tenant:list', 4, 1, 'C', 200),"
          + "(1381, 9532, '机构查询', NULL, NULL, 'system:tenant:list', 1, 1, 'F', 200)");
        statement.execute("INSERT INTO us_sys_role_menu (role_id, menu_id) VALUES "
          + "(1, 9532), (1, 1381), (6, 9532), (6, 1381), (5, 9532), (5, 1381)");
      }
      Flyway.configure()
        .dataSource(dataSource)
        .baselineVersion(MigrationVersion.fromVersion("20260924180000"))
        .baselineDescription("before legacy organization menu unbinding")
        .load()
        .baseline();

      Flyway flyway = flyway(mysql);
      // 20260924180000 之后的六条增量：M5 PC 菜单、重复监管菜单清理、机构管理菜单解绑、监管课程管理菜单解绑、
      // 监管标签岗位菜单补齐、监管菜单管理读权限补齐。
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(6);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        // 企业与培训机构角色的绑定被清除。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu "
          + "WHERE menu_id IN (9532, 1381) AND role_id IN (1, 6)")).isZero();
        // 监管租户角色的独立绑定保留。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu "
          + "WHERE menu_id IN (9532, 1381) AND role_id = 5")).isEqualTo(2L);
        // 菜单节点本身不软删，superAdmin 仍可维护租户主数据。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu "
          + "WHERE menu_id = 9532 AND deleted = 0 AND is_visible = 1")).isEqualTo(1L);
      }
      assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
  }

  /**
   * 监管租户角色为「课程制作」快捷入口补授的祖先菜单（300 课程管理、9531 课程中心）在升级后必须解绑，
   * 使监管侧边栏不再出现点击必然 403（course:category:list 从未授予监管）的死入口；
   * 企业租户的课程管理授权不受影响，菜单节点本身不软删，重复执行保持幂等。
   */
  @Test
  void shouldUnbindCourseManagementFromRegulatorRoleOnUpgrade() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      DriverManagerDataSource dataSource = initializeFullSchema(mysql);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        // 复现误授状态：监管(org 1)与企业(org 2)的 admin 角色都绑了课程管理祖先菜单。
        statement.execute("INSERT INTO sts_org_organization "
          + "(organization_id, org_code, org_name, org_type, status, description) VALUES "
          + "(1, 'REGULATOR', '监管平台', 'REGULATOR', 1, '监管租户'),"
          + "(2, 'DEMO', '示例企业', 'ENTERPRISE', 1, '企业租户')");
        statement.execute("INSERT INTO us_sys_role (role_id, organization_id, role_name, role_code) VALUES "
          + "(1, 2, '管理员', 'admin'), (5, 1, '监管管理员', 'admin')");
        statement.execute("INSERT INTO us_sys_menu "
          + "(menu_id, parent_id, menu_name, path, component, perms, sort_order, is_visible, menu_type, module_id) VALUES "
          + "(9530, 0, '应用中心', '/admin/business-center', 'Layout', NULL, 2, 1, 'D', 9530),"
          + "(300, 9530, '课程管理', 'course', 'Layout', NULL, 1, 1, 'M', 9530),"
          + "(9531, 300, '课程中心', 'center', 'course/center/index', 'course:course:list', 1, 1, 'C', 9530),"
          + "(301, 300, '课程分类', 'classify', 'course/classify/index', 'course:category:list', 2, 1, 'C', 9530)");
        // 监管角色只绑了 300/9531 这两个祖先；企业角色另有 301，用来验证不受牵连。
        statement.execute("INSERT INTO us_sys_role_menu (role_id, menu_id) VALUES "
          + "(5, 9530), (5, 300), (5, 9531), (1, 300), (1, 9531), (1, 301)");
      }
      Flyway.configure()
        .dataSource(dataSource)
        .baselineVersion(MigrationVersion.fromVersion("20260930150000"))
        .baselineDescription("before regulator course management unbinding")
        .load()
        .baseline();

      Flyway flyway = flyway(mysql);
      // 20260930150000 之后的三条增量：监管课程管理菜单解绑、监管标签岗位菜单补齐、监管菜单管理读权限补齐。
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        // 监管角色的两条课程管理祖先绑定被清除；保留的 9530 是「培训成果 › 证书管理」下钻链路的载体。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu "
          + "WHERE menu_id IN (300, 9531) AND role_id = 5")).isZero();
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu "
          + "WHERE menu_id = 9530 AND role_id = 5")).isEqualTo(1L);
        // 企业租户角色的课程管理授权不受影响。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu "
          + "WHERE menu_id IN (300, 9531, 301) AND role_id = 1")).isEqualTo(3L);
        // 菜单节点本身不软删，企业侧后台与 superAdmin 仍需该入口。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu "
          + "WHERE menu_id IN (300, 9531) AND deleted = 0 AND is_visible = 1")).isEqualTo(2L);
      }
      assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
  }

  /**
   * 监管租户角色必须补齐「标签管理 / 岗位管理」整套菜单及按钮权限：用户管理页（菜单 201）在 onMounted 阶段
   * <b>无条件</b>并发拉取标签/岗位下拉（org:tag:list / org:position:list），而监管角色此前没有任何 org:* 授权，
   * 一进页面就弹两条「权限不足」。企业（role 1）与培训机构（role 6）都已有这一套，监管属授权漏配。
   * 只对 org_type='REGULATOR' 的角色授出，且 C 菜单与其 F 按钮一并授出 —— 只授 F 会被菜单树的
   * 「向上补父级」递归拉出半截状态；企业租户角色的授权不受影响，重复执行保持幂等。
   */
  @Test
  void shouldGrantTagAndPositionMenusToRegulatorRoleOnUpgrade() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      DriverManagerDataSource dataSource = initializeFullSchema(mysql);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        // 复现漏配状态：监管(org 1)与企业(org 2)各有一个 admin 角色；标签/岗位菜单节点已存在。
        statement.execute("INSERT INTO sts_org_organization "
          + "(organization_id, org_code, org_name, org_type, status, description) VALUES "
          + "(1, 'REGULATOR', '监管平台', 'REGULATOR', 1, '监管租户'),"
          + "(2, 'DEMO', '示例企业', 'ENTERPRISE', 1, '企业租户')");
        statement.execute("INSERT INTO us_sys_role (role_id, organization_id, role_name, role_code) VALUES "
          + "(1, 2, '管理员', 'admin'), (5, 1, '监管管理员', 'admin')");
        statement.execute("INSERT INTO us_sys_menu "
          + "(menu_id, parent_id, menu_name, path, component, perms, sort_order, is_visible, menu_type, module_id) VALUES "
          + "(200, 0, '系统管理', '/admin/system', 'Layout', NULL, 2, 1, 'D', 200),"
          + "(500, 200, '机构组织', NULL, 'Layout', NULL, 1, 1, 'M', 200),"
          + "(201, 500, '用户管理', 'user', 'system/org/user/index', 'sys:user:list', 1, 1, 'C', 200),"
          + "(9534, 500, '标签管理', 'tag', 'system/org/tag/index', 'org:tag:list', 2, 1, 'C', 200),"
          + "(1511, 9534, '标签查询', NULL, NULL, 'org:tag:list', 1, 1, 'F', 200),"
          + "(1512, 9534, '标签新增', NULL, NULL, 'org:tag:add', 2, 1, 'F', 200),"
          + "(1513, 9534, '标签编辑', NULL, NULL, 'org:tag:edit', 3, 1, 'F', 200),"
          + "(1514, 9534, '标签删除', NULL, NULL, 'org:tag:delete', 4, 1, 'F', 200),"
          + "(9535, 500, '岗位管理', 'position', 'system/org/position/index', 'org:position:list', 3, 1, 'C', 200),"
          + "(1521, 9535, '岗位查询', NULL, NULL, 'org:position:list', 1, 1, 'F', 200),"
          + "(1522, 9535, '岗位新增', NULL, NULL, 'org:position:add', 2, 1, 'F', 200),"
          + "(1523, 9535, '岗位编辑', NULL, NULL, 'org:position:edit', 3, 1, 'F', 200),"
          + "(1524, 9535, '岗位删除', NULL, NULL, 'org:position:delete', 4, 1, 'F', 200)");
        // 监管角色只绑了系统管理目录与用户管理；企业角色已自查有标签/岗位各一条，用来验证不被牵连。
        statement.execute("INSERT INTO us_sys_role_menu (role_id, menu_id) VALUES "
          + "(5, 200), (5, 500), (5, 201), "
          + "(1, 200), (1, 500), (1, 9534), (1, 1511), (1, 9535), (1, 1521)");
      }
      Flyway.configure()
        .dataSource(dataSource)
        .baselineVersion(MigrationVersion.fromVersion("20260930160000"))
        .baselineDescription("before regulator tag/position menu grant")
        .load()
        .baseline();

      Flyway flyway = flyway(mysql);
      // 20260930160000 之后的两条增量：监管标签岗位菜单补齐、监管菜单管理读权限补齐。
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        // 监管角色补齐标签/岗位的 C 菜单与全部 F 按钮，共 10 条。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu "
          + "WHERE role_id = 5 AND menu_id IN (9534, 1511, 1512, 1513, 1514, 9535, 1521, 1522, 1523, 1524)"))
          .isEqualTo(10L);
        // C 与 F 一并到位，不出现「菜单可见但按钮缺失」的半截状态。
        assertThat(singleLong(statement, "SELECT COUNT(DISTINCT menu_id) FROM us_sys_role_menu "
          + "WHERE role_id = 5 AND menu_id BETWEEN 1511 AND 1530")).isEqualTo(8L);
        // 原有授权不被破坏。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu "
          + "WHERE role_id = 5 AND menu_id IN (200, 500, 201)")).isEqualTo(3L);
        // 企业租户角色的 5 条授权不受影响，也未重复插入。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu WHERE role_id = 1")).isEqualTo(5L);
      }
      assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
  }

  /**
   * 监管租户角色必须补齐「菜单管理」的<b>只读</b>权限：角色管理页（菜单 202）在 onMounted 阶段
   * <b>无条件</b>拉取菜单树（GET /api/system/menus/tree，受 sys:menu:list 保护）来渲染角色权限勾选树，
   * 而监管角色此前没有任何 sys:menu:* 授权，一进页面就弹「权限不足」。企业（role 1）与培训机构（role 6）
   * 都已有这一套，监管属授权漏配。
   * 只授 204（菜单管理）与 1031（菜单查询）—— 刻意不授 1032/1033/1034：us_sys_menu 是全局菜单定义
   * （无 organization_id，getMenuTree 也不做租户过滤），授出写权限等于让监管能改全系统菜单。
   * 只针对 org_type='REGULATOR' 的角色授出，企业租户角色的授权不受影响，重复执行保持幂等。
   */
  @Test
  void shouldGrantMenuReadOnlyPermissionToRegulatorRoleOnUpgrade() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      DriverManagerDataSource dataSource = initializeFullSchema(mysql);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        // 复现漏配状态：监管(org 1)与企业(org 2)各有一个 admin 角色；菜单节点已存在。
        statement.execute("INSERT INTO sts_org_organization "
          + "(organization_id, org_code, org_name, org_type, status, description) VALUES "
          + "(1, 'REGULATOR', '监管平台', 'REGULATOR', 1, '监管租户'),"
          + "(2, 'DEMO', '示例企业', 'ENTERPRISE', 1, '企业租户')");
        statement.execute("INSERT INTO us_sys_role (role_id, organization_id, role_name, role_code) VALUES "
          + "(1, 2, '管理员', 'admin'), (5, 1, '监管管理员', 'admin')");
        statement.execute("INSERT INTO us_sys_menu "
          + "(menu_id, parent_id, menu_name, path, component, perms, sort_order, is_visible, menu_type, module_id) VALUES "
          + "(200, 0, '系统管理', '/admin/system', 'Layout', NULL, 2, 1, 'D', 200),"
          + "(500, 200, '机构组织', NULL, 'Layout', NULL, 1, 1, 'M', 200),"
          + "(201, 500, '用户管理', 'user', 'system/org/user/index', 'sys:user:list', 1, 1, 'C', 200),"
          + "(202, 500, '角色管理', 'role', 'system/org/role/index', 'sys:role:list', 2, 1, 'C', 200),"
          + "(204, 200, '菜单管理', 'menu', 'system/menu/index', 'sys:menu:list', 3, 1, 'C', 200),"
          + "(1031, 204, '菜单查询', NULL, NULL, 'sys:menu:list', 1, 1, 'F', 200),"
          + "(1032, 204, '菜单新增', NULL, NULL, 'sys:menu:add', 2, 1, 'F', 200),"
          + "(1033, 204, '菜单编辑', NULL, NULL, 'sys:menu:edit', 3, 1, 'F', 200),"
          + "(1034, 204, '菜单删除', NULL, NULL, 'sys:menu:delete', 4, 1, 'F', 200)");
        // 监管角色已能进角色管理(202)，只是缺菜单树权限；企业角色另有整套菜单管理，用来验证不被牵连。
        statement.execute("INSERT INTO us_sys_role_menu (role_id, menu_id) VALUES "
          + "(5, 200), (5, 500), (5, 201), (5, 202), "
          + "(1, 200), (1, 500), (1, 202), (1, 204), (1, 1031), (1, 1032), (1, 1033), (1, 1034)");
      }
      Flyway.configure()
        .dataSource(dataSource)
        .baselineVersion(MigrationVersion.fromVersion("20260930170000"))
        .baselineDescription("before regulator menu read grant")
        .load()
        .baseline();

      Flyway flyway = flyway(mysql);
      // 20260930170000 之后的唯一增量：监管菜单管理读权限补齐。
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        // 监管角色补齐菜单管理的 C 菜单与查询按钮，共 2 条。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu "
          + "WHERE role_id = 5 AND menu_id IN (204, 1031)")).isEqualTo(2L);
        // 刻意只给读：新增/编辑/删除三条按钮权限不得被授出。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu "
          + "WHERE role_id = 5 AND menu_id IN (1032, 1033, 1034)")).isZero();
        // 原有授权不被破坏。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu "
          + "WHERE role_id = 5 AND menu_id IN (200, 500, 201, 202)")).isEqualTo(4L);
        // 企业租户角色的授权不受影响，也未重复插入。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu WHERE role_id = 1")).isEqualTo(8L);
      }
      assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
  }

  /**
   * 已部署库缺失的「开课编辑 / 开课删除」权限必须在升级后恢复：init 种子 menus.json 把 9541/9542 定义为
   * 开课编辑/开课删除，但更早的 V20260920210000 把这两个 ID 复用给了监管统计菜单并覆盖了种子定义，
   * 于是 course:offering:edit / course:offering:delete 在库中彻底缺失，管理员编辑开课与配置阅卷人恒 403。
   * 迁移按种子口径恢复两条权限，并只补授给已持有兄弟权限 course:offering:list(9539) 的角色；
   * 已软删的监管统计行被改回开课口径（模块迁到 9810 后不再需要 9541/9542），重复执行保持幂等。
   */
  @Test
  void shouldRestoreCourseOfferingEditAndDeleteMenusOnUpgrade() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      DriverManagerDataSource dataSource = initializeFullSchema(mysql);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        // 复现历史漂移：9539/9540/9543 已在，9541/9542 被复用为口径不同的监管统计菜单。
        statement.execute("INSERT INTO us_sys_menu "
          + "(menu_id, parent_id, menu_name, path, component, perms, sort_order, is_visible, menu_type, module_id, deleted) VALUES "
          + "(9539, 9531, '开课查询', NULL, NULL, 'course:offering:list', 99, 1, 'F', 9530, 0),"
          + "(9540, 9531, '开课新增', NULL, NULL, 'course:offering:add', 99, 1, 'F', 9530, 0),"
          + "(9543, 9531, '开课发布', NULL, NULL, 'course:offering:publish', 99, 1, 'F', 9530, 0),"
          + "(954, 950, '数据中心', 'learning-stats', 'regulation/learning/index', 'regulation:learning:view', 2, 1, 'C', 950, 0),"
          + "(9541, 954, '学习统计查看', NULL, NULL, 'regulation:learning:view', 1, 1, 'F', 950, 9541),"
          + "(9542, 954, '学习统计导出', NULL, NULL, 'regulation:learning:export', 2, 1, 'F', 950, 9542)");
        // 复现授权状态：企业与培训机构管理员持有开课查询；另有一条无关绑定验证不被波及。
        statement.execute("INSERT INTO us_sys_role_menu (role_id, menu_id) VALUES (1, 9539), (6, 9539), (9, 954)");
      }
      Flyway.configure()
        .dataSource(dataSource)
        .baselineVersion(MigrationVersion.fromVersion("20261010130000"))
        .baselineDescription("before course offering edit/delete menu restore")
        .load()
        .baseline();

      Flyway flyway = flyway(mysql, "20261010140000");
      // 已初始化最新业务结构，只验证菜单恢复增量，不能重复执行已有字段的DDL。
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu "
          + "WHERE menu_id = 9541 AND parent_id = 9531 AND perms = 'course:offering:edit' "
          + "AND menu_type = 'F' AND module_id = 9530 AND is_visible = 1 AND deleted = 0")).isEqualTo(1L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu "
          + "WHERE menu_id = 9542 AND parent_id = 9531 AND perms = 'course:offering:delete' "
          + "AND menu_type = 'F' AND module_id = 9530 AND is_visible = 1 AND deleted = 0")).isEqualTo(1L);
        // 两条 ID 不再残留监管统计口径。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu "
          + "WHERE menu_id IN (9541, 9542) AND perms LIKE 'regulation:%'")).isZero();
        // 只补授给已持有兄弟权限 9539 的两个角色，其它角色不被波及。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu "
          + "WHERE menu_id IN (9541, 9542) AND role_id IN (1, 6)")).isEqualTo(4L);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu "
          + "WHERE menu_id IN (9541, 9542) AND role_id NOT IN (1, 6)")).isZero();
        // 既有开课权限与无关绑定不受影响。
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu "
          + "WHERE menu_id IN (9539, 9540, 9543) AND deleted = 0 AND is_visible = 1")).isEqualTo(3L);
        assertThat(singleLong(statement,
          "SELECT COUNT(*) FROM us_sys_role_menu WHERE role_id = 9 AND menu_id = 954")).isEqualTo(1L);
      }
      // 二次执行幂等。
      assertThat(flyway.migrate().migrationsExecuted).isZero();
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        assertThat(singleLong(statement,
          "SELECT COUNT(*) FROM us_sys_role_menu WHERE menu_id IN (9541, 9542)")).isEqualTo(4L);
      }
    }
  }

  /** 从第六批已部署基线升级人员分类，保留原人员且不扩权既有角色。 */
  @Test
  void shouldUpgradePersonClassificationWithoutGrantingExistingRoles() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      DriverManagerDataSource dataSource = initializeFullSchema(mysql);
      long bindings;
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        statement.execute("DROP TABLE sts_person_profile");
        statement.execute("DROP TABLE sts_person_group");
        statement.execute("DELETE FROM us_sys_menu WHERE menu_id IN (9830,9831,9832,9833)");
        statement.execute("INSERT INTO us_sys_user (organization_id,user_code,user_name,status) VALUES (1,'batch7_upgrade','升级前人员',1)");
        bindings = singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu");
      }
      Flyway.configure().dataSource(dataSource).baselineVersion(MigrationVersion.fromVersion("20261010140000"))
        .load().baseline();
      Flyway flyway = flyway(mysql, "20261011090000");
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('sts_person_profile','sts_person_group')")).isEqualTo(2);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_user WHERE user_code='batch7_upgrade' AND user_name='升级前人员'")).isEqualTo(1);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_menu WHERE menu_id IN (9830,9831,9832,9833) AND deleted=0")).isEqualTo(4);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu")).isEqualTo(bindings);
      }
      assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
  }

  /** T2仅创建矩阵资产，保留已有来源与角色授权。 */
  @Test
  void shouldUpgradeTrainingMatrixWithoutGrantingExistingRoles() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      DriverManagerDataSource dataSource = initializeFullSchema(mysql);
      long bindings;
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        statement.execute("DROP TABLE sts_training_matrix_source");
        statement.execute("DROP TABLE sts_training_matrix");
        statement.execute("DELETE FROM us_sys_menu WHERE menu_id IN (9840,9841)");
        bindings = singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu");
      }
      Flyway.configure().dataSource(dataSource).baselineVersion(MigrationVersion.fromVersion("20261011090000")).load().baseline();
      Flyway flyway = flyway(mysql, "20261011110000");
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('sts_training_matrix','sts_training_matrix_source')")).isEqualTo(2);
        assertThat(singleLong(statement, "SELECT COUNT(*) FROM us_sys_role_menu")).isEqualTo(bindings);
      }
      assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
  }

  /** T4升级只登记新权限，审批历史不回填，已有角色保持原授权。 */
  @Test void shouldUpgradePlanApprovalWithoutGrantingExistingRoles() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start(); DriverManagerDataSource dataSource = initializeFullSchema(mysql); long bindings;
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        statement.execute("DROP TABLE sts_training_plan_approval");
        statement.execute("DROP TABLE sts_training_annual_plan_link");
        statement.execute("DROP TABLE sts_training_annual_plan");
        statement.execute("DELETE FROM us_sys_menu WHERE menu_id IN (9850,9851,9852)");
        bindings=singleLong(statement,"SELECT COUNT(*) FROM us_sys_role_menu");
      }
      Flyway.configure().dataSource(dataSource).baselineVersion(MigrationVersion.fromVersion("20261011110000")).load().baseline();
      Flyway flyway=flyway(mysql,"20261011120000");assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
      try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
        assertThat(singleLong(statement,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('sts_training_plan_approval','sts_training_annual_plan_link','sts_training_annual_plan')")).isEqualTo(3);
        assertThat(singleLong(statement,"SELECT COUNT(*) FROM sts_training_plan_approval")).isZero();
        assertThat(singleLong(statement,"SELECT COUNT(*) FROM us_sys_role_menu")).isEqualTo(bindings);
      }
      assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
  }

  /** 变更记录升级幂等且不扩大任何已有角色授权。 */
  @Test void shouldUpgradePlanChangesWithoutGrantingExistingRoles() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();var dataSource=initializeFullSchema(mysql);long bindings;
      try(var connection=connection(mysql);var statement=connection.createStatement()) {
        statement.execute("DROP TABLE sts_training_plan_change");statement.execute("DELETE FROM us_sys_menu WHERE menu_id=9853");
        bindings=singleLong(statement,"SELECT COUNT(*) FROM us_sys_role_menu");
      }
      Flyway.configure().dataSource(dataSource).baselineVersion(MigrationVersion.fromVersion("20261011120000")).load().baseline();
      var migration=flyway(mysql,"20261011130000");assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
      try(var connection=connection(mysql);var statement=connection.createStatement()) {
        assertThat(singleLong(statement,"SELECT COUNT(*) FROM sts_training_plan_change")).isZero();
        assertThat(singleLong(statement,"SELECT COUNT(*) FROM us_sys_menu WHERE menu_id=9853 AND perms='training:plan:change'")).isEqualTo(1);
        assertThat(singleLong(statement,"SELECT COUNT(*) FROM us_sys_role_menu")).isEqualTo(bindings);
      }
      assertThat(migration.migrate().migrationsExecuted).isZero();
    }
  }

  private void initializeFullSchemaAndBaseline(MySQLContainer<?> mysql) {
    DriverManagerDataSource dataSource = initializeFullSchema(mysql);
    Flyway.configure()
      .dataSource(dataSource)
      .baselineVersion(MigrationVersion.fromVersion(INIT_SCHEMA_VERSION))
      .baselineDescription("admin-init modular full schema")
      .load()
      .baseline();
  }

  private DriverManagerDataSource initializeFullSchema(MySQLContainer<?> mysql) {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
      mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
    for (String script : INIT_SCHEMA_SCRIPTS) {
      populator.addScript(new ClassPathResource(script));
    }
    populator.execute(dataSource);
    return dataSource;
  }

  /**
   * 最新 init 已含最终形态的 {@code sts_course_setting}，且不再包含材料规则表。
   * 验证 {@code V20260920190000} 时需先还原到该脚本执行前的表结构。
   */
  private void restorePreLearningRuleSimplificationTables(Statement statement) throws SQLException {
    statement.execute("DROP TABLE IF EXISTS sts_course_setting");
    statement.execute(LEGACY_COURSE_SETTING_DDL);
    statement.execute("DROP TABLE IF EXISTS sts_course_item_material_rule");
    statement.execute(LEGACY_MATERIAL_RULE_DDL);
  }

  /**
   * 最新 init 已吸收 {@code V20260921170000} 新增的归档版本文件列。
   * 把基线设在该脚本之前的用例必须先去掉该列，否则迁移会因重复列名失败。
   */
  private void revertArchiveVersionFileColumn(Statement statement) throws SQLException {
    statement.execute("ALTER TABLE sts_archive_version DROP COLUMN file_info_id");
  }

  @Test
  void shouldRejectUnknownNonEmptyDatabaseWithoutAutomaticBaseline() throws SQLException {
    try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")) {
      mysql.start();
      try (Connection connection = connection(mysql);
           Statement statement = connection.createStatement()) {
        statement.execute("CREATE TABLE unknown_table (id bigint PRIMARY KEY)");
      }

      assertThatThrownBy(() -> flyway(mysql).migrate())
        .isInstanceOf(FlywayException.class)
        .hasMessageContaining("non-empty schema");
    }
  }

  private Flyway flyway(MySQLContainer<?> mysql) {
    // 历史迁移用例只验证原版本边界，避免重跑最新init已吸收的后续业务DDL。
    return flyway(mysql, "20260930180000");
  }

  private Flyway flyway(MySQLContainer<?> mysql, String targetVersion) {
    return Flyway.configure()
      .target(MigrationVersion.fromVersion(targetVersion))
      .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
      .locations("classpath:db/migration")
      .failOnMissingLocations(true)
      .validateMigrationNaming(true)
      .validateOnMigrate(true)
      .baselineOnMigrate(false)
      .outOfOrder(false)
      .cleanDisabled(true)
      .load();
  }

  private Connection connection(MySQLContainer<?> mysql) throws SQLException {
    return DriverManager.getConnection(
      mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
  }

  private long singleLong(Statement statement, String sql) throws SQLException {
    try (var resultSet = statement.executeQuery(sql)) {
      assertThat(resultSet.next()).isTrue();
      return resultSet.getLong(1);
    }
  }
}
