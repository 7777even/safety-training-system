# Tasks：补齐培训域 API 契约与纵向执行入口

> 状态：待人工确认。确认前不得修改业务代码、API Contract、权限或数据库。
>
> Owner：lims
>
> 执行限制：一次只执行一个已确认 Task；单次最多连续完成三个 Task。Task 3 开始前必须再次确认其跨域人工决策点。

## Task 1：完成现有培训与评价接口契约闭环

- 状态：已完成（2026-07-31）
- Owner：lims
- 输入：培训域 5 个 Controller 的 57 个 operation、评价表单 7 个 operation、评价结果 6 个 operation；现有
  DTO/VO；数据库字段与枚举；OpenAPI 编写规范
- 输出：补齐后的 `training.openapi.json`、`evaluation-form.openapi.json`、`evaluation-result.openapi.json`
  ；Controller-operation 差异检查证据
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：只登记已有 Controller；不新增路径，不修改已有业务代码
- 数据影响：无
- 验收标准：
  - [x] `training.openapi.json` 登记全部 57 个已有 operation，不再遗漏现有 Controller 方法
  - [x] `evaluation-form.openapi.json` 7 个已有 operation 与 Controller 一致
  - [x] `evaluation-result.openapi.json` 登记 `GET /api/evaluation/records/{evaluationRecordId}`，与 6 个已有 operation
    一致
  - [x] 已有接口请求和响应均引用具体 DTO/VO/ID/void/执行结果 Schema，不使用无字段通用 `object`
  - [x] 培训类型、标准、规则、模板阶段/内容、计划、参训人、线下执行和评价表单 Schema 字段完整
  - [x] 所有分页接口具有具体 `PageResult<T>`，所有已有 Query 字段均登记为 `query.*` 参数
  - [x] `formType` 只声明 `COMPOSITE/EXPERT_INSPECTION`
  - [x] 业务枚举、400/401/403/404/409、真实业务码（含 `8038`）与代码和数据库一致
  - [x] 每个 Controller/operation/字段有中文说明，每个 operation 有完整参数、请求体和成功响应 example，并包含
    `x-permission`
  - [x] 不借契约补齐改变路径、权限语义、数据库结构或业务代码
- 测试方式：
  - JSON 解析全部通过
  - 自动比对 Controller 与 OpenAPI operation 集合
  - 按 `docs/architecture/openapi-contract-writing-guide.md` 检查 tags、description、example、错误响应和 `x-permission`
  - `openspec validate 2026-07-31-complete-training-api-contract --strict`
  - `git diff --check`
- 当前验证（2026-07-31）：JSON 解析通过；Controller 与 OpenAPI operation 集合逐项一致（training 57、evaluation-form
  7、evaluation-result 6）；字段 description、operation 元数据、request example 缺失检查均为 0；`openspec validate --strict` 与
  `git diff --check` 通过。
- Review：规格符合性通过（只补齐已有路径、请求、响应和参数）；代码质量通过（无业务代码、权限或数据库改动；JSON 引用与结构检查通过）。

## Task 2：完成培训管理输入与可信并发审计能力

- 状态：已完成（2026-07-31）
- Owner：lims
- 输入：Task 1；模板分类层级表；评价表单不可变版本表；聚合根 `version`；当前登录态与既有权限
- 输出：模板分类树、表单版本历史；整体替换和发布并发保护；可信发布人/教师/执行人审计；同步 OpenAPI、零 DB 测试和相关 IT
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：
  - 新增 `GET /api/training/template-categories/tree`
  - 新增 `GET /api/evaluation/forms/{evaluationFormId}/versions`
  - 修改标准规则替换、模板阶段替换及发布请求的 `version` 契约
  - 修改评价表单发布规则的项目关联字段
  - 修改教师任务和执行人审计身份语义
- 数据影响：复用现有表，不新增 migration；版本检查更新现有聚合根 `version`
- 验收标准：
  - [x] 分类树一次返回当前可见范围内的完整层级，同级排序稳定，不依赖分页拼树
  - [x] 表单版本历史可从 `evaluationFormId` 得到各 `formVersionId` 和发布摘要，按版本号稳定排序
  - [x] 标准规则和模板阶段整体替换先原子校验父对象 `version + DRAFT`，失败时不删除任何子记录
  - [x] 发布操作使用版本校验，旧版本、重复发布和发布后编辑返回 HTTP 409
  - [x] 评价规则通过同批次 `evaluationItemCode` 映射真实新建项目 ID，不接受尚未生成的 ID
  - [x] `publishedBy`、`confirmedBy`、`scoredBy` 等执行人从登录态派生
  - [x] 教师任务只读取当前登录教师，不接受或信任客户端 `teacherUserId`
  - [x] 删除保护、资源状态和业务错误码在实现与 OpenAPI 中一致
