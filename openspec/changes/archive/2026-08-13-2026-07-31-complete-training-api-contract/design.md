# Design：培训域 API 契约与纵向执行入口

## 1. 既有事实与边界

### 1.1 契约差异基线

| 契约                               | Controller 实际 operation | 当前 OpenAPI operation | 缺失 |
|----------------------------------|------------------------:|---------------------:|---:|
| `training.openapi.json`          |                      57 |                   35 | 22 |
| `evaluation-form.openapi.json`   |                       7 |                    7 |  0 |
| `evaluation-result.openapi.json` |                       6 |                    5 |  1 |

培训域 22 个缺失 operation：

- 计划：详情、更新、删除、阶段整体替换、评价表单整体绑定、参训人列表、参训人指派，共 7 个。
- 执行：线下场次详情/更新/删除、签到确认、线下成绩列表、附件 CRUD、奖惩 CRUD，共 15 个。

评价结果缺失 operation：

- `GET /evaluation/records/{evaluationRecordId}`。

### 1.2 数据模型支撑

- 模板分类表已有 `parent_id`、`ancestors`、`sort_order` 和 `status`，可以一次查询后在后端构建稳定树。
- 评价表单版本表已有 `form_version_id`、`evaluation_form_id`、`version_no`、发布状态、发布人、发布时间和版本说明，可以按表单列出版本历史。
- 标准、模板、计划和评价表单聚合根已有 `version` 乐观锁字段；子集合整体替换必须使用父对象版本作为并发令牌。
- `sts_training_execution_link` 以 `(plan_content_id, execution_type)` 唯一，支持读取和幂等补建执行关联。
- `sts_training_participant` 支持逻辑删除，但移除参训人必须受计划状态和下游执行事实保护。

本 Change 复用这些表，不创建第二套 DDL。

## 2. OpenAPI 完整性

### 2.1 现有 operation 登记

第一阶段只登记已有 Controller 行为，不借契约补齐改变路径、方法、权限或业务语义。每个 operation 必须：

- 使用实际 Controller 类名作为根级和 operation tag。
- 包含中文 `summary`、`description` 和 `x-permission`。
- 完整描述路径、分页和 `query.*` 参数。
- 请求体和成功响应引用具体 Schema。
- 提供参数、请求体和 `code/message/data` 成功响应 example。
- 声明实际可能发生的 400、401、403、404、409 和业务错误码。

### 2.2 Schema

`training.openapi.json` 至少补齐：

- 培训类型、标准、标准规则、模板阶段/内容、计划、参训人。
- 线下场次、签到、线下成绩、附件、奖惩和执行生成结果。
- 创建 ID、详情、void 和各类 `PageResult<T>` 的具体 `Result` 外壳。
- `publishStatus`、`ruleType`、`stageType`、`contentType`、各业务 `status`、`isRequired` 等枚举。

`evaluation-form.openapi.json` 至少补齐：

- 发布请求、版本详情/摘要、评价项目请求/响应、评分规则请求/响应。
- 表单分页、版本列表和发布结果响应。
- `formType`、发布状态、项目类型、计算类型、来源类型、启停状态等枚举。

`formType` 固定采用数据库真相源：

- `COMPOSITE`
- `EXPERT_INSPECTION`

### 2.3 查询参数

所有代码中已存在的 Query 字段必须在对应分页 operation 中逐项登记，不得只写 `query.organizationId`。参数命名保持
`query.<field>`，与当前 `PageRequestArgumentResolver` 绑定方式一致。

## 3. 管理输入接口

### 3.1 模板分类树

`GET /training/template-categories/tree`

- 权限：复用 `training:template:list`。
- 查询范围：沿用模板分类列表的机构数据范围；允许按 `organizationId`、`status` 筛选，不接受绕过数据范围的机构。
- 返回：`TrainingTemplateCategoryTreeVO[]`。
- 字段：`templateCategoryId`、`organizationId`、`parentId`、`categoryName`、`sortOrder`、`status`、`children`。
- 排序：同级按 `sortOrder`、`templateCategoryId` 升序。
- 孤儿节点：不得静默丢弃；若因数据范围过滤导致父节点不可见，则不返回该节点，避免伪造根节点语义。

### 3.2 评价表单版本历史

`GET /evaluation/forms/{evaluationFormId}/versions`

- 权限：复用 `evaluation:form:list`。
- 返回：按 `versionNo` 降序排列的 `EvaluationFormVersionSummaryVO[]`。
- 字段：`formVersionId`、`evaluationFormId`、`versionNo`、`publishedStatus`、`publishedBy`、`publishedTime`、`versionNote`。
- 必须先验证表单存在且在当前数据范围内；不得只按外部传入 ID 查询版本。

## 4. 并发、不可变和审计语义

### 4.1 整体替换

下列请求增加必填 `version`：

- 标准规则整体替换。
- 模板阶段/内容整体替换。

事务内先以 `id + version + DRAFT` 条件更新聚合根版本，再删除并重建子集合。更新数为 0 时重新判定：

- 资源不存在：404。
- 资源已发布或状态不可编辑：409。
- 版本不一致：409。

不得先删除子记录再验证版本。

### 4.2 发布

标准、模板、计划和评价表单发布请求使用明确请求 Schema，并携带聚合根 `version`。发布必须原子检查草稿状态和版本；重复发布或旧版本发布返回
409。

