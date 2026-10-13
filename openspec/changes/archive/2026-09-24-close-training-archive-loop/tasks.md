# Tasks：补齐培训档案事实与可交付档案文件闭环

> 状态：**已人工确认并实施完成**（2026-09-24，Task 1～5 全部完成，含真实登录态纵向验收）。一次只实施一个 Task；各 Task 的自动化、真实角色验收与文档证据完成后再标完成。
> 约束：不新增业务表与菜单、不做数据库迁移、不引入新依赖或字体资源、不做 ZIP 档案袋、不重开签到采集、不改证书签发规则。

## Task 1：档案事实组装与契约扩展

- Owner：后端。
- 状态：**已完成**（2026-09-24，真实 HTTP 证据见文末「Task 5 验收记录」）。
- 输入：功能项 44 三个子项原文、`openspec/specs/m4-minimum-closure/spec.md` 档案需求、现有 `ArchiveViewVO`、`docs/api/archive.openapi.json` 1.2.1、`sts_training_attachment` / `sts_training_plan_content` / `sts_training_reward_penalty` / `sts_certificate_certificate`。
- 输出：`ArchiveViewVO` 扩展为完整事实结构（计划、企业、培训内容、参训人员、签到情况、学员成绩、综合评价、奖惩、影像记录、附件、证书、数据统计）；`buildView` 一次性聚合、批量化用户/机构/类型查询；签到情况按线下成绩映射（`SIGNED`/`NOT_RECORDED`/`NOT_APPLICABLE`）；统计派生字段；证书清单按 `certificate:cert:list` 门控（无权限时 MUST NOT 返回该字段）；`archive.openapi.json` 同步（含中文字段 description、example、错误码、`x-permission`）。
- 前后端影响：后端契约与查询；前端 Task 4 必须同步 `ArchiveView` 类型。数据影响：**无新表、无迁移**。
- API 依赖：`GET /api/archive/trainings/{trainingPlanId}`、`GET /api/archive/enterprises`、`GET /api/archive/persons/{userId}`（均已存在，仅响应结构扩展）；权限沿用 `archive:training:list`，不新增权限。
- 验收标准：三视角返回同一份事实且字段一致；签到情况随线下成绩录入变化，且 `missingItems` **不含**签到相关项；计划无线下内容时签到为 `NOT_APPLICABLE`；统计口径与 `TrainingWorkflowServiceImpl` 的 `PASSED/FAILED/INCOMPLETE` 判定一致，分母为 0 的比率返回 `null`；无 N+1 逐条查询。
- 测试方式：先写失败测试（Red）覆盖事实字段、签到映射三态、统计口径与三视角一致性，再最小实现（Green）；涉及真实 SQL 聚合的路径补 MySQL IT；执行受影响模块定向编译与测试、`openspec validate --strict`、双轴 Review。

### Task 1 实施记录（2026-09-24）

- 已改动：
  - `archive/vo/ArchiveViewVO.java`：新增 `contents / attendance / evaluations / materials / certificates / statistics / archiveFileIsPdf`；`certificates` 加字段级 `@JsonInclude(NON_NULL)` 做权限门控（全局无 `NON_NULL` 配置，已核对）。
  - `archive/service/ArchiveService.java`：三视角查询签名加 `boolean includeCertificateFacts`。
  - `archive/service/ArchiveServiceImpl.java`：`buildView` 一次聚合（计划/企业/类型/内容/阶段/参训人/线下成绩/奖惩/资料），`loadUsers` 改 `selectBatchIds` 消除逐人 `selectById`；签到三态映射；统计派生；`currentVersionFileInfoId` + `isPdfFile` 回填 `archiveFileIsPdf`。
  - `archive/service/ArchiveCertificateGuard.java`（新建）：`canListCertificates()` 封装 `StpUtil.hasPermission("certificate:cert:list")`，未登录按无权限处理。
  - `archive/controller/ArchiveController.java`：三视角查询传入证书权限判定。
  - `docs/api/archive.openapi.json`：1.2.1 → 1.3.0；`ArchiveViewVO` 扩为 22 个顶层属性并补齐中文字段说明与 example；两个 `/file` 响应改 `application/pdf`；企业档案补 4 个时间筛选参数。
