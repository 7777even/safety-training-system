# Design：培训档案事实组装、快照写实与 PDF 正本

## 业务路径

培训计划结束且结果完整 → 管理员在档案页（或计划工作台）更新档案 → 服务端复用既有 `createFromPlan` 组装**一份**档案事实 → 写实参训人快照并把影像/附件登记进 `sts_archive_asset` → 生成 **PDF 正本**并持久化当前版本的受控 `fileInfoId` → 一期档案详情 / 企业档案列表 / 个人档案详情三个视角读同一份事实 → 下载档案文件或按受控端点取影像、附件、证书原件。

## 一、档案事实的单一来源

一期、企业、个人三视角**共用 `ArchiveViewVO`**，不再各自组装。字段与数据来源：

| 事实 | 字段 | 数据来源 |
|---|---|---|
| 培训计划 | planCode / planName / trainingTypeName / trainingNature / startTime / endTime / location / endedTime / applicableRequiredHours | `sts_training_plan` + `sts_training_type` |
| 企业 | organizationId / organizationName / orgCode | `sts_organization` |
| 培训内容 | planContentId / contentName / contentType / isRequired / isScored / scoreWeight / requiredHours / planStageId | `sts_training_plan_content`（+ `sts_training_plan_stage` 取阶段名） |
| 参训人员 | trainingParticipantId / userId / userName / userCode / participantOrgName | `sts_training_participant` + `us_sys_user` |
| **签到情况** | signInStatus / offlineScore / recordedTime | 由 `sts_training_participant.offline_score` 与 `sts_training_offline_score` 映射，见下节 |
| 学员成绩 | courseScore / examScore / offlineScore / compositeScore / resultStatus | `sts_training_participant` |
| 综合评价 | compositeScore / resultStatus | `sts_training_participant`（沿用既有 `evaluation_snapshot` 口径） |
| 奖惩 | rewardPenaltyId / recordType / title / scoreChange / occurredDate / description / evidenceFileId / status | `sts_training_reward_penalty`（只取 `status = 1`，见下） |
| **影像记录** | trainingAttachmentId / attachmentName / `attachmentType ∈ {IMAGE, VIDEO}` / fileInfoId / 阶段与排序 | `sts_training_attachment`（Change A 建立的资料能力） |
| **附件** | 同上，`attachmentType ∈ {GENERAL, REWARD_PENALTY, EVALUATION}` | 同上 |
| 证书 | certificateId / certificateNo / sourceType / issuedTime / certificateStatus | `sts_certificate_certificate`（只读聚合，下载走既有证书文件端点） |
| 数据统计 | 见「统计指标口径」 | 上述事实的派生计算 |

**影像记录与附件同源分流**：二者都是培训资料（`sts_training_attachment`），按 `attachmentType` 拆成 `materials`（IMAGE/VIDEO，对应功能项"影像记录"）与 `attachments`（其余，对应功能项"附件"），与功能项列举顺序一一对应，不新增数据来源。

**奖惩只取启用记录**：`status = 1`。停用（作废）的奖惩不进入档案事实，与 `TrainingWorkflowServiceImpl` 正式结果口径（`TrainingEndedResultVO` 同样只取 `status = 1`）保持一致。过程记录页仍可见全部记录（含停用），二者差异是"过程视图"与"正式档案视图"的差异，属刻意为之，不是口径不一致。

**培训内容必须先取阶段**：`sts_training_plan_content` **只有 `plan_stage_id`、没有 `training_plan_id`**，所以内容清单按"先 `sts_training_plan_stage` 取本期阶段 → 再按 `plan_stage_id IN (...)` 取内容"两步查询，不用计划 ID 直查内容。

**企业列表**：功能项"企业列表"按 `organizationId + organizationName` 呈现（当前租户内单一机构），已人工确认不跨租户，不再扩展。

## 二、签到情况映射规则（本 Change 的关键口径）

