# 2026-09-24 L3/L4 变更需求清单（当日整理）

> 本文只做**当日 L3/L4 变更的需求口径台账**（功能项覆盖、契约/权限/数据影响、状态与遗留），
> 便于收工对齐；**业务规格唯一来源仍是 `openspec/`**（change 与 `openspec/specs/`），
> 本文不新增、不改写任何业务规则，也不构成新的实施授权。
> 生成时间：2026-09-24 收工前；范围：本日有代码/契约/数据改动的 L3/L4 事项。

## 一、当日收口并已归档的三个变更

### 1. 培训资料上传与受控读取闭环 —— Change `2026-09-24-close-training-material-loop`

| 项 | 内容 |
|---|---|
| 级别 | L4（新增对外端点 + 受控读取规则；无 DDL、无权限/菜单变更） |
| 功能项 | **34**（培训全部内容含附件资料上传）、**36**（支持上传培训过程中的影像、培训附件、奖惩评价、综合评价等资料） |
| 需求要点 | 资料挂培训计划、可选关联阶段；五类资料类型（GENERAL/IMAGE/VIDEO/REWARD_PENALTY/EVALUATION）；上传、编辑、删除、预览；原件**只按资料记录受控读取**，不接受调用方传文件 ID |
| 契约 | `training.openapi.json` 新增 `GET /api/training/attachments/{trainingAttachmentId}/file` |
| 权限 | 沿用既有 `training:attachment:*`，**零新增权限/菜单** |
| 数据 | 无迁移；无新表新字段 |
| 状态 | T1～T3 完成 → **已归档**（delta 已并入基线，能力 `training-material`） |
| 证据 | `engineering/qa/2026-09-24-close-training-material-loop.md` |
| 后续修订 | 其"五类资料类型"随后被变更 4 收窄为四类（奖惩佐证移出资料类型，改由奖惩记录自身承载） |

### 2. 培训档案事实完整化与 PDF 正本 —— Change `2026-09-24-close-training-archive-loop`

| 项 | 内容 |
|---|---|
| 级别 | L4（契约升版、新错误码、档案文件格式更换、新增字段级权限门控） |
| 功能项 | **44**（个人档案 / 企业档案 / 一期一档，三个子项整体从"部分符合"推进到最小闭环） |
| 需求要点 | 档案呈现计划、企业、培训内容、参训人员、签到情况、学员成绩、综合评价、奖惩、影像记录、附件、证书与数据统计（22 个顶层属性）；签到**由线下成绩派生三态**（不依赖签到表、不进入 `missingItems`）；档案文件改 **PDF 单文件正本**；历史 JSON 档案标注"需重新更新档案" |
| 契约 | `archive.openapi.json` **1.2.1 → 1.3.0**；`ArchiveViewVO` 补全事实字段；两个 `/file` 响应由 `application/json` 改 `application/pdf`；新增错误码 **9211**（档案文件生成失败）；企业档案补 4 个时间筛选参数 |
| 权限 | 不新增档案权限（沿用 `archive:training:list` / `archive:training:create`）；**证书清单额外以 `certificate:cert:list` 门控**，缺权限时整字段不返回；**PDF 只呈现证书数量**，不写入编号/持证人/来源 ID（避免持久化文件旁路证书权限） |
| 数据 | 无 DDL、无新表（`sts_archive_asset` 与六类快照列原本就有，只是从未写实）；历史 JSON 档案**不迁移** |
| 状态 | T1～T5 完成 → **已归档**（delta 并入 `m4-minimum-closure`，需求 7 → 16 条） |
| 证据 | `engineering/qa/2026-09-24-close-training-archive-loop.md` |
| 待人工决策 | `openPersonFile` 对多期缺失项取并集 → 任一期缺事实即拒绝整份个人档案导出；是否改为"只导出完整的期"待产品定 |

### 3. 证书签发来源隔离 —— Change `2026-09-24-separate-certificate-issue-sources`