- 测试方式：
  - 按 TDD 先观察 Controller/Service 目标测试失败，再实现通过
  - 零 DB 测试覆盖请求绑定、登录身份、状态保护、版本冲突和未发生副作用
  - MySQL IT 覆盖树查询、版本排序、真实乐观锁、整体替换事务和删除保护
  - `mvn -pl sts-service-admin -am test`
  - `mvn -pl sts-service-admin -am -Pintegration-test test`
  - OpenAPI/实现差异检查、`openspec validate --strict`、双轴 Review
- 当前验证（2026-07-31）：
  - 目标零 DB 测试 10/10 通过；Admin 全量零 DB 测试 394/394 通过。
  - Task 2 定向 MySQL IT 5/5 通过，覆盖分类层级与稳定排序、表单版本倒序和培训标准/评价表单真实乐观锁。
  - OpenAPI JSON/元数据/关键身份语义检查通过；operation 数为 training 58、evaluation-form 8、evaluation-result 6；
    `openspec validate --strict` 与 `git diff --check` 通过。
  - 全量 MySQL IT 可启动并运行，但既存 `ExamAttemptQueryMapperIT` 的两条 SQL 因引用不存在的 `sts_organization` 表失败；该错误不在
    Task 2 修改范围，已登记但不以其替代定向 IT。
- Review：规格符合性通过；代码质量通过。

## Task 3：完成培训执行、教师处理与学员结果纵向入口

- 状态：已完成（2026-07-31）
- Owner：lims
- 输入：Task 2；已发布培训计划及参训人；`sts_training_execution_link`；课程开课、考试 Candidate/Attempt、线下成绩与正式综合结果事实
- 输出：草稿参训人移除、教师任务详情、执行关联查询/单内容补建、培训上下文开考、学员培训任务/详情、结果同步/分项结果；同步
  OpenAPI、零 DB 测试和相关 IT
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：
  - `DELETE /api/training/plans/{trainingPlanId}/participants/{trainingParticipantId}`
  - `GET /api/training/teacher-tasks/{offlineSessionId}`
  - `GET /api/training/plans/{trainingPlanId}/executions`
  - `POST /api/training/plans/{trainingPlanId}/contents/{planContentId}/provision`
  - `POST /api/training/learner/participants/{trainingParticipantId}/contents/{planContentId}/exam-attempts`
  - `GET /api/training/learner/tasks`
  - `GET /api/training/learner/participants/{trainingParticipantId}`
  - `POST /api/training/plans/{trainingPlanId}/results/synchronize`
  - `GET /api/training/plans/{trainingPlanId}/participants/{trainingParticipantId}/results`
- 数据影响：原则上复用现有表；如需新增排除事实、幂等键或上下文字段，立即暂停并按数据库变更规则另行确认 migration
- 验收标准：
  - [x] 只有草稿计划且不存在下游执行/结果事实时可移除所属参训人
  - [x] 教师任务详情仅当前场次教师可见，可处理动作由后端根据权限和状态派生
  - [x] 执行关联可追溯到计划内容；单内容补建幂等且不重复创建执行实例
  - [x] 培训上下文开考复用考试域 Candidate/Attempt，并按计划、参训人和内容隔离
  - [x] 学员任务和参训详情只返回当前登录用户本人数据
  - [x] 结果同步只读取课程、考试和线下正式事实，重复执行不产生重复或回退
  - [x] 分项结果可追溯到计划内容、执行实例和正式来源状态
  - [x] 不自行改变课程、考试、评价计算、权限或数据库语义
- 测试方式：
  - [x] 开始前记录人工确认的 provision、考试上下文、结果同步和 audience 排除决策
  - 按 TDD 完成 Controller/Service 零 DB 测试
  - MySQL IT 覆盖逻辑删除保护、执行关联唯一键、跨上下文隔离和结果同步幂等
  - 执行受影响 Admin 零 DB 与相关 IT、OpenAPI/实现差异检查、`openspec validate --strict`
  - 完成规格符合性和代码质量双轴 Review，并同步 QA、Retro、原 M3 验收状态和进度表