- `signInStatus` 取值：
  - `NOT_APPLICABLE`：该培训计划**不含线下培训内容**（无线下阶段/线下内容），签到不适用；
  - `SIGNED`：计划含线下内容，且该参训人**已录入线下成绩**（`offline_score` 非空或存在 `sts_training_offline_score` 记录）；
  - `NOT_RECORDED`：计划含线下内容，但该参训人尚无任何线下成绩记录。
- **不新增签到表、签到记录、签到列**，不采集扫码/定位/现场证据；`signInStatus` 为**查询期派生字段**，不落库、不出现在归档快照的必需项中。
- **签到情况 MUST NOT 进入 `missingItems`**：当前 `missingItems` 仍只有 `RESULTS`（存在 `INCOMPLETE` 参训人）。线下成绩缺失通常已通过结果完整性间接拦住归档，档案不因此新增阻塞条件。
- 与规格「MUST NOT 要求签到」一致：本设计**只呈现事实，不新增前置条件**。

## 三、统计指标口径

`statistics` 全部为上述事实的派生值，不新增表：

- **培训内容**：内容项数、必修项数。
- **人数**：`participantCount`（参训人总数）。
- **整体完成情况**：`completedCount`（`resultStatus != INCOMPLETE`）、`passedCount`（`PASSED`）、`failedCount`（`FAILED`）、`incompleteCount`（`INCOMPLETE`）——与 `TrainingWorkflowServiceImpl` 既有统计口径**同一套判定**，不另立规则。
- **派生比率**：`passRate = passedCount / (passedCount + failedCount)`，分母为 0 时为 `null`（不写 0 冒充）。
- **成绩指标**：`averageCompositeScore`（已定稿综合分均值）。
- **学时指标**：`onlineHoursTotal`、`offlineHoursTotal`（参训人学时求和）。
- **资料指标**：`imageCount`、`attachmentCount`、`materialCount`（= 前二者之和）。
- **奖惩指标**：`rewardCount`、`penaltyCount`。
- **签到指标**：`signedCount`、`notRecordedCount`（`NOT_APPLICABLE` 时二者均不参与）。

## 四、归档快照写实与资料接线

- `createFromPlan` 中两处硬编码 `"{}"` 改为写真实事实：
  - `reward_penalty_snapshot`：该参训人的奖惩明细（`recordType/title/scoreChange/occurredDate`）；
  - `certificate_snapshot`：该参训人的证书清单（`certificateId/certificateNo/sourceType/issuedTime`）。
- **`sts_archive_asset` 接线**：归档时把该培训的资料逐条登记为档案素材：
  - `source_attachment_id` = `training_attachment_id`，`file_info_id`、`file_name_snapshot`、`sort_order` 取自资料记录，`checksum` 留空；
  - **`asset_type` 收敛到列的声明域**：`sts_archive_asset.asset_type` 的列注释是 `NOTICE/ATTENDANCE/IMAGE/VIDEO/SUMMARY/OTHER`，
    所以 `IMAGE`/`VIDEO` 原样写入，`GENERAL`/`REWARD_PENALTY`/`EVALUATION` 统一写 `OTHER`。
    来源类型仍可由 `source_attachment_id` 与档案事实里的 `attachmentType` 追溯。**不改 DDL 注释**（改注释属结构变更、需迁移），
    因此本 Change 依然**无迁移**；
  - 唯一键 `uk_version_file(archive_version_id, file_info_id)` 在代码里先去重：同一受控文件被多条资料引用时只登记一条，
    否则会触发唯一键冲突并把整个归档事务一起回滚（这是"同一文件不产生重复档案素材行"的实现方式，而不是靠异常兜底）。
- 快照写实：`member_snapshot` 由 `userId/userName/userCode` 组成（原实现只有 `userId`）；
  `learning/exam/evaluation` 快照改用**允许 null 值**的显式 map（原实现用 `Map.of`，分数为 null 时会 NPE）。
  奖惩/证书快照按参训人各自的事实落库，无事实时写 `[]` 而不是 `{}`。