| 项 | 内容 |
|---|---|
| 级别 | L4（业务规则收口 + 契约 + 错误码 + 历史脏数据处置） |
| 功能项 | **19**（证书可配置于理论考试、课程和培训） |
| 需求要点 | 三类来源**彼此独立**且各以主数据 ID 触发：独立课程完成用 `course_id`、独立理论考试通过用 `exam_id`、培训结果用 `training_plan_id`；**培训计划内的课程/考试不再单独发证**；`EXAM` 候选排除培训派生考试；**同一来源对象启用态至多一条规则**且禁用后再启用 |
| 契约 | `certificate.openapi.json`：候选接口与规则创建/更新说明改为三分口径；新增错误码 **9418**（该来源已存在启用规则，HTTP 409） |
| 权限 | 无新增（沿用 `certificate:rule:*`）；证书成品 `SOURCE_TYPE` 变量仍用原中文口径 |
| 数据 | 无 DDL；**脏数据处置**：原"ID 数值撞车"签出的 `certificate_id=3/4` 已逻辑删除（脚本 `cleanup-collided-certificates.sql`） |
| 状态 | T1～T4 完成 → **已归档**；真实 HTTP 端到端已跑（三类来源各 1 张证书、幂等、9418 生效） |
| 证据 | `engineering/qa/2026-09-24-separate-certificate-issue-sources.md` |
| 未完成 | 候选 SQL 过滤条件的 IT 断言（Mockito 断言不了 Wrapper SQL）；前端证书规则页的浏览器渲染验收 |

## 二、当日实施完成、尚未归档的一个变更

### 4. 培训资料范围收口与学员侧只读 —— Change `2026-09-24-close-training-material-scope-loop`

| 项 | 内容 |
|---|---|
| 级别 | L4（有损数据迁移 + 列注释收窄 + 新增学员端点 + 权限边界确认） |
| 功能项 | **34 / 36**（资料范围）、**44**（档案奖惩表补佐证列） |
| 需求要点（文祥六条） | ① 资料类型去掉"奖惩佐证"，奖惩佐证统一由奖惩记录自身的 `evidenceFileId` 承载（**两处奖惩列表都保留**）；② 档案与学员侧资料**一律只取 `status=1`**；③ 学员端**只开放 GENERAL**；④ **只预览不下载**；⑤ 新增**学员侧只读端点**；⑥ 停用资料学员端不可见 |
| 契约 | `training.openapi.json` 新增学员侧 2 个端点、附件类型枚举收窄为 4 值；`archive.openapi.json` 同步附件/影像清单描述与枚举 |
| 权限 | **零权限变更**：学员侧沿用 `training:learner:task`，**不向学员发放 `training:attachment:list`**；红线 `archive:training:list` 维持不给学员（该权限在服务端完全信任入参，开放等于可查他人档案） |
| 数据 | 迁移 **`V20260924180000`**：`attachment_type='REWARD_PENALTY'` **有损**改写为 `GENERAL`（无自动可逆路径，回滚需人工反推）+ 列注释收窄；`sts.init.schema-version` → `20260924180000`；无新表新字段、无菜单/权限种子变化 |
| 状态 | Task 1～6 完成；**待归档**（delta 是否并入 `openspec/specs/` 需人工确认；新增能力 `training-material-scope`、`learner-material-reading`，`m4-minimum-closure` 为 MODIFIED） |
| 证据 | `engineering/qa/2026-09-24-close-training-material-scope-loop.md`、`engineering/retro/2026-09-24-close-training-material-scope-loop.md` |
| 未覆盖 | 移动端原生分支未真机；跨租户拒绝仅有 IT 证据（单租户环境）；停用态文件读取 404 仅接口层；管理端档案"下载"按钮刻意保留 |

## 三、当日推进但未闭环 / 未开始的 L3、L4 事项

### 5. 奖惩佐证受控预览与可选字段 —— Change `2026-09-23-close-training-reward-penalty-loop` Task 2.1

