# Design：移动端课程题库练习与课程测验

## 业务边界

- 课程内容仅处理 `QUESTION_BANK` 与 `QUIZ`，并使用 `/api/course/learning/enrollments/{enrollmentId}/items/{itemId}/attempts/**`。
- 作答状态按课程 OpenAPI Contract：无 Attempt 时开始；`ANSWERING` 继续；`PENDING_REVIEW` 查看已交卷；`RESULT_EFFECTIVE` 与 `EFFECTIVE` 结果事实查看结果。
- 题库练习和纯客观题课程测验交卷后自动生效；含主观题课程测验在完成批阅后自动生效。仅结果生效后标记课程内容项完成；不合格不阻断课程完成。

## 移动端设计

- 在 `pages/course/` 新增独立课程作答和结果页，并由课程学习页携带 `enrollmentId`、`itemId`、`attemptId` 和内容项标题跳转。
- 页面复用独立考试页的移动卡片、题卡、进度、交卷确认与结果解析信息层级，不复制独立考试的 anticheat、倒计时或超时交卷行为。
- 课程结果只有 `scoreVisible=true` 且 `scorePublishStatus=EFFECTIVE` 时显示分数、标准答案和解析；其他状态只显示等待批阅说明与本人已交卷事实。

## API 与数据

- `LearnerCourseTaskItem.examTaskStatus` 的 Contract 枚举更新为 `NOT_STARTED / IN_PROGRESS / WAITING_RESULT / RESULT_EFFECTIVE`，对应后端已输出并由 PC 端消费的目录任务状态；Attempt 详情继续使用 `ANSWERING / PENDING_REVIEW / RESULT_EFFECTIVE`。
- 复用既有共享 `CourseAssessmentAttempt`、`LearnerExamQuestion`、保存答案和交卷载荷类型，并将任务状态类型收窄到更新后的 Contract 枚举。
- 移动端课程 API 与 Mock 补齐查询、暂存和提交函数，字段和 URL 完全按 `course.openapi.json`。
- 不新增 Contract、持久化字段或 Mock 专用字段。
