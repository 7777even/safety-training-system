# Tasks：移除跨机构资源授权能力

> 状态：已人工确认（2026-08-11）；一次只执行一个 Task。
>
> Owner：lims

## Task 1：关闭跨机构资源访问路径

- 状态：已完成（2026-08-11）
- Owner：lims
- 输入：现有资源授权服务、跨租户读取组件、课程详情、课程考试内容项和培训基础资源访问逻辑。
- 输出：课程、考试、培训类型、培训标准和培训模板统一执行当前机构边界；删除跨租户资源读取旁路及授权校验依赖；对应自动化测试。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：不新增接口；现有课程、考试和培训接口的资源边界收敛为当前机构。
- 数据影响：本 Task 不改表；不再读取 `sts_common_resource_org_grant`。
- 验收标准：
  - [x] 先观察跨机构课程、考试和培训资源拒绝测试在旧实现下失败，再完成最小实现。
  - [x] 当前机构资源的既有成功路径不受影响。
  - [x] 其他机构课程详情不可见，响应不泄露资源信息。
  - [x] 课程不能引用其他机构考试。
  - [x] 培训类型、培训标准和培训模板不能跨机构读取或引用。
  - [x] 生产代码不存在绕过租户插件读取业务资源的 `CrossTenantResourceReader`。
- 测试方式：Admin 受影响零 DB 测试；课程与培训租户边界 MySQL IT。
- 当前验证（2026-08-11）：
  - Red：目标零 DB 29 个用例中 5 个按预期失败；补齐培训标准/模板后，4 个培训资源用例中 3 个跨机构拒绝按预期失败。
  - Green：`CourseServiceImplTest`、`CourseItemServiceImplTest`、`TrainingResourceAccessHelperTest`、
    `TenantLineHandlerImplTest` 共 36/36 通过；Review 补充的 3 个禁用状态护栏先 Red 后 Green。
  - MySQL IT：`TenantIsolationIT`、`ExamTenantBoundaryIT`、`TrainingTenantBoundaryIT` 共 17/17 通过。

## Task 2：撤销资源授权管理能力与存量数据

- 状态：已完成（2026-08-11）
- Owner：lims
- 输入：Task 1；资源授权 Controller/Service/Mapper/Entity/DTO/VO、菜单权限、租户豁免、授权表、初始化资产和
  `resource-grant.openapi.json`。
- 输出：资源授权 API 和后端实现删除；新增退役 migration；当前 schema、菜单、权限、OpenAPI 和初始化数据不再包含资源授权能力。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：删除 `/api/common/resource-grants/**` 和 `common:grant:*`，无兼容期、无替代接口。
- 数据影响：新增 migration 删除资源授权菜单/角色关联和 `sts_common_resource_org_grant`；全部存量授权记录永久删除。历史
  migration 保留。
- 验收标准：
  - [x] `com.sts.grant` 生产实现及专属测试全部删除，不保留空壳或桩。
  - [x] `TenantLineHandlerImpl` 不再豁免授权表，应用代码不再引用授权表。
  - [x] migration 先清理角色菜单关联及菜单节点，再删除授权表。
  - [x] 当前全量 schema 和初始化菜单资产不再定义资源授权表、菜单或权限码。
  - [x] `docs/api/resource-grant.openapi.json` 删除，其他当前契约不再声明跨机构授权。
  - [x] 历史 migration、已完成 OpenSpec 和历史 QA 保留且不改写。
- 测试方式：migration MySQL IT/空库顺序构建；应用编译；菜单与权限引用扫描；OpenAPI JSON 和 `$ref` 检查。
- 当前验证（2026-08-11）：
  - 后端 `mvn -pl sts-service-admin -am -DskipTests compile` 通过。
  - `ResourceGrantRetirementMigrationIT` 1/1 通过，确认角色菜单关联、5 个菜单/权限节点和授权表删除。
  - `course.openapi.json`、`training.openapi.json` 与三份初始化菜单 JSON 解析通过。

## Task 3：完成退役验收与工程闭环

- 状态：已完成（2026-08-11）
- Owner：lims
- 输入：Task 1～2；路线图、进度表、当前长期文档与验收规则。
- 输出：当前文档同步、QA、Retro、双轴 Review、OpenSpec Task 状态和进度记录。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：确认已下线 Contract 无残余入口。
- 数据影响：核对 DROP migration、备份前置要求和恢复边界；不直接操作生产数据库。
- 验收标准：
  - [x] 当前路线图、进度表、角色流程、系统菜单和 API 清单均标明资源授权已退役。
  - [x] 受影响零 DB 和 MySQL IT 全部通过；Docker 不可用时如实记录未执行项并保持 Task 未完成。
  - [x] 静态扫描仅允许历史 migration、历史 OpenSpec、历史 QA 和归档设计保留资源授权事实。
  - [x] 规格符合性 Review 结论为“通过”。
  - [x] 代码质量 Review 结论为“通过”。
  - [x] QA、Retro、tasks.md 和进度表记录实际命令、结果、数据破坏及回滚边界。
- 测试方式：受影响模块零 DB、integration-test、migration/菜单检查、OpenAPI 检查、`openspec validate --strict`、
  `git diff --check`。
- 当前验证（2026-08-11）：
  - Admin 零 DB 全量 561/561 通过；MySQL/MinIO IT 全量 191/191 通过。
  - 当前 14 份 schema 在 Testcontainers 空库依次初始化成功；退役 migration IT 1/1 通过。
  - OpenAPI 与三份菜单 JSON 分别解析通过；活动生产路径和当前真相源残余扫描通过。
  - `openspec validate 2026-08-11-remove-resource-org-grant --strict` 与 `git diff --check` 通过。
  - 独立规格符合性 Review：通过；独立代码质量 Review：通过。
