# QA：移动端课程题库练习与课程测验闭环

日期：2026-08-28  
OpenSpec：`2026-08-28-complete-mobile-course-assessment` Task 1

## 验收结果

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 课程目录状态分流 | 通过 | 无作答进入开始；`IN_PROGRESS` 继续；`WAITING_RESULT` 进入已交卷结果页；`RESULT_EFFECTIVE` 回看结果。|
| 课程域接口边界 | 通过 | 新增查询、暂存、交卷均调用 `/api/course/learning/enrollments/{enrollmentId}/items/{itemId}/attempts/**`，课程页面未调用 `/api/exam/**`。|
| 作答与交卷 | 通过 | 支持单选、多选、填空和简答；交卷前暂存全量答案；题库练习未完成全部题目时 Mock 返回后端一致的 `7064`。|
| 结果裁剪与完成状态 | 通过 | 仅 `scoreVisible=true` 且 `scorePublishStatus=EFFECTIVE` 显示分数、答案和解析；待批阅仅提示等待，结果生效后刷新课程目录为已完成。|
| OpenAPI 一致性 | 通过 | `LearnerCourseTaskItem.examTaskStatus` 更新为后端和 PC 使用的 `NOT_STARTED`、`IN_PROGRESS`、`WAITING_RESULT`、`RESULT_EFFECTIVE`；补齐既有 7021、7022、7064 错误码说明；JSON 解析通过。|
| 定向自动化测试 | 通过 | `node --test frontend/mobile-app/tests/learner-course-learning.test.mjs`，15/15。|
| 类型、Lint 与构建 | 通过 | `pnpm --filter @safety-training/mobile-app typecheck`；受影响源码 ESLint；`pnpm --filter @safety-training/mobile-app build:h5`。|
| 差异与双轴 Review | 通过 | `git diff --check` 无空白错误；规格符合性与代码质量 Review 均通过，复审后消除状态混用、类型断言和 Mock 错误码偏差。|

## 未执行/后续验证

- 未在非生产环境以真实学员账号完成课程题库练习、纯客观题测验及含主观题测验的真实接口联调；当前已由课程域 Mock 和定向测试覆盖关键状态。
- 全量移动端 lint 未作为本 Change 验收命令：现存 `frontend/mobile-app/src/pages/course/detail.vue` 的未使用变量错误与 LoginPage 告警不属于本次修改；受影响源码 ESLint 已通过。