- 当前验证（2026-07-31）：
  - Red：`TrainingWorkflowServiceImplTest` 首次因目标服务不存在编译失败。
  - 目标零 DB：工作流、Controller、计划执行生成和考试域共 17/17 通过；新增结果不回退用例 6/6 通过。
  - Admin 全量零 DB：401/401 通过。
  - 定向 MySQL IT：`TrainingContractMapperIT`、`TrainingPlanMapperIT`、`ExamMapperIT` 共 8/8 通过。
  - training OpenAPI 67 个 operation，Schema 引用缺失 0；`openspec validate --strict` 与 `git diff --check` 通过。
- Review：规格符合性通过；代码质量通过。未新增 migration、权限模型或前端改动。

## Task 4：闭合复审提出的契约同步与并发语义缺口

- 状态：已完成（2026-07-31）
- Owner：lims
- 输入：Task 1～3 复审结论（未通过，前端 TDD 暂停）；现有 DTO/VO、Service 与 OpenAPI；`org.openapi.json` 错误响应样板
- 输出：training OpenAPI 字段与参数同步；计划整体替换并发保护；6 处普通更新乐观锁护栏；两份 OpenAPI 错误响应与评价表单契约注释
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：
  - `TrainingPlanStagesReplaceRequest`、`TrainingPlanEvaluationFormBindRequest` 新增必填 `version`
  - `GET /api/training/template-categories/tree` 参数由 `query.organizationId`/`query.status` 更正为 `organizationId`/
    `status`
  - 新增业务码 `8003` 培训类型版本冲突、`8041` 模板分类版本冲突
- 数据影响：无。复用 `BaseEntity.@Version` 与既有 `OptimisticLockerInnerInterceptor`，不新增 migration
- 验收标准：
  - [x] training OpenAPI 补齐 `parentId`、`children`、`sessionStatus`、`stageStatus`、`isScored`、`contentStatus`、
    `attemptType`
    七个已存在于 DTO/VO 的字段，字段顺序与 Java 定义一致
  - [x] 分类树接口参数与裸 `Query` 对象绑定一致，分页接口保留 `query.*` 前缀
  - [x] 计划阶段替换和评价表单绑定先按 `version` 乐观锁更新计划，成功后才删除子记录；冲突返回 409 业务码 `8104`
    且不删除任何原有阶段、内容或绑定
  - [x] 培训类型、标准、模板分类、模板、计划、评价表单六处 `update()` 在 `updateById == 0` 时返回 409 与对应版本冲突码
  - [x] 两份 OpenAPI 的 `BadRequest/Unauthorized/Forbidden/NotFound/Conflict` 具备 `application/json`、
    `ResultEnvelope` Schema 引用和真实业务错误示例
  - [x] 评价表单 8 个 operation 说明含关键规则与 `8501~8507`；枚举字段说明各取值含义；可空字段声明 `nullable`；
    发布与版本详情示例不再出现 `items: [null]` / `scoreRules: [null]`
  - [x] 不新增路径、不改权限模型、不改数据库结构、不动前端
- 测试方式：
  - 按 TDD 先观察目标测试失败，再实现通过
  - `mvn -pl sts-service-admin -am test`
  - `mvn -pl sts-service-admin -am -Pintegration-test test`
  - OpenAPI JSON 解析、`$ref` 完整性、Schema 与 DTO/VO 字段交叉比对
  - `openspec validate --strict`、`git diff --check`
- 当前验证（2026-07-31）：
  - Red：6 个 `update_versionConflict` 用例先失败（28 例中 6 失败）；计划替换用例因 `version` 不存在编译失败。
  - Green：Admin 全量零 DB 412/412 通过（原 401 + 新增 11）。
  - 全量 MySQL IT 93/93 通过；Task 2 登记的 `ExamAttemptQueryMapperIT` 既存失败已随
    `2026-07-31-fix-exam-attempt-organization-join` 消除。
  - 17 份 OpenAPI 全部解析通过；training 与 evaluation-form 悬空 `$ref` 均为 0；
    91 个 Schema 与后端 DTO/VO 逐字段比对不一致 0 处。
  - `openspec validate --strict` 与 `git diff --check` 通过。
- Review：规格符合性通过（未新增路径、权限或数据库改动）；代码质量通过（乐观锁写法统一为
  `TrainingStandardServiceImpl.replaceRules` 既有模式）。
