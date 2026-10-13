# Tasks：隔离证书签发来源，建立三类独立达标触发

> 状态：**已人工确认并实施完成**（2026-09-24，Task 1～4 全部完成，含三类来源真实 HTTP 端到端签发验收）。
>
> Owner：fxw11
>
> 执行限制：一次只实施一个已确认 Task；上一个 Task 验收通过后才能实施下一个。
> 本 Change 不涉及 DDL、权限节点与 `sts.init.schema-version`。

## Task 1：后端建立三效触发并隔离培训内容触发

- **状态**：已完成（2026-09-24）
- **Owner**：fxw11
- **输入**：本 Change Proposal/Design/Spec Delta；功能项 19；当前证书、课程、考试、培训实现；
  实测的来源 ID 错配事实与触发现状。
- **输出**：证书域通用自动签发服务；培训域收敛为仅培训结果触发；课程域独立课程完成触发；
  考试域独立考试通过触发；`EXAM` 候选排除培训派生考试；同步测试。
- **前后端影响**：仅后端。
- **API 依赖**：不新增、不删除公开路径；`GET /api/certificate/rule-source-candidates` 的 `EXAM`
  候选结果集收窄；`docs/api/certificate.openapi.json` 同步该语义说明。
- **数据影响**：不改表、列、索引、权限；不改初始化快照与 schema version。
- **验收标准**：
  1. 证书域 `CertificateAutoIssueService` 提供 `issueForSource(sourceType, sourceId, userId, sourceResultId)`，
     且不依赖课程、考试、培训任何实体；培训域旧实现删除。
  2. 培训计划内课程/考试内容完成时，不再调用自动签发；培训计划整体通过时仍以 `training_plan_id` 触发。
  3. 独立课程（开课 `plan_content_id` 为空）学习状态首次由非 `COMPLETED` 变为 `COMPLETED` 时，
     以 `course_id` 触发 `COURSE` 规则；培训开课不触发。
  4. 独立考试（`assignment_source='ASSIGN'` 且考试 `plan_content_id` 为空）考生结论达到 `PASSED` 时，
     以 `exam_id` 触发 `EXAM` 规则；课程考核与培训考试考生不触发；人工发布与自动生效两条路径都能触发。
  5. `EXAM` 候选排除 `plan_content_id` 非空的派生考试，实测候选由 16 条收敛为 10 条；既有规则详情
     仍按原 ID 返回且不报错。
  6. 同一来源重复触发不产生第二张证书；规则禁用或编辑不影响已签发历史证书。
- **测试方式**：先写证书域、培训域、课程域、考试域与候选查询的失败测试，再最小实现；
  运行受影响模块的零 DB 测试与 MySQL IT；`openspec validate --strict`、`git diff --check` 通过；
  完成规格符合性与代码质量双轴 Review。

## Task 2：前端三分来源与来源展示优化

- **状态**：已完成（2026-09-24）
- **Owner**：fxw11
- **输入**：Task 1 候选口径；`docs/api/certificate.openapi.json`；签发规则页现状；
  `constants/enum-options.ts` 字典口径。
- **输出**：签发规则页来源类型与来源对象的分明展示；候选选择与归属说明；字典与类型同步。
- **前后端影响**：仅前端（`pc-web`）。
- **API 依赖**：只消费既有 `GET /api/certificate/rules`、`rules/{id}`、
  `rule-source-candidates`，不改契约结构。
- **数据影响**：无。
- **验收标准**：
  1. 来源类型口径为「独立课程 / 独立理论考试 / 培训计划」，与既有三端中文口径字典一致，不出现英文编码。
  2. 表单内给出归属说明，明确「培训计划内的课程与考试随培训结果发证，不单独配置」。
  3. 候选下拉按当前类型隔离加载，展示来源编码与名称与状态；同名学生/课程可区分。
  4. 列表把来源类型与来源对象合并为可自解释展示；来源解析失败时保留编号兜底，不显示空白。
  5. 编辑既有规则时来源回显正确，类型切换后不残留上一次来源。
- **测试方式**：`vue-tsc --noEmit`；对改动文件运行 `eslint --max-warnings=0` 并与 HEAD 基线对照；
  受影响端 build；必要时用 CDP 直连做真实渲染与交互验收，量取几何而非只看截图。

