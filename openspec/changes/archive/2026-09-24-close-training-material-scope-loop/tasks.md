# Tasks：培训资料范围收口与学员侧只读

> 状态：**已实施完成（Task 1～6 全部完成，2026-09-24）**。
> 2026-09-24 已确认六项业务口径与 `design.md` 末节三项默认值（历史 `REWARD_PENALTY` 资料降级为 `GENERAL`、
> 学员端只预览不下载、学员端单一资料区块不按阶段分组）。
> 证据：`engineering/qa/2026-09-24-close-training-material-scope-loop.md`、
> `engineering/retro/2026-09-24-close-training-material-scope-loop.md`。
> 一次只实施一个 Task；每个 Task 的自动化、真实登录态验收与文档证据均已完成。
> 约束：不新增权限、不新增业务表、不向学员开放下载、不引媒体播放器。

## Task 1：后端资料类型收口与既有数据迁移

- Owner：后端。
- 输入：`TrainingAttachmentServiceImpl.MATERIAL_TYPES`、`docs/api/training.openapi.json`、`docs/api/archive.openapi.json`、
  `backend/sts-service-admin-init/src/main/resources/schema/schema-training.sql:977`、最新迁移 `V20260924093000`。
- 输出：`MATERIAL_TYPES` 收敛为 `GENERAL/IMAGE/VIDEO/EVALUATION`；新增迁移
  `V20260924180000__narrow_training_material_type.sql` 把既有 `REWARD_PENALTY` 资料改写为 `GENERAL` 并同步列注释；
  `sts.init.schema-version` 与 IT 基线同步为 `20260924180000`；契约枚举与描述收窄。
- 前后端影响：后端校验与契约；前端枚举属 Task 4。
- API 依赖：`POST/PUT /api/training/attachments`（沿用，错误码 8234 语义不变）。
- 数据影响：1 条增量迁移（有损改写 `REWARD_PENALTY` → `GENERAL`，保留文件与记录）；无新表新字段；无权限/菜单种子变化。
- 验收标准：新增或编辑资料传入 `REWARD_PENALTY` 返回 400/8234；契约 `attachmentType` 枚举只剩 4 值；
  迁移后既有记录可正常回读、预览与编辑；`sts.init.schema-version` 与 IT 基线一致。
- 测试方式：`TrainingAttachmentServiceImplTest` / `TrainingExecutionControllerTest` 定向单测（Red → Green）；
  `TrainingAttachmentServiceIT` 真实 MySQL 覆盖类型收敛与迁移后回读；`FlywayMigrationIT` 覆盖迁移链路。

## Task 2：后端档案口径统一与奖惩佐证事实

- Owner：后端。
- 输入：`ArchiveServiceImpl.buildView`（:314-320 奖惩与资料查询）、`registerAssets`（:606-611）、
  `ArchiveViewVO.rewardPenalties[].evidenceFileId`（:480）、`ArchivePdfRenderer`。
- 输出：两处资料查询追加 `status = 1`；统计口径随查询自然收敛；`docs/api/archive.openapi.json` 同步
  「附件清单」描述与枚举，并把 `rewardPenalties[].evidenceFileId` 说明写实为「在线三视角可经受控端点预览，PDF 不含佐证明细」。
- 前后端影响：仅后端与契约；呈现层属 Task 4。
- API 依赖：`GET /api/archive/**` 三视角查询（字段不变，仅事实收敛为启用资料）。
- 数据影响：无 DDL；档案快照哈希输入不含资料，历史归档版本不受影响；新归档只登记启用资料。
- 验收标准：停用资料不再出现在三视角的影像记录/附件清单，也不计入 `imageCount`/`attachmentCount`/`materialCount`；
  启用资料行为不变；归档文件登记的资料集合与在线三视角一致；PDF 仍不内嵌佐证明细。
- 测试方式：`ArchiveServiceIT` 增加「停用资料不进档案/统计」「启用资料保持」两组断言（先在当前实现上 Red）；
  `ArchivePdfRenderer` 既有测试保持全绿。

## Task 3：后端学员侧只读端点

- Owner：后端。
- 输入：`TrainingWorkflowController:135`（既有学员详情端点）、`TrainingWorkflowServiceImpl:329`（本人收口与可见性校验）、
  `TrainingAttachmentServiceImpl`、`TrainingLearnerParticipantDetailVO`。
