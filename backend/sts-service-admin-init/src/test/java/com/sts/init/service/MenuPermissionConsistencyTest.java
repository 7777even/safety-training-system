package com.sts.init.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MenuPermissionConsistencyTest {

  private static final Pattern PERMISSION_ANNOTATION =
    Pattern.compile("@SaCheckPermission\\s*\\((.*?)\\)", Pattern.DOTALL);
  private static final Pattern QUOTED_VALUE = Pattern.compile("\"([^\"]+)\"");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void menusShouldMatchEveryControllerPermissionAndContainNoUnsupportedPermission() throws IOException {
    Set<String> controllerPermissions = readControllerPermissions();
    Set<String> menuPermissions = readMenus().stream()
      .map(menu -> (String) menu.get("perms"))
      .filter(permission -> permission != null && !permission.isBlank())
      .collect(java.util.stream.Collectors.toSet());

    assertThat(menuPermissions)
      .as("菜单必须覆盖全部 Controller 权限，且不得保留没有后端能力的占位权限")
      .containsExactlyInAnyOrderElementsOf(controllerPermissions);
  }

  @Test
  void attendancePermissionsShouldBeAbsent() throws IOException {
    assertThat(readMenus()).extracting(menu -> (String) menu.get("perms"))
      .doesNotContain("training:attendance:list", "training:attendance:edit", "training:attendance:confirm");
    assertThat(objectMapper.writeValueAsString(readFrontendMenus()))
      .doesNotContain("training:attendance:list", "training:attendance:edit", "training:attendance:confirm");
  }

  @Test
  void menuTreeShouldHaveUniqueIdsValidParentsAndCorrectRootModules() throws IOException {
    List<Map<String, Object>> menus = readMenus();
    Set<Long> ids = new HashSet<>();
    for (Map<String, Object> menu : menus) {
      assertThat(ids.add(number(menu, "id")))
        .as("menu_id 必须唯一: %s", menu.get("id"))
        .isTrue();
    }
    for (Map<String, Object> menu : menus) {
      long id = number(menu, "id");
      long parentId = number(menu, "pid");
      long moduleId = number(menu, "moduleId");
      if (parentId == 0) {
        assertThat(moduleId).as("根菜单 moduleId 必须等于自身 id: %s", id).isEqualTo(id);
      } else {
        assertThat(ids).as("父菜单必须存在: %s -> %s", id, parentId).contains(parentId);
      }
    }
  }

  @Test
  void displayMenuTreeShouldMatchCurrentFrontendMenuTree() throws IOException {
    List<String> expected = new ArrayList<>();
    for (Map<String, Object> root : readFrontendMenus()) {
      flattenFrontendDisplayMenus(root, "ROOT", expected);
    }

    List<Map<String, Object>> menus = readMenus();
    Map<Long, List<Map<String, Object>>> childrenByParent = new LinkedHashMap<>();
    menus.stream()
      .filter(menu -> !"F".equals(menu.get("type")))
      .forEach(menu -> childrenByParent
        .computeIfAbsent(number(menu, "pid"), ignored -> new ArrayList<>())
        .add(menu));
    childrenByParent.values().forEach(children -> children.sort(
      Comparator.comparingLong(menu -> number(menu, "sort"))));

    List<String> actual = new ArrayList<>();
    flattenSeedDisplayMenus(0L, "ROOT", childrenByParent, actual);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  @Test
  void seedMenusShouldIncludeLearnerTrainingAndTeacherTaskEntries() throws IOException {
    List<Map<String, Object>> menus = readMenus();

    assertThat(menus).anySatisfy(menu -> assertThat(menu)
      .containsEntry("id", 9564)
      .containsEntry("pid", 9527)
      .containsEntry("name", "培训列表")
      .containsEntry("perms", "training:learner:task")
      .containsEntry("type", "F")
      .containsEntry("moduleId", 1));
    assertThat(menus).anySatisfy(menu -> assertThat(menu)
      .containsEntry("id", 9565)
      .containsEntry("pid", 800)
      .containsEntry("name", "教学任务")
      .containsEntry("path", "teacher-tasks")
      .containsEntry("comp", "business-center/training/teacher-tasks/index")
      .containsEntry("perms", "training:teacher:task")
      .containsEntry("type", "C")
      .containsEntry("moduleId", 9530));
  }

  @Test
  void shouldSeedM4ResultMenusWithoutConflictingIdentifiers() throws IOException {
    List<Map<String, Object>> menus = readMenus();

    assertThat(menus).anySatisfy(menu -> assertThat(menu)
      .containsEntry("id", 9700)
      .containsEntry("pid", 9530)
      .containsEntry("name", "培训成果")
      .containsEntry("path", "/result")
      .containsEntry("comp", "Layout")
      .containsEntry("type", "M")
      .containsEntry("moduleId", 9530));
    assertThat(menus).anySatisfy(menu -> assertThat(menu)
      .containsEntry("id", 9701)
      .containsEntry("pid", 9700)
      .containsEntry("path", "survey")
      .containsEntry("comp", "survey/index")
      .containsEntry("perms", "survey:survey:list")
      .containsEntry("type", "C")
      .containsEntry("moduleId", 9530));
    assertThat(menus).anySatisfy(menu -> assertThat(menu)
      .containsEntry("id", 9702)
      .containsEntry("pid", 9700)
      .containsEntry("path", "archive")
      .containsEntry("comp", "archive/index")
      .containsEntry("perms", "archive:training:list")
      .containsEntry("type", "C")
      .containsEntry("moduleId", 9530));
    assertThat(menus).anySatisfy(menu -> assertThat(menu)
      .containsEntry("id", 9703)
      .containsEntry("pid", 9700)
      .containsEntry("path", "certificate")
      .containsEntry("comp", "certificate/index")
      .containsEntry("perms", "certificate:template:list")
      .containsEntry("type", "C")
      .containsEntry("moduleId", 9530));
    // 证书三个叶子页面必须各自存在，页面才能按菜单路径解析。
    assertThat(menus).anySatisfy(menu -> assertThat(menu)
      .containsEntry("id", 9704)
      .containsEntry("pid", 9703)
      .containsEntry("path", "templates")
      .containsEntry("comp", "certificate/templates/index"));
    assertThat(menus).anySatisfy(menu -> assertThat(menu)
      .containsEntry("id", 9705)
      .containsEntry("pid", 9703)
      .containsEntry("path", "rules")
      .containsEntry("comp", "certificate/rules/index")
      .containsEntry("perms", "certificate:rule:list"));
    assertThat(menus).anySatisfy(menu -> assertThat(menu)
      .containsEntry("id", 9706)
      .containsEntry("pid", 9703)
      .containsEntry("path", "certificates")
      .containsEntry("comp", "certificate/certificates/index")
      .containsEntry("perms", "certificate:cert:list"));

    // M4 能力必须可授权：创建/更新档案与证书签发配置都有独立按钮权限。
    assertThat(menus).anySatisfy(menu -> assertThat(menu)
      .containsEntry("id", 9721)
      .containsEntry("pid", 9702)
      .containsEntry("perms", "archive:training:create")
      .containsEntry("type", "F"));
    assertThat(menus).anySatisfy(menu -> assertThat(menu)
      .containsEntry("id", 9742)
      .containsEntry("pid", 9705)
      .containsEntry("perms", "certificate:rule:edit")
      .containsEntry("type", "F"));
    // 签发规则来源收敛为课程、理论考试和培训，未实现的撤销/重发不得出现在菜单里。
    assertThat(menus).extracting(menu -> String.valueOf(menu.get("perms")))
      .doesNotContain("certificate:cert:issue", "certificate:cert:revoke", "certificate:cert:reissue");
  }

  private Set<String> readControllerPermissions() throws IOException {
    Path sourceRoot = findBackendRoot()
      .resolve("sts-service-admin/src/main/java");
    Set<String> permissions = new HashSet<>();
    try (Stream<Path> files = Files.walk(sourceRoot)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        Matcher annotationMatcher = PERMISSION_ANNOTATION.matcher(Files.readString(file));
        while (annotationMatcher.find()) {
          Matcher valueMatcher = QUOTED_VALUE.matcher(annotationMatcher.group(1));
          while (valueMatcher.find()) {
            String value = valueMatcher.group(1);
            if (value.contains(":")) {
              permissions.add(value);
            }
          }
        }
      }
    }
    return permissions;
  }

  private List<Map<String, Object>> readMenus() throws IOException {
    try (var input = new ClassPathResource("data/menus.json").getInputStream()) {
      return objectMapper.readValue(input, new TypeReference<>() {
      });
    }
  }

  private List<Map<String, Object>> readFrontendMenus() throws IOException {
    try (var input = new ClassPathResource("data/current-online-menu-tree.json").getInputStream()) {
      return objectMapper.readValue(input, new TypeReference<>() {
      });
    }
  }

  @SuppressWarnings("unchecked")
  private void flattenFrontendDisplayMenus(Map<String, Object> menu, String parentKey,
                                           List<String> output) {
    if (!"F".equals(menu.get("menuType"))) {
      String key = displayKey(
        parentKey,
        (String) menu.get("menuType"),
        (String) menu.get("menuName"),
        (String) menu.get("path"),
        (String) menu.get("component"),
        (String) menu.get("permission"),
        (String) menu.get("icon"),
        ((Number) menu.get("sortOrder")).longValue()
      );
      output.add(key);
      for (Map<String, Object> child : (List<Map<String, Object>>) menu.getOrDefault("children", List.of())) {
        flattenFrontendDisplayMenus(child, key, output);
      }
    }
  }

  private void flattenSeedDisplayMenus(long parentId, String parentKey,
                                       Map<Long, List<Map<String, Object>>> childrenByParent,
                                       List<String> output) {
    for (Map<String, Object> menu : childrenByParent.getOrDefault(parentId, List.of())) {
      String key = displayKey(
        parentKey,
        (String) menu.get("type"),
        (String) menu.get("name"),
        (String) menu.get("path"),
        (String) menu.get("comp"),
        (String) menu.get("perms"),
        (String) menu.get("icon"),
        number(menu, "sort")
      );
      output.add(key);
      flattenSeedDisplayMenus(number(menu, "id"), key, childrenByParent, output);
    }
  }

  private String displayKey(String parentKey, String type, String name, String path,
                            String component, String permission, String icon, long sort) {
    return String.join("|",
                       parentKey,
                       value(type),
                       value(name),
                       value(path),
                       value(component),
                       value(permission),
                       value(icon),
                       String.valueOf(sort)
    );
  }

  private String value(String value) {
    return value == null ? "" : value;
  }

  private Path findBackendRoot() {
    return findRepositoryRoot().resolve("backend");
  }

  private Path findRepositoryRoot() {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      if (Files.isDirectory(current.resolve("backend/sts-service-admin/src/main/java"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("无法定位仓库根目录");
  }

  private long number(Map<String, Object> menu, String key) {
    return ((Number) menu.get(key)).longValue();
  }
}
