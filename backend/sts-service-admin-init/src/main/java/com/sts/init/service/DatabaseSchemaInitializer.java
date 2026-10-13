package com.sts.init.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.*;

@Slf4j
@Service
public class DatabaseSchemaInitializer {

  private static final String SCHEMA_PATTERN = "classpath*:schema/*.sql";
  private static final List<String> SCHEMA_ORDER = List.of(
    "schema-system.sql",
    "schema-org.sql",
    "schema-course.sql",
    "schema-asset.sql",
    "schema-question.sql",
    "schema-exam.sql",
    "schema-training.sql",
    "schema-evaluation.sql",
    "schema-survey.sql",
    "schema-archive.sql",
    "schema-certificate.sql",
    "schema-ops.sql",
    "schema-regulation.sql"
  );

  private final JdbcTemplate jdbcTemplate;
  private final ResourcePatternResolver resourceResolver;
  private final SqlScriptExecutor sqlScriptExecutor;

  @Autowired
  public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
    this(
      jdbcTemplate,
      new PathMatchingResourcePatternResolver(),
      scripts -> executeScripts(jdbcTemplate.getDataSource(), scripts)
    );
  }

  DatabaseSchemaInitializer(
    JdbcTemplate jdbcTemplate,
    ResourcePatternResolver resourceResolver,
    SqlScriptExecutor sqlScriptExecutor
  ) {
    this.jdbcTemplate = jdbcTemplate;
    this.resourceResolver = resourceResolver;
    this.sqlScriptExecutor = sqlScriptExecutor;
  }

  public void initializeEmptyDatabase() {
    Integer tableCount = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM information_schema.tables "
        + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'",
      Integer.class
    );
    if (tableCount == null) {
      throw new IllegalStateException("无法检测当前数据库是否为空");
    }
    if (tableCount > 0) {
      throw new IllegalStateException(
        "拒绝初始化：当前数据库已存在 " + tableCount + " 张表，仅允许对空库执行"
      );
    }

    List<Resource> scripts = loadSchemaScripts();
    log.info("Empty database confirmed. Executing {} schema scripts...", scripts.size());
    for (Resource script : scripts) {
      log.info("Executing schema script: {}", script.getFilename());
    }
    sqlScriptExecutor.execute(scripts);
    log.info("All schema scripts executed successfully.");
  }

  List<Resource> loadSchemaScripts() {
    try {
      Resource[] discovered = resourceResolver.getResources(SCHEMA_PATTERN);
      Map<String, Resource> scriptsByName = new LinkedHashMap<>();
      for (Resource resource : discovered) {
        String filename = resource.getFilename();
        if (filename != null) {
          scriptsByName.putIfAbsent(filename, resource);
        }
      }
      if (scriptsByName.isEmpty()) {
        throw new IllegalStateException("未找到任何 schema SQL 资源");
      }

      Comparator<Resource> comparator = Comparator
        .comparingInt((Resource resource) -> schemaOrder(resource.getFilename()))
        .thenComparing(resource -> resource.getFilename() == null ? "" : resource.getFilename());

      List<Resource> scripts = new ArrayList<>(scriptsByName.values());
      scripts.sort(comparator);
      return scripts;
    } catch (IOException e) {
      throw new IllegalStateException("读取 schema SQL 资源失败", e);
    }
  }

  private static int schemaOrder(String filename) {
    int index = SCHEMA_ORDER.indexOf(filename);
    return index >= 0 ? index : Integer.MAX_VALUE;
  }

  private static void executeScripts(DataSource dataSource, List<Resource> scripts) {
    if (dataSource == null) {
      throw new IllegalStateException("数据源未配置");
    }
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
    populator.addScripts(scripts.toArray(Resource[]::new));
    populator.execute(dataSource);
  }

  @FunctionalInterface
  interface SqlScriptExecutor {
    void execute(List<Resource> scripts);
  }
}
