# Tasks：课程测验交卷事实可见性

> 状态：2026-09-17 经人工确认口径后实施（选择「交卷即投影进度」）。Owner：fxw11。

## Task 1：课程测验交卷即投影内容项参与事实

- **状态**：已完成（2026-09-17）
- **Owner**：fxw11
- **输入**：本 Change 的 proposal、design、spec delta；`CourseLearningServiceImpl.applyEffectiveAssessmentResult`；
  `ExamAttemptServiceImpl.finalizeSubmit`；功能项 56、74。
- **输出**：`CourseAssessmentSubmitted` 事实记录、`CourseLearningService.applySubmittedAssessmentResult`、
  交卷分支调用与幂等的作答次数收敛、受影响零 DB 测试。
- **前后端影响**：仅后端。
- **API 依赖**：无接口、字段或错误码变化，`docs/api/course.openapi.json` 不需同步。
- **数据影响**：不改表结构、不加 migration、不改 Entity；仅改变 `course_item_progress` 对课程测验
  内容项的产生时机。
- **验收标准**：
  1. 含主观题的课程测验交卷后，内容项进度为 `LEARNING`、作答次数 1、时间为交卷时间。
  2. 交卷不写 `course_assessment_result`，不返回任何分数或判分结论。
  3. 课程进度百分比、累计学习时长与学分与交卷前一致。
  4. 成绩生效后同一内容项转 `COMPLETED`，作答次数仍为 1。
  5. 重复交卷回调只刷新最近学习时间，不重复建行或累加。
  6. 全客观题课程测验、题库练习的既有自动生效路径行为不变。
- **测试方式**：
  1. Red → Green：`applyEffectiveAssessmentResult_afterSubmittedProjection_keepsAttemptCount` 在未改
     `attemptCount + 1` 时失败（实测 `attemptCount=2`），修正后转绿。
  2. `mvn -pl sts-service-admin -am test -Dtest=CourseLearningServiceImplTest,ExamAttemptServiceImplTest
     -Dsurefire.failIfNoSpecifiedTests=false`。
  3. 不改 SQL/Entity/Mapper，不跑 IT。

## Task 2：移动端课程目录与结果页按考核状态收敛

- **状态**：已完成（2026-09-17）
- **Owner**：fxw11
- **输入**：Task 1 的进度口径；`pages/course/detail.vue`、`pages/course/learning.vue`、
  `pages/course/assessment-result.vue`；`pages/exam/result.vue` 作为移动端既有样式基线。
- **输出**：两页统一的考核状态文案函数、待批阅结果页的等待提示与答题回看、答案选项文本还原。
- **前后端影响**：仅移动端。
- **API 依赖**：仅使用课程任务快照与课程作答详情的既有字段，无契约变化。
- **数据影响**：无。
- **验收标准**：
  1. 课程详情页与课程学习页对同一内容项给出相同考核状态文案。
  2. 已交卷待批阅显示等待批阅，结果已生效显示成绩结论。
  3. 待批阅时结果页展示等待提示与本人已提交答案，不显示判分、标准答案与解析。
  4. 结果生效后结果页展示成绩、标准答案与解析。
  5. 选择题「我的答案」显示为「选项键 + 选项内容」，不显示裸键或空值。
- **测试方式**：
  1. `eslint` 受影响的三个页面，`--max-warnings=0`。
  2. mobile-app `vue-tsc --noEmit` 与 `uni build -p h5`。
  3. 待补：真实联调（后端 + 含主观题的课程测验走完交卷 → 批阅 → 生效）。

## 遗留

1. 真实端到端联调未执行，Task 1 / Task 2 的验收标准 1～5 均为单测、类型检查与构建层面的证据。
2. 尚未更新 `engineering/plans/end-to-end-development-progress-tracker.md` 与 QA / Retro 记录。
3. 双轴 Review（规格符合性、代码质量）待独立复核。