- 级别：L4（DB 约束放宽 + 新端点 + 新错误码）。
- 功能项：**36 / 39 / 40 / 44**（奖惩记录与佐证）。
- 契约：`training.openapi.json` 新增 `GET /api/training/reward-penalties/{id}/evidence`（`training:reward:list`），新错误码 **8247**。
- 数据：迁移 **`V20260924093000`** 把 `description` / `occurred_date` 由 NOT NULL 放宽为可空（与既有"可选、编辑可清空"契约一致）。
- 状态：**未闭环**。Task 1 / 2 / 2.1 开发侧完成；真实管理员登录态与对象存储预览待 Task 3 验收。
- 已知未修：`TrainingRewardPenaltyServiceIT.rewardWriteBumpsPlanVersion_soStaleEndCannotFreeze` 仍失败（同一事务内旧计划对象更新返回 1、期望 0），属**既有计划乐观锁逻辑**、本轮未改。
- 证据：`engineering/qa/2026-09-23-close-training-reward-penalty-loop.md`、`engineering/retro/2026-09-24-training-reward-evidence-preview.md`。

### 6. 证书模板设计器 —— Change `2026-09-21-implement-m4-pc-outcome-closure` Task 6

- 级别：L3（前端）。
- 功能项：**19**（证书设计：任意图片作模板底图 + 添加证书编号、学员姓名等内容）。
- 状态：**未闭环**。模板页已从占位接通真实接口（分页/新建/元素增删/底图上传/保存与发布/预览/删除），真实角色浏览器验收仍待补。
- 该 Change 的 Task 2～8 均为"实现完成、真实角色验收待补"。

### 7. 移动端学员成果 —— Change `2026-09-24-close-mobile-learner-outcome-loop`

- 级别：L3。
- 功能项：**50**（个人中心-查看证书）、**57**（我的培训档案：按培训类型统计任务数量、查看结果与课程考试记录）。
- 状态：**已人工确认但尚未开始实施（代码零改动）**；结论是**不用新增档案端点**（`/certificate/my-certificates` 权限为 `cert:list` **OR** `training:learner:task`，学员可访问），唯一契约缺口是 `TrainingLearnerTaskVO` 缺 `trainingTypeName`。

## 四、当日新增的 L4 数据变更（需人工执行/复核）

| 迁移 | 影响 | 备注 |
|---|---|---|
| `V20260924180000__narrow_training_material_type.sql` | 改写历史 `REWARD_PENALTY` 资料为 `GENERAL` + 收窄列注释 | **有损**，无自动回滚；目标环境若无该类型资料则零影响 |
| `V20260924093000__allow_optional_training_reward_fields.sql` | 奖惩 `description` / `occurred_date` 改可空 | 放宽约束，无数据改写 |

- 已部署库需**人工执行**上述两条迁移（`main.flyway.enabled=false` 且建议不 `out-of-order`）；共享库结构变更与菜单/权限写入按项目约定须人工确认，未自行执行。
- 本轮**无菜单/权限种子变更**，故 `docs/architecture/menu-role-matrix.csv` 无需回填。

## 五、当日遗留待决策（需人工）

1. 个人档案导出：多期缺失项取并集 → 任一期缺事实即整体拒绝，是否改为"只导出完整的期"（变更 2）。
2. 奖惩计划乐观锁既有 IT 失败的诊断时机（变更 5）。
3. 变更 4 的 delta 是否合并进 `openspec/specs/`（属规格基线变更）。
4. ~~验收证据目录惯例：本轮落在 `engineering/assets/`，仓库既有惯例为 `engineering/qa/evidence/<主题-日期>/`，是否统一。~~
   **已决（2026-09-28）**：`engineering/assets/` 下的截图、结构化 JSON 与 PDF 样例**已整体清理**，只保留
   `certificate-bg/` 两张证书底图（体积 11MB → 3.0MB）；相关文档中的证据路径文字保留并统一标注
   「证据已于 2026-09-28 清理」，**不再另建 `engineering/qa/evidence/` 目录**。
