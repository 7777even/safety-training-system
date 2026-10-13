# Design：课程考核与独立考试分离

> 状态：课程考核规则和设计已于 2026-08-25 人工确认，并已授权按 `tasks.md` 实施。

## 1. 设计原则

1. **功能项语义优先**：课程题库就是课程对题库的引用；课程测验就是课程对理论试卷的引用，不用“已发布考试”替代。
2. **资源、任务、执行分层**：题库/试卷是可复用资源；课程内容/独立考试是业务任务；Candidate/Attempt/答案是执行事实。
3. **共用引擎不等于共用业务对象**：课程和独立考试共用作答快照、保存、交卷、判分和批阅服务，但不共用一条“考试任务”作为模板。
4. **来源是不可变事实**：开始作答后，来源、资源、题目版本和执行规则均不得改写。
5. **最优模型优先、正确资产复用**：不为原有数据或错误语义妥协；现有表和代码职责与最终模型一致时直接复用，职责混合时调整，能力缺失时才新增。

## 2. 领域角色

| 角色     | 建议业务对象                               | 职责                       |
|--------|--------------------------------------|--------------------------|
| 题库资源   | `QuestionBank`                       | 题目集合及可用题目版本的来源           |
| 试卷资源   | `PaperVersion`                       | 已发布、不可变的题目、分值和基础规则快照     |
| 课程考核定义 | `CourseItemSnapshot`                 | 表达课程测验或课程题库练习及其课程规则      |
| 独立考试任务 | 现有 `Exam`                            | 安排时间、考生、阅卷人、成绩发布和补考      |
| 考核执行   | 新增 `AssessmentExecution`             | 把一个业务来源和题库/试卷资源冻结为可执行上下文 |
| 参与和作答  | 演进现有 `ExamCandidate` / `ExamAttempt` | 保存人员、次数、作答、成绩和状态事实       |

`AssessmentExecution` 是内部执行边界，不是新的管理端业务模板。管理员仍然只看到“课程内容”或“独立考试”。

## 3. 业务分类与三个维度

不再用一个 `examType` 同时表达业务归属、内容类型和题目来源，而是分为：

| 维度               | 枚举                                                                                                   | 含义             |
|------------------|------------------------------------------------------------------------------------------------------|----------------|
| `sourceType`     | `STANDALONE_EXAM` / `STANDALONE_PRACTICE` / `COURSE_QUIZ` / `COURSE_BANK_PRACTICE` / `TRAINING_EXAM` | 这份结果属于哪个业务场景   |
| `resourceType`   | `PAPER_VERSION` / `QUESTION_BANK`                                                                    | 本次作答的题目从哪类资源产生 |
| `assessmentKind` | `FORMAL_EXAM` / `QUIZ` / `BANK_PRACTICE` / `SPECIAL_PRACTICE`                                        | 业务行为和结果规则      |

映射如下：

| 业务场景   | `sourceType`           | `resourceType`                 | 业务主键                              |
|--------|------------------------|--------------------------------|-----------------------------------|
| 独立在线考试 | `STANDALONE_EXAM`      | `PAPER_VERSION`                | `examId`                          |
| 独立专项练习 | `STANDALONE_PRACTICE`  | `PAPER_VERSION` 或题库规则（待既有规格约束） | `examId`                          |
| 独立题库练习 | `STANDALONE_PRACTICE`  | `QUESTION_BANK`                | `examId`                          |
| 课程测验   | `COURSE_QUIZ`          | `PAPER_VERSION`                | `courseOfferingId + courseItemId` |
| 课程理论题库 | `COURSE_BANK_PRACTICE` | `QUESTION_BANK`                | `courseOfferingId + courseItemId` |

`TRAINING_EXAM` 仅为避免未来枚举冲突的保留值；本 Change 不建设培训执行链，后续如需引入必须创建独立 OpenSpec Change。

## 4. 推荐数据模型

### 4.1 课程定义与版本快照

1. `sts_course_item` 新增可空 `paper_version_id`，仅课程测验使用。
2. 课程理论题库复用现有 `sts_course_item_question_bank`，每个内容项只允许一个题库。
3. 新建内容项只接受：

- `QUIZ + paperVersionId`；
- `QUESTION_BANK + questionBankId`。