- 实施中发现并已纠偏的两点（已写回 `design.md`）：
  - `sts_training_plan_content` **没有** `training_plan_id`，内容清单必须"先取阶段 → 再按 `plan_stage_id IN (...)` 取内容"。
  - 奖惩清单只取 `status = 1`，与 `TrainingEndedResultVO` 正式结果口径一致；停用记录仍可在过程记录页看到，属刻意差异。
- 验证证据：
  - 单测 `ArchiveServiceImplTest`(10) + `ArchiveControllerTest`(4) + `ArchiveCertificateGuardTest`(3) = **17 例全绿**。
  - MySQL IT `ArchiveServiceIT`(4) + `ArchiveMapperIT`(1) + `ArchiveTenantBoundaryIT`(4) = **9 例全绿**（Testcontainers MySQL 8.0.45，`TESTCONTAINERS_RYUK_DISABLED=true`）。新增 `threeViewsShareSameFacts` 用例断言一期/企业/个人三视角 `statistics`、`attendance`、`contents` 逐字段相等，以及签到 `SIGNED`/`NOT_RECORDED` 与 `signedCount`/`notRecordedCount`。
  - `openspec validate 2026-09-24-close-training-archive-loop --strict` 通过。
- 已补：真实 HTTP 三视角事实与归档链路由 Task 5 实测；前端 `ArchiveView` 类型与三视角页面已完成（Task 4）；PDF 正本与 `archiveFileIsPdf=true` 已实测。**唯一未覆盖**：证书清单「无权」分支需第二种权限身份，改共享库权限需人工确认，由单测锁定。

## Task 2：归档快照写实与资料资产接线

- Owner：后端。
- 状态：**已完成**（2026-09-24，真实 HTTP 证据见文末「Task 5 验收记录」）。
- 输入：Task 1 的事实结构、`ArchiveServiceImpl.createFromPlan`、`sts_archive_asset`（`uk_version_file` 唯一键）、`sts_archive_version`。
- 输出：`reward_penalty_snapshot`、`certificate_snapshot` 写真实事实；归档时把培训资料登记为 `sts_archive_asset`（`source_attachment_id`、`asset_type`、`file_info_id`、`file_name_snapshot`、`sort_order`）；`snapshot_hash` 输入纳入参训人事实快照。
- 前后端影响：仅后端归档写入；对前端无新增接口。数据影响：**无新表、无迁移**，复用既有列。
- API 依赖：`POST /api/archive/trainings/create-from-plan`（已存在，行为修正）；权限沿用 `archive:training:create`。
- 验收标准：无 `"{}"` 空快照写入；同一文件重复引用不重复登记（唯一键生效）；重复更新档案版本号递增且快照哈希随事实变化；结果不完整时仍拒绝归档且无残留写入（事务回滚）。
- 测试方式：先写失败测试断言快照内容与资产行（Red），再最小实现（Green）；补 MySQL IT 验证唯一键与事务回滚；定向测试 + Review。

### Task 2 实施记录（2026-09-24）

- 已改动：
  - `ArchiveServiceImpl`：新增 `ArchiveAssetMapper` 依赖；`createFromPlan` 改为「先算哈希 → 写版本 → 写参训人 → 登记素材 → 生成文件」；
    `registerAssets` 按受控文件先去重再逐条写 `sts_archive_asset`；`participantHashInput` 把六类参训人快照拼进哈希输入；
    参训人查询加 `orderByAsc(trainingParticipantId)` 保证哈希不抖动。
  - 快照写实：`member_snapshot` 扩为 `userId/userName/userCode`（取自档案事实，不再只有 `userId`）；
    `learning/exam/evaluation` 快照改用可空显式 map（**原实现用 `Map.of`，分数为 null 时必然 NPE**，属顺带修掉的既有缺陷）；
    奖惩/证书快照按参训人各自事实落库，无事实写 `[]` 而非 `{}`。
