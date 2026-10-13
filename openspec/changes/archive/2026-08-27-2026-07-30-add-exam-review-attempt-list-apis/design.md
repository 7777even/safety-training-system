# Design：考试批阅任务与作答成绩分页

## 1. 既有事实与边界

- `sts_exam_reviewer` 表达考试与批阅人的分配关系。
- `sts_exam_attempt` 表达每次作答、提交、批阅完成和成绩发布时间。
- `sts_exam_answer` 表达题目答案及 `grading_status`。
- `sts_exam_candidate` 表达考试任务、学员和冻结的人员档案快照。
- `sts_exam_review_record` 是追加式批阅记录，不作为待阅任务的唯一来源。
- 本 Change 只读取既有事实，不新增表、索引或字段。

## 2. Controller 与权限

两个接口归入既有 `ExamAttemptController`：

| 接口                       | 权限                  | 数据范围                                               |
|--------------------------|---------------------|----------------------------------------------------|
| `GET /exam/review-tasks` | `exam:review`       | 当前登录用户必须存在对应 `sts_exam_reviewer` 记录；只返回这些考试中的主观题答案 |
| `GET /exam/attempts`     | `exam:attempt:list` | 按当前用户既有机构数据范围过滤 Attempt，不接受调用方指定越权机构               |

不新增权限码。批阅任务不能仅凭 `exam:review` 查看全部考试；必须同时满足批阅人分配关系。

## 3. 教师批阅任务分页

### 3.1 查询参数

使用 `PageRequest<ExamReviewTaskQuery>`：

- `pageNum`、`pageSize`
- `query.examId`
- `query.reviewStatus`：`PENDING`、`REVIEWED`
- `query.learnerKeyword`：匹配冻结学员姓名、工号；不匹配敏感身份字段
- `query.submittedStartTime`
- `query.submittedEndTime`

默认排序：待阅优先，其次按 `submittedTime` 升序、`examAnswerId` 升序，保证先提交先处理且分页稳定。

### 3.2 任务粒度

一个可批阅的主观题答案是一条批阅任务。其来源必须同时满足：

1. 当前用户是该 `examId` 的有效批阅人。
2. Attempt 已提交并处于 `PENDING_REVIEW`、`PENDING_PUBLISH` 或 `PUBLISHED`。
3. 答案对应题目的 `gradingMode=MANUAL`。
4. `PENDING` 对应答案 `gradingStatus=PENDING_REVIEW`。
5. `REVIEWED` 对应答案已完成人工评分；展示最近一次追加式批阅记录摘要。

对于尚未生成 `ExamAnswer` 的未作答主观题，不生成可调用现有单题批阅接口的任务。此类未作答题是否需要补零分批阅属于判分规则，超出本
Change。

### 3.3 返回字段

`ExamReviewTaskVO`：

- `examId`、`examName`
- `examCandidateId`
- `examAttemptId`、`attemptNo`、`attemptStatus`
- `examAnswerId`、`attemptQuestionId`
- `questionType`、`stemSnapshot`、`score`
- `answerText`
- `learnerUserId`
- `learnerName`、`learnerCode`、`learnerOrganizationId`、`learnerOrganizationName`
- `submittedTime`
- `reviewStatus`
- `latestManualScore`、`latestReviewComment`、`latestReviewedTime`
- `scorePublishStatus`、`scorePublishedTime`

学员展示信息优先从 Candidate 的冻结人员快照读取，快照缺失时允许按既有机构成员事实补充；两者均不得返回身份证号、手机号等非本接口必要字段。

## 4. 成绩 / 作答记录分页

### 4.1 查询参数

使用 `PageRequest<ExamAttemptRecordQuery>`：

- `pageNum`、`pageSize`
- `query.examId`
- `query.attemptStatus`
- `query.reviewStatus`：`NOT_REQUIRED`、`PENDING`、`COMPLETED`
- `query.scorePublishStatus`：`UNPUBLISHED`、`PUBLISHED`
- `query.learnerKeyword`
- `query.submittedStartTime`
- `query.submittedEndTime`

默认排序：`submittedTime` 降序、`examAttemptId` 降序。尚未提交的 Attempt 使用创建时间参与稳定排序，但 `submittedTime` 保持为空。

### 4.2 返回字段

`ExamAttemptRecordVO`：

- `examId`、`examName`
- `examCandidateId`
- `examAttemptId`、`attemptNo`、`attemptType`、`attemptStatus`
- `learnerUserId`
- `learnerName`、`learnerCode`、`learnerOrganizationId`、`learnerOrganizationName`
- `startedTime`、`submittedTime`、`usedSeconds`
- `reviewStatus`、`reviewCompletedTime`
- `objectiveScore`、`subjectiveScore`、`finalScore`、`isPassed`
- `scorePublishStatus`、`scorePublishedTime`

管理端接口可以返回尚未正式发布的内部评分事实，供批阅完成和发布判断使用；`scorePublishStatus`
必须明确区分是否已经正式发布。该能力不改变学员侧的成绩可见性。

## 5. 状态派生

### 5.1 批阅状态

- `NOT_REQUIRED`：Attempt 不存在需要人工批阅的题目。
- `PENDING`：至少一个人工判分答案仍为 `PENDING_REVIEW`。
- `COMPLETED`：存在人工判分题且不存在待阅答案，并且 `reviewCompletedTime` 存在。

事实不完整时安全归入 `PENDING`，不得误报完成。

### 5.2 成绩发布状态

- `PUBLISHED`：`attemptStatus=PUBLISHED` 且 `scorePublishedTime` 非空。
- `UNPUBLISHED`：其他情况。

该定义与学员读取契约保持一致。

## 6. 查询实现

- Mapper 使用真实分页聚合查询，不在 Service 中全量查询后内存分页。
- 批阅任务查询联结 Reviewer、Exam、Attempt、Candidate、AttemptQuestion、Answer，并按需取得最近批阅记录。
- 作答记录查询联结 Exam、Attempt、Candidate，并以聚合或 `EXISTS` 派生批阅状态。
- 避免按列表项逐条加载考试、人员、答案或批阅记录的 N+1。
- MyBatis 映射和 MySQL 聚合语义由带 DB IT 验证。

## 7. OpenAPI

修改 `docs/api/exam.openapi.json`：

- 两个 operation 均使用 `ExamAttemptController` tag。
- 完整描述 `pageNum`、`pageSize` 和所有 `query.*` 参数。
- 每个字段包含中文 `description`、类型、可空性和业务 example。
- 成功响应提供完整 `code/message/data.list/data.total` example。
- 写明 `x-permission`、401、403 和相关业务错误码。

## 8. 风险与取舍

1. Candidate 冻结快照的字段结构可能存在历史差异；实现时必须集中解析并安全降级，不把原始 JSON 直接暴露给前端。
2. 批阅人目前按考试维度分配，因此同一考试的所有人工题对所有已分配批阅人可见；更细的题目/考生分片分配不在现有模型中，本
   Change 不自行扩展。
3. 本 Change 复用既有机构数据范围能力；若现有考试查询尚未接入可复用的数据范围组件，实施时必须暂停并提交人工决策，不得临时发明过滤规则。

