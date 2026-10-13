# Tasks：优化考试流程入口、批阅人回显与契约覆盖

> 状态：Task 1～5 已完成。

## Task 1：限制考试情况入口并补充列表直达

- 状态：已完成
- 前后端影响：仅 PC Web；不修改 API、权限、数据或路由契约。
- 验收：草稿不显示且不能进入考试情况；已发布考试在列表和工作台都可进入。

## Task 2：收敛批阅人回显字段

- 状态：已完成
- 前后端影响：仅 PC Web；不修改后端、OpenAPI、Mock 或 API 类型。
- 验收：重新进入人员配置时展示账号、姓名、所属机构和配置来源，不展示部门、职务。

## Task 3：补齐考试状态字典

- 状态：已完成
- 前后端影响：仅 PC Web；不修改 API、权限或数据。
- 验收：`PENDING_REVIEW` 等当前返回值不直接显示英文枚举。

## Task 4：完成考试 OpenAPI 调用覆盖审计

- 状态：已完成
- 前后端影响：PC Web 路由、文档与测试；不修改后端、接口、OpenAPI 或权限语义。
- 验收：批阅和成绩处置从考试工作台可直达；每个 operation 都有调用方，无产品范围排除项。

### 覆盖审计记录

| OpenAPI operation | PC Web 调用封装 |
| --- | --- |
| `GET/POST /api/exam/exams` | `getExamPage` / `createExam` |
| `GET/PUT/DELETE /api/exam/exams/{examId}` | `getExam` / `updateExam` / `deleteExam` |
| `POST /api/exam/exams/{examId}/publish` | `publishExam` |
| `POST /api/exam/exams/{examId}/candidates/bind-selection` | `bindExamSelection` |
| `GET /api/exam/exams/{examId}/candidates` | `getExamCandidates` |
| `PUT/GET /api/exam/exams/{examId}/reviewers` | `replaceExamReviewers` / `getExamReviewers` |
| `GET /api/exam/review-tasks` | `getExamReviewTaskPage` |
| `GET /api/exam/attempts` | `getExamAttemptRecordPage` |
| `POST /api/exam/attempts/start` | `startExamAttempt` |
| `GET /api/exam/learner/attempts/{examAttemptId}` | `getLearnerExamAttempt` |
| `PUT /api/exam/attempts/{examAttemptId}/answers` | `saveExamAnswers` |
| `POST /api/exam/attempts/{examAttemptId}/submit` | `submitExamAttempt` |
| `POST /api/exam/attempts/{examAttemptId}/timeout-submit` | `timeoutSubmitExamAttempt` |
| `POST /api/exam/answers/{examAnswerId}/review` | `reviewExamAnswer` |
| `POST /api/exam/attempts/{examAttemptId}/publish-score` | `publishExamScore` |
| `POST /api/exam/candidates/{examCandidateId}/retake` | `createRetakeAttempt` |
| `POST /api/exam/candidates/{examCandidateId}/practice-reset` | `resetPractice` |
| `GET /api/exam/learner/tasks` | `getLearnerExamTaskPage` |
| `GET /api/exam/attempts/{examAttemptId}` | `getReviewAttempt` |
| `POST /api/exam/attempts/{examAttemptId}/anticheat-events` | `recordExamAnticheatEvent` |
| `GET /api/exam/attempts/{examAttemptId}/anticheat-events` | `getExamAnticheatEvents` |
| `GET /api/exam/attempts/{examAttemptId}/score-publish-logs` | `getExamScorePublishLogs` |

## Task 5：优化批阅可查看性与用户文案

- 状态：已完成
- 前后端影响：仅 PC Web 样式、页面文案与回归测试；不修改后端、接口、OpenAPI、Mock 或权限语义。
- 验收：反作弊事件较多时可滚动查看并继续批阅；教师批阅、成绩处置及相关考试页面的用户提示不出现“服务端、Contract、OpenAPI、接口、快照”等实现术语。