## Task 3：清理撞车脏数据并端到端验收

- **状态**：已完成（2026-09-24）：脏数据清理已执行并复验；三类来源真实端到端签发验收已完成，
  证据见 `engineering/qa/2026-09-24-separate-certificate-issue-sources.md`。
- **Owner**：fxw11
- **输入**：Task 1 已完成的三效触发；已识别的撞车证书事实；共享库只读核对结果。
- **输出**：定向清理脚本与执行记录；改造后的端到端验收证据；QA 记录。
- **前后端影响**：仅数据与验收记录。
- **API 依赖**：使用既有证书查询、模板预览下载、学员本人证书端点做验收。
- **数据影响**：对撞车产生的历史证书做定向逻辑删除；不改结构、不改规则配置、不动证书文件对象。
- **验收标准**：
  1. 清理前逐条列出受影响证书（含来源类型、来源 ID、持有人、签发时间），确认其来源 ID 与规则
     绑定对象语义不一致后再执行。
  2. 清理采用逻辑删除，执行后管理员与学员列表均不再返回这些证书；其余历史证书条数不变。
  3. 独立课程完成、独立考试通过、培训计划通过三类场景分别真实产出证书，来源 ID 与主数据一致。
  4. 培训计划内课程/考试完成不再产生课程或理论考试证书；重复同步不新增证书。
  5. 端到端结果为真实环境证据，不以单元测试或 Mock 代替。
- **测试方式**：清理前后各做一次只读核对并留痕；按三类来源构造真实数据跑通签发与查询；
  记录未覆盖项与遗留风险。

## Task 4：收口同一来源单启用规则

- **状态**：已完成（2026-09-24）
- **Owner**：fxw11
- **输入**：本 Change Design 4.6/4.7；归档证书设计 8.2「一个来源对象只能选择一个启用模板」；
  `sts_certificate_issue_rule` 现有唯一键 `uk_source_rule_deleted`；共享库规则只读核对结果。
- **输出**：`CertificateIssueRuleServiceImpl` 新建/更新时的启用唯一性校验；新错误码 9418；
  OpenAPI 错误码与说明同步；前端冲突提示；同步测试。
- **前后端影响**：后端证书域校验；前端签发规则页提示文案。
- **API 依赖**：不新增、不删除路径；`POST /api/certificate/rules` 与 `PUT /api/certificate/rules/{id}`
  在启用态冲突时返回 409 + 9418；`docs/api/certificate.openapi.json` 同步。
- **数据影响**：不改表、列、索引、权限；**不清理历史规则数据**（实测无启用重复组合）。
- **验收标准**：
  1. 同一 `source_type + source_id` 在 `status=1 AND deleted=0` 下 MUST 至多一条；新建启用规则冲突时返回 409 + 9418。
  2. 更新为启用态时排除自身比对，改自身其他字段（如规则名、模板）不误报。
  3. 禁用态规则不参与约束：同来源可保留多条禁用规则；「先禁用旧规则再启用新规则」可成功。
  4. 既有唯一键行为不变：同来源同模板完全重复仍返回 9412；编辑或禁用规则 MUST NOT 影响已签发历史证书。
  5. 共享库存量规则在收口后仍可正常列表、详情与编辑（无启用重复组合，不需要数据清理）。
- **测试方式**：先写失败测试再实现；覆盖新建冲突、更新冲突（含排除自身）、禁用态放行、
  禁用后换绑四条用例；跑受影响模块定向测试；`openspec validate --strict`、`git diff --check` 通过。

## 依赖与顺序

Task 1 → Task 2 与 Task 3（Task 2、Task 3 之间无依赖，但均须在 Task 1 验收后进行）。
Task 4 依赖 Task 1（候选/校验口径），与 Task 3 的端到端验收互相独立。

## 实施记录（2026-09-24）

### 代码

