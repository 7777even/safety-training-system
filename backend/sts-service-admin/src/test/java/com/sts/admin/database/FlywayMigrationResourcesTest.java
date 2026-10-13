package com.sts.admin.database;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationResourcesTest {

  private static final Pattern MIGRATION_NAME =
    Pattern.compile("^V(?<version>\\d{14})__(?<description>[a-z0-9_]+)\\.sql$");

  @Test
  void shouldPackageOnlyVersionedIncrementalMigrationsWhenPresent() throws IOException {
    Resource[] resources = new PathMatchingResourcePatternResolver()
      .getResources("classpath*:db/migration/*.sql");
    List<MigrationFile> migrations = Arrays.stream(resources)
      .map(Resource::getFilename)
      .filter(filename -> filename != null)
      .map(MigrationFile::parse)
      .sorted()
      .toList();

    Map<String, Long> countsByName = migrations.stream()
      .collect(Collectors.groupingBy(MigrationFile::filename, Collectors.counting()));
    assertThat(countsByName).allSatisfy((name, count) -> assertThat(count)
      .as("migration 文件名必须唯一: %s", name)
      .isEqualTo(1L));

    Map<String, List<String>> filenamesByVersion = migrations.stream()
      .collect(Collectors.groupingBy(
        MigrationFile::version,
        Collectors.mapping(MigrationFile::filename, Collectors.toList())));
    assertThat(filenamesByVersion).allSatisfy((version, filenames) -> assertThat(filenames)
      .as("Flyway 不允许重复版本 %s: %s", version, filenames)
      .hasSize(1));
  }

  private record MigrationFile(String version, String filename)
    implements Comparable<MigrationFile> {

    static MigrationFile parse(String filename) {
      Matcher matcher = MIGRATION_NAME.matcher(filename);
      assertThat(matcher.matches())
        .as("Flyway migration 文件名不符合项目规范: %s", filename)
        .isTrue();
      return new MigrationFile(matcher.group("version"), filename);
    }

    @Override
    public int compareTo(MigrationFile other) {
      return version.compareTo(other.version);
    }
  }
}
