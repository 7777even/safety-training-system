# QA：培训计划准备完成门禁与学员可见性

## 范围

OpenSpec Change：`2026-09-07-gate-training-start-and-learner-visibility`。

覆盖培训计划开始门禁、管理端准备进度提示、PC/移动学员任务可见性与 OpenAPI 契约。未改数据库结构、Entity、Mapper 或权限码。

## 自动化验证

- 后端零 DB：
  `mvn -pl sts-service-admin -am '-Dtest=TrainingWorkflowServiceImplTest,TrainingPlanLifecycleServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  → 25 项通过，0 失败、0 错误、0 跳过。
- PC 培训链路：
  `node --test frontend/pc-web/tests/training-composition-linkage.test.mjs`
  → 23 项通过。
- PC 定向 ESLint：培训执行面板、学员任务 API 和列表页通过。
- 移动端定向 ESLint：学员任务 API 和列表页通过。
- PC、移动端 `typecheck` 通过。
- `docs/api/training.openapi.json` JSON 解析通过。
- `git diff --check` 通过。

## 覆盖场景

1. 必修内容未安排时，开始计划返回 `409/8162` 且不更新计划状态。
2. 非必修线下内容未安排时，开始计划同样返回 `409/8162`。
3. 课程、考试或题库练习以及线下内容均已安排时，开始计划进入 `RUNNING`。
4. `PUBLISHED` 计划不出现在学员任务列表，任务详情返回 `404/8155`，进入内容返回 `409/8154`。
5. `RUNNING` 计划可见可进入，`ENDED` 可见但不可进入。
6. 管理端部分安排显示“继续安排培训内容”，全部安排后突出“开始计划”。

## MySQL IT

未运行。本 Change 不涉及 Entity、Mapper、自定义 SQL、schema、索引或持久化事务；零 DB 服务测试已覆盖状态门禁和关键副作用。

## 真实登录态待验收

需要在非生产环境以管理员和学员账号验证：

1. 5 项内容中 4 项安排成功、1 项失败：管理员只能继续安排，学员端不显示该计划。
2. 5 项均安排成功后开始：计划进入执行中，学员 PC/移动端出现“查看并执行”。
3. 结束后：学员仅可查看结果。
4. 取消后：学员任务列表不显示该计划。
