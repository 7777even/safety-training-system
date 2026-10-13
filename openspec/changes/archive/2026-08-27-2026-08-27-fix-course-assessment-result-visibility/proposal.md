# Proposal：补齐课程考核学员作答详情的自动生效可见性

> 状态：设计已在本 Change 写明；用户已授权按 `tasks.md` 实施。

## 背景

课程题库练习和课程测验交卷后按已确认规则自动生效（`RESULT_EFFECTIVE`），不进入独立考试人工发布。但学员
`GET /api/course/learning/enrollments/{enrollmentId}/items/{itemId}/attempts/{attemptId}`
复用了独立考试 `getMyAttempt()` 的裁剪：只认 `attemptStatus=PUBLISHED` 且 `scorePublishedTime` 非空，并要求存在
`Exam` / `ExamPolicy` 才返回标准答案和解析。

课程执行没有 `examId`、没有成绩发布时间，因此无论有无主观题、结果是否已生效，详情都返回
`scorePublishStatus=UNPUBLISHED`、`scoreVisible=false`，并裁掉总分、题目得分、标准答案和解析。课程任务列表和交卷响应已按
`EFFECTIVE` 处理，与详情口径不一致。

## 功能项依据

| 功能项   | 原文核心语义                     | 本 Change 的对齐方式                   |
|-------|----------------------------|----------------------------------|
| 22    | 章节可关联理论题库和理论试卷测验，学员作答并记录成绩 | 学员回看课程考核详情时，结果生效后可见成绩、对错、标准答案和解析 |
| 48～49 | 学员课程学习与独立考试是不同入口           | 课程详情按自动生效裁剪，不得套用独立考试人工发布策略       |

## 目标

1. 明确课程考核学员详情的成绩/答案可见性，与「自动生效、不人工发布」一致。
2. 结果已生效时返回 `scorePublishStatus=EFFECTIVE`、`scoreVisible=true`，以及总分、合格、题目得分、标准答案和解析。
3. 作答中或待批阅时仍只回看本人已提交答案，不提前泄露标准答案、得分和解析。
4. 独立考试学员读取规则不变。

## 范围

- 课程题库练习（`COURSE_BANK_PRACTICE`）和课程测验（`COURSE_QUIZ`）学员作答详情的可见性。
- `ExamLearnerService` 按来源分支裁剪；同步 `docs/api/course.openapi.json` 与架构说明。
- 仅后端；前端以 OpenAPI 为准另开。

## 非目标范围

- 前端页面、Mock、UI 或联调。
- 为课程新增 `ExamPolicy`、`answerDetailMode` 或人工发布成绩接口。
- 改变独立考试的发布、`answerDetailMode`、`isScoreVisible`、`isAnalysisVisible`。
- 改变课程自动生效、阅卷人或课程完成规则。
- 数据库结构变更。
- 向学员返回内部批阅意见。

## 已确认可见性规则

1. 课程结果以 `attemptStatus=RESULT_EFFECTIVE` 且 `resultEffectiveTime` 非空视为已生效。
2. 已生效：`scorePublishStatus=EFFECTIVE`，`scoreVisible=true`；返回总分、合格、分项分、对错数、题目得分、正确性、标准答案、正确选项标记和题目解析。
3. 未生效（作答中、待批阅等）：`scorePublishStatus=UNPUBLISHED`，`scoreVisible=false`；保留本人已答内容，裁剪成绩、标准答案、正确选项和解析。
4. 课程本期不引入独立的解析开关；生效后解析与标准答案一并可见。
5. 学员详情始终不返回 `reviewComment`。