- 输出：新增 `GET /api/training/learner/participants/{trainingParticipantId}/materials` 与
  `.../materials/{trainingAttachmentId}/file`；新增 `TrainingLearnerMaterialVO`
  （`trainingAttachmentId`/`attachmentName`/`description`/`planStageId`/`stageName`/`sortOrder`/`hasFile`，**不含 `fileInfoId`**）；
  服务层复用 `requireParticipant` 与 `requireLearnerVisiblePlan`；`docs/api/training.openapi.json` 补两个端点。
- 前后端影响：后端与契约；前端消费属 Task 4/5。
- API 依赖：新增 2 个端点，权限沿用 `training:learner:task`（**不新增权限、不向学员发放 `training:attachment:list`**）。
- 数据影响：无。
- 验收标准：只返回本人参训计划下 `GENERAL` 且 `status = 1` 的资料；`IMAGE/VIDEO/EVALUATION` 与停用资料一律不返回；
  他人 `trainingParticipantId` → 404/8155；跨计划资料 ID → 404/8233；**权限模型零变更**（学员角色未获任何新权限）。
- 测试方式：`TrainingWorkflowControllerTest` 补端点契约用例；`TrainingWorkflowServiceIT` 覆盖本人/他人、
  类型过滤、停用过滤与跨计划拒绝（真实 MySQL）。

## Task 4：PC 前端（工作台类型、档案佐证列、门户培训资料）

- Owner：PC 前端。
- 输入：Task 1～3 契约、`ArchiveFactsSections.vue`、`PlanProcessResultsPanel.vue`、`constants/enum-options.ts`、
  `api/training-process.ts`、`views/portal/training/detail.vue`、`AssetViewer.vue`。
- 输出：
  1. `materialTypeOptions` / `TrainingAttachmentType` / `TRAINING_ATTACHMENT_TYPE_OPTIONS` 去掉 `REWARD_PENALTY`；
  2. `ArchiveFactsSections.vue` 奖惩表新增「佐证」列，按 `training:reward:list` 门控并复用
     `getTrainingRewardEvidence` + `AssetViewer`；影像/附件预览仍独立按 `training:attachment:list` 门控；
  3. `views/portal/training/detail.vue` 新增「培训资料」区块，消费学员侧列表端点并支持在线预览，**无下载入口**；
  4. `api/training-learner.ts`（PC 侧同名调用）补学员资料列表与文件读取方法。
- 前后端影响：仅 PC。
- API 依赖：Task 1 枚举、Task 2 档案事实、Task 3 学员端点、既有 `training:reward:list` 佐证端点。
- 数据影响：无。
- 验收标准：工作台资料类型下拉只有 4 项；档案奖惩表在具备 `training:reward:list` 时可预览佐证、缺权限显示「—」；
  门户培训详情能看到一般资料并预览，看不到下载按钮；停用资料在档案与门户均不出现。
- 测试方式：`vue-tsc --noEmit` + 改动文件 eslint `--max-warnings=0`；真实登录态浏览器验收归 Task 6。

## Task 5：移动端培训详情培训资料区块

- Owner：移动端前端。
- 输入：Task 3 契约、`mobile-app/src/pages/training/detail.vue`、`api/training-learner.ts`、页面路由 `pages.json`。
- 输出：`api/training-learner.ts` 补学员资料列表与文件读取方法；培训详情新增「培训资料」区块，
  展示名称/关联阶段并支持在线预览，空列表不渲染区块，窄屏单列；**无下载入口**。
- 前后端影响：仅移动端。
- API 依赖：Task 3 的学生端点。
- 数据影响：无。
- 验收标准：学员在移动端能看到本人计划下的一般资料并预览；停用资料与他类资料不出现；
  不引新播放器、不改变既有内容卡片结构。
- 测试方式：`uni.js build -p h5` + `vue-tsc --noEmit`；真实渲染验证按 `sts-zero-new-failure-verification`
  的 CDP 直连姿势执行（无 playwright 时）。

## Task 6：纵向验收与状态同步