- 快照哈希：`snapshot_hash` 输入从 `plan + org + summary` 扩展为**再拼接全部参训人的六类快照 JSON**；
  参训人按 `training_participant_id` 升序取，保证哈希稳定不抖动。哈希算法仍是既有 `SnapshotHashUtil`，**不改算法本身**。
- **无新表、无迁移**：`sts_archive_asset`、`sts_archive_participant`、`sts_archive_version` 结构已具备所需列。

## 五、档案文件改为 PDF 正本

### 生成方式（最小闭环）

- 新增 `ArchivePdfRenderer`，**复用证书已验证的 AWT/Java2D 渲染路径**绘制页面（中文由逻辑字体 `Font.SANS_SERIF` 呈现在既有环境中已验证可用），再用**已在依赖中的 PDFBox 3.0.5** 把页面图像装入 `PDDocument` 输出 `application/pdf`。
- **不新增依赖、不新增字体资源**（仓库当前零 ttf/otf/ttc，证书同样是走系统字体渲染的 PNG）。
- **页面尺寸与栅格分开**：绘页栅格是 A4@150dpi 的像素数（1240×1754），PDF 页面尺寸固定取 `PDRectangle.A4`（595.28×841.89 **点**），栅格整页铺满页面，实际打印分辨率 150dpi。
  **不要把像素数当点数用**：`new PDRectangle(1240, 1754)` 会产出 17×24 英寸的海报页（实施中踩过并已修）。
- PDF 内容顺序：封面信息（计划编号/名称/企业/起止时间/结束时间）→ 数据统计 → 培训内容清单 → 参训人员成绩表（含签到情况列）→ 综合评价 → 奖惩清单 → 影像记录清单 → 附件清单 → 证书数量。
- 行数超过单页可用高度时**自动新开一页**，表格单元格按列宽截断（尾部省略号），保证不串列、不截断正文。
- 影像与附件**只出清单**（名称/类型/阶段/文件大小/上传时间），原件经受控端点另行获取；**PDF 不内嵌影像与附件二进制**。
- 证书区**只呈现数量**（`ArchivePdfRenderer.certificateSummary`）：证书编号、持证人 ID、来源 ID 一律不写入档案文件，理由见第八节收口结论。
- 一期档案：`createFromPlan` 生成 PDF 后写入 `generatedFileStore.store(bytes, "archive-<planCode>.pdf", "archive", "application/pdf", "pdf")`，并把 `fileInfoId` 持久化到当前归档版本（沿用既有逻辑）。
- 个人档案：`openPersonFile` **即时组装 PDF**，继续不落库、不使用占位文件 ID。

### 已评估的替代方案与代价（需人工确认可否接受）

| 方案 | 代价 | 取舍 |
|---|---|---|
| **AWT 光栅页 + PDFBox 装载（采纳）** | PDF 文本不可检索/复制，文件体积偏大 | 零新依赖、零新字体资源、中文必显、可打印可交付 |
| 文本 PDF（PDType0Font + 引入 CJK 字体资源） | 需新增二进制字体资源（数 MB），属 AGENTS.md「未确认引入依赖」范围 | 体积小、可检索；**本次不做**，如甲方需要可另开 Change |

## 六、契约与数据影响