- 实施中发现并已纠偏（已写回 `design.md`）：
  - **`sts_training_attachment` 有 `uk_plan_file_deleted(plan_id, file_info_id, deleted)`**：同一计划下同一文件**本来就无法**被两条资料引用，
    所以"同文件去重"在真实数据里造不出来；该分支由单测（mock 造同 `fileInfoId` 的两条资料）覆盖，IT 只验证真实登记。
  - `sts_archive_asset.asset_type` 的列声明域是 `NOTICE/ATTENDANCE/IMAGE/VIDEO/SUMMARY/OTHER`，故 `IMAGE`/`VIDEO` 原样写、
    其余收敛为 `OTHER`；不改 DDL 注释（改注释需迁移），本 Change 仍**无迁移**。
- 验证证据：
  - 单测新增 2 例（`shouldWriteRealParticipantSnapshotsAndDeduplicateAssets`、`shouldChangeSnapshotHashWhenParticipantFactsChange`），
    合计 `ArchiveServiceImplTest`(12) + `ArchiveControllerTest`(4) + `ArchiveCertificateGuardTest`(3) = **19 例全绿**。
  - MySQL IT 新增 1 例（`createFromPlan_writesRealSnapshotsAndAssets`），合计 `ArchiveServiceIT`(5) + `ArchiveMapperIT`(1) + `ArchiveTenantBoundaryIT`(4) = **10 例全绿**：
    断言两次不同文件各登记一条素材、`IMAGE`/`OTHER` 映射、奖惩快照非空、证书快照 `[]`、事实变化后版本号 +1 且哈希变化。
- 已补：真实 HTTP 端到端与影像/附件受控读取已实测（Task 5；页面预览见 material-loop QA）。

## Task 3：档案文件改为 PDF 正本

- Owner：后端。
- 状态：**已完成**（2026-09-24，真实 HTTP 证据见文末「Task 5 验收记录」）。
- 输入：Task 1 事实结构、`GeneratedFileStore`、证书既有 AWT 渲染实现（`CertificateImageRenderer`）、依赖中的 PDFBox 3.0.5、`docs/api/archive.openapi.json`。
- 输出：新增 `ArchivePdfRenderer`（AWT 绘页 + PDFBox 装载输出 `application/pdf`）；`createFromPlan` 改为生成 PDF 并持久化受控 `fileInfoId`；`openPersonFile` 改为即时组装 PDF；契约两个 `/file` 响应改为 `application/pdf`，新增错误码 **9211**（档案文件生成失败）。
- 前后端影响：后端文件格式与契约；前端下载后缀与文件格式展示（Task 4）。数据影响：**无新表、无迁移**，旧版本 JSON 文件不迁移。
- API 依赖：`GET /api/archive/trainings/{trainingPlanId}/file`、`GET /api/archive/persons/{userId}/file`（已存在，响应类型变更）；权限不变。
- 验收标准：真实下载得到 `application/pdf`、文件名 `.pdf`、可打开且中文正常显示；PDF 内含封面、统计、内容清单、成绩表（含签到情况）、综合评价、奖惩、影像与附件清单、证书清单；**不内嵌影像/附件原件**；缺少必需事实时 409/9204 且不生成占位文件；生成失败返回 9211 而非空文件。
- 测试方式：先写失败测试断言 `content-type`、文件名后缀与 PDF 魔数（`%PDF-`）及必需章节（Red），再最小实现（Green）；真实 HTTP 下载后抽页校验；定向测试 + Review。

### Task 3 实施记录（2026-09-24）