评价表单发布规则通过 `evaluationItemCode` 引用同次请求中的项目。后端插入项目后建立 `itemCode → evaluationItemId`
映射，再写入规则；不存在或重复的项目编码返回参数错误或冲突，不接受客户端猜测尚未生成的数据库 ID。

### 4.3 登录身份

- `publishedBy`、签到 `confirmedBy`、线下成绩 `scoredBy` 等执行人/审计人从登录态派生。
- `GET /training/teacher-tasks` 不接受或不信任客户端 `teacherUserId`，只查询当前登录用户。
- 客户端可提交的目标用户、机构和参训人 ID 必须执行数据范围与业务归属校验，不能被当作当前执行人身份。

### 4.4 发布后不可变与删除保护

- 已发布标准规则、模板阶段/内容和评价表单版本不可覆盖。
- 表单主记录名称、描述和启停状态是否允许在已有发布版本后维护，保持现有定义/版本分离模型；实现前由人工确认具体边界。
- 被计划、执行实例或正式结果引用的资源不得删除；状态冲突统一返回 HTTP 409 和稳定业务码。

## 5. 后续纵向入口

### 5.1 草稿计划移除参训人

`DELETE /training/plans/{trainingPlanId}/participants/{trainingParticipantId}`

- 仅草稿计划允许。
- 参训人必须属于路径中的计划。
- audience 绑定产生的参训人是否允许单独移除，暂定允许形成显式排除；若现有模型无法留存排除事实，实施前暂停并人工决策。
- 已存在执行人员、签到、成绩、奖惩、评价或正式结果时不得移除。

### 5.2 当前教师任务详情

`GET /training/teacher-tasks/{offlineSessionId}`

- 当前用户必须是该场次 `teacher_user_id`。
- 返回场次、计划、参训/签到摘要以及依据权限和场次状态计算的可处理动作。
- 可处理动作是后端派生结果，不接受客户端声明教师身份。

### 5.3 执行关联与补建

- `GET /training/plans/{trainingPlanId}/executions`
- `POST /training/plans/{trainingPlanId}/contents/{planContentId}/provision`

`provision` 是计划级 `generate-executions` 的单内容幂等补建/重试入口，不替代计划首次批量生成。`planContentId`
必须属于路径计划；幂等事实由 `sts_training_execution_link` 唯一键保证。

课程开课的真实创建、考试实例复用/复制策略仍需跨域确认；未确认时不得用 `resourceId` 冒充已成功 provision 的执行实例。

### 5.4 培训上下文考试启动

`POST /training/learner/participants/{trainingParticipantId}/contents/{planContentId}/exam-attempts`

- 当前用户必须是该参训记录本人。
- 内容必须属于参训记录对应计划且类型为 `EXAM`。
- 复用考试域 Candidate/Attempt，不新建培训域考试状态。
- 业务上下文必须包含培训计划、参训人和计划内容，避免同一考试跨上下文串成绩。

### 5.5 学员任务与结果

- `GET /training/learner/tasks`
- `GET /training/learner/participants/{trainingParticipantId}`
- `POST /training/plans/{trainingPlanId}/results/synchronize`
- `GET /training/plans/{trainingPlanId}/participants/{trainingParticipantId}/results`

学员接口只读取当前用户本人事实。管理端同步接口必须幂等，只从课程、考试和线下正式事实更新参训人分项结果；不得读取页面临时值或未发布成绩。结果详情必须返回来源类型、计划内容、执行实例和正式状态，支持追溯。

同步幂等键、覆盖策略和综合结果边界在第三阶段实施前由人工确认。

## 6. 测试与验收

- 第一阶段为契约修复：执行 JSON 解析、OpenAPI 结构检查、Controller-operation 差异检查和 `openspec validate --strict`。
- 第二、三阶段按 Admin 两层测试规则执行 TDD：
  - Controller/Service 零 DB 测试验证参数、登录身份、状态和版本冲突。
  - Mapper/Service IT 验证真实乐观锁、树查询、版本排序、逻辑删除、唯一键和事务。
- 修改后实际运行受影响的零 DB 测试；涉及 Mapper、自定义 SQL、逻辑删除或乐观锁时同时运行相关 MySQL IT。
- L4 实施完成后执行规格符合性和代码质量双轴 Review。

## 7. 人工确认点

2026-07-31，人工确认 Task 3 按以下规则实施：

1. `joinSource=AUDIENCE` 的参训人本期不支持单人移除，返回 HTTP 409；不新增显式排除表。
2. COURSE/EXAM provision 只关联既有真实执行实例，不复制课程或考试定义；必须校验实例可执行，不能直接把未经验证的
   `resourceId` 当作成功实例。
3. 培训考试 Candidate 使用既有唯一范围 `(examId, userId, contextType=TRAINING, contextId=planContentId, deleted)`，同时写入
   `trainingParticipantId`；作答次数服从考试策略，培训域不另设次数。
4. 结果同步以 `trainingParticipantId + planContentId + executionType` 为逻辑幂等范围，只读取已发布考试成绩、正式课程完成事实和已确认线下成绩；只推进或刷新分项结果，不覆盖
   `compositeScore`、`officialCalculationRunId`，综合结果仍由评价计算域负责。

评价表单主记录维护边界属于 Task 2 后续独立议题，不扩大 Task 3。
