# 培训档案闭环 QA

对应 Change：`2026-09-24-close-training-archive-loop`（功能项 44 三段）。
记录时间：2026-09-24。

## 范围

档案事实组装与契约扩展、归档快照写实与资料资产接线、档案文件改为 PDF 正本、PC 档案页展示与操作补全。

## 已验证（开发侧自动化，2026-09-24 实跑）

- 后端真实编译 `Compiling 1032 source files` 通过；档案相关定向单测 **28/28 全绿**：
  `ArchiveServiceImplTest` 12、`ArchiveControllerTest` 4、`ArchivePdfRendererTest` 6、
  `ArchiveCertificateGuardTest` 3、`ArchiveLabelsTest` 3。
- MySQL IT **10/10 全绿**（`-P integration-test` + `TESTCONTAINERS_RYUK_DISABLED=true`）：
  `ArchiveServiceIT` 5（含 `threeViewsShareSameFacts` 三视角逐字段相等、
  `createFromPlan_writesRealSnapshotsAndAssets` 快照写实与素材登记）、`ArchiveMapperIT` 1、`ArchiveTenantBoundaryIT` 4。
- PC 端：`vue-tsc --noEmit` 退出码 0；改动 9 个文件 eslint `--max-warnings=0` 退出码 0。
- 前端定向测试零新增失败：受影响文件集合内 36 例 35 通过、1 失败，失败项锚在
  `certificate/rules/index.vue`（其他会话在改的文件），HEAD 基线同样失败；逐条比对一致。
- 样例视觉验收：`engineering/assets/2026-09-24-archive-pdf-sample.pdf`（证据已于 2026-09-28 清理）（+ `-page1.png` / `-page2.png`）——
  中文清晰、表格对齐、章节齐全、长内容自动分页到第 2 页。
- `openspec validate 2026-09-24-close-training-archive-loop --strict` 通过。

## 本轮收口：档案 PDF 证书明细的权限旁路（原 design 第 8 节遗留）

- **问题**：一期档案 PDF 在归档时一次性生成并持久化，内含证书编号与持证人 ID；
  而 `/file` 下载只校验 `archive:training:list`。等于用档案权限旁路了证书清单的
  `certificate:cert:list` 门控，且对外交付物外带内部主键。
- **收口方式**：让 PDF **不再承载证书明细**——证书区改为只呈现数量
  （`ArchivePdfRenderer.certificateSummary`：有权限给「本期已签发证书 N 张。」，无权限给缺权限说明，
  无证书给「无已签发证书」），证书编号 / 持证人 / 来源 ID 一律不写入文件。
  这样「文件是固定快照」与「按调用方权限裁剪」不再冲突。
- **未采纳的两条路**：① 下载时按权限即时渲染（放弃持久化正本，与「一期档案须持久化受控 `fileInfoId`」冲突）；
  ② 维护「含证书/不含证书」两份文件（同一归档版本指向两份文件，版本语义与存储成本都变复杂）。
- **同步**：`spec-delta.md` 增补「证书明细 MUST NOT 出现在档案文件中」并修正两个 Scenario；
  `design.md` 第五节与第八节改为收口结论；新增 `ArchivePdfRendererTest` 2 例锁定该口径（共 6 例）。
- 在线三视角的证书清单**不变**：仍按 `certificate:cert:list` 门控返回，缺权限时整个字段不返回。

## 真实环境验收（2026-09-24 完成）

环境：本地后端 `20080`（`spring.profiles.active=vpn`，连团队库 `19.191.192.146:8045` / Redis `16379` /
MinIO `19000`），PC `pc-web` dev（独立端口 `5176`，`/api` 代理指向本地 `20080`），
无头 Chrome 153 经 CDP 直连做真实登录态渲染。真实登录账号 `DEMO/admin`。

### 后端起停与请求链路

- 首次启动失败在 `MinIO bucket is not accessible: sts-dev-private`（403）。
  **根因不是 MinIO 故障**：环境注入了 HTTP 代理 `http://127.0.0.1:55364`，AWS SDK v2 默认读
  `http_proxy` 环境变量，经代理访问 MinIO 被拒。直连（绕过代理）求证 bucket 返回 200，
  清除代理变量后后端正常启动。**后续本地起后端一律先 `unset http_proxy https_proxy` 及大写变体。**

### 接口层（真实 HTTP）

