# QA：培训考试闭环恢复与双端统一

## 已验证

- PC Node 回归：8 项通过，覆盖待开始须知、进行中恢复、题库练习直入、终态跳结果、异常截止时间与培训列表状态卡片。
- 移动端 Node 回归：5 项通过；`pnpm --dir frontend --filter mobile-app typecheck` 通过；H5 构建完成。
- Training OpenAPI JSON 解析、OpenSpec 严格校验和 `git diff --check` 通过。
- PC 生产构建通过；PC typecheck 的唯一报错为既有 `src/api/training-plan.ts:64` 的 `trainingStandardId` 空值类型不兼容，未改动该文件。

## 后端验证

本次改动后，目标服务测试曾通过 80 项（`TrainingWorkflowServiceImplTest`、`ExamAttemptServiceImplTest`、`ExamAttemptTimeoutSchedulerTest`）。后续重复执行时，现有模块构建产物缺少 `CourseContentSnapshot` 与 `TrainingPlanMapper`，导致 Mockito 装载失败；这是测试类路径/构建产物问题，发生在测试初始化前，非本次断言失败。

## 待真实登录态验收

1. 待开始理论考试进入须知前不产生 Attempt，点击开始后才开始计时。
2. PC、移动端进行中考试退出重进后按同一服务端截止时间继续倒计时。
3. 已到期和终态作答直接进入结果；非法截止时间显示异常，不误交卷。
4. 题库练习始终直接进入答题。
