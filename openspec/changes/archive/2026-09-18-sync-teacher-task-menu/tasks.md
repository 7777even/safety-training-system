# Tasks：同步教学任务菜单与初始化权限

## T1 菜单种子与线上菜单树 [x]

- **Owner**：后端
- **输入**：确认的线上菜单树、既有教师任务路由和 Controller 权限
- **输出**：`9564` 培训列表按钮与 `9565` 教学任务菜单
- **前后端影响**：仅菜单路由数据；不修改页面或接口
- **API 依赖**：既有 `training:learner:task`、`training:teacher:task`
- **数据影响**：新增/修正 `us_sys_menu` 种子数据
- **验收标准**：教学任务菜单可由教师任务权限显示并解析为既有页面；门户培训列表权限节点存在
- **测试方式**：菜单一致性定向测试、JSON 解析

## T2 已部署库菜单修复 [x]

- **Owner**：后端
- **输入**：既有 `us_sys_menu` 菜单数据
- **输出**：幂等 Flyway 数据迁移
- **前后端影响**：无
- **API 依赖**：无
- **数据影响**：插入 `9564`，更新 `9565`
- **验收标准**：重复执行不产生重复菜单；`9565` 不再保留空权限或空模块归属
- **测试方式**：Flyway 资源命名检查、SQL 静态检查

## 验证记录

- `mvn -pl sts-service-admin-init -am "-Dtest=MenuPermissionConsistencyTest#seedMenusShouldIncludeLearnerTrainingAndTeacherTaskEntries+displayMenuTreeShouldMatchCurrentFrontendMenuTree" "-Dsurefire.failIfNoSpecifiedTests=false" test`：2/2 通过。
- `mvn -pl sts-service-admin -am "-Dtest=FlywayMigrationResourcesTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`：1/1 通过。
- `mvn -pl sts-service-admin -am -P integration-test "-Dtest=FlywayMigrationIT#shouldRepairExistingTeacherTaskMenuAndAddLearnerTrainingPermission" "-Dsurefire.failIfNoSpecifiedTests=false" test`：MySQL Testcontainers 1/1 通过，覆盖已有空权限 `9565` 修复、`9564` 新增与重复执行。
- `menus.json` 与 `current-online-menu-tree.json`：JSON 解析通过；`git diff --check` 通过。