| 用例 | 结果 |
|---|---|
| 三视角事实一致性（一期 / 企业 / 个人） | 逐字段**完全一致**（`statistics`/`attendance`/`contents`/`participantCount`/`organizationName`/`planCode`），三者顶层字段均 22 |
| 一期事实 `GET /archive/trainings/{id}` | plan 1/2/4 均 200；plan 1 取到 `missingItems=['RESULTS']` |
| 签到三态 | plan 1：`SIGNED` 1 + `NOT_RECORDED` 3；plan 2/4：`SIGNED` 1。**无签到事实时不影响 `missingItems`** |
| 结果不完整计划归档 | plan 1 → `409 / 9204 缺少归档必需事实: RESULTS`，且**归档后 `fileInfoId` 仍为 `None`（无残留写入）** |
| 首次归档产出 PDF | plan 2 → 200、`archiveStatus=SEALED`、`currentVersionNo=1` |
| 一期 PDF 下载 | `200`、`Content-Type: application/pdf`、`Content-Disposition` 文件名 `archive-091801测试培训.pdf`、魔数 `%PDF-1.6`、198562 字节 |
| 历史 JSON 档案 | plan 4 归档前：`200` + `application/json` + `archive-1231.json` + 魔数 `{"traini` + `archiveFileIsPdf=false`（`fileInfoId=24`） |
| 历史 JSON 重归档为 PDF | plan 4 → 200、版本 `1 → 3`；下载变 `application/pdf` + `archive-1231.pdf` + `%PDF-1.6`；`archiveFileIsPdf=true`（`fileInfoId=40`） |
| 企业档案筛选 | `trainingTypeId=28` → 3 条；`trainingTypeId=999999` → 0 条；`planName=测试` → 2 条；`startTimeFrom=2026-09-19T00:00:00&endTimeTo=2026-09-21T00:00:00` → 1 条 |
| 时间参数格式 | 空格分隔（`2026-09-19 00:00:00`）或纯日期 → `400/1001` 类型转换失败；**ISO 格式正确**。PC 端 `ElDatePicker` 已用 `value-format="YYYY-MM-DDTHH:mm:ss"`，**前后端格式一致，非缺陷** |
| 个人档案 | `GET /archive/persons/1` → 3 期、`missingItems=['RESULTS']`；`/file` → `409/9204`（任一期缺事实即拒绝整个个人档案，属已登记语义取舍） |

### 浏览器渲染（真实管理员登录态，CDP 直连无头 Chrome）

- **企业档案**：真实渲染 3 行 —— `1231`（归档事实**完整**、文件格式 **PDF**）、`091801测试培训`（完整、**PDF**）、
  `测试培训计划`（**待补全**、**JSON**）；每行含「查看一期档案」「下载档案」；培训类型显示中文「测试培训类型」。
- **一期档案详情**：渲染 11 个区块 —— 归档事实核查、数据统计、培训内容、**参训人员成绩（含签到情况）**、
  综合评价、奖惩记录、影像记录、附件清单、**证书清单**、归档文件、更新档案；
  KV 展示「培训编号=1231 / 参训人数=1 / **文件格式=PDF** / 文件状态=已生成，可下载」；
  成绩表列含**工号**与**签到情况**；证书表列含证书编号/来源类型/来源ID/签发时间/状态/证书文件。
- **个人档案**：选「管理员」后 —— 行 `测试培训计划 | 示例企业 | 结算结果=结果不完整 | 归档事实=待补全`；
  **「本人证书」卡共 2 张**（`CERT-2582E180C1F44450 / 课程 / 2`、`CERT-432B197505DC4C98 / 理论考试 / 38`，
  来源类型为**中文**，含「查看 / 下载」）；展开行内渲染 7 个区块且**不含证书清单**（证书统一由独立卡片承载）。
- **下载链路**：页面内调用 `downloadArchive(4)` 取回 **219827 字节 `application/pdf`**；
  `downloadCertificate(6)` 取回 **67424 字节 `image/png`**。无头环境下 `<a download>` 未落盘，
  故以真实 blob 体积与 MIME 作为下载链路证据。
- 全程 **无控制台错误**；仅出现预期内的 `409 /api/archive/persons/\\{id\\}/file`（即"任一期缺事实"的拒绝）。

### 未覆盖项（如实记录，不做伪证）