4. 课程目标模型不包含 `EXAM / PRACTICE + examId`；课程理论作答类型只使用 `QUIZ` 和 `QUESTION_BANK`。
5. 课程版本快照升级为显式版本，新快照至少冻结：

- `itemType`、`resourceType`、`resourceId`；
- 题库/试卷名称等必要显示快照；
- 时长、题序、计分、完成条件等已确认课程规则；
- 发布时的资源版本或题库可用性事实。

### 4.2 统一执行对象

新增 `sts_assessment_execution`：

| 字段                                                            | 语义                                   |
|---------------------------------------------------------------|--------------------------------------|
| `assessment_execution_id`                                     | 内部执行主键                               |
| `organization_id`                                             | 执行事实所属机构                             |
| `source_type`                                                 | 独立考试/练习、课程测验/题库练习或培训考试               |
| `source_business_id`                                          | 独立场景为 `examId`；课程场景为 `courseItemId`  |
| `exam_id`                                                     | 仅独立/培训考试任务有值，课程场景为 NULL              |
| `course_offering_id` / `course_version_id` / `course_item_id` | 仅课程场景有值                              |
| `assessment_kind`                                             | 正式考试、测验或练习                           |
| `resource_type` / `resource_id`                               | 试卷版本或题库                              |
| `policy_snapshot_json` / `snapshot_hash`                      | 开始执行前冻结的规则及防篡改摘要                     |
| `execution_status`                                            | `DRAFT / READY / CLOSED / CANCELLED` |

唯一性：

- 独立考试：一个 `examId` 只有一个当前执行对象。
- 课程：一个 `courseOfferingId + courseVersionId + courseItemId` 只有一个执行对象。

课程执行对象在开课发布时或第一次开始时幂等生成（推荐开课发布时生成，便于提前验证快照和资源）。它不出现在“独立考试”列表中。

### 4.3 Candidate、Attempt 和 Reviewer

1. `sts_exam_candidate` 以 `assessment_execution_id` 作为执行归属真相源，移除候选人上的冗余 `exam_id` 归属。
2. `sts_exam_attempt` 以 `assessment_execution_id`、`source_type`、`assessment_kind` 表达执行和来源，移除作答上的冗余
   `exam_id` 归属。
3. `paper_version_id` 继续允许 NULL；`paper_source_type` 和 `paper_source_id` 分别保存 `PAPER_VERSION` 或 `QUESTION_BANK`
   及其 ID。
4. 批阅人关联从只按 `exam_id` 演进为按 `assessment_execution_id`
   定位。既有独立考试阅卷人可在生成执行对象时复制/映射；课程测验包含主观题时，必须在开课对应执行中显式指定至少一名阅卷人。
5. 新唯一键以 `assessment_execution_id + user_id + 业务上下文 + deleted` 隔离，避免同一资源被多个课程、开课或独立任务复用时串数据。

## 5. 执行流程

### 5.1 独立考试

```text
创建独立考试任务
  → 关联已发布试卷版本
  → 设置时间/人员/阅卷人/发布规则
  → 发布考试并生成 STANDALONE_EXAM 执行对象
  → 学员开考
  → 冻结试卷题目版本
  → 交卷/判分/批阅/发布成绩
```

### 5.2 课程测验

```text
章节添加测验并直接选择已发布试卷版本
  → 课程发布时冻结试卷版本和课程规则
  → 开课发布时生成 COURSE_QUIZ 执行对象并校验主观题阅卷人
  → 学员从本人课程报名进入
  → 生成课程上下文 Candidate/Attempt
  → 纯客观题交卷判分后自动生效；主观题全部批阅后自动生效
  → 结果幂等投影到该报名和内容项进度
```

### 5.3 课程题库练习

```text
章节添加理论题库并直接选择题库
  → 课程发布时冻结题库引用和已确认练习规则
  → 开课发布时生成 COURSE_BANK_PRACTICE 执行对象
  → 学员从本人课程报名进入
  → 冻结题库全部可用已发布题目版本
  → 直接冻结 AttemptQuestion/Option/答案/分值
  → 全部题目至少作答一次并提交后完成一轮
  → 保存答题、正确率和错题事实
```

题库分支必须：

