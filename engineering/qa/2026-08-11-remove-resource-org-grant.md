# 跨机构资源授权退役 QA（2026-08-11）

## 范围

OpenSpec Change：`2026-08-11-remove-resource-org-grant` Task 1～3，lims 纯后端范围；未修改前端。

## 验收结果

- 课程、课程考试内容项、培训类型、培训标准和培训模板统一限制为当前机构资源，其他机构资源按不存在处理或拒绝引用。
- `/api/common/resource-grants/**`、`common:grant:*`、`com.sts.grant` 生产实现及其专属测试已删除。
- 当前 schema、初始化菜单和 OpenAPI 不再定义资源授权表、菜单、权限或接口；历史 migration、历史 OpenSpec 和历史 QA 保留。
- 新 migration 先清理角色菜单关联及 5 个菜单/权限节点，再删除 `sts_common_resource_org_grant`。
- 路线图、进度表、角色流程、系统菜单和相关 API 契约已同步为严格机构隔离。

## TDD 证据

- Red：旧实现下，课程/考试/培训跨机构拒绝目标测试共观察到 8 个预期失败；当前机构成功路径仍通过。
- Green：`CourseServiceImplTest`、`CourseItemServiceImplTest`、`TrainingResourceAccessHelperTest`、
  `TenantLineHandlerImplTest` 共 36/36 通过；独立 Review 补充的禁用状态护栏先观察到 3 个失败，再达到 Green。
- 租户边界 IT：`TenantIsolationIT`、`ExamTenantBoundaryIT`、`TrainingTenantBoundaryIT` 共 17/17 通过。
- 退役 migration IT：`ResourceGrantRetirementMigrationIT` 1/1 通过，确认角色菜单关联、菜单节点和授权表均被删除。

## 完整验证

- `mvn -pl sts-service-admin -am -DskipTests compile`：通过。
- `mvn -pl sts-service-admin -am test`：561/561 通过。
- `mvn -pl sts-service-admin -am -Pintegration-test test`：191/191 通过，使用 MySQL 8.0.45 Testcontainers。
- 完整 IT 启动时依次在空库执行当前 14 份 `backend/sql/schema-*.sql`，确认当前全量 schema 可顺序构建；历史 migration
  保持不可变，退役 migration 另由 `ResourceGrantRetirementMigrationIT` 验证。
- `jq empty docs/api/course.openapi.json docs/api/training.openapi.json` 及三份初始化菜单 JSON：通过。
- `openspec validate 2026-08-11-remove-resource-org-grant --strict`：通过。
- 当前代码、schema、API、菜单与长期文档残余引用扫描：仅退役 migration 和退役验证测试保留当前事实；历史资产不改写。
- `git diff --check`：通过。

## Review

- 规格符合性：通过。首次 Review 发现的活动文档、成功/租户边界测试和验收记录缺口已修正，复审无阻断问题。
- 代码质量：通过。首次 Review 发现的培训资源禁用状态护栏遗漏已按 TDD 修正，复审无阻断问题。

## 数据破坏、部署与恢复

- `V20260811140000__remove_resource_org_grant.sql` 会永久删除全部存量授权记录、相关菜单授权关系及授权表。
- 执行 migration 前必须完成数据库备份；本次工作未连接或修改生产数据库。
- 数据恢复依赖执行前备份，并需回退到仍包含资源授权实现的应用版本；没有应用内自动回滚或数据转换路径。

## 未纳入范围

- 当前 Git 身份为 `lims`，按仓库规则未修改 `frontend/`。前端残留页面、路由和 Mock 需由非 lims 协作方按已删除的 OpenAPI
  另行移除。