1. **证书清单"无权"分支**：真实库只有 `admin` 角色同时持有 `archive:training:list` 与
   `certificate:cert:list`，无法构造"有档案权限、无证书权限"的第二种身份（改共享库权限需人工确认）。
   该分支由 `ArchiveControllerTest`（缺权限时响应不含证书清单字段）与 `ArchiveCertificateGuardTest`（3 例）锁定。
   有权限分支已实测：plan 1 返回 1 张证书、个人档案返回 2 张。
2. **跨租户读取拒绝**：真实环境仅 `DEMO` 单租户，由 `ArchiveTenantBoundaryIT`（4 例）覆盖。
3. **个人档案 PDF 下载成功路径**：现有数据下 `person 1/2/4` 都参与了缺 `RESULTS` 的 plan 1，
   故导出按并集规则被拒；前端正确降级为**按钮禁用 + 提示文案**（已实测渲染）。
   导出逻辑本身由 `ArchiveServiceImplTest.shouldComposePersonArchiveFileFromVisibleEndedTrainings` 与
   `ArchiveServiceIT` 覆盖。
4. **档案素材真实登记**：当前无「已结束且带资料」的期（已结束计划 1/2/4 资料数均为 0，资料只能在 `RUNNING` 期创建），
   由 `ArchiveServiceIT.createFromPlan_writesRealSnapshotsAndAssets` 覆盖。

### 验收产生的数据

- plan 2 首次归档（`currentVersionNo=1`，`fileInfoId=39`）、plan 4 重归档（`currentVersionNo=3`，`fileInfoId=40`）——
  均为正常业务操作，保留了这两次归档结果（plan 4 的 JSON 正本因此被 PDF 取代，这正是本 Change 的目标行为）。
- 证据截图 9 张见 `engineering/assets/2026-09-24-ui-archive-*.png`（证据已于 2026-09-28 清理）；结构化事实见
  `engineering/assets/2026-09-24-ui-archive-round*.json`（证据已于 2026-09-28 清理）。

## 双轴 Review

### 规格符合性

- 三个视角共用同一份事实（`buildView` 单一来源），字段集合一致；`missingItems` **不含**签到相关项；
  签到由线下成绩映射为 `SIGNED` / `NOT_RECORDED` / `NOT_APPLICABLE` 三态且不落库；无线下内容时为不适用。
- 统计口径与既有结果判定一致，分母为 0 的比率返回 `null` 而非 0；奖惩只取 `status=1`，与正式结果口径一致。
- 证书清单受 `certificate:cert:list` 门控，缺权限时整个字段不返回（字段级 `@JsonInclude(NON_NULL)`）。
- 契约 `archive.openapi.json` 1.3.0 与实现一致：22 个顶层属性、两个 `/file` 响应 `application/pdf`、
  企业档案 4 个时间筛选参数、错误码 9211。
- 历史 JSON 档案不迁移，页面按 `archiveFileIsPdf` 标注重新更新提示。

### 代码质量

复核通过项：`loadUsers` 用 `selectBatchIds` 消除逐人查询；资料登记按 `fileInfoId` 先去重；
快照哈希纳入参训人事实且参训人按主键升序取以保证稳定；`Map.of` 改可空显式 map（原实现在分数为 null 时必然 NPE，
属顺带修掉的既有缺陷）；PDF 页面尺寸取点、绘制栅格取像素两者分离。

**记录但不属本次范围的三条**（均为既有设计或需人工决策，未擅自扩大改动）：

1. `listEnterprise` 对每期都执行完整事实聚合，而企业档案列表只渲染 8 个字段
   （编号/名称/类型/企业/结束/人数/归档事实/文件格式）。期数多时是明显的过度查询，
   建议后续改为列表轻量投影、详情再取完整事实。
2. `getPerson` 内 `for (row : rows) { trainingPlanMapper.selectById(...) }` 为逐期查询，
   单人参训期数多时线性放大；可批量取计划后组装。上述两条都不改语义，属性能优化。
3. `openPersonFile` 把各期 `missingItems` 取并集，任一期缺事实即拒绝导出整个个人档案。
   保守但粗放——是否应改为「只导出完整的期」需产品确认，**标记为需人工决策**。

### 遗留

- 档案 PDF 依赖运行环境的系统 CJK 字体（与既有证书 PNG 同一依赖）；纯 Linux 精简镜像若未装中文字体，
  中文会渲染为方框。本次按已确认口径不引入 CJK 字体资源。