- `paperVersionId = NULL`；
- `paperSourceType = QUESTION_BANK`；
- `paperSourceId = questionBankId`；
- 不调用 `requirePaperVersion`；
- 题库无可用题目时返回题库专用业务错误，不得返回“未绑定试卷版本”。
- 完成条件只要求冻结题目全部至少作答一次并提交，不设置题量抽取、正确率或合格门槛；重复练习更新统计但不撤销已完成状态。

### 5.4 已确认的课程完成规则

1. 开课默认沿用 `ALL_REQUIRED_ITEMS`，只统计 `isRequired=1` 的内容项；配置为 `ALL_ITEMS` 时统计全部内容项。
2. 课程题库练习完成一轮后，该内容项完成。
3. 课程测验在最终成绩自动生效后完成；提交但仍待主观题批阅时不得提前完成。
4. 课程测验继续计算并保存 `isPassed`，但本期不以合格阻断课程完成，也不新增补考次数、重做窗口或最高分覆盖规则。

## 6. 批阅与成绩归属

### 6.1 共用的事实与服务

- Attempt 题目/选项快照。
- 答案保存和幂等交卷。
- 客观题自动判分。
- 主观题追加式批阅记录。
- 独立考试成绩发布流水和作废审计；课程测验保存自动生效事实，不伪造人工发布动作。

### 6.2 分离的业务查询

- **独立考试批阅**：以 `examId` 为业务入口，只返回 `STANDALONE_EXAM / STANDALONE_PRACTICE` 来源。
- **课程测验批阅**：以 `courseOfferingId + courseItemId` 为业务入口，只返回 `COURSE_QUIZ` 来源；仅显式配置在该执行对象上的阅卷人可处理。
- **课程题库结果**：以 `courseEnrollmentId + courseItemId` 查看练习次数、正确率、错题和进度；除非后续确认允许主观题，不默认进入正式考试成绩列表。

聚合教师任务可以展示多种来源，但每条必须返回：

- `sourceType`、`sourceBusinessId`、`sourceName`；
- 独立来源的 `examId/examName`；
- 课程来源的 `courseId/courseName/courseOfferingId/courseItemId/courseItemTitle`；
- `assessmentExecutionId`、`candidateId`、`attemptId`。

课程成绩自动生效时，只能按冻结的 `courseEnrollmentId + courseItemId + assessmentExecutionId` 幂等更新课程进度；不得根据同一试卷或题库的其他结果回写。

## 7. API Contract（提案）

### 7.1 课程管理

`POST /api/course/items` 和对应更新契约调整为互斥引用：

```text
ASSET          -> assetId
LIVE           -> liveCourseId/liveSession reference
QUESTION_BANK  -> questionBankId
QUIZ           -> paperVersionId
```

服务端校验资源属于当前机构、启用且可用；`QUIZ` 必须引用已发布试卷版本。本 Change 不恢复已取消的跨机构资源授权语义。

### 7.2 学员课程执行

保留课程域入口，但语义从“启动关联 examId”改为“启动冻结课程考核”：

```text
POST /api/course/learning/enrollments/{courseEnrollmentId}/items/{courseItemId}/attempts
```

请求不接受 `userId`、`examId`、`questionBankId`、`paperVersionId`、`sourceType` 或课程上下文；服务端只从当前登录人、报名、开课和课程版本快照派生。

### 7.3 批阅与成绩

- 独立考试的列表、批阅和发布仍保持在 `/api/exam/**`。
- 课程测验的阅卷人配置、结果/批阅入口放在 `/api/course/**`，内部委托共用判分与批阅服务；课程端不提供人工发布成绩接口。
- 通用 Attempt 详情必须返回来源摘要，并根据来源和当前人权限裁剪数据。
- OpenAPI 实施时必须同步请求/响应 example、错误码、`x-permission` 及 Controller tags。

## 8. 权限与数据边界

- 学员课程作答权限只来自本人有效报名，不来自独立考试 Candidate 分配。
- 独立考试作答权限只来自考试人员分配，不因同一试卷被课程引用而获得。
- 阅卷权限同时校验功能权限、执行对象来源和阅卷任务归属。
- 所有列表、详情、批阅、成绩发布、统计和导出必须使用相同来源过滤，禁止只在页面层区分。
- 不新建权限模型；如现有课程/考试权限无法表达阅卷边界，实施前标记“需人工决策”。

## 9. 最优模型与复用原则

