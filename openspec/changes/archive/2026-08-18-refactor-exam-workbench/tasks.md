# Tasks：收敛考试工作台与人员配置路由

> 状态：Task 1 已完成。一次只执行一个已确认 Task。

## Task 1：统一考试工作台与人员配置路由

- 状态：已完成（2026-08-18）
- Owner：AI
- 输入：当前考试新建、详情和人员配置页面；试卷工作台阶段式结构；`docs/api/exam.openapi.json`。
- 输出：统一的新建/详情考试工作台、内嵌人员配置 Tab、发布前核查 Tab，以及无独立人员配置路由的列表入口。
- 前后端影响：仅 PC Web；不修改 OpenAPI、后端、权限或数据库。
- API 依赖：既有 `createExam`、`getExam`、`updateExam`、`bindExamSelection`、`getExamCandidates`、`getExamReviewers`、`replaceExamReviewers`、`publishExam`。
- 数据影响：无。
- 验收标准：新增和详情使用同一工作台组件；创建成功后可在同页继续人员配置；人员配置为工作台组件而非路由；发布 Tab 按详情页样式核查考试信息、考生与批阅人，并仅在前端已检测到考生和批阅人时显示可用发布按钮；已发布考试不可重复发布；不新增接口或权限。
- 测试方式：先增加失败的工作台路由/组件边界与发布核查定向测试；最小实现转绿；PC Web typecheck、受影响 lint 与 `git diff --check`。

完成记录：先新增统一工作台的路由、组件边界和发布核查定向用例并观察缺少工作台文件的失败；随后新建 `workspace/index.vue`，将新增和详情入口统一到该组件，人员配置迁入 `components/ExamAssignmentPanel.vue`，发布核查迁入 `components/ExamPublishCheck.vue`。移除独立人员配置路由和旧页面文件，列表入口改为 `stage=assignment`。考试管理、契约回读和 PC UI 标准 Node 测试 32/32 通过；PC Web `typecheck`、受影响 ESLint 与 `git diff --check` 通过。规格符合性 Review：通过；代码质量 Review：通过。
