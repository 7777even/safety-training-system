# QA：2026-09-20-realign-m5-minimum-closure（后端）

日期：2026-09-20  
负责人：lims  
范围：仅后端；前端以 OpenAPI 为准另开。功能项 4「授权」未确认，不新增授权模型。

## 结论

按 lims 人工确认「后端可交互即可」归档本 Change。前端页面验收不在本协作范围。Task 1～7 后端已交付：机构分类、监管工作台、学习统计、学时预警真实
CSV、企业工作台，以及超范围公开入口删除与 SQL COMMENT。功能项 4「授权」未确认，不伪装完成。

## 功能项追踪

| 功能项           | 语义                     | 后端                                  |
|---------------|------------------------|-------------------------------------|
| 1 园区监管工作台     | 概览、类型/特种作业、机构摘要、快捷入口   | 已交付 `GET /regulation/workbench`     |
| 2 学时预警        | 计划学时不足自动刷新、待查看/已查看、CSV | 已交付                                 |
| 3/7 学习统计与数据中心 | Design 5.2 口径、下钻、CSV   | 已交付                                 |
| 4 机构管理        | 企业/培训机构分类 CRUD、只读进入    | 分类与只读已交付；授权未确认                      |
| 8 企业工作台       | 权限化入口、概览、分类待办          | 已交付 `GET /ops/workbench/enterprise` |

## Task 证据

| Task          | 后端状态    | 证据                                                                   |
|---------------|---------|----------------------------------------------------------------------|
| 1 基线与收口       | 完成      | `inventory.md`；删除 teacher/learner/stats/export/rules/detect/处置 API   |
| 2 机构分类        | 完成；授权另开 | `TRAINING_INSTITUTION`；监管可维护受监管机构；`X-Target-Tenant-Id` 覆盖两类          |
| 3 工作台与学习统计    | 完成      | 合并 overview + aggregates；`RegulatorAggregateMapperIT` 含培训机构与学习零分母    |
| 4 学时预警        | 完成      | 计划保存刷新；重复 UNREAD 幂等；补足学时 ENDED；UTF-8 BOM CSV                         |
| 5 企业工作台       | 完成      | 快捷入口服从 `StpUtil.hasPermission`；待办分类可加总                               |
| 6 删除与 COMMENT | 完成      | Flyway `V20260920190000`；init schema-version `20260920190000`；菜单种子收口 |
| 7 关闭          | 后端口径已归档 | 2026-09-20 人工确认归档；主规格已同步 `openspec/specs/m5-minimum-closure/spec.md` |

## 验证

零 DB（受影响）：

```
mvn -pl sts-service-admin -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest=Utf8CsvExporterTest,RegulationWorkbenchServiceTest,RegulationLearningStatsServiceTest,WarningServiceImplTest,RegulationControllerTest,RegulationControllerTrainingRecordTest,PlanHoursWarningRefreshServiceTest,OpsWorkbenchServiceImplTest,OpsControllerWebSliceTest,OrganizationServiceImplTest,RegulatorRolePermissionsTest,RegulatorViewResolverTest,RegulatorIdentityGuardTest,TrainingPlanServiceImplTest,TrainingPlanLifecycleServiceImplTest
```

结果：93 tests, 0 failures。

MySQL IT：

```
mvn -pl sts-service-admin -am -Pintegration-test test -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PlanHoursWarningRefreshServiceIT,RegulatorAggregateMapperIT,FlywayMigrationIT,OrganizationMapperIT,TrainingMixedPlanLifecycleIT,OpsExportJobMapperIT,RegulationWarningMapperIT
```

结果：22 tests, 0 failures。含 `FlywayMigrationIT` 5、`RegulatorAggregateMapperIT` 6、`PlanHoursWarningRefreshServiceIT` 1。

`git diff --check` 通过。

## 未运行项

- 全量 Admin 零 DB / 全量 IT：用受影响目标测试代替。
- PC/移动端页面与真实双角色登录联调：他方负责。
- 存量计划一次性回填：提供 `refreshAllActivePlans()`，未挂 ApplicationRunner，避免部署时意外扫全库。

## 阻塞

- 前端以 `docs/api/regulation.openapi.json`、`ops.openapi.json`、`org.openapi.json` 另开。
- 功能项 4「授权」未选择，不得声称完整覆盖该项。
