# Proposal：课程测验交卷事实可见性

## Why

课程测验含主观题时，交卷后作答进入 `PENDING_REVIEW`，但**课程侧没有任何交卷事实**：

- 内容项进度只在成绩生效时才由 `applyEffectiveAssessmentResult` 写入，因此「学员学习进度」的
  分块进度里完全不出现该测验内容项（实测：三门内容项只显示两门，学员已交卷却看不到记录）。
- 管理端「课程测验结果」读 `course_assessment_result`，同样只在生效后才有行。
- 学员端课程目录的状态文案只看 `progressStatus`，于是出现「状态=待学习 / 按钮=查看已交卷」的自相矛盾。
- 移动端结果页在待批阅时只渲染一张等待卡片，学员看不到自己交上去的答案（PC 端同一接口是能看的）。

事实本身在系统里是存在的（`exam_attempt` + `exam_candidate` 已记录交卷），缺的是把它投影到课程域的
内容项进度，以及按考核状态渲染目录与结果页。

## What Changes

- 课程测验交卷（`PENDING_REVIEW`）即按 `assessmentExecutionId + courseEnrollmentId + courseItemId`
  幂等投影内容项进度：内容项转 `LEARNING`、作答次数记 1、首次开始时间与最近学习时间取交卷时间。
- 交卷投影 MUST NOT 写 `course_assessment_result`、MUST NOT 记分、MUST NOT 把内容项标记为已完成；
  进度百分比、累计学习时长、学分不因交卷变化。
- 成绩生效回调改为「至少 1 次作答」，避免同一份答卷被交卷与生效两次投影重复计数。
- 移动端课程目录（详情页与学习页）状态文案按考核状态收敛，两页共用同一口径。
- 移动端课程测验结果页在待批阅时展示本人已提交答案，成绩生效后再展示成绩、标准答案与解析。
- **BREAKING（语义）**：`course_item_progress` 对课程测验内容项的产生时机从「成绩生效后」提前到
  「交卷后」。连带影响：该内容项的考核资源引用会更早被 `assertAssessmentReferenceMutable` 锁住
  （由「首份成绩生效」提前到「首份交卷」），与「已有参与事实不可改」的既有语义一致。

## Capabilities

### New Capabilities

- 无。

### Modified Capabilities

- `course-assessment-source-separation`：课程测验的课程侧事实从「只有生效结果」扩展为
  「交卷参与事实 + 生效结果」两段，并明确交卷阶段的进度口径与幂等边界。

## Impact

- 后端：`CourseLearningService` / `CourseLearningServiceImpl`、`ExamAttemptServiceImpl`，
  新增 `CourseAssessmentSubmitted`；不改数据库、不改权限码、不改 OpenAPI（无接口或字段变化）。
- 前端：`frontend/mobile-app/src/pages/course/{detail,learning,assessment-result}.vue`。
- 不变：课程完成规则（`ALL_REQUIRED_ITEMS / ALL_ITEMS`）、学分授予、培训投影口径、批阅与发布流程。

## 功能项追溯

- **功能项 74**：在线学习「主要是实现用户在线学习，包含学习记录、学习进度等功能……不同终端的学习进度、学时等员工学习情况可实时同步」。
- **功能项 56**：我的培训「展示学员的培训任务，包括课程任务和考试任务，支持课程学习和考试，显示课程完成度和考试结果」。

## 目标

1. 学员交卷后，管理端学员学习进度可见该测验内容项的作答次数与时间轨迹。
2. 交卷不改变课程完成度、学分与学习时长，不提前产生任何成绩结论。
3. 学员端课程目录不再出现「待学习 + 查看已交卷」并存。
4. 学员在待批阅期间可回看本人答案，生效后切换为成绩与解析。

## 非目标范围

- 不改批阅流程、阅卷人分配、成绩发布与 `course_assessment_result` 的写入时机。
- 不改管理端「课程测验结果」面板的可见范围（其语义仍是生效结果列表）。
- 不为 `course_item_progress` 增加字段或 migration；不引入「交卷次数」新口径。
- 不调整课程完成规则、学分规则与培训学时投影。