- 已改动：
  - `archive/service/ArchivePdfRenderer.java`（新建）：`render(ArchiveViewVO)` 渲一期一档、`renderPerson(ArchivePersonVO)` 渲个人档案；
    AWT 逻辑字体（`Font.SANS_SERIF`）绘页位图 → PDFBox 3.0.5 `LosslessFactory.createFromImage` + `PDPageContentStream.drawImage` 装入 PDF。
    **零新依赖、零新字体资源**（仓库无字体资源，PDFBox 此前只用于读）。
    节点顺序：封面 → 数据统计 → 培训内容 → 参训人员成绩（含签到情况列）→ 综合评价 → 奖惩 → 影像记录 → 附件 → 证书清单。
    内部类 `PageWriter` 提供 `title/section/line/gap/header/row`，`reserve()` 超页高自动新开页；单元格 `ellipsize` 按列宽截断、正文 `wrap` 逐字折行。
    `toPdf` 失败统一抛 `BusinessException(500, 9211, "档案文件生成失败")`。
  - `archive/service/ArchiveServiceImpl.java`：`createFromPlan` 改为 `generatedFileStore.store(archivePdfRenderer.render(view), "archive-<planCode>.pdf", "archive", "application/pdf", "pdf")`；
    `openPersonFile` 改为即时 `archivePdfRenderer.renderPerson(person)`，文件名 `archive-person-<userCode>.pdf`、`application/pdf`。
  - `archive/vo/ArchiveViewVO.java`：`archiveFileIsPdf` 回填，供页面与 PDF 自身标注历史 JSON 档案。
  - `docs/api/archive.openapi.json`：两个 `/file` 响应 `application/json` → `application/pdf`，example 改 `%PDF-1.7`（Task 1 已随契约 1.3.0 落地）。
  - `design.md`：补"PDF 页面尺寸 vs 绘制栅格"与"自动分页/单元格截断"两节，遗留风险新增"依赖系统 CJK 字体"。
- 实施中发现并已纠偏（已写回 `design.md`）：
  - **把 A4@150dpi 的像素数（1240×1754）当点数传给 `new PDRectangle(...)`**，产出 17×24 英寸海报尺寸页面。
    修正为 `new PDPage(PDRectangle.A4)` + `drawImage(img, 0, 0, A4.width, A4.height)`：**页面尺寸取点（595.28×841.89），绘制栅格取像素**，两者必须分开；
    `ArchivePdfRendererTest` 断言改为 `PDRectangle.A4.getWidth()/getHeight()`。
- 验证证据：
  - 单测新增 `ArchivePdfRendererTest`(4)，合计 `ArchivePdfRendererTest`(4) + `ArchiveServiceImplTest`(12) + `ArchiveControllerTest`(4) + `ArchiveCertificateGuardTest`(3) = **23 例全绿**（重跑 `Tests run: 23, Failures: 0, Errors: 0`）。
    断言 PDF 魔数 `%PDF-`、页数为 1、页面尺寸等于 `PDRectangle.A4`、160 人时分页 > 1 页、个人档案可打开、空记录仍产出 >1000 字节。
  - MySQL IT **10 例全绿**（`ArchiveServiceIT`(5) + `ArchiveMapperIT`(1) + `ArchiveTenantBoundaryIT`(4)）：`generatedFileStore.store` 收到以 `%PDF-` 开头的字节。
  - 样例视觉验收：`engineering/assets/2026-09-24-archive-pdf-sample.pdf`（证据已于 2026-09-28 清理）（+ `-page1.png` / `-page2.png`）——中文清晰、表格对齐、封面/统计/内容/成绩（含签到列）/综合评价/奖惩/影像/附件与证书清单齐全，长内容自动分页至第 2 页。