| 位置 | 改动 |
|---|---|
| `certificate/service/CertificateAutoIssueService(+Impl)` | 新增证书域通用自动签发服务，提供 `issueForSource`，不依赖业务域实体 |
| `training/service/CertificateAutoIssueService(+Impl)` | 删除（原实现位于培训域，且以执行实例 ID 充当来源 ID） |
| `training/service/TrainingResultSyncServiceImpl` | 移除内容级触发；培训整期通过改调 `issueForSource("TRAINING", planId, userId, participantId)` |
| `course/service/CourseLearningServiceImpl` | 独立开课首次由非 `COMPLETED` 转 `COMPLETED` 时以 `course_id` 触发 |
| `exam/service/ExamAttemptServiceImpl` | 考生结果两个收口（发布 / 自动生效）统一埋点；`ASSIGN` 且考试非派生且 `PASSED` 时以 `exam_id` 触发 |
| `certificate/service/CertificateIssueRuleServiceImpl` | `EXAM` 候选查询与绑定校验增加 `plan_content_id IS NULL` |
| `docs/api/certificate.openapi.json` | 候选接口说明与 `query.sourceType` 说明同步三分口径 |
| `frontend/.../certificate/rules/index.vue`、`constants/enum-options.ts` | 规则页改用「独立课程 / 独立理论考试 / 培训计划」口径；候选提示、状态标注与归属说明 |
| `certificate/service/CertificateIssueRuleServiceImpl` | 新建/更新增加「同来源启用唯一」校验（错误码 9418），更新时排除自身比对 |
| `docs/api/certificate.openapi.json` | 创建/更新规则错误码与说明补充 9418 |
| `frontend/.../certificate/rules/index.vue` | 状态项补充「同一来源只能有一条启用规则；更换模板请先停用旧规则」提示 |

### 验证

- 后端 `test-compile` 通过（主代码 + 测试代码共 293 个文件）。
- 受影响单元测试全部通过、零失败：`CertificateAutoIssueServiceImplTest`(4)、`ExamAttemptServiceImplTest`(60)、
  `CourseLearningServiceImplTest`(24)、`CourseLearnerReadingServiceImplTest`(6)、
  `TrainingResultSyncServiceImplTest`(30)、`CertificateIssueRuleServiceImplTest`(4)、
  `CertificateServiceImplTest`(4)、`CertificateTemplateServiceImplTest`(9)。
- 前端 `vue-tsc --noEmit` 与改动文件 `eslint --max-warnings=0` 通过（EXIT=0）。
- `openspec validate 2026-09-24-separate-certificate-issue-sources --strict` 通过；`git diff --check` 通过。
- 脏数据：`certificate_id=3`（COURSE/2）、`certificate_id=4`（EXAM/38）已按逻辑删除约定置
  `deleted = certificate_id`，复核后生效证书剩余 0 条；脚本见 `cleanup-collided-certificates.sql`。
- 规则唯一性校验（Task 4）：`CertificateIssueRuleServiceImplTest` 8/8 通过（新增 4 例：新建冲突、
  禁用态放行、更新冲突、排除自身），已做 Red 验证（实现前 3 例失败）。
- 真实端到端（Task 3/4）：后端起 20080 走真实 HTTP，三类来源各产出 1 张证书；同来源重复启用返回 9418；
  详见 `engineering/qa/2026-09-24-separate-certificate-issue-sources.md`。

### 未完成 / 遗留

- **已完成**三类来源真实端到端签发验收：`EXAM/38`（证书 5）、`COURSE/2`（证书 6）、`TRAINING/1`（证书 7），
  来源 ID 均等于主数据 ID；培训计划内课程/考试不再单独发证；重复触发不新增证书。
  证据见 `engineering/qa/2026-09-24-separate-certificate-issue-sources.md`。
- **已收口**同来源多启用模板偏差（Task 4，错误码 9418；真实 HTTP 已验证生效、排除自身不误报）。
- **未做**前端浏览器端真实渲染验收（本机无 playwright/agent-browser，需 CDP 直连）。
- `EXAM` 候选排除派生考试的 SQL 条件未用单测断言（Mockito 无法断言 Wrapper 的 SQL 片段），
  目前以只读库核对（候选 16 条 → 10 条）为准；必要时补 MySQL IT。
- 环境侧事项（非本次改动引入）：`admin` 缺 `course:offering:edit`，课程测验评阅需临时补阅卷人记录；
  培训结束预检要求「所有参与者 × 必修内容均为终态」，使多人多内容计划的端到端验收成本较高。
