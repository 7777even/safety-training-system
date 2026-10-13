# Design：培训资料范围收口与学员侧只读

## 业务路径

### 路径一：奖惩佐证归位

管理员在计划工作台「过程与结果」→ 新增/编辑奖惩 → 上传佐证（唯一入口）→ 保存 → 结束培训 → 归档 →
一期/企业/个人三视角打开档案 → 奖惩表出现「佐证」列 → 点「预览」→ 经受控端点读取到同一个文件。

「培训资料」类型下拉不再出现「奖惩佐证」，管理员上传现场照片/视频/制度文件/评价佐证时不会再把佐证录成资料。

### 路径二：停用口径统一

管理员在计划工作台把误传的资料停用（`status = 0`）→ 该资料仍在工作台列表可见并可改回启用 →
档案的「影像记录 / 附件清单」与「数据统计」中的影像/附件/资料合计**不再计入** → 学员端资料列表也**不再返回**。

### 路径三：学员查看一般资料

学员（PC 门户或移动端）打开「我的培训」→ 进入某个培训计划详情 → 「培训资料」区块列出该计划下
`GENERAL` 且 `status = 1` 的资料 → 点「预览」→ 必经学员侧受控端点按记录读取文件字节 → 页面以 blob 渲染，
不持有可直连地址，也不提供下载。

## 后端

### 1. 资料类型收口（Task 1）

- **只动 `TrainingAttachmentType`，不动 `EvaluationSourceType`**：`REWARD_PENALTY` 字面量在这两个枚举里同名但无关——
  评价表单的「成绩来源类型」`REWARD_PENALTY`（=「奖惩记录」）出现在
  `docs/api/evaluation-form.openapi.json`、`evaluation-form/workspace/index.vue`、`evaluation-form/version/index.vue`、
  `PlanEvaluationPanel.vue`、`api/evaluation-form.ts`，**一律不改**。本 Task 的改动面**仅限**指向培训资料类型的资产：
  `TrainingAttachmentServiceImpl`、`docs/api/training.openapi.json`、`docs/api/archive.openapi.json`、
  `schema-training.sql` 列注释、`api/training-process.ts`、`constants/enum-options.ts`、`PlanProcessResultsPanel.vue`。
- `TrainingAttachmentServiceImpl.MATERIAL_TYPES` 收敛为 `Set.of("GENERAL", "IMAGE", "VIDEO", "EVALUATION")`。
  `requireMaterialType` 保持既有错误码 **8234 / 400「培训资料类型无效」**；新增、编辑传入 `REWARD_PENALTY` 一律拒绝。
- **不新增 DDL 约束**：`sts_training_attachment.attachment_type` 仍是 `varchar(24)`，类型收敛只由服务端与契约保证。
  本次 DDL 变更**仅同步列注释**，避免为枚举收口引入 check constraint 而抬高历史数据风险。
- **既有数据处理（需人工确认执行）**：新增增量迁移
  `V20260924180000__narrow_training_material_type.sql`，把既有 `attachment_type = 'REWARD_PENALTY'` 的资料
  **改写为 `'GENERAL'`**（保留文件与记录，不删除事实），并同步 `schema-training.sql` 列注释与
  `sts.init.schema-version` → `20260924180000`。
  - 迁移**不**尝试把历史资料自动挂到某条奖惩记录上：历史资料与奖惩记录之间没有可判定的对应关系，
    自动绑定会伪造事实。归档时由人工按需在奖惩记录上补挂佐证。
  - 若目标环境不存在 `REWARD_PENALTY` 资料，迁移为零影响。
  - **本条属数据影响，实施前需人工确认**（是否接受「历史奖惩佐证降级为一般资料」）。

### 2. 档案口径统一与奖惩佐证呈现（Task 2）

- `ArchiveServiceImpl.buildView` 取资料时追加 `.eq(TrainingAttachment::getStatus, 1)`（与既有奖惩口径一致）；
  归档素材登记路径（`ArchiveServiceImpl:607-611`）同样追加该条件，保证**在线三视角与归档文件是同一批资料**。
- 统计口径随查询自然收敛：`imageCount` / `attachmentCount` / `materialCount` 只计启用资料，不另外写第二套算法。
- 分流规则**不变**：`IMAGE` / `VIDEO` 进「影像记录」，`GENERAL` / `EVALUATION` 进「附件清单」。
  收口后 `REWARD_PENALTY` 不会再出现在任一分流里（历史数据已在 Task 1 改写为 `GENERAL`）。
- 奖惩事实**新增能力描述**：`ArchiveViewVO.rewardPenalties[].evidenceFileId` 已经返回，本 Task 不改字段，
  只在契约里把「佐证文件 ID 经奖惩佐证端点另行获取」写实为「三视角可预览」。
- **档案 PDF 不加佐证**（延续既有权限收口）：PDF 一旦持久化，下载只校验档案权限，
  内嵌佐证明细等于旁路 `training:reward:list`。在线三视角的佐证预览由 `training:reward:list` 门控，与证书清单同一原则。

### 3. 学员侧只读端点（Task 3）

只读、只本人、只 `GENERAL`、只启用。**不新增权限**，沿用 `training:learner:task`；
管理端 `training:attachment:list` **不发放给学员角色**（否则等于把档案与全员成绩的读取口开给学员）。