- 已补：真实 HTTP 下载校验 `content-type`（`application/pdf`）、文件名后缀（`.pdf`）与魔数（`%PDF-1.6`）均通过（Task 5）。
  （2026-09-24 环境恢复后 Task 5 已完成真实 HTTP 与浏览器验收：plan 2 首次归档产出 PDF 正本、
  plan 4 历史 JSON 重归档为 PDF 且版本 `1 → 3`。早前记录的「`19.191.192.146` 全端口不可达导致验收无法执行」
  已作废。）前端下载后缀与文件格式展示已在 Task 4 完成。
- **已收口（2026-09-24，同轮修复）**：原遗留「一期档案 PDF 持久化后含证书编号与持证人 ID，而 `/file` 下载只校验
  `archive:training:list`」的权限旁路，改为**PDF 不再承载证书明细**——证书区只呈现数量
  （`ArchivePdfRenderer.certificateSummary`），证书编号/持证人/来源 ID 一律不写入文件；在线三视角的证书清单
  仍按 `certificate:cert:list` 门控。已同步 `spec-delta.md`、`design.md`（第五节与第八节），并新增 2 例测试锁定
  （`ArchivePdfRendererTest` 共 6 例）。

## Task 4：PC 档案页展示与操作补全

- Owner：PC 前端。
- 状态：**已完成**（2026-09-24，真实登录态证据见文末「Task 5 验收记录」）。
- 输入：Task 1～3 契约与文件端点、`PlanArchivePanel.vue`、`result/archive/index.vue`、`api/m4-outcome.ts`、企业档案既有查询参数。
- 输出：`ArchiveView` 类型按扩展契约同步；一期档案详情补统计、培训内容、成绩表（含签到情况）、综合评价、奖惩、影像记录、附件（受控预览/下载）与证书清单；个人档案详情页 + 证书查看/下载 + PDF 导出；企业档案补齐培训类型与起止时间筛选、每行"下载档案文件"；文件格式展示由 `JSON` 改为 `PDF`；**历史 JSON 档案标注"需重新更新档案以获得 PDF"**。
- 前后端影响：仅 PC；无数据库、菜单或新业务接口变更。API 依赖：Task 1 三视角查询、Task 3 档案文件端点、Change A 的资料受控文件端点、既有证书文件端点、文件上传能力。
- 验收标准：不手输全局主键；三视角均能看到完整事实且与后端一致；一期与个人档案下载为 PDF；影像与附件只出清单，原件经受控端点预览/下载；无 `certificate:cert:list` 权限时不展示证书下载入口；操作受既有权限与计划状态控制。
- 测试方式：先写失败的定向用例（可见事实与请求载荷），再最小实现；执行受影响 lint、PC `vue-tsc --noEmit`；真实管理员登录态操作验证。

### Task 4 实施记录（2026-09-24）

- 已改动：
  - `api/m4-outcome.ts`：`ArchiveView` / `ArchivePerson` 按契约 1.3.0 同步（22 个顶层属性 + `plan` / `organization` / `contents` / `participants` / `attendance` / `scores` / `evaluations` / `rewardPenalties` / `materials` / `certificates` / `statistics` / `archiveFileIsPdf`）。
  - `libs/controlled-file.ts`（新建）：受控文件「记录名 + MIME → 预览文件名」的唯一实现，供档案事实块与计划过程结果面板共用，避免两处各写一张 MIME 后缀表。
  - `constants/enum-options.ts`：新增 `TRAINING_CONTENT_TYPE_OPTIONS` / `TRAINING_RESULT_STATUS_OPTIONS` / `TRAINING_ATTACHMENT_TYPE_OPTIONS` / `TRAINING_REWARD_RECORD_TYPE_OPTIONS` / `ARCHIVE_SIGN_IN_STATUS_OPTIONS`；结果状态中文与培训成果页统一为 **合格 / 不合格 / 结果不完整**（不是"通过/未通过"）。
  - `result/archive/components/ArchiveFactsSections.vue`（新建）：数据统计、培训内容、参训人员成绩（含签到情况列）、综合评价、奖惩记录、影像记录、附件清单、证书清单共 8 区块；影像/附件走受控端点预览与下载，证书用独立表格组件；无 `certificates` 字段时整块隐藏。
  - `result/archive/components/ArchiveCertificateTable.vue`（新建）：证书清单 + 查看/下载，供事实块与个人档案「本人证书」卡共用。
  - `result/archive/components/PlanArchivePanel.vue`：接入事实区块；「文件格式」由 `JSON` 改为按 `archiveFileIsPdf` 显示，历史 JSON 档案显式 `ElAlert` 提示"需重新更新档案以获得 PDF 正本"。
  - `result/archive/index.vue`：企业档案补计划名称 / 计划编号 / 培训类型 / 计划开始区间 / 计划结束区间筛选与行内「查看一期档案 / 下载档案」；个人档案补培训行展开（事实区块）与「本人证书」卡（按证书查询权限门控）；三视角下载后缀统一 `.pdf`。
  - `PlanProcessResultsPanel.vue`：原内联 `boundFileName` 抽到共用工具，行为不变。
