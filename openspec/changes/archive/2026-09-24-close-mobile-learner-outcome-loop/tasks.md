# Tasks：移动端学员成果闭环

> 状态：**已闭环（Task 1～4 全部完成，2026-09-28）** —— Task 1 后端 `trainingTypeName` 批量回填 + 契约同步、
> Task 2「我的证书」、Task 3「我的培训档案」与个人中心入口均已实现并通过自动化验证（移动端静态契约 3/3、
> `vue-tsc --noEmit` 退出码 0、改动文件 `eslint --max-warnings=0` 退出码 0、H5 build `DONE`；后端定向单测 41/41）。
> **Task 4 真实登录态纵向验收已于 2026-09-28 在 VPN 真实环境实跑通过**（后端 20080 + 团队远程库 + uni H5 5174 +
> CDP 无头浏览器）：superAdmin 走通「个人中心 → 我的证书 → 在线查看（受控 blob 1400×990）/ 下载（受控端点
> 200 image/png）」与「个人中心 → 我的培训档案 → 测试培训类型 共 4 期 → 期次详情（participantId=7 只读回放）」
> 两条链路；学员 `m4-learner-01` 取得证书/档案双空态，且读非本人证书文件返回 403/9416。
> 证据见 `engineering/qa/2026-09-28-dual-m4-real-environment-acceptance.md` 与 `engineering/assets/2026-09-28-mobile-*.png`（证据已于 2026-09-28 清理）。
> 展示层文案已随后续 `2026-09-28-improve-m4-certificate-and-mobile-archive` Task 3 调整（见下）。
> 约束：不新增业务表、不新增权限节点、不改数据库结构、不改 PC 端已交付页面；移动端不得使用 Element Plus。
> 边界决策：**不向学员开放 `archive:training:list`**、移动端不导出档案 PDF（依据见 `proposal.md` 决策记录）。

## Task 1：后端补培训类型名称并同步契约

- Owner：后端。
- 状态：已完成（2026-09-24）。`TrainingLearnerTaskVO` 增 `trainingTypeName`；`listLearnerTasks` 对可见任务的
  `trainingTypeId` 集合 `selectBatchIds` 批量查名称回填（`fillTrainingTypeNames`），类型缺失/停用时为空；
  OpenAPI `training.openapi.json` 的 `TrainingLearnerTaskVO` 补 `trainingTypeName`（中文 description + example）。
  定向测试 41/41（`TrainingWorkflowControllerTest` 8 + `TrainingWorkflowServiceImplTest` 33，含新增
  `listLearnerTasks_typeNameMissingOrStoppedFallsBackToNull` 与 `trainingTypeName` 批量回填断言）。
- 输入：`TrainingWorkflowController.listLearnerTasks`、`TrainingLearnerTaskVO`、`TrainingTypeMapper`、
  `docs/api/training.openapi.json`。
- 输出：`TrainingLearnerTaskVO` 增加 `trainingTypeName`；按本次返回的 `trainingTypeId` 集合**批量**查名称回填；
  OpenAPI 补字段中文说明与 example。
- 前后端影响：后端响应结构扩展（只增字段），移动端 Task 3 消费。
- API 依赖：`GET /api/training/learner/tasks`（已存在，响应扩展）；权限沿用 `training:learner:task`。
- 数据影响：**无新表、无迁移**。
- 验收标准：每个任务返回其培训类型名称；类型不存在或已停用时该字段为空且调用方可按「未分类」兜底；
  不产生逐条类型查询。
- 测试方式：先写失败测试断言返回类型名称与批量查询（Red），再最小实现（Green）；
  执行受影响模块定向编译与测试、`openspec validate --strict`。

## Task 2：移动端「我的证书」

- Owner：移动端。
- 状态：已完成（2026-09-24）。新增 `src/api/learner-certificate.ts`（`getMyCertificates` / `getMyCertificateFileUrl` /
  `getMyCertificateFile` / `downloadMyCertificateFile`）与 `src/pages/certificate/index.vue`；个人中心新增「我的证书」入口。
  列表卡片展示来源类型中文 + 证书编号 + 签发时间；「在线查看」走受控读取 Blob 预览、「下载」走受控端点带鉴权头；
  空态与失败重试均已实现。真实登录态渲染验收归 Task 4。
- 输入：Task 1 无关；`docs/api/certificate.openapi.json` 的 `my-certificates` 两个端点；
  `src/api/request.ts`；个人中心 `src/pages/profile/index.vue`。
- 输出：新增 `src/api/learner-certificate.ts`；新增证书列表页；个人中心新增「我的证书」入口；
  支持在线查看与下载原件。
- 前后端影响：仅移动端；无新接口。
- API 依赖：`GET /api/certificate/my-certificates`、`GET /api/certificate/my-certificates/{id}/file`。
- 验收标准：不手输人员主键（服务端按登录人收口）；来源类型以中文展示，不出现英文枚举码；
  无证书时展示空态；加载失败可重试；遵守窄屏与触控规范，不使用 Element Plus。
