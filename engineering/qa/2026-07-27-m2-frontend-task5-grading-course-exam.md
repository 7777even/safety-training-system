# QA：M2 前端 Task 5 批阅、成绩与课程考试项

日期：2026-07-29

- TDD：先验证教师批阅/成绩处置与课程考试项 API、页面不存在的失败测试；实现后定向测试 4/4 通过。
- Contract：批阅、发布、补考、练习重置严格映射 `exam.openapi.json`；课程项以 `examId` 仅关联已发布考试，学员从课程项启动考试走
  `course.openapi.json` 的 `exam-attempts` 路径。
- 验证：PC/mobile typecheck、PC build、mobile H5 build、定向 lint 与 `git diff --check` 通过。
- Review：规格符合性与代码质量均通过；未新增依赖，不伪造课程完成态，完全展示 Contract 返回事实。
- 未完成：真实接口联调保留至 Task 6。

## 2026-07-29 审计修复复验

- 修复：通用开始作答与课程考试启动均以 `ExamAttemptVO` 作答快照导航；PC 端补齐 Course 考试启动接口、Mock 和状态展示；课程配置
  Mock 提供一条已发布考试。
- Contract：`exam.openapi.json` 的开始作答响应和 `course.openapi.json` 的课程考试启动响应均为 `ExamAttemptVO`；四份 M2
  OpenAPI 均以 UTF-8 成功解析。
- 验证：M2 定向 Node 39/39、PC/移动端 `typecheck`、PC `build`、mobile H5 `build` 均通过；真实接口联调仍留待 Task 6。

## 2026-07-30 补全 Contract 后业务闭环复验

- TDD：先增加教师批阅任务分页、作答成绩分页、学员解析字段、补考幂等令牌及处置按钮权限断言并观察失败，最小实现后转绿。
- Contract：核验 `review-tasks`、`attempts`、作答快照、批阅、成绩发布、补考、练习重置和学员结果接口；修正必填
  `clientAttemptToken`，支持可选 `publishedBy`，字段与查询参数未再发现缺口。
- 页面：增加“考试批阅”和“成绩管理”两个可见工作台；批阅/处置保持独立详情页；PC/移动端学员结果页展示 Contract 允许返回的正确答案、标准答案和解析。
- 权限：教师入口需 `exam:review` + `exam:attempt:list`；成绩入口需 `exam:attempt:list` + `exam:score:publish`；
  补考和练习重置按钮分别使用 `exam:attempt:start`、`exam:exam:edit`。
- 验证：M2 定向 Node 39/39、PC/移动端定向 lint、PC build、mobile H5 build 通过；OpenAPI UTF-8 解析及路径/参数静态核验通过。
  PC 全量 typecheck 复跑被同期工作区非本 Task 文件 3 处隐式 `any` 阻塞，本 Task 文件未报类型错误。
- Review：规格符合性与代码质量均通过；未改 OpenAPI、数据库或依赖，真实接口联调仍归 Task 6。
