package com.sts.init.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DatabaseSchemaInitializerTest {

  @Test
  void productionConstructorShouldBeExplicitSpringInjectionPoint() throws NoSuchMethodException {
    assertThat(DatabaseSchemaInitializer.class
                 .getConstructor(JdbcTemplate.class)
                 .isAnnotationPresent(Autowired.class))
      .isTrue();
  }

  @Test
  void shouldRejectInitializationWhenCurrentDatabaseContainsAnyTable() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
    DatabaseSchemaInitializer.SqlScriptExecutor executor =
      mock(DatabaseSchemaInitializer.SqlScriptExecutor.class);
    when(jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM information_schema.tables "
        + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'",
      Integer.class
    )).thenReturn(1);

    DatabaseSchemaInitializer initializer =
      new DatabaseSchemaInitializer(jdbcTemplate, resolver, executor);

    assertThatThrownBy(initializer::initializeEmptyDatabase)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("仅允许对空库执行");
    verifyNoInteractions(resolver, executor);
  }

  @Test
  void shouldExecuteSystemSchemaBeforeBusinessModules() throws Exception {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
    List<List<Resource>> executions = new ArrayList<>();
    List<Resource> resources = List.of(
      namedResource("schema-z-extension.sql"),
      namedResource("schema-system.sql"),
      namedResource("schema-asset.sql")
    );
    when(resolver.getResources("classpath*:schema/*.sql"))
      .thenReturn(resources.toArray(Resource[]::new));
    when(jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM information_schema.tables "
        + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'",
      Integer.class
    )).thenReturn(0);

    DatabaseSchemaInitializer initializer =
      new DatabaseSchemaInitializer(jdbcTemplate, resolver, executions::add);

    initializer.initializeEmptyDatabase();

    assertThat(executions).hasSize(1);
    assertThat(executions.getFirst())
      .extracting(Resource::getFilename)
      .containsExactly(
        "schema-system.sql",
        "schema-asset.sql",
        "schema-z-extension.sql"
      );
  }

  @Test
  void everyPackagedSchemaShouldContainExecutableSql() throws Exception {
    Resource[] schemas = new PathMatchingResourcePatternResolver()
      .getResources("classpath*:schema/*.sql");
    assertThat(schemas)
      .extracting(Resource::getFilename)
      .containsExactlyInAnyOrder(
        "schema-system.sql", "schema-org.sql", "schema-course.sql", "schema-asset.sql",
        "schema-question.sql", "schema-exam.sql", "schema-training.sql",
        "schema-evaluation.sql", "schema-survey.sql", "schema-archive.sql",
        "schema-certificate.sql", "schema-ops.sql", "schema-regulation.sql"
      );
    for (Resource schema : schemas) {
      String content = schema.getContentAsString(StandardCharsets.UTF_8)
        .replaceAll("(?m)^\\s*--.*$", "")
        .replaceAll("(?s)/\\*.*?\\*/", "")
        .trim();
      assertThat(content)
        .as("schema 必须包含可执行 SQL: %s", schema.getFilename())
        .isNotBlank();
      assertThat(content)
        .as("init 完整 SQL 不得包含 ALTER: %s", schema.getFilename())
        .doesNotContainIgnoringCase("ALTER TABLE");
    }
  }

  private Resource namedResource(String filename) {
    return new ByteArrayResource("SELECT 1;".getBytes()) {
      @Override
      public String getFilename() {
        return filename;
      }
    };
  }
}
