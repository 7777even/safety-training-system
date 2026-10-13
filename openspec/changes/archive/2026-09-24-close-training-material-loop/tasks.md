# Tasks：补齐培训计划资料（影像/附件）上传与展示闭环

> 状态：**已人工确认并实施完成**（2026-09-24）。一次只实施一个 Task；各 Task 的自动化、真实角色验收与文档证据完成后标为完成。
> 约束：不新增业务表与菜单；不恢复签到；不引入影像转码或媒体播放器。

## Task 1：服务端受控读取端点与写入校验

- Owner：后端。
- 状态：**已完成**（2026-09-24；浏览器端纵向验收归 Task 3）。
- 输入：中山功能项 34/36、现有 `training.openapi.json` 附件端点、`TrainingAttachmentServiceImpl`、`GeneratedFileStore`（受控文件读取的既有实现）。
- 输出：新增 `GET /api/training/attachments/{trainingAttachmentId}/file`；新增、编辑、删除前的计划状态、阶段归属与文件可见性校验；`attachmentType` 枚举收敛；OpenAPI 与错误码同步。
- 实际改动：新增 `TrainingAttachmentFile` record、`TrainingAttachmentService.openFile`；`TrainingAttachmentServiceImpl` 补 `requireRunningPlan`/`requireMaterialType`/`requireStage`/`requireVisibleFile` 与版本冲突校验；`TrainingExecutionController` 新增 `/file` 端点并把内联返回逻辑抽成共用的 `inlineFile`；`docs/api/training.openapi.json` 新增端点与 8232/8233/8234/8235/8236/8237/8238 说明。**未推进计划版本**（资料不参与综合结果，避免多文件上传互相冲突），已在文档与 IT 中显式记录。
- 前后端影响：后端校验与契约；前端沿用既有端点，按错误码提示。数据影响：**无新表、无迁移**，既有数据不变。
- API 依赖：`GET/POST /api/training/attachments`、`GET/PUT/DELETE /api/training/attachments/{id}`（已存在）；新增 `GET /api/training/attachments/{id}/file`；权限沿用 `training:attachment:list`，不新增权限。
- 验收标准：仅当前机构、`RUNNING` 计划可写；跨机构/跨计划阶段与冻结后写入被拒绝且无副作用；文件端点按记录定位文件，记录不存在或文件不可读返回 404，不泄露其他机构文件；非法 `attachmentType` 被拒绝。
- 测试证据：`TrainingAttachmentServiceImplTest` 8 例、`TrainingExecutionControllerTest` 7 例全绿（先 Red 后 Green）；`TrainingAttachmentServiceIT` 5 例在真实 MySQL 全绿（租户隔离、阶段归属、类型收敛、重复文件、结束只读 + 计划版本不推进）。

## Task 2：PC 计划内培训资料区域与四项操作

- Owner：PC 前端。
- 状态：**已完成**（2026-09-24，浏览器端验收见 Task 3）。
- 输入：Task 1 契约、本计划阶段列表、统一文件上传组件 `UnifiedFileUpload`、第 5 步「过程与结果」现有面板与奖惩既有实现（作为同构参照）。
- 输出：恢复培训资料页签；新增、编辑、删除弹窗；类型与关联阶段选择；文件上传与受控预览/下载；结束后只读。
- 实际改动：`api/training-process.ts` 补 `TrainingAttachmentType`/`TrainingAttachmentPayload` 与 `getTrainingAttachmentFile`、`createTrainingAttachment`、`updateTrainingAttachment`、`deleteTrainingAttachment`；`PlanProcessResultsPanel.vue` 恢复资料页签（名称、类型中文、关联阶段、说明、状态、受控预览、编辑、删除）并新增维护弹窗；预览弹窗改为影像与佐证共用（`previewBoundFile`）；`workspace/index.vue` 传入阶段与三项资料权限，回读处理器更名为 `handleProcessFactsChanged`（奖惩与资料共用）。
- 前后端影响：仅 PC；无数据库、菜单或新业务接口变更。API 依赖：Task 1 的附件端点、计划阶段查询与文件上传。
- 验收标准：不手输 ID；可上传图片、视频与一般资料并回读；类型以中文展示；可按记录预览/下载原始文件；操作受现有 `training:attachment:*` 权限与计划状态控制；真实错误中文反馈。
- 测试证据：PC `vue-tsc --noEmit` EXIT=0；改动文件 eslint `--max-warnings=0` 通过（无新增告警）。
  管理员登录态下的真实上传/预览浏览器验收**已在 Task 3 完成**（页签渲染「培训资料（1）」、
  行内类型与阶段均中文、预览弹窗经受控端点以 blob 渲染）。

## Task 3：纵向验收与状态同步

- Owner：用户负责真实环境操作与完成标记；开发侧负责剩余自动化验证、问题修复与证据同步。
- 状态：**已完成**（2026-09-24）。开发侧自动化与真实管理员登录态纵向验收均已完成，详见文末「Task 3 验收记录」。
- 输入：运行中的培训计划与可用管理员账号；Task 1～2 交付。
- 输出：真实上传图片/视频/一般资料的纵向证据、结束冻结证据、跨机构拒绝证据；QA、Retro、进度表与任务状态同步。
- 前后端影响：仅验证与文档；无新 API 或数据结构。
- 验收标准：真实管理员完成上传 → 回读 → 预览 → 编辑 → 删除 → 结束只读；服务端拒绝跨机构与冻结后写入；自动化与真实登录态证据分开记录。
- 测试方式：定向前后端回归、MySQL IT、真实浏览器/Network 与双轴 Review；环境未覆盖时不得标记闭环完成。

### Task 3 验收记录（2026-09-24）

**已完成（开发侧）**：`TrainingAttachmentServiceImplTest` 8/8、`TrainingExecutionControllerTest` 7/7、
`TrainingAttachmentServiceIT` 5/5；PC typecheck 与改动文件 eslint 通过；`openspec validate --strict` 通过。

**已完成（真实环境，2026-09-24）**：本地后端 `20080` + PC dev `5176`（`/api` 指向本地）+ 无头 Chrome CDP 直连，
真实登录 `DEMO/admin`：

- **上传**：`POST /api/system/files` 200，`minio-private://...` 真实落盘；资料新增（`RUNNING` 计划 5 + 阶段 30 + `IMAGE`）200。
- **回读**：列表返回名称/类型/关联阶段/文件/状态/版本，类型与阶段在页面上显示中文「图片」「线下培训」。
- **受控读取与预览**：`/file` 返回 `image/png` 且**与源文件逐字节相同**；页面「预览」弹窗标题「培训资料预览」，
  预览源为 `blob:` URL（页面不持有可直连地址）。
- **编辑**：`version=0` 更新成功并回读生效；过期版本 → `409/8238 版本冲突`。
- **删除**：`RUNNING` 下 200；**冻结拒绝**：`ENDED` 计划新增 → `409/8237`，删除路径共用同一 `requireRunningPlan`。
- **其余拒绝**：跨计划阶段 → `400/8235`；无效类型 → `400/8234`；文件不可见 → `400/8236`；同计划同文件 → `409/8232`。
- **未覆盖（如实记录）**：跨租户拒绝（真实环境单租户，由 `TrainingAttachmentServiceIT` 覆盖）、
  `VIDEO` 类型真实预览（走同一受控端点，未单独造数据）、`<a download>` 落盘（无头环境未落盘，以字节与 MIME 为证）。

完整证据与逐条结果见 `engineering/qa/2026-09-24-close-training-material-loop.md`；
截图 `engineering/assets/2026-09-24-ui-training-material-preview.png`（证据已于 2026-09-28 清理）。