- Owner：用户负责真实环境操作与完成标记；开发侧负责自动化验证、问题修复与证据同步。
- 输入：运行中的培训计划、管理员与学员账号（PC 门户 + 移动端）、Task 1～5 交付。
- 输出：真实登录态下三条业务路径的纵向证据；QA、Retro、进度表与任务状态同步。
- 前后端影响：仅验证与文档。
- 验收标准：真实管理员完成「上传资料 → 停用 → 档案不含 → 改回启用 → 档案含」；真实管理员在档案预览奖惩佐证；
  真实学员在 PC 门户与移动端看到一般资料并预览、看不到下载与停用资料；
  「奖惩佐证」类型在资料类型下拉中不可选。
- 测试方式：起后端 20080（先 `unset *_proxy`）+ 临时 vite config 代理 `/api` + CDP 无头 Chrome 取 DOM 事实与截图；
  服务端拒绝项以真实 HTTP 状态码与业务码为准；环境不可用时如实记为「环境阻断」，不得标记闭环完成。

## 完成记录（2026-09-24）

| Task | 状态 | 关键证据 |
|---|---|---|
| Task 1 资料类型收口与迁移 | ✅ 完成 | `MATERIAL_TYPES` 收敛为 4 类；`V20260924180000` + `sts.init.schema-version` → `20260924180000`；新增/编辑 `REWARD_PENALTY` → **400/8234**（真实 HTTP）；`TrainingAttachmentServiceImplTest` 10/10、`TrainingAttachmentServiceIT` 5/5 |
| Task 2 档案口径统一与奖惩佐证事实 | ✅ 完成 | 档案三视角与归档素材登记统一 `status = 1`，统计同源收敛；契约同步佐证可预览说明；`ArchiveServiceImplTest` 12/12、`ArchiveControllerTest` 4/4、`ArchiveServiceIT` 6/6、`FlywayMigrationIT` 9/9 |
| Task 3 学员侧只读端点 | ✅ 完成 | 新增 2 个端点 + `TrainingLearnerMaterialVO`（**不含 `fileInfoId`**）；`TrainingWorkflowControllerTest` 8/8、`TrainingWorkflowServiceImplTest` 32/32、`TrainingWorkflowServiceIT` 3/3；真实 HTTP 13 组用例逐条命中（含 md5 逐字节一致、他人/不存在参训统一 8155） |
| Task 4 PC 前端 | ✅ 完成 | 工作台类型下拉只剩 4 项（DOM 实测 `["一般资料","图片","视频","评价佐证"]`，`hasRewardPenalty: false`）；档案奖惩「佐证」列 + 预览弹窗；门户「培训资料」区块（`hasDownloadButton: false`）；PC `vue-tsc` 退出码 0、定向 ESLint 0 警告、静态契约 12/12 |
| Task 5 移动端 | ✅ 完成 | 「培训资料」区块 + 按字节头判定格式的预览（PDF canvas / 图片 blob）；`vue-tsc` 退出码 0、`src` 定向 ESLint 0 警告、H5 build `DONE`、静态契约 6/6；真实渲染 `hasDownloadWord: false` |
| Task 6 纵向验收与状态同步 | ✅ 完成 | **管理端 UI 全链路 9 步**（上传 → 档案含 → 停用 → 档案不含 → 启用 → 档案含 → 删除）；学员端停用态页面补证（停用资料不出现 + 全不可见时不渲染区块）；QA `engineering/qa/2026-09-24-close-training-material-scope-loop.md`、Retro `engineering/retro/2026-09-24-close-training-material-scope-loop.md`；进度表与项目记忆已同步 |

**四项人工决策（2026-09-24 文祥确认，已按默认值实施）**：① 历史 `REWARD_PENALTY` 资料**接受**有损降级为 `GENERAL`；
② 学员端**只预览不下载**；③ 学员端**单一「培训资料」区块**（展示关联阶段），不按阶段重组；
④ 档案 PDF **不内嵌佐证明细**（延续既有权限收口）。

**未覆盖项（如实记录，非阻塞）**：移动端原生分支未真机验证；跨租户拒绝仅有 IT 证据（环境为单租户 `DEMO`）；
停用态下的文件读取 404 仅接口层证据；管理端档案的「下载」按钮**刻意保留**（只约束学员端）。

**闭环判定**：Task 1～6 全部完成，四项验收标准逐条有真实登录态证据；本 Change 可归档。
归档前需确认是否合并 delta 进 `openspec/specs/`（`training-material-scope`、`learner-material-reading` 为新增能力，
`m4-minimum-closure` 为 MODIFIED）——属规格基线变更，**需人工确认后执行**。
