# Design：学员考试查询与答案可见性

## 现状与约束

- `sts_exam_candidate` 是学员获得考试任务的事实，`sts_exam_attempt` 是每次作答事实。
- `sts_exam_answer` / `sts_exam_answer_item` 已保存暂存答案，无需数据库变更。
- `sts_exam_attempt.score_published_time` 和 `attempt_status=PUBLISHED` 已表达单次成绩发布事实。
- 学员可凭候选人事实访问跨机构的本人任务，不要求成为考试主办机构成员。
- Git 身份为 `lims`，本 Change 仅交付后端和 `docs/api/exam.openapi.json`。

## API 设计

### 1. 学员本人考试任务

新增 `ExamLearnerController`，提供：

```text
GET /api/exam/learner/tasks
```

使用统一 `PageRequest<ExamLearnerTaskQuery>`：

- `pageNum`、`pageSize`
- `query.taskStatus`：`NOT_STARTED` / `IN_PROGRESS` / `WAITING_RESULT` / `RESULT_PUBLISHED` / `CLOSED`
- `query.examName`
- `query.contextType`

用户身份必须从 Sa-Token 当前登录会话取得，不接受调用方传入 `userId`。查询以 `sts_exam_candidate.user_id=当前用户`
为根，关联考试定义及当前有效 Attempt；跨机构任务以候选人事实授权。

`ExamLearnerTaskVO` 至少包含：

- candidate：`examCandidateId`、`candidateStatus`、`attemptCount`
- exam：`examId`、`examName`、`examType`、`openStartTime`、`openEndTime`、`durationSeconds`
- context：`contextType`、`contextId`
- current attempt：`currentAttemptId`、`attemptStatus`、`deadlineTime`
- result：`scorePublishStatus`、`scorePublishedTime`、可见时的 `finalScore`、`isPassed`
- action：`availableAction`

`availableAction` 是服务端基于稳定业务状态得出的展示动作枚举：

- `START`
- `CONTINUE`
- `WAIT_REVIEW`
- `VIEW_RESULT`
- `NONE`

它只用于减少前端重复推导，不替代写接口的服务端状态校验。

### 2. 学员 Attempt 详情

学员详情使用独立本人路径：

```text
GET /api/exam/learner/attempts/{examAttemptId}
```

服务端必须验证：

1. Attempt 存在。
2. Attempt 对应 Candidate 存在。
3. Candidate 与 Attempt 的 `userId` 均等于当前登录用户。

原有管理/阅卷详情如需保留，必须继续使用管理或阅卷权限及对应任务归属校验，不能借用学员本人路径绕过。

详情返回 `ExamAttemptVO`，每个 `questions[]` 增加可空的 `answer`：

```text
ExamAttemptQuestionVO.answer -> ExamAnswerVO
```

没有暂存答案时 `answer=null`；有答案时返回 `answerText`、`items`、`isAnswered`、`answeredTime`、`lastSavedTime`
，支持单选、多选、判断、填空和简答恢复。

### 3. 答案 schema

`ExamAnswerVO`：

- `examAnswerId`
- `examAttemptId`
- `attemptQuestionId`
- `answerText`
- `items: ExamAnswerItemVO[]`
- `isAnswered`
- `gradingStatus`
- `answeredTime`
- `lastSavedTime`
- `autoScore`
- `manualScore`
- `finalScore`
- `isCorrect`

`ExamAnswerItemVO`：

- `answerItemId`
- `itemType`
- `itemKey`
- `itemValue`
- `sortOrder`

OpenAPI 必须对枚举、可空字段、每个 property 和各题型 example 给出中文说明。

## 成绩与答案可见性

### 学员作答中

- 可见：本人暂存答案、是否已答、暂存时间。
- 不可见：所有评分字段、正确性、正确答案快照、正确选项标记、题目解析。

### 已交卷但成绩未发布

- 可见：本人提交的答案，且只读。
- 不可见：`objectiveScore`、`subjectiveScore`、`finalScore`、`isPassed`、`correctCount`、`wrongCount`，以及答案级评分、正确性、正确答案和解析。
- 返回 `scorePublishStatus=UNPUBLISHED`、`scoreVisible=false`。