- `docs/api/archive.openapi.json` 从 1.2.1 升版：`ArchiveViewVO` 扩展为完整事实结构（新增 `plan/organization/contents/participants/attendance/scores/evaluations/rewardPenalties/materials/attachments/certificates/statistics`，原 9 字段保留）；两个 `/file` 响应 `content-type` 由 `application/json` 改为 `application/pdf`；企业档案补充已支持的查询参数说明与前端暴露；新增档案文件生成失败错误码 **9211**；`ArchivePersonVO.trainings` 沿用同一 `ArchiveViewVO`。
- **前端 `ArchiveView` 必须按扩展后的契约同步**，否则新增事实会被静默丢弃（本次要修的正是这个坑）。
- 权限不新增：档案统一沿用 `archive:training:list` / `archive:training:create`。**证书清单受权限门控**——缺少 `certificate:cert:list` 时档案响应 MUST NOT 包含证书清单；具备该权限时返回只读清单，下载仍走既有证书文件端点。
- 已归档的旧版本文件仍是 JSON：**不迁移历史文件**，重新更新档案即产出 PDF 正本。

## 七、PC 交互

- **一期档案详情**（`PlanArchivePanel.vue`）：由"事实核查 + 文件信息"扩展为概览 + 统计卡 + 培训内容清单 + 参训人成绩表（含签到情况）+ 综合评价 + 奖惩清单 + 影像记录清单 + 附件清单（带受控预览/下载）+ 证书清单 + 更新档案与下载档案；"文件格式"字段由 `JSON` 改为 `PDF`。
- **个人档案**（`result/archive/index.vue` 个人页签）：新增详情页——个人培训记录（每期可展开）、参训情况、奖惩记录、综合评价与**证书清单 + 查看/下载**；"下载个人档案"产出 PDF。
- **企业档案**：补齐筛选（培训名称、培训编号、**培训类型**、**开始时间范围**、**结束时间范围**），列表每行新增"下载档案文件"（复用一期 `/file`），保留"查看一期档案"。
- 不新增菜单与路由；沿用既有 `archive:training:list` 权限与 `/admin/result/archive` 页面。

## 八、已确认决策与遗留风险

2026-09-24 文祥对下列三点逐项确认：

1. **光栅 PDF 的文本不可检索：接受**。按第五节方案 A 实施（AWT 绘页 + PDFBox 装载），**不引入 CJK 字体资源**；将来若甲方要求可检索文本，另开 Change 处理。
2. **证书清单必须同时具备 `certificate:cert:list` 才返回**：档案查询在**缺少该权限时 MUST NOT 返回证书清单字段**（不是"返回清单但禁用下载按钮"），避免仅凭档案权限窥得证书编号与签发时间。下载仍走既有证书文件端点。
3. **历史 JSON 档案必须在页面标注**：既有已归档版本指向旧 JSON 文件，页面 MUST 提示"需重新更新档案以获得 PDF"。**不迁移历史文件**。

遗留风险：

- 若档案事实体量较大（参训人 + 内容 笛卡尔积），事实组装需按计划一次性查询聚合，避免 N+1；`buildView` 中已有的逐人 `userMapper.selectById` 应在本次一并批量化（属本 Change 范围内的事实组装优化，不改语义）。
- 光栅 PDF 的体积随参训人数与清单行数增长；单期档案页数需在实现时按固定行高分页，避免单页内容溢出被截断。
- **已收口（2026-09-24）**：原遗留「档案 PDF 内包含证书编号与持证人 ID，但下载只校验 `archive:training:list`」的权限旁路问题，
  收口方式是**让 PDF 不再承载证书明细**——证书区改为只呈现数量（有权限时给张数，无权限时给"缺权限"说明），
  证书编号/持证人/来源 ID 一律不写入文件。这样"文件是固定快照"与"按调用方权限裁剪"不再冲突：
  下载只需档案权限，证书明细一律回受控端点（`certificate:cert:list`）在线查看。
  已评估但未采纳的两条路：①下载时按权限即时渲染（放弃持久化正本，与"一期档案须持久化受控 `fileInfoId`"冲突）；
  ②维护"含证书/不含证书"两份文件（同一归档版本指向两份文件，版本语义与存储成本都变复杂）。
- 光栅 PDF 依赖运行环境的系统 CJK 字体（与既有证书 PNG 同一依赖）；纯 Linux 精简镜像若未安装中文字体，中文会渲染为方框。