- 实施中发现并已纠偏：
  - 后端 `scores` 事实**不含 `userCode`**，工号必须由 `participants` 关联；前端成绩表与 PDF 渲染器同步按此补齐，避免工号列整列空。
  - 结果状态中文曾按"通过/未通过"落笔，与仓库既有 `result/training-result` 与门户口径（合格/不合格）冲突，已统一。
- 验证证据：
  - PC `vue-tsc --noEmit` 退出码 `0`。
  - 改动 9 个文件 eslint `--max-warnings=0` 退出码 `0`（`archive/index.vue`、`PlanArchivePanel.vue`、`ArchiveFactsSections.vue`、`ArchiveCertificateTable.vue`、`api/m4-outcome.ts`、`constants/enum-options.ts`、`libs/controlled-file.ts`、`PlanProcessResultsPanel.vue`、`api/training-process.ts`）。
  - 前端定向测试已同步断言（`m4-minimum-ui-closure` 由 `.json` 改判 PDF 与"需重新更新档案"提示）：受影响文件集合内**零新增失败**，用 HEAD 基线逐条对照证明；剩余 2 条失败（证书模板创建用例、计划过程页奖惩用例）在 HEAD 上同样失败，且锚点分别落在其他会话正在改的 `certificate/rules/index.vue` 与 `training/plan/workspace/index.vue`，与本 Change 无关。
- 已补：真实管理员登录态的三视角浏览、PDF 下载、影像/附件预览、证书查看下载均已实测（Task 5）。

## Task 5：纵向验收与状态同步

- Owner：用户负责真实环境操作与完成标记；开发侧负责剩余自动化验证、问题修复与证据同步。
- 状态：**已完成**（2026-09-24）。开发侧自动化与真实登录态纵向验收均已完成，详见文末「Task 5 验收记录」。
- 输入：已结束且结果完整的培训计划、管理员账号、Task 1～4 交付。
- 输出：三视角事实一致性证据、签到情况三态证据、PDF 正本下载与抽页证据、影像/附件受控读取证据、证书清单与下载证据、企业档案筛选与下载证据；QA、Retro、进度表与任务状态同步。
- 前后端影响：仅验证与文档；无新 API 或数据结构。
- 验收标准：真实管理员完成"更新档案 → 三视角查看 → 下载 PDF → 预览影像/附件 → 查看下载证书 → 企业档案筛选与下载"全链路；结果不完整计划被拒绝归档且无残留写入；自动化与真实登录态证据分开记录。
- 测试方式：定向前后端回归、MySQL IT、真实浏览器与 Network 验证（文件 `content-type` 与后缀）、`openspec validate --strict`、双轴 Review；环境未覆盖时不得标记闭环完成。

### Task 5 验收记录（2026-09-24）

