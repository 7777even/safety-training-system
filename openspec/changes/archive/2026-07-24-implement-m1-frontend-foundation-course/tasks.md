# Tasks：M1 前端基础与课程真实闭环

> 状态：进行中。确认后按顺序一次只执行一个 Task；本轮最多连续完成三个 Task。

## Task 1：机构协作与课程参与范围配置

- 状态：已完成（2026-07-24）
- Owner：AI
- 输入：BASE-02～04 OpenAPI；角色工作流第 3、5.1 节
- 输出：PC 部门/角色扩展表单，机构成员、标签、岗位、参与范围、监管关系和资源授权的管理能力
- 前后端影响：仅 PC Web；使用既有后端 Contract，不修改后端
- API 依赖：`system-admin`、`org`、`common-audience`、`resource-grant` OpenAPI
- 数据影响：无数据库变化；浏览器仅保存页面筛选与弹窗状态
- 验收标准：
  - 部门与角色完整提交 Contract 新增字段，CUSTOM 数据范围可维护部门及包含子部门标记。
  - 机构成员、标签、岗位、参与范围、监管关系与资源授权具备 Contract 范围内的分页和 CRUD；参与范围可预览与冻结成员。
  - 所有列表遵循统一分页；版本冲突、权限不足、参数错误与删除保护提供中文反馈。
  - 每新增动态菜单页面均在交付说明和 design 菜单配置交付清单中列明路由路径、组件路径和菜单权限。
- 测试方式：先写 API/页面行为失败测试；运行定向 Node 测试、PC `lint`、`typecheck`、`build`。

完成记录：部门/角色扩展、机构主数据（当前 Contract 的分页、详情、新增）、标签、岗位、成员、参与范围、监管关系和资源授权页面及 API 已交付；机构选择统一经 API 的 Mock/真实分支，成员和联系人通过系统用户分页列表选择，主岗位限定为已选岗位。定向 Node 测试、PC typecheck、PC build、定向 lint 与 `git diff --check` 通过。机构更新/删除因当前 `org.openapi.json` 未定义而未实现；全量 lint 仅被既存 `AssetPreviewPanel.vue` 格式警告阻断，未混入本 Task 修复。

## Task 2：课程管理 Contract Mock 闭环与可用接口联调

- 状态：已完成（2026-07-27）
- Owner：AI
- 输入：已归档课程 PC 前端 Change；COURSE-01/03/04 Contract；Task 1
- 输出：素材、课程、编排、版本、开课、报名和管理端学习详情的 Contract Mock 流程；仅对人工确认可用的接口接入真实 API
- 前后端影响：仅 PC Web；后端未完成接口保留对应 Mock，不修改后端
- API 依赖：`asset.openapi.json`、`course.openapi.json`
- 数据影响：无数据库变化
- 验收标准：
  - 后端未完成接口可通过 Contract Mock 完成查询、创建、更新、状态流转、报名和学习详情交互。
  - Mock 请求参数、`Result` 外壳、分页、乐观锁和业务错误与 Contract 一致。
  - 人工确认可用的接口才切换真实请求；联调通过后只移除该接口的 Mock 分支。
- 测试方式：先写 Mock/真实数据源边界失败测试，再完成最小实现；运行课程定向测试与 PC `lint`、`typecheck`、`build`。

完成记录：课程、素材、编排、版本、开课、报名和管理端学习详情均通过 API 层的 Contract Mock/真实分支调用；开课保存后可设置 `BOUND + OFFERING + PARTICIPANT` 参与范围，报名指派使用人员选择器，未手输参与范围或用户 ID。课程及 M1 定向 Node 回归 19/19、PC typecheck、PC build 与 `git diff --check` 通过；后端未确认可联调的课程接口继续保留 Mock 分支。

## Task 3：学员 PC 与移动端课程学习

- 状态：已完成（2026-07-27）
- Owner：AI
- 输入：Task 2；课程学习、报名、退选、评价 Contract；角色工作流第 7 节
- 输出：PC 我的课程及移动学习任务流，含学习详情、会话、退选和评价
- 前后端影响：PC Web、mobile-app 与必要的 shared 纯类型；不改后端
- API 依赖：课程报名、学员学习详情、学习会话、退选和评价接口
- 数据影响：无数据库变化；学习事实仅由后端接口写入
- 验收标准：
  - PC 与移动端仅展示当前用户可访问的课程/报名事实；加载、空数据、权限和网络异常有明确状态。
  - 学员可在 Contract 允许状态下查看学习详情、上报会话、退选和提交评价；不支持预览/播放时只展示真实状态。
  - 移动端保持任务卡片与触控交互，不复用 PC 表格结构。
- 测试方式：先写 PC 与移动 API/页面行为失败测试；运行定向测试、两端 `lint`、`typecheck`、各自 `build`。

完成记录：PC 门户既有 `/portal/class` 入口已完成我的课程、可报名课程、自主报名、任务抽屉、会话上报、退选与已完成课程评价；移动端“学习”Tab 完成课程卡片与任务详情触控流。两端 API 均保留 Contract 一致的 Mock/真实分支，任务页不伪造媒体预览或播放。先观察定向 Node 测试失败，再实现并验证 PC 3/3、移动 2/2 测试通过；两端 typecheck、移动 lint、PC 新增文件定向 lint、PC build 和移动 H5 build 通过。PC 全量 lint 仅受既存 `AssetPreviewPanel.vue` 格式警告阻断。规格符合性与代码质量自审通过；真实联调与删除 Mock 分支留待 Task 4。

补漏记录（2026-09-29）：Task 3 原完成记录遗漏了移动端评价提交。本次按既有评价 Contract 补齐移动端已完成课程提交与本人回看，不增加接口、权限或数据库；自动化和真实验收边界见 `engineering/qa/2026-09-29-mobile-course-evaluation-gap.md`。同次开课的学员评价列表另见待确认 Change `2026-09-29-show-peer-course-evaluations`。

## Task 4：M1 前端端到端验收与记录

- 状态：进行中（真实联调环境与测试账号待提供）
- Owner：AI
- 输入：Task 1～3 均通过
- 输出：M1 前端 QA、Retro、OpenSpec/进度表回填与双轴 Review 结论
- 前后端影响：工程记录与前端验收；不改变业务功能
- API 依赖：Task 1～3 全部 Contract
- 数据影响：无
- 验收标准：
  - 管理员配置参与范围→开课报名→学员 PC/移动学习→评价的可用 Contract 路径通过真实联调。
  - Mock 不作为验收证据；所有已实现 M1 接口无遗留 Mock 分支。
  - QA、Retro、tasks.md 和进度表完整记录测试命令、结果、阻塞与双轴 Review。
- 测试方式：执行 Task 1～3 的回归测试和两端目标构建；人工核验真实联调路径。

阶段记录（2026-08-10）：已按最新 `course.openapi.json` 完成课程字段与交互复核，补齐 `QUESTION_BANK`、`EXAM`、`PRACTICE`
内容项与对应考试类型筛选；讲师、参与范围和辅助音频均改为选择器，章节编辑改为弹窗，下载资料回显已选名称。课程定向 Node 测试
9/9、带兼容参数的 PC `vue-tsc`、PC build 和 `git diff --check` 通过，详见
`engineering/qa/2026-08-10-m1-frontend-task4-course-contract-audit.md`。当前真实 API 地址未提供，本机只发现 Vite 且
`http://localhost/api/course/categories/tree` 返回 404；缺少管理员、讲师、学员账号，不能核验真实 Result、鉴权、分页、状态与学习事实，不以
Mock 关闭 Task 4。
