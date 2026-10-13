# Design：课程考核学员作答详情可见性

## 1. 问题

课程学员详情入口：

```text
GET /api/course/learning/enrollments/{enrollmentId}/items/{itemId}/attempts/{attemptId}
```

`CourseAssessmentAttemptService.get()` 在校验课程归属后委托 `ExamLearnerService.getMyAttempt()`。该实现按独立考试规则裁剪：

- `scorePublishStatus` / `scoreVisible` 只认 `PUBLISHED + scorePublishedTime`
- 答案详情还要求 `exam != null` 且满足 `answerDetailMode`

课程考核自动生效写的是 `RESULT_EFFECTIVE` + `resultEffectiveTime`，没有 `examId` 和 `ExamPolicy`，因此生效后仍被当成未发布。

交卷写路径 `ExamAttemptServiceImpl.toVO()` 和课程任务列表已经按 `EFFECTIVE` 返回成绩，只有 GET 详情接错口径。

## 2. 来源分支

| 来源                                        | 成绩可见门槛                                                    | `scorePublishStatus`        | 标准答案 / 题目得分 / 解析                          |
|-------------------------------------------|-----------------------------------------------------------|-----------------------------|-------------------------------------------|
| `COURSE_QUIZ` / `COURSE_BANK_PRACTICE`    | `RESULT_EFFECTIVE` 且 `resultEffectiveTime` 非空             | `EFFECTIVE` 或 `UNPUBLISHED` | 与成绩可见绑定；课程无 `ExamPolicy`，生效后一并返回          |
| `STANDALONE_EXAM` / `STANDALONE_PRACTICE` | `PUBLISHED` 且 `scorePublishedTime` 非空，再看 `isScoreVisible` | `PUBLISHED` 或 `UNPUBLISHED` | 仍按 `answerDetailMode`、`isAnalysisVisible` |

判定以来源 `sourceType` 为准，不得用「有没有主观题」或「有没有 `examId`」猜测。

## 3. 字段裁剪

已生效的课程详情返回：

- 汇总：`finalScore`、`isPassed`、`objectiveScore`、`subjectiveScore`、`correctCount`、`wrongCount`、`resultEffectiveTime`
- 题目：`standardAnswer`、`correctAnswerSnapshotJson`、选项 `isCorrectSnapshot`、`analysisSnapshot`
- 答案：`autoScore`、`manualScore`、`finalScore`、`isCorrect`；本人 `answerText` / `items` 始终可回看

未生效时上述评分与标准答案字段必须为空或不返回；本人已提交答案仍返回。

`reviewComment` 在学员详情中始终为 `null`。

课程不读取、不伪造 `ExamPolicy`。本期不增加课程级 `answerDetailMode`。

## 4. API

不改路径、权限和错误码。同步 `docs/api/course.openapi.json`：

- GET 详情 `description` 写明生效前后可见性
- 成功 example 覆盖 `RESULT_EFFECTIVE` + `scorePublishStatus=EFFECTIVE` + `scoreVisible=true`
- `CourseAssessmentAttemptVO` 字段说明与上述口径一致

独立考试 `docs/api/exam.openapi.json` 的学员发布裁剪不改。

## 5. 实现

只改学员读取映射，不改交卷、判分、自动生效和课程进度回写。

`ExamLearnerServiceImpl.toAttemptVO()`：

1. 课程来源按第 2 节计算 `scoreVisible` / `scorePublishStatus`，答案详情不再要求 `exam != null`
2. 独立考试路径保持现有 `isPublished` + policy 逻辑

`CourseAssessmentAttemptService.get()` 继续委托 `getMyAttempt()`，归属校验不变。

不改数据库，不改权限码。

## 6. 测试

零 DB：

- 课程测验 / 题库练习 `RESULT_EFFECTIVE`：`EFFECTIVE`、`scoreVisible=true`，返回总分、标准答案、题目得分和解析
- 课程测验 `PENDING_REVIEW`：`UNPUBLISHED`、隐藏成绩和标准答案，保留本人答案
- 独立考试未发布 / 已发布 + `answerDetailMode`：原有断言不变

不新增 IT：无 schema、SQL 或持久化语义变化。

## 7. 风险

- 把独立考试误标为课程来源会提前露答案。对策：只认 `COURSE_QUIZ` / `COURSE_BANK_PRACTICE`。
- 生效后默认给解析，与独立考试 `isAnalysisVisible` 不同。对策：课程是学习回看，本 Change 明确采用该默认，不引入新开关。