- 测试方式：先写失败的定向用例（入口存在、请求路径与载荷、空态分支），再最小实现；
  移动端 `vue-tsc --noEmit` 与改动文件 `eslint --max-warnings=0`；真实学员登录态渲染验收。

## Task 3：移动端「我的培训档案」

- Owner：移动端。
- 状态：已完成（2026-09-24；文案于 2026-09-28 调整）。新增 `src/pages/archive/index.vue`，按培训类型分组汇总
  （`trainingTypeName` 缺失兜底「类型名称待补全」，**不误称为未分类**）+ 任务项状态与结果 + 进入期次详情
  （复用 `pages/training/detail?trainingParticipantId=`）；个人中心新增「我的培训档案」入口。
  结果文案以 2026-09-28 `2026-09-28-improve-m4-certificate-and-mobile-archive` Task 3 为准：
  `PASSED→合格`、`FAILED→不合格`、`INCOMPLETE→结果待补全`、无结果→`结果待同步`，且**仅 `publishStatus=ENDED`
  才渲染结果 chip**（进行中不显示结果）。真实登录态渲染验收见 Task 4。
- 输入：Task 1 的 `trainingTypeName`；`GET /api/training/learner/tasks`；既有 `pages/training/detail`。
- 输出：新增档案页（按培训类型汇总数量 + 任务项状态与结果 + 进入期次详情）；个人中心新增入口。
- 前后端影响：仅移动端；无新接口。
- API 依赖：`GET /api/training/learner/tasks`（消费新增字段）。
- 验收标准：按培训类型分组并展示数量；类型名称缺失时兜底为「未分类」；结果中文与 PC 一致
  （合格 / 不合格 / 结果待补全 / 待同步）；点击任务进入该期详情且能查看课程考试记录；
  已结束培训只允许查看结果。
- 测试方式：先写失败的定向用例（分组逻辑、兜底、入口跳转参数），再最小实现；
  移动端 `vue-tsc --noEmit` 与改动文件 eslint；真实学员登录态渲染验收。

## Task 4：纵向验收与状态同步

- Owner：用户负责真实环境操作与完成标记；开发侧负责剩余自动化验证、问题修复与证据同步。
- 状态：已完成（2026-09-28，真实登录态纵向验收实跑）。
- 实测事实：
  - 环境：后端 20080（工作树代码 + vpn profile + 团队远程库）· uni H5 5174（临时 `vite.qa.config.ts` 代理 `/api`）·
    CDP 无头 Chrome 390×844。
  - 「个人中心 → 我的证书」：1 张 `TRAINING` 证书，卡片显示 `培训证书 / 测试培训计划 / 签发于 2026-09-24 /
    编号 CERT-893DC2DD7AFF477F`；在线查看预览层图片真实渲染 1400×990；点击「下载」捕获
    `GET /api/certificate/my-certificates/7/file`（`Bearer`）→ 200 / image/png。
  - 「个人中心 → 我的培训档案」：分组 `测试培训类型 / 共 4 期`，3 条 `合格`、1 条进行中不显示结果；
    点击首条进入 `pages/training/detail?trainingParticipantId=7`，页面提示「培训计划已结束，仅可查看结果」。
  - 本人收口：学员 `m4-learner-01` 证书与档案双空态；其读非本人证书文件 → 403 / 9416「只能查看本人证书」。
- 证据：`engineering/qa/2026-09-28-dual-m4-real-environment-acceptance.md`、
  `engineering/assets/2026-09-28-mobile-super-*.png`（证据已于 2026-09-28 清理）、`engineering/assets/2026-09-28-mobile-learner-*.png`（证据已于 2026-09-28 清理）。
- 未覆盖：移动端原生端（App/小程序 `openDocument` 分支）未真机验证；H5 下载未在带下载目录的浏览器核对落盘字节；
  分组仅 1 个培训类型（库内计划 `training_type_id` 全为 28）。
- 输入：可用的学员账号与含有已结束培训、已签发证书的学员数据；Task 1～3 交付。
- 输出：「我的证书」查看与下载证据、「我的培训档案」按类型统计与结果证据、期次详情跳转证据；
  QA、Retro、进度表与任务状态同步。
- 验收标准：真实学员登录态下完成「个人中心 → 我的证书 → 查看/下载」与
  「个人中心 → 我的培训档案 → 按期次进入详情」两条链路；确认只能看到本人数据；
  自动化与真实登录态证据分开记录。
- 测试方式：定向移动端回归、后端 Task 1 定向测试、真实浏览器与 Network 验证、`openspec validate --strict`、
  双轴 Review；环境未覆盖时不得标记闭环完成。