| 方法与路径 | 说明 |
|---|---|
| `GET /api/training/learner/participants/{trainingParticipantId}/materials` | 返回本人该参训计划下 `GENERAL` 且 `status = 1` 的资料列表（`TrainingLearnerMaterialVO`） |
| `GET /api/training/learner/participants/{trainingParticipantId}/materials/{trainingAttachmentId}/file` | 受控读取该资料原始文件，内联返回 |

- 收口顺序（任一不满足即拒绝，**不泄漏存在性**）：
  1. `trainingParticipantId` 对应参训记录存在且 `userId == 当前登录人`，否则 **404 / 8155「培训任务对当前学员不可见」**（与既有
     `getLearnerParticipant` 的 8150/8155 口径一致，不改成 403 以免暴露他人参训记录是否存在）；
  2. 计划仍属学员可见（复用 `requireLearnerVisiblePlan`）；
  3. 资料属于该计划、`attachmentType = GENERAL`、`status = 1`，否则文件端点 **404 / 8233「培训资料文件不存在或不可读取」**，
     列表端点直接不返回该条。
- `TrainingLearnerMaterialVO` 字段：`trainingAttachmentId`、`attachmentName`、`description`、`planStageId`、`stageName`、
  `sortOrder`、`hasFile`。
  - **不返回 `fileInfoId`**：学员只按记录读取，不给文件主键。
  - `hasFile` 用于前端决定是否给「预览」入口，避免发出必然 404 的请求。
- 列表排序沿用 `sortOrder`（与工作台一致）。分页：资料量级小，本期返回 `List`，不引分页；
  与 `GET /training/learner/tasks` 的既有形态保持一致。

## PC 交互

- **计划工作台**（`PlanProcessResultsPanel.vue`）：`materialTypeOptions` 去掉「奖惩佐证」；
  `api/training-process.ts` 的 `TrainingAttachmentType` 与 `constants/enum-options.ts` 的
  `TRAINING_ATTACHMENT_TYPE_OPTIONS` 同步去掉 `REWARD_PENALTY`。奖惩表单的「佐证文件」上传口**保留**（这是唯一入口）。
- **档案三视角**（`ArchiveFactsSections.vue`）：奖惩表新增「佐证」列，
  `evidenceFileId` 非空且具备 `training:reward:list` 时给「预览」，经 `getTrainingRewardEvidence(rewardPenaltyId)` 取 blob，
  复用现有 `AssetViewer` 弹窗；缺权限或佐证为空显示「—」，不显示「无权限」字样。
  影像记录与附件清单的受控预览仍按 `training:attachment:list` 门控，两处权限**分别门控、不互相顶替**。
- **PC 门户学员端**（`views/portal/training/detail.vue`）：新增「培训资料」区块，列出 `GET .../materials` 结果；
  每行展示资料名称、类型说明、关联阶段，`hasFile` 为真时给「预览」；空列表不渲染区块。
  **学员侧无下载按钮。**

## 移动端

- `pages/training/detail.vue` 新增「培训资料」区块，`api/training-learner.ts` 补
  `getTrainingLearnerMaterials` 与 `getTrainingLearnerMaterialFile`。
- 预览复用既有受控 blob 渲染方式（与门户同一协议），受控图片可用既有 `loadProtectedImageUrl` 思路，
  非图片类以弹层或跳转查看页承载；实现时以移动端既有 `AssetViewer`/预览能力为准，不新引播放器。
- 窄屏一行一列；资料多时区块自身滚动，不撑破页面。

## 数据与契约影响

- **数据库**：仅 1 条增量迁移——`REWARD_PENALTY` 资料改写为 `GENERAL` + 列注释同步；
  无新表、无新字段、无新索引、无权限与菜单种子变更。`sts.init.schema-version` 同步为 `20260924180000`，
  IT 基线（`AbstractMysqlIntegrationTest.INIT_SCHEMA_VERSION`）同步。
- **契约**：`docs/api/training.openapi.json` 收窄 `attachmentType` 枚举（4 处枚举 + 相关 `description`），
  新增 2 个学员侧端点（含 `summary/description`、字段中文 `description`、example、错误码 8155/8233、`x-permission: training:learner:task`）；
  `docs/api/archive.openapi.json` 同步「附件清单」描述与枚举（去掉 `REWARD_PENALTY`），
  并把 `rewardPenalties[].evidenceFileId` 描述写实为「在线三视角可经受控端点预览」。
- **错误码**：不新增错误码。类型无效沿用 8234、文件不可读沿用 8233、学员不可见沿用 8155、
  本人校验沿用 8150；管理端冻结/归属校验沿用 8235/8236/8237/8238。
- **Mock**：学员侧端点同步补入 Mock（若该端点在 Mock 清单内），保持契约与 Mock 一致。

## 回滚

- Task 1 的迁移是有损改写（`REWARD_PENALTY` → `GENERAL`），**回滚需要人工按归档记录反推**，
  因此迁移脚本必须显式注释可回滚范围与不可自动恢复的部分。
- Task 2～5 均为过滤条件与呈现层改动，去掉新增条件即可回到当前行为。

## 需人工决策（已于 2026-09-24 确认，按下列默认值实施）

1. **历史 `REWARD_PENALTY` 资料降级为 `GENERAL` 是否接受** → **接受**（已由 `V20260924180000` 实施；
   不接受则改为「保留历史、仅禁止新建」，档案侧需另加兼容分支）。
2. **学员端是否允许下载** → **只预览、不下载**。
3. **学员端培训资料是否需按阶段分组** → **单一「培训资料」区块 + 展示关联阶段**；按阶段重组另开 Change。