- 遗留项：已于 Task 5 全部处理，无未决项。

## Task 5：闭合契约字段说明、枚举约束与执行人审计真实性

- 状态：已完成（2026-07-31）
- Owner：lims
- 输入：第二轮复审（结论未通过，但其数据对应 `f5daf90`，与当前实现不符，见下方核对结论）；`backend/sql/schema-training.sql`
  与 `schema-exam.sql` 列注释；`org.openapi.json` 样板
- 输出：training/evaluation-form 契约字段说明与枚举约束补齐；5 处执行人审计改为登录态派生；重复 Schema 清理
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：不新增路径。`publishStatus` 在标准/模板上由 6 值收窄为 DDL 规定的 3 值；14 个字段新增 `enum` 约束
- 数据影响：无。审计列改为写入真实登录用户 ID，不改表结构
- 前置核对结论（第二轮复审逐条验证）：
  - 复审称 training 有 35 operation / 9 Schema、evaluation-form 5 Schema、`formType` 为 `COURSE/TEACHER/TRAINING`
    ——经还原历史版本，这些数值精确对应 `f5daf90`（2026-07-30），落后当时 HEAD 两个提交。
  - 复审列出的 22 个「遗漏 operation」实际全部已登记；11 个「必须新增接口」实际全部已存在；
    `GET /api/evaluation/records/{evaluationRecordId}` 已存在；第 4 节 5 条语义修正已完成 4 条半。
  - 唯一成立的残留是「执行人从登录态派生」在 `confirmedBy` 上未落实，且同类缺陷共 5 处，见下。
- 验收标准：
  - [x] `CalculationRunServiceImpl.confirmedBy` 及 `Paper`/`Question`/`CertificateTemplate`/`Survey`
    四处 `publishedBy` 均改为 `StpUtil.getLoginIdAsLong()`，全仓库无 `setPublishedBy(0L)` / `setConfirmedBy(0L)` 残留
  - [x] training 契约 566 处 `接口字段：xxx。` 占位描述清零，枚举字段全部写明各取值含义
  - [x] `publishStatus` 按聚合根收窄：培训标准与模板为 `DRAFT/PUBLISHED/ARCHIVED`，培训计划保留 6 值
  - [x] DDL 有明确取值域但契约未声明 `enum` 的 14 个字段补上约束（29 处）；DDL 无取值域的
    `attendanceRule`、`signMethod`、`scorePublishStatus` 如实说明后端不限制取值，不编造枚举
  - [x] `evaluation-form.openapi.json` 移除与 `evaluation-result.openapi.json` 完全重复且未被引用的 8 个 Schema
  - [x] 不新增路径、不改权限模型、不改数据库结构、不动前端
- 测试方式：
  - 按 TDD 先观察目标测试失败，再实现通过
  - `mvn -pl sts-service-admin -am test`
  - `mvn -pl sts-service-admin -am -Pintegration-test test`
  - Controller↔OpenAPI 集合比对、Schema↔DTO/VO 字段比对、枚举取值域↔DDL 列注释比对
  - `openspec validate --strict`、`git diff --check`
- 当前验证（2026-07-31）：
  - Red：5 个执行人审计用例先失败（14 例中 5 失败）。
  - Green：Admin 全量零 DB 415/415 通过；全量 MySQL IT 93/93 通过。
  - 培训域 Controller 67 ↔ OpenAPI 67，评价域 14 ↔ 14，差异均为 0。
  - 91 个 Schema 与 DTO/VO 逐字段比对不一致 0 处。
  - training 与 evaluation-form 占位描述 0、枚举无语义 0；无描述字段仅剩两份业务错误码表的码值键，
    与样板 `org.openapi.json` 的 `OrgBusinessErrorCodes` 写法一致。
  - `contentType`/`stageType`/`ruleType`/`sessionStatus`/`stageStatus`/`contentStatus` 六项枚举
    与 DDL 列注释逐值一致。
  - `openspec validate --strict` 与 `git diff --check` 通过。
- Review：规格符合性通过；代码质量通过。
- 遗留项：`asset.openapi.json` 与 `course.openapi.json` 存在悬空 `$ref`（`responses/NotFoundError`、
  `responses/ConflictError`，合计被 21 个 operation 引用），属素材与课程域既存缺陷，不在本 Change 范围，另行处理。