**已完成（开发侧）**：档案定向单测 28/28、MySQL IT 10/10、PC typecheck 与改动文件 eslint 通过、
前端定向测试零新增失败（HEAD 基线逐条对照）、`openspec validate --strict` 通过；
双轴 Review 见 `engineering/qa/2026-09-24-close-training-archive-loop.md`（含三条记录未改的问题，
其中「个人档案缺失项取并集导致整体拒绝导出」标为需人工决策）。
遗留风险收口：档案 PDF 证书明细的权限旁路已按「PDF 只呈现数量」收口（见 Task 3）。

**已完成（真实环境，2026-09-24）**：本地后端 `20080` + PC dev `5176`（`/api` 指向本地）+ 无头 Chrome CDP 直连，
真实登录 `DEMO/admin`：

- **三视角事实一致性**：一期 / 企业 / 个人三视角在 `statistics`/`attendance`/`contents`/`participantCount`/
  `organizationName`/`planCode` 上**逐字段完全一致**，顶层字段均 22。
- **签到三态**：plan 1 = `SIGNED` 1 + `NOT_RECORDED` 3；plan 2/4 = `SIGNED` 1；签到**不进** `missingItems`。
- **缺事实拒绝且无残留**：plan 1 归档 → `409/9204`，归档后 `fileInfoId` 仍为 `None`。
- **PDF 正本**：plan 2 首次归档 → `application/pdf`、`archive-091801测试培训.pdf`、魔数 `%PDF-1.6`、198562 字节；
  plan 4 由历史 JSON（`application/json` + `archive-1231.json`）重归档为 PDF（`archive-1231.pdf`），版本 `1 → 3`，
  `archiveFileIsPdf` 由 `false` 变 `true`。
- **企业档案筛选**：`trainingTypeId` / `planName` / `startTimeFrom`+`endTimeTo`（ISO 格式）均有效；
  空格格式时间返回 `400/1001`，而 PC 端 `ElDatePicker` 已用 ISO `value-format`，**前后端格式一致**。
- **浏览器渲染**：企业档案 3 行（含「完整/待补全」与「PDF/JSON」标注、行内查看与下载）；
  一期详情 11 个区块（含「参训人员成绩（含签到情况）」与「证书清单」，成绩表含工号与签到情况列）；
  个人档案选择人员后展示行数据与**「本人证书」卡 2 张**（来源类型中文，带查看/下载），
  展开行内 7 个区块且**不重复证书清单**。
- **下载链路**：页面内 `downloadArchive(4)` 取回 **219827 字节 `application/pdf`**；
  `downloadCertificate(6)` 取回 **67424 字节 `image/png`**。全程无控制台错误。

**未覆盖（如实记录，不做伪证）**：
① 证书清单「无权」分支——真实库仅 `admin` 角色同时持有两个权限，无法构造第二种身份，
由 `ArchiveControllerTest` 与 `ArchiveCertificateGuardTest` 锁定（有权分支已实测）；
② 跨租户拒绝——真实环境单租户，由 `ArchiveTenantBoundaryIT` 覆盖；
③ 个人档案 PDF **成功**路径——现有数据下所有参与 plan 1 的人员都因 `RESULTS` 缺失被拒，
前端正确降级（按钮禁用 + 提示），导出逻辑由单测与 IT 覆盖；
④ 档案素材真实登记——当前无「已结束且带资料」的期，由 `ArchiveServiceIT` 覆盖。

完整逐条结果见 `engineering/qa/2026-09-24-close-training-archive-loop.md`；
证据截图 9 张与结构化事实见 `engineering/assets/2026-09-24-ui-archive-*.png|json`（证据已于 2026-09-28 清理）。

**未闭环事项**：`openPersonFile` 对多期缺失项取并集导致整体拒绝导出 —— 需产品确认是否改为
「只导出完整的期」，**标记为需人工决策**，本 Change 未擅自变更语义。
