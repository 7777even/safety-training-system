# Tasks：M3 当前机构可引用资源 Contract

> 状态：已人工确认（2026-08-04）。确认后一次只执行一个 Task。
>
> Owner：lims

## Task 1：完成当前机构可引用资源统一查询

- 状态：进行中（2026-08-04；实现与零 DB 已完成，MySQL IT 因 Docker 不可用待验证）
- Owner：lims
- 输入：现有课程、考试、题库、登录身份与机构成员实现；M3 培训模板和计划内容编排缺口。
- 输出：当前登录机构的 `COURSE`、`EXAM`、`QUESTION_BANK` 统一分页查询；`docs/api/training.openapi.json`；零 DB 测试与 MySQL
  IT。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：新增 `GET /api/training/reference-resources`；当前机构由服务端派生，不接受 `organizationId`。
- 数据影响：只读既有资源和版本数据，不新增 migration；QUESTION_BANK 保存 `questionBankId`，开考时复用既有考试快照。
- 验收标准：
  - [x] 仅返回当前机构自己的资源，跨机构资源不出现在结果或分页总数中。
  - [x] 父子机构、监管关系和历史授权记录均不扩大查询范围。
  - [x] 支持资源类型、关键字和统一分页，排序稳定。
  - [x] 仅返回已发布、启用且可执行资源。
  - [x] COURSE 返回 `courseVersionId`，EXAM 返回 `paperVersionId`，QUESTION_BANK 返回 `questionBankId` 且版本标识为空。
  - [x] OpenAPI 满足 Controller tag、中文字段说明、参数及响应 example、错误码和 `x-permission`。
- 测试方式：先观察 Controller/Service 零 DB 测试失败，再实现通过；MySQL IT 覆盖三类资源真实分页、状态过滤、稳定排序和机构隔离。
- 当前验证（2026-08-04）：
  - Red：目标测试因 Controller、Service、Mapper、DTO/VO 不存在而按预期编译失败。
  - Green：新增零 DB 4/4；Admin 全量零 DB 433/433 通过。
  - MySQL IT：已新增 `TrainingReferenceResourceMapperIT` 2 个用例；本机无有效 Docker 环境，Testcontainers 启动前失败，尚未形成通过证据。
  - 契约：OpenAPI JSON、`openspec validate --strict`、`git diff --check` 通过。
  - Review：规格符合性与代码质量自审未发现越界修改；因必需 IT 未通过，Task 保持“进行中”。

## Task 2：完成模板和计划的同机构写入与发布校验

- 状态：待确认
- Owner：lims
- 输入：Task 1；现有培训模板、培训计划、发布快照和执行关联实现。
- 输出：模板/计划内容写入同机构校验；计划发布时资源状态、机构归属和稳定版本复核；必要的 OpenAPI 错误契约。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：复用现有模板/计划写入与发布 operation，不新增跨机构授权接口。
- 数据影响：优先复用既有快照和执行关联字段；如现有结构无法冻结稳定版本，暂停并由人工确认 migration。
- 验收标准：
  - [ ] 模板和计划不能保存其他机构的课程、考试或题库资源。
  - [ ] 跨机构拒绝不泄露资源名称、所属机构或其他业务信息。
  - [ ] 发布事务内重新校验资源属于计划机构且仍处于已发布、启用、可执行状态。
  - [ ] 发布后冻结稳定资源版本，源资源后续变化不改写历史计划事实。
  - [ ] 不调用或扩展 `sts_common_resource_org_grant`。
- 测试方式：TDD 零 DB覆盖三类资源写入护栏和发布复核；MySQL IT 覆盖发布竞态、机构隔离、资源状态和快照可追溯性。

## Task 3：完成 M3 契约、验证与工程闭环

- 状态：待确认
- Owner：lims
- 输入：Task 1～2；OpenSpec 验收标准和 API Contract 规范。
- 输出：完整 `training.openapi.json`、目标测试结果、双轴 Review、QA、Task 与进度记录。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：校验当前 Change 涉及的全部 training operation。
- 数据影响：无新增数据变更。
- 验收标准：
  - [ ] Controller、DTO/VO 与 OpenAPI 对齐，所有 `$ref` 可解析。
  - [ ] 受影响零 DB 与 MySQL IT 全部通过。
  - [ ] 规格符合性和代码质量 Review 结论均为“通过”。
  - [ ] tasks.md、QA 和进度表记录实际命令、结果与验收证据。
  - [ ] 不把前端 Mock、联调环境或种子数据作为本后端 Change 的完成证据。
- 测试方式：受影响模块零 DB、integration-test、OpenAPI 对齐检查、`openspec validate --strict`、`git diff --check`。