### 成绩已发布

- 可见：正式 `finalScore`、`isPassed`、`scorePublishedTime`。
- `objectiveScore`、`subjectiveScore`、答对/答错数、答案级得分、正确性、正确答案和解析是否可见，必须同时满足“成绩已发布”和既有
  `answerDetailMode`：
  - `SUBMITTED`：成绩已发布后可见。
  - `PASSED`：成绩已发布且该 Attempt 正式结果为及格后可见。
  - `EXAM_ENDED`：成绩已发布且当前时间晚于考试开放结束时间后可见。
  - `RETAKE_ENDED`：成绩已发布且已无可用补考机会后可见；“无可用补考机会”按既有 Attempt 次数上限和考试开放期共同判断。
  - `NEVER`：始终不可见。
- 策略值为空、未知或其依赖事实不完整时采用安全默认：不返回分项成绩、正确性、正确答案和解析。
- 返回 `scorePublishStatus=PUBLISHED`、`scoreVisible=true`。

### 管理与阅卷场景

- 管理端成绩发布人员可读取发布所需的 Attempt 汇总。
- 被指派阅卷人只可读取其任务范围内的待阅答案。
- 不复用学员字段裁剪规则来扩大管理或阅卷权限；具体接口沿用既有权限码与业务归属。

## DTO 与查询设计

- `ExamLearnerTaskQuery` 只承载筛选条件，不含 `userId`。
- `ExamLearnerTaskVO` 是面向学员任务页的聚合读模型，不直接复用 `ExamCandidateVO` 或 `ExamVO`，避免前端多接口拼装及泄露管理字段。
- `ExamAttemptVO` 补充：
  - `scorePublishStatus`
  - `scorePublishedTime`
  - `scoreVisible`
- `ExamAttemptQuestionVO` 补充 `answer`。

## 权限设计

- 新增学员读取权限码：
  - `exam:learner:task:list`
  - `exam:learner:attempt:view`
- 权限码只控制功能入口；数据访问还必须通过当前登录用户与 Candidate / Attempt 本人执行链校验。
- 学员路径不接受 `userId`、`publishedBy` 等可伪造身份参数。
- 非本人访问返回 HTTP 403；资源不存在返回 HTTP 404，具体错误码纳入 exam 7000～7199 段。

## 数据与事务

- 不新增表、字段、索引或 migration。
- 任务列表读取既有 Candidate、Exam、Attempt 数据。
- Attempt 详情批量读取题目、选项、答案和答案项，禁止按题逐条查询造成 N+1。
- 查询接口不产生业务写入。

## 测试设计

按 TDD 实施：

1. Controller standalone MockMvc：

- 分页默认值和 `query.*` 绑定。
- 当前登录身份传递。
- 无权限 403 与参数错误 400。

2. Service 纯 Mockito：

- 仅返回本人 Candidate 任务。
- 任务状态、发布状态和 `availableAction` 映射。
- 本人 Attempt 返回暂存答案。
- 非本人 Attempt 拒绝。
- 未发布成绩和答案评分字段被裁剪。
- 已发布时仅按策略返回允许字段。

3. Mapper IT：

- 学员任务分页和筛选。
- Candidate / Attempt / Answer / AnswerItem 关联读取。
- 跨机构但属于本人的 Candidate 可见，其他用户不可见。

## 风险与取舍

- 风险：把 Entity 直接作为 VO 返回会泄露评分或正确答案。对策：专用 VO 和显式可见性映射。
- 风险：任务页通过多次查询拼装产生 N+1。对策：使用目标 Mapper 聚合查询，并通过 IT 验证真实 SQL。
- 风险：`attemptStatus=PUBLISHED` 与发布时间不一致。对策：只有状态为 `PUBLISHED` 且 `scorePublishedTime`
  非空时视为正式发布；不一致时采用未发布的安全行为并记录异常。
- `scoreVisible` 只表示正式最终成绩是否可见，不表示正确答案和解析可见；后者由 `answerDetailMode` 单独判定，避免前端混用。