本能力不考虑原有数据的保留和转换，但必须评估并复用职责正确的现有表、字段和代码。复用的判断依据是能否清晰支撑最终模型，而不是是否已经存在。

1. 课程题库/测验仅使用最终资源引用。
2. Candidate、Attempt、Reviewer 仅使用 `assessment_execution_id` 表达执行归属。
3. 代码、Schema 和 API 中均不建立课程 `EXAM / PRACTICE + examId` 路径。
4. 测试、种子和验收数据直接按最终模型创建。
5. 每次结构变化同时更新 `backend/sts-service-admin-init/src/main/resources/schema/` 的最终完整 SQL，并在
   `backend/sts-service-admin/src/main/resources/db/migration/` 新增同版本 `V`；不承担旧业务数据转换职责。

复用决策：

- 直接复用题库、题目版本、试卷、试卷版本及其发布能力。
- 复用 `sts_course_item` 并补充测验资源引用，直接复用 `sts_course_item_question_bank`。
- 复用 `sts_exam_exam` 和 `sts_exam_policy` 表达独立考试任务及规则。
- 演进现有 Candidate、Attempt、题目/选项/答案快照、批阅记录和成绩发布流水，使其统一归属于 `AssessmentExecution`。
- 仅新增现有模型缺少的中立执行边界 `AssessmentExecution`，不重复建设已有作答和判分能力。

实施前必须复核字段职责、唯一约束和查询成本。不得为了复用保留职责混合，也不得为了采用新模型重建职责正确的现有能力。

## 10. 方案取舍

### 推荐：独立 `AssessmentExecution`

优点：

- 明确分离业务任务与共用执行。
- Candidate、Attempt、Reviewer 有统一且稳定的归属键。
- 可扩展培训考试，不需要伪造隐藏考试或试卷。

代价：需要同步演进 init 最终完整 SQL 和 Admin Flyway `V`，并建设新的执行服务及课程、考试执行归属，属于 L4。

### 不采用：为每个课程项自动创建隐藏考试

虽然改动小，但会继续让独立考试表承担课程模板和执行容器，污染考试列表、阅卷、统计和数据清理语义。

### 不采用：只在现有表上把 `exam_id` 改为可空

可以修复题库报错，但 Candidate、Reviewer、Attempt 缺少共同的执行归属，唯一性、权限和批阅查询仍需要多套特例。

## 11. 测试与验收策略

### 零 DB

- 课程内容项题库/试卷互斥引用、机构隔离、发布锁定。
- 课程入口只从本人报名和快照派生来源，拒绝伪造资源或上下文。
- `PAPER_VERSION` 与 `QUESTION_BANK` 两条快照分支均先 Red 后 Green。
- 课程主观题阅卷人显式配置、课程成绩自动生效、独立考试人工发布、课程进度幂等回写和跨课程隔离。

### MySQL IT

- 新表/列映射、审计填充、唯一索引、逻辑删除和机构隔离。
- 同一试卷/题库在两个课程和一个独立考试中执行时，Candidate/Attempt/成绩不串联。
- 题库练习 `paper_version_id IS NULL` 仍可完整保存、提交、重放和统计。
- init 完整 SQL 可在空库建立最终 schema；同版本 Admin `V` 可把上一版本升级到相同结构，且两条路径只包含最终执行归属和课程资源引用。

### 契约与典型闭环

1. 课程题库：建课引用题库 → 发布/开课 → 学员刷题 → 答题、正确率和错题可追溯。
2. 课程测验：建课引用试卷版本 → 发布/开课 → 学员作答 → 必要批阅 → 结果只回写该课程报名。
3. 独立考试：安排试卷/人员/阅卷人/时间 → 学员作答 → 批阅/发布 → 结果只进入独立考试统计。

## 12. 已确认设计决策与风险

- 执行表命名为 `sts_assessment_execution`，归属考试执行域，由课程和独立考试业务入口共同调用。
- 课程执行对象在开课发布时幂等生成，不采用学员第一次开始时懒生成。
- 课程测验的最终代码枚举使用 `QUIZ`；`EXAM` 只属于独立考试领域，不作为课程内容枚举。
- 课程完成、主观题阅卷人和成绩自动生效规则已确认，不再作为实施暂停项。
- 该能力横跨课程和考试模块；必须在已确认 Task 中完成 schema、服务和契约的完整纵向闭环，不交付局部目标模型。
