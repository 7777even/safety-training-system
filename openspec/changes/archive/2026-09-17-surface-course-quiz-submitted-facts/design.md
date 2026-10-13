# Design：课程测验交卷事实可见性

## 数据流

```
学员交卷
  └─ ExamAttemptServiceImpl.finalizeSubmit
       ├─ 全客观题 → markCourseResultEffective → applyEffectiveAssessmentResult（生效事实，含分数）
       └─ 含主观题 → attemptStatus = PENDING_REVIEW
                      └─ applySubmittedAssessmentResult（新增，只投影参与事实）
                              └─ course_item_progress: LEARNING / attemptCount=1 / 时间=交卷时间

教师批完最后一道主观题
  └─ reviewCourseAnswer → applyEffectiveAssessmentResult
       └─ 同一内容项: COMPLETED / attemptCount=1（不再累加）/ bestScore
```

## 关键决策

### 1. 交卷投影只写内容项进度，不写 `course_assessment_result`

`course_assessment_result` 的唯一键（`assessmentExecutionId + courseEnrollmentId + courseItemId`）
承担了生效回调的幂等：`applyEffectiveAssessmentResult` 命中重复键时提前 return。
若交卷阶段先插入该行，成绩生效回调会被判为重复而短路，内容项将**永远无法转为已完成**。
因此交卷投影不得触碰该表，幂等由「查一次 `course_item_progress` 再插入」承担。

### 2. 作答次数按「至少 1 次」收敛，而不是累加

原生效路径用 `attemptCount + 1`；行由生效路径自己创建时（0 → 1），可观测结果恒为 1。
交卷投影先建行并置 1 之后，若生效路径继续 `+1` 就会变成 2（同一份答卷被记两次）。
改为 `max(attemptCount, 1)`：对原有路径观测等价，对交卷投影路径不重复计数。
不新增字段、不改表结构。

### 3. 交卷不改变完成度

`recomputeEnrollment` 只统计 `COMPLETED` 的内容项，交卷产生的 `LEARNING` 行不计入，
进度百分比、学分与 `isEnrollmentCompleted` 均不受影响；`lastStudyTime` 会随交卷刷新，
与「最近学习时间」的既有语义一致。

### 4. 已知副作用（已向人工确认）

`CourseItemServiceImpl.assertAssessmentReferenceMutable` 以「该内容项是否已有进度记录」
判定考核资源能否修改。交卷即建行后，测验内容项的试卷版本会从「首份成绩生效」提前到
「首份交卷」即被锁定。这与错误码 5043「内容项已开课或已有参与事实，不可修改考核资源」的
语义一致，接受该提前量。

### 5. 前端按考核状态收敛

移动端详情页与学习页原本是两套文案函数：学习页看考核状态、详情页只看 `progressStatus`，
导致同一学员在两页看到不同状态。统一为：`QUIZ` 看 `scorePublishStatus` / `examTaskStatus`；
题库练习不计分，仍以内容项进度为主，但存在在答轮次时显示「正在作答」。

结果页待批阅分支改为「等待成绩发布 + 答题回看」，复用 `pages/exam/result.vue` 的既有类名与结构；
成绩生效分支保持不变。答案文本统一走 `items[].itemKey → 选项文本` 的还原口径（与 PC 一致），
不再直接渲染 `answerText`（选择题存的是裸 key）。

## 验证口径

- 后端：`CourseLearningServiceImplTest`、`ExamAttemptServiceImplTest` 定向单测；不改 SQL/Entity，不跑 IT。
- 前端：受影响页面 lint、mobile-app 类型检查与 H5 构建。
- 未验证：真实联调（需后端 + 一份含主观题的课程测验走完交卷 → 批阅 → 生效）。
