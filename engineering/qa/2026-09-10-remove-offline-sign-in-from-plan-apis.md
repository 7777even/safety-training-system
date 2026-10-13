# QA：移除线下培训签到残留

## 范围

OpenSpec Change：`remove-offline-sign-in-from-plan-apis` Task 1.1～2.1。

- 发布预检与发布不再要求签到窗口、签退要求或签到规则。
- 批量生成与单内容补建只创建教师、地点、起止时间和状态等线下场次事实。
- 场次请求/响应、Entity 和 Training OpenAPI 删除签到字段。
- 删除 `sts_training_attendance`、场次三个签到列、空签到归档快照列，以及签到 Java 资产。
- 删除教师工作台待确认签到统计和三个签到权限/菜单种子。
- 不改变场次状态机、线下成绩、学员详情和成绩投影。

## TDD 证据

Red：移除测试构造中的签到配置后，`TrainingPlanPublishValidatorTest` 仍返回旧提示并阻止冻结，
`TrainingExecutionProvisionerTest` 仍观察到默认 `SIGN_IN`，共 2 个失败、1 个错误。

Green：在 `backend/` 执行：

```text
mvn -pl sts-service-admin \
  -Dtest=TrainingPlanPublishValidatorTest,TrainingPlanServiceImplTest,TrainingExecutionProvisionerTest,TrainingOfflineSessionServiceImplTest,TrainingWorkflowServiceImplTest,OpsWorkbenchServiceImplTest,ArchiveServiceImplTest test
```

结果：96 项通过，0 失败。

```text
mvn -pl sts-service-admin \
  -Dtest=TrainingExecutionControllerTest,TrainingPlanControllerTest,TrainingResultSyncServiceImplTest,OpsControllerWebSliceTest test
```

结果：33 项通过，0 失败。

## 真实 MySQL 与初始化验证

```text
mvn -pl sts-service-admin -Dtest=FlywayMigrationIT test
```

结果：3 项通过，确认旧结构可升级，签到表、场次签到列、归档签到快照列和签到菜单均被删除；最新 init baseline 不重复执行
migration。

```text
mvn -pl sts-service-admin -Dtest=TrainingPlanMapperIT,TrainingMixedPlanLifecycleIT test
```

结果：10 项通过，确认无签到列时线下场次可真实写入，混合计划仅凭合格成绩完成线下项。

```text
mvn -pl sts-service-admin-init -Dtest=AdminInitFlywayIT test
```

结果：1 项通过，13 份完整 init SQL、`20260910195000` baseline 和菜单种子可在 MySQL 8.0.45 完成初始化。

```text
mvn -pl sts-service-admin-init \
  -Dtest=MenuPermissionConsistencyTest#attendancePermissionsShouldBeAbsent,DatabaseSchemaInitializerTest test
```

结果：5 项通过，确认三个签到权限不在菜单与当前菜单树中，模块化 schema 可执行。

## 契约与静态验证

- `training.openapi.json`、`ops.openapi.json` 及三份菜单 JSON 均通过 `jq empty`。
- 四个线下相关 Schema 均不含签到字段，required 列表同步。
- `openspec validate remove-offline-sign-in-from-plan-apis --strict` 通过。
- `git diff --check` 通过。

## 已知非本次问题

完整 `MenuPermissionConsistencyTest` 仍因仓库既有 OPS、监管、证书等 Controller 权限未进入当前菜单种子，以及既有
`training:offline-score:edit`、`asset:asset:preview` 多余权限而失败。本次新增的签到权限专项用例通过，失败差异中已无三个签到权限；未扩大范围修复其他权限模型。

## Review

- 规格符合性：通过。指定接口、数据表、权限和契约均按确认范围删除，明确保留的成绩与状态接口未改。
- 代码质量：通过。生成入口继续共用 Provisioner；没有空字段兼容、占位规则或残留签到 Bean；DDL、init、Entity 和基线版本一致。

