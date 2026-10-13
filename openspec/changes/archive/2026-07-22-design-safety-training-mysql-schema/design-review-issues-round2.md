# 数据库设计第二轮评审问题清单

## 1. 文档信息

- **评审对象**：`2026-07-21-design-safety-training-mysql-schema` OpenSpec Change 及其 131 张建表 DDL 草案（130 张培训业务表、1
  张系统数据权限表）
- **需求依据**：`docs/product/安全教育培训需求规格说明书.md`（V1.3）、`docs/product/安全教育培训74条FR新需求描述对照表.md`、
  `docs/product/魔学院功能调研文档_排版优化终稿.md`
- **前置文档**：`design-review-issues.md`（第一轮评审，问题一至问题七均已确认或修复）
- **文档目的**：记录第一轮评审未覆盖的新问题，给出证据位置、风险、建议方案和验收标准，供产品、架构和数据库负责人确认
- **当前状态**：已关闭（2026-07-21 需求方确认评审通过）；全部 12 项完成决策及 DDL 与文档修复，并已完成本地 MySQL 8.0.45
  空库建表、唯一性用例和 `EXPLAIN` 抽查（连同第一轮问题六遗留验证，见第 7 节）
- **本轮核对范围**：7 个 DDL 文件逐表核对；与 `backend/sql/schema-system.sql` 的兼容性核对（`us_sys_dept`/`us_sys_role`
  既有列、索引、`deleted` 语义均匹配，无冲突）；建表数量核对（DDL 实际 131 张，与 `design.md`、`table-catalog.md` 一致）

## 2. 总体结论

第一轮评审已收敛的架构决策（系统 RBAC 复用、课程考试执行链、不可变版本、唯一键哨兵值）在 DDL 中落实情况良好。本轮发现 12
个新问题：4 个属于会造成运行期错误或约束失效的设计缺陷，6 个属于业务缺口或语义隐患，2 个属于文档与卫生问题。

| 编号    | 问题                              | 优先级 | 处理类型               |
|-------|---------------------------------|-----|--------------------|
| R2-01 | 试卷题目唯一键按版本去重，与答卷快照唯一键冲突         | 高   | 已修复（未验证）           |
| R2-02 | 培训计划 LIVE 内容类型没有执行落点            | 高   | 已决策并修复（本期移除 LIVE）  |
| R2-03 | 证书幂等唯一键阻止撤销后重发和复训重发             | 高   | 已决策并修复（简单规则）       |
| R2-04 | 填空答案唯一键混入 sort_order，唯一性失效      | 高   | 已修复（未验证）           |
| R2-05 | 公共支撑表 business_type 枚举与引用方不齐    | 中   | 已修复（未验证）           |
| R2-06 | 客户端幂等令牌全局唯一，跨用户碰撞互相拒写           | 中   | 已修复（未验证）           |
| R2-07 | 用户账号和参训人员批量导入缺少批次/错误模型          | 中   | 已决策并修复（方案 A 通用导入表） |
| R2-08 | 素材反向引用无索引，删除保护检查全表扫描            | 中   | 已修复（未验证）           |
| R2-09 | `plan_content_id` 单值回指与资源复用语义冲突 | 中   | 已修复（设计说明）          |
| R2-10 | 开课类型（选修/必修）未落字段                 | 中   | 已决策并修复（方案 A 独立字段）  |
| R2-11 | C 类版本表携带可变发布状态但无审计列             | 中   | 已修复（未验证）           |
| R2-12 | design.md 与 DDL 残留漂移及注释、索引卫生问题  | 低   | 已修复（未验证）           |

## 3. 高优先级问题

### R2-01 试卷题目唯一键按版本去重，与答卷快照唯一键冲突

**现状与证据**

- `sts_paper_version_question` 只保存 `question_version_id`，唯一键为
  `uk_version_question(paper_version_id, question_version_id)`（`ddl/03_question_paper.sql:356`），表内没有 `question_id`
  列。
- `sts_exam_attempt_question` 的唯一键为 `uk_attempt_question(exam_attempt_id, question_id)`（`ddl/04_exam.sql:234`），按题目
  ID 去重。

**风险**

同一逻辑题目的两个版本（题目编辑后新旧版本并存）可以同时进入同一个试卷版本，数据库层无法拦截。该试卷组卷阶段完全合法，但学员开考物化作答快照时，两行会映射到同一个
`question_id`，插入 `sts_exam_attempt_question` 直接触发唯一键冲突，考试无法开始。随机卷抽题服务同样需要按 `question_id`
去重，当前结构无法表达。

**建议方案**

1. `sts_paper_version_question` 增加 `question_id bigint NOT NULL` 冗余列。
2. 唯一键改为 `uk_version_question(paper_version_id, question_id)`。
3. `question_version_id` 保留普通索引，继续作为快照物化来源。
4. 组卷服务与随机抽题服务按 `question_id` 去重；同步修改 `design.md` 第 10 节。

**修复记录（2026-07-21）**

已按建议方案修复：`ddl/03_question_paper.sql` 的 `sts_paper_version_question` 增加 `question_id` 冗余列，唯一键改为
`uk_version_question(paper_version_id, question_id)`，`question_version_id` 保留普通索引；`design.md` 第 10 节和
`table-catalog.md` 已同步。库上唯一性验证待执行。

**验收标准**

- [x] 同一试卷版本中插入同一题目的两个版本时第二次插入失败（2026-07-21 库上验证通过）。
- [ ] 固定卷、自定义卷、随机卷开考物化快照均不会因重复题目触发唯一键冲突。

### R2-02 培训计划 LIVE 内容类型没有执行落点

**现状与证据**

- `sts_training_template_content.content_type` 与 `sts_training_plan_content.content_type` 均包含 `LIVE`（
  `ddl/05_training.sql:147、247`）。
- `design.md` 第 39 行模块依赖图写明"培训计划 ──→ 课程 / 考试 / 直播 / 线下阶段"。
- 唯一的直播执行表 `sts_course_live_session` 的 `course_item_id` 为 `NOT NULL` 且被 `uk_course_item_deleted` 唯一约束（
  `ddl/02_asset_course.sql:269、285`），即直播场次只能 1:1 挂在课程内容项下，没有 `plan_content_id` 通道。
- FR-ENT-TRN-002 本期模板内容清单为"理论题库、测验、理论考试、课程和线下培训阶段"，并不包含直播。

**风险**

培训计划直接编排 `LIVE` 内容时（不包裹在课程内），系统没有任何表可以保存直播名称、老师、链接、时间和参与范围，功能不可实现；枚举与需求清单也互相矛盾。

**建议方案（二选一，需产品确认）**

- 方案 A（推荐，与 FR-ENT-TRN-002 一致）：从 `sts_training_template_content`、`sts_training_plan_content` 的 `content_type`
  枚举中删除 `LIVE`，`design.md` 依赖图同步删除"直播"分支；培训需要直播时通过编排含直播内容项的课程实现。
- 方案 B：`sts_course_live_session` 的 `course_item_id` 改为可空，新增 `plan_content_id bigint NULL`
  并建索引，由应用校验两者恰好其一非空；唯一键相应调整。

**决策与修复记录（2026-07-21）**

需求方确认培训直投直播本期不做，采用方案 A：`sts_training_template_content`、`sts_training_plan_content` 的 `content_type`
枚举移除 `LIVE`；`design.md` 模块依赖图和第 13 节、`table-catalog.md`、`proposal.md` 决策状态已同步；直播仍通过课程内容项执行。后续如需培训直投直播，按方案
B 补执行通道并创建独立 OpenSpec Change。

**验收标准**

- [x] 培训模板/计划可编排的内容类型与 FR-ENT-TRN-002 完全一致（LIVE 已移除）。
- [ ] 若保留 LIVE，培训直投直播具备完整执行链（场次、参与范围、学员入口）——本期不适用，后续启用时随独立变更验收。

### R2-03 证书幂等唯一键阻止撤销后重发和复训重发

**现状与证据**

- `sts_certificate_certificate` 唯一键
  `uk_holder_source_deleted(holder_user_id, source_type, source_id, certificate_template_id, deleted)`（
  `ddl/06_certificate_survey_evaluation_archive_regulation.sql:125`）。
- 证书撤销只改 `certificate_status=REVOKED`，记录仍保持 `deleted=0`（撤销流水表 `sts_certificate_revocation` 仅追加日志）。
- `source_id` 注释仅为"来源对象ID"，COURSE 来源指向课程还是开课未定义；`sts_certificate_issue_rule.source_id` 按
  FR-ENT-AUX-002 关联到课程级。

**风险**

1. 撤销后重发：学员证书被撤销后重新达标，插入新证书撞唯一键；若以逻辑删除旧证规避，又破坏"已撤销证书可查、可追溯"的展示链。
2. 周期复训：安全培训存在年度复审（`sts_training_type` 有初培/复审学时）。TRAINING 来源每期新计划 ID 可重发；但若 COURSE 来源
   `source_id=course_id`，学员次年在新开课再次合格将永远无法获得第二张证书。

**建议方案**

1. 产品确认 COURSE 来源证书的粒度：按课程终身一张，还是按开课（期次）一张。若按期次，`source_id` 定义为 `course_offering_id`
   ，并同步说明签发规则（course 级）与证书实例（offering 级）的映射。
2. 产品确认撤销后是否允许重发。若允许，唯一键需增加可区分维度（例如 `issue_seq` 序号列，或将 `certificate_status=REVOKED`
   的记录排除出幂等键的哨兵方案），由服务保证"同源同模板最多一张有效证书"。
3. 结论落入 `design.md` 第 14 节与第 25 节待确认清单。

**决策与修复记录（2026-07-21）**

需求方确认采用简单规则，不改唯一键结构、不引入签发序号或自动重发：

- 来源粒度：签发规则 `source_id` 挂业务定义（COURSE 为课程 ID、EXAM 为考试 ID、TRAINING 为培训计划 ID）；证书实例 `source_id`
  挂执行实例（COURSE 为开课 ID、EXAM 为考试 ID、TRAINING 为培训计划 ID），`source_result_id` 分别为课程报名、考试人员、参训人员
  ID。
- 复训：年度复训通过新培训计划或新开课自然签发新证书；`valid_to` 到期只迁移为 EXPIRED，不自动重发。
- 撤销后重发：已撤销证书仍占用幂等唯一键；确需重发时由管理员先逻辑删除已撤销证书再重新签发（人工动作），撤销与删除均留痕。

已同步 `ddl/06` 两处 `source_id`/`source_result_id` 注释、`design.md` 第 14 节说明与第 25 节待确认清单、`proposal.md`
决策状态。

**验收标准**

- [x] 撤销后重发规则已确认：先逻辑删除旧证再人工重发，撤销历史通过撤销流水和逻辑删除记录完整保留。
- [x] 年度复训场景下每期培训/开课可独立签发证书（来源粒度按执行实例）。
- [x] 幂等唯一键保证同一有效证书不重复，撤销后逻辑删除再重发流程可行（2026-07-21 库上验证通过；并发安全由唯一键保证）。

### R2-04 填空答案唯一键混入 sort_order，唯一性失效

**现状与证据**

`sts_question_blank_answer` 唯一键
`uk_version_blank_answer(question_version_id, blank_no, answer_group_no, sort_order)`（`ddl/03_question_paper.sql:164`）。
`design.md` 第 9 节同样如此描述。

**风险**

`(题目版本, 空位, 答案组)` 相同、仅 `sort_order` 不同的两行可以同时插入，同一答案组出现两个互相矛盾的答案文本，自动判分结果不确定。唯一键实际未起到约束作用。

**建议方案**

唯一键改为 `uk_version_blank_answer(question_version_id, blank_no, answer_group_no)`，`sort_order` 仅用于展示排序；同步修改
`design.md`。

**修复记录（2026-07-21）**

已按建议方案修复：`ddl/03_question_paper.sql` 的 `uk_version_blank_answer` 剔除 `sort_order`，改为
`(question_version_id, blank_no, answer_group_no)`；`design.md` 第 9 节已同步并注明 `sort_order` 仅用于展示排序。库上唯一性验证待执行。

**验收标准**

- [x] 同一题目版本、同一空位、同一答案组只能存在一条答案记录（2026-07-21 库上验证通过）。

## 4. 中优先级问题

### R2-05 公共支撑表 business_type 枚举与引用方不齐

**现状与证据**

- `sts_common_share_link`、`sts_common_reminder_rule`、`sts_common_message` 的 `business_type` 枚举均为
  `COURSE/OFFERING/EXAM/TRAINING/SURVEY`（`ddl/01_org_common.sql:226、249、289`），缺少 `SURVEY_INSTANCE`
  。而问卷已确认按投放实例发放，参与、答卷和统计都固定在实例上。
- `sts_common_audience.business_type` 枚举（`ddl/01_org_common.sql:138`）缺少 `LIVE`，而
  `sts_course_live_session.audience_id`（`ddl/02_asset_course.sql:275`）引用参与范围；直播的 BOUND 范围没有合法的业务对象类型可用。

**风险**

问卷第二轮投放的扫码链接、提醒和消息无法与第一轮区分，只能挂在问卷定义上产生歧义；直播参与范围无法按既定模式落为绑定范围，实施时只能违反
`uk_business_purpose_deleted` 约定或使用错误的类型值。

**建议方案**

1. 三张表的 `business_type` 注释补充 `SURVEY_INSTANCE`，问卷相关链接、提醒、消息一律挂实例。
2. `sts_common_audience.business_type` 补充 `LIVE`（business_id 为 `live_session_id`）。
3. `design.md` 第 6 节同步。

**修复记录（2026-07-21）**

已修复：`share_link`/`reminder_rule`/`message` 的 `business_type` 注释将 `SURVEY` 改为 `SURVEY_INSTANCE` 并注明问卷一律挂投放实例；
`audience` 枚举补充 `LIVE`（业务对象为直播场次 ID）；`design.md` 第 6.1 节枚举和说明段已同步。

**验收标准**

- [ ] 同一问卷两轮投放的链接、提醒和消息可分别追溯到各自实例。
- [ ] 直播参与范围可按 `BOUND + LIVE` 落库且满足唯一键。

### R2-06 客户端幂等令牌全局唯一，跨用户碰撞互相拒写

**现状与证据**

- `sts_course_study_session.uk_client_trace_deleted(client_trace_id, deleted)`（`ddl/02_asset_course.sql:467`）。
- `sts_exam_attempt.uk_client_attempt_token_deleted(client_attempt_token, deleted)`（`ddl/04_exam.sql:210`）。

两个令牌均由客户端生成，唯一键为全库全局唯一。

**风险**

任意两个用户令牌碰撞（弱随机实现或恶意构造他人可预测的令牌）会使对方写入失败：学习会话丢失、考试尝试无法创建。幂等的业务语义本来就是"
同一执行上下文内不重复"，全局唯一超出了需要并引入干扰面。

**建议方案**

- 学习会话唯一键改为 `(course_enrollment_id, client_trace_id, deleted)`。
- 考试尝试唯一键改为 `(exam_candidate_id, client_attempt_token, deleted)`。
- `design.md` 第 8、11 节同步。

**修复记录（2026-07-21）**

已修复：`sts_course_study_session` 唯一键改为
`uk_enrollment_trace_deleted(course_enrollment_id, client_trace_id, deleted)`；`sts_exam_attempt` 唯一键改为
`uk_candidate_token_deleted(exam_candidate_id, client_attempt_token, deleted)`；两个令牌字段注释注明幂等作用域；
`design.md` 已同步。

**验收标准**

- [x] 不同用户/不同执行上下文使用相同令牌互不影响（2026-07-21 库上验证通过）。
- [x] 同一上下文内重复提交仍保证幂等（2026-07-21 库上验证通过）。

### R2-07 用户账号和参训人员批量导入缺少批次/错误模型

**现状与证据**

- FR-ENT-SYS-004 要求用户账号 Excel 批量导入导出；FR-ENT-TRN-006 要求参训人员批量导入。
- 题库导入有 `sts_question_import_batch` + `sts_question_import_error`（`ddl/03_question_paper.sql:195-231`）；线下成绩导入有
  `sts_training_offline_score_import`（`ddl/05_training.sql:400`）但无错误明细表，失败信息只落 `result_file_id` 文件。
- 用户账号导入、参训人员导入没有任何批次或错误表。

**风险**

同一系统内三种导入三种模式：有批次有明细、有批次无明细、完全无痕。导入失败的定位口径、重试策略和审计能力不一致，后续 API
Contract 无法统一。

**建议方案（需产品确认）**

- 方案 A（推荐）：抽象一张通用导入批次表 + 一张通用导入错误表（`business_type` 区分题库/用户/参训/线下成绩），替换现有两张专用批次表。
- 方案 B：维持现状，在 `design.md` 明确用户和参训导入为同步接口、错误即时返回不落库，并给线下成绩导入补错误明细表或明确文件即错误清单。

**决策与修复记录（2026-07-21）**

需求方确认采用方案 A。已修复：

- `ddl/01` 新增 `sts_common_import_batch`（A 类）和 `sts_common_import_error`（C 类），`business_type` 区分
  QUESTION/USER/TRAINING_PARTICIPANT/OFFLINE_SCORE，`business_id` 按类型指向题库/培训计划/线下场次（USER 为 0），保留
  `result_file_id` 回执能力。
- 删除 `ddl/03` 的 `sts_question_import_batch`、`sts_question_import_error` 和 `ddl/05` 的
  `sts_training_offline_score_import`；`sts_training_offline_score.import_batch_id` 改为逻辑关联公共导入批次。
- 建表总数 131 → 130（129 张培训业务表 + 1 张系统数据权限表）；`design.md`（新增 §6.3、模块表数、合计）、`table-catalog.md`
  （重新编号）、`ddl/README.md`、`proposal.md` 已同步。
- 各类导入模板列定义和校验规则仍待产品确认（原 FR-ENT-EXM-002 待确认项不变）。

**验收标准**

- [x] 四类导入的批次、错误、重试和审计口径一致或差异被明确记录（统一为公共导入表）。
- [x] 导入批次和逐行错误按统一模型写入并可按批次追溯（2026-07-21 库上验证通过）。

### R2-08 素材反向引用无索引，删除保护检查全表扫描

**现状与证据**

`design.md` 第 3.4 节规定"删除或停用前执行反向引用检查""高频逻辑外键必须建立索引"。但以下素材引用列均无以 `asset_id`
打头的索引：

- `sts_course_item.asset_id`（`ddl/02_asset_course.sql:223`，现有索引仅 chapter/course 维度）。
- `sts_question_asset.asset_id`（`ddl/03_question_paper.sql:191`，唯一键以 `question_version_id` 打头）。
- `sts_course_item_material_rule.aux_audio_asset_id`（`ddl/02_asset_course.sql:302`）。

**风险**

FR-ENT-CRS-001 的素材批量删除是高频管理操作，每删除一个素材需对上述表全表扫描做反向引用检查；素材与题目素材表都是预期大表，删除接口会随数据量线性劣化。

**建议方案**

- `sts_course_item` 增加 `idx_asset(asset_id)`。
- `sts_question_asset` 增加 `idx_asset(asset_id)`。
- `sts_course_item_material_rule` 增加 `idx_aux_audio_asset(aux_audio_asset_id)`。
- `design.md` 索引清单同步。

**修复记录（2026-07-21）**

已修复：`sts_course_item` 增加 `idx_asset(asset_id)`；`sts_question_asset` 增加 `idx_asset(asset_id)`；
`sts_course_item_material_rule` 增加 `idx_aux_audio_asset(aux_audio_asset_id)`；`design.md` 已同步。

**验收标准**

- [x] 素材删除前的反向引用检查在三张表上均走索引（2026-07-21 `EXPLAIN` 验证通过）。

### R2-09 `plan_content_id` 单值回指与资源复用语义冲突

**现状与证据**

- `sts_exam_exam.plan_content_id`（`ddl/04_exam.sql:37`）与 `sts_course_offering.plan_content_id`（
  `ddl/02_asset_course.sql:359`）都是单值回指列。
- 设计同时确认"同一个考试可以被多个课程内容项或开课使用"，培训执行上下文已由
  `exam_candidate.context_type=TRAINING, context_id=plan_content_id` 表达。

**风险**

管理员将一个已发布的既有考试编排进两个培训计划内容时，`exam.plan_content_id`
只能指向其一，字段语义失效。若实施时开发者将其当作归属或权限判断依据（而非仅溯源），会产生错误的过滤和越权风险。开课天然按培训创建，风险较低，但同样缺少说明。

**建议方案**

在 `design.md` 第 11、13 节明确：`plan_content_id` 回指列仅表示"该考试/开课因某培训内容而创建"的溯源信息；复用既有资源时保持
`NULL`；禁止参与归属、权限或统计判断，培训执行链一律以 `plan_content.resource_id` 正向引用和候选人 `context_id` 为准。

**修复记录（2026-07-21）**

已修复：`design.md` 第 8.2 节（开课）和第 11.1 节（考试）分别补充溯源语义边界说明。

**验收标准**

- [x] 设计文档已明确该字段语义边界；API Contract 评审时据此检查。

### R2-10 开课类型（选修/必修）未落字段

**现状与证据**

- FR-ENT-CRS-008 要求设置"开课类型"；需求规格说明书业务字典 8.4 明确开课类型为选修/必修。
- `sts_course_offering.enrollment_type` 枚举为 `ASSIGNED/OPEN/LINK`（`ddl/02_asset_course.sql:363`
  ），表达的是报名渠道，不是修读性质；指派的课程也可能是允许退选的选修课。
- `design.md` 未将此项列入第 25 节待确认清单。

**风险**

选修/必修概念被报名渠道吞并，FR-LRN-007 的退选规则（待确认）将来无处落地；统计口径（必修完成率等）无法表达。

**建议方案（需产品确认）**

- 方案 A：`sts_course_offering` 增加 `course_type varchar(16)`（ELECTIVE/REQUIRED），与 `enrollment_type` 正交。
- 方案 B：产品确认本期以 ASSIGNED≈必修、OPEN≈选修的映射为准，则在 `design.md` 与业务字典中写死该映射及其局限。

**决策与修复记录（2026-07-21）**

需求方确认采用方案 A。已修复：`sts_course_offering` 增加 `course_type varchar(16)`（REQUIRED必修/ELECTIVE选修），与
`enrollment_type` 正交，不建低选择性索引；`design.md` 第 8.2 节补字段和正交性说明（UI 按报名类型给默认值、存储显式）；
`proposal.md` 决策状态同步。选修退选规则（FR-LRN-007）保持待产品确认；需求书业务字典 8.4 的编码列可回填
REQUIRED/ELECTIVE，由产品确认后更新基线文档。

**验收标准**

- [x] 选修/必修在数据模型中显式可表达（`course_type` 独立字段）。
- [ ] 必修完成率等统计与退选规则实施时按该字段取数（随后续 Task 验收）。

### R2-11 C 类版本表携带可变发布状态但无审计列

**现状与证据**

以下 C 类表（仅 `create_by`/`create_time`，定义为"只追加"）携带 `published_status(DRAFT/PUBLISHED/ARCHIVED)` 可变状态：

- `sts_question_version`（`ddl/03_question_paper.sql:129`）
- `sts_paper_version`（`ddl/03_question_paper.sql:316`）
- `sts_certificate_template_version`（`ddl/06_certificate_survey_evaluation_archive_regulation.sql:35`）
- `sts_evaluation_form_version`（`ddl/06_certificate_survey_evaluation_archive_regulation.sql:442`）

对照组：`sts_course_version`、`sts_survey_version` 无状态字段，写入即已发布，是纯粹的 C 类。

**风险**

状态从 DRAFT 到 PUBLISHED/ARCHIVED 的迁移必然是 UPDATE，违反自定义的 C 类"只追加"规则；且无 `update_by`/`update_time`
，谁在何时发布或归档一张试卷/题目版本不可追溯，与"版本发布"这一关键审计动作的重要性不匹配。

**建议方案（二选一）**

- 方案 A（推荐）：与课程/问卷版本对齐——草稿保留在工作表，版本表写入即 PUBLISHED，删除 `published_status` 中的 DRAFT
  语义；归档语义上移到主表 `archive_status`。
- 方案 B：将四张表调整为 A 类（补 `update_by/update_time/deleted/version`），接受状态可变但留下审计。

**修复记录（2026-07-21）**

复核后发现方案 A
的前提不成立：题目、试卷、证书模板和评价表单没有独立的工作草稿表（区别于课程/问卷），草稿期就在版本行及其子记录上编辑，"
写入即发布"会破坏组卷预览、换题等草稿工作流；方案 B 的完整 A 类升级会级联到全部 C
类子表（选项、填空答案、抽题规则、评价项等），改动面过大。实际采用最小修复：四张版本表（`sts_question_version`、
`sts_paper_version`、`sts_certificate_template_version`、`sts_evaluation_form_version`）增加 `published_by`、
`published_time` 为发布动作留痕；`design.md` 第 3.2 节将"版本表 DRAFT
期可编辑、发布后不可变、发布留痕、归档随新版本发布事务执行"明确为 C 类的受控例外。

**验收标准**

- [x] 审计类别定义与表行为一致（C 类受控例外已写入 §3.2）；版本发布动作经 `published_by/published_time` 可追溯。

## 5. 低优先级问题

### R2-12 design.md 与 DDL 残留漂移及注释、索引卫生问题

第一轮问题六声称文档已同步，以下为本轮复核发现的残留：

**结构性漂移（需逐项对齐，以确认后的一方为准）**

| 位置                                     | design.md                                                     | DDL                                                                      | 说明            |
|----------------------------------------|---------------------------------------------------------------|--------------------------------------------------------------------------|---------------|
| `sts_course_setting`                   | 有 `setting_name`、`is_default`，按名称唯一，暗示一机构多套配置（design.md §8.1） | 每机构一条 `uk_org_deleted(organization_id, deleted)`，字段全展开（`ddl/02:106-128`） | 两个不同设计，不是措辞差异 |
| `sts_course_item_knowledge.sort_order` | 有（design.md §8.1）；table-catalog 也描述"及顺序"                      | 无此列（`ddl/02:242-251`）                                                    | 编排顺序丢失        |
| 开课自主报名字段名                              | `is_self_enrollment_allowed`（design.md §8.2）                  | `is_self_enroll_allowed`（`ddl/02:366`）                                   | 命名不一致         |
| 关联表主键名                                 | `course_item_knowledge_id`、`course_item_question_bank_id`     | `item_knowledge_id`、`item_question_bank_id`（`ddl/02:243、255`）            | 命名不一致         |
| 表数量口径                                  | design.md/catalog：131/130                                     | `design-review-issues.md` 头部仍写 128/127                                   | 历史文档未更新       |

**注释卫生（违反 design.md §3.1"状态、类型字段注释必须列允许值"）**

- `sts_course_offering.completion_rule`（`ddl/02:364`）
- `sts_course_study_session.end_reason`（`ddl/02:458`）
- `sts_training_attendance.sign_method`（`ddl/05:382`）
- `sts_common_reminder_rule.receiver_filter`（`ddl/01:252`）
- `sts_regulation_warning_rule.warning_level` / `sts_regulation_warning.warning_level`（`ddl/06:701、747`）
- `sts_archive_export_job.export_format`（`ddl/06:676`）

其中部分取值确属"待确认"，注释中应显式标注"待确认"，不应留空白语义。

**索引与类型卫生**

- `sts_exam_answer_item.idx_option_value(item_type, item_key)`（`ddl/04:298`）：全局低选择性索引（全表仅 OPTION/BLANK ×
  少量键值），任何统计都必须先经 attempt 过滤才有意义；建议删除，避免拖慢该预期大表的写入。
- `sts_course_item_material_rule.watermark_opacity decimal(5,2)`（`ddl/02:301`）：违反 §3.1"比例使用 decimal(7,4)"约定。
- `sts_training_offline_score.import_batch_id` 无索引（`ddl/05:433`）：按批次对账/回滚查询将扫表，建议补
  `idx_import_batch(import_batch_id)`。

**修复记录（2026-07-21）**

已按"以已确认 DDL 为准"的第一轮原则逐项修复：

- `sts_course_setting`：`design.md` 按 DDL 重写为机构级单条默认配置，删除 `setting_name`/`is_default` 描述。
- `sts_course_item_knowledge`：DDL 补 `sort_order` 列（design.md 与 table-catalog 均声明有顺序）。
- 命名对齐：`design.md` 改为 `is_self_enroll_allowed`、`item_knowledge_id`、`item_question_bank_id`。
- 表数量口径：`design-review-issues.md` 三处 128/127 更新为 131/130。
- 注释补"允许值待产品确认"：`completion_rule`、`end_reason`、`sign_method`、`receiver_filter`、`warning_level`（规则表和预警表）、
  `export_format`。
- 删除 `sts_exam_answer_item.idx_option_value`（DDL 与 design.md）。
- `watermark_opacity` 改为 `decimal(7,4)` 并注明 0 到 1。
- `sts_training_offline_score` 补 `idx_import_batch(import_batch_id)`。

**验收标准**

- [x] design.md、table-catalog.md 与 DDL 三方对每张表的字段、索引、唯一键描述一致（2026-07-21 修复并经建表验证）。
- [x] 状态/类型字段注释列出允许值或显式标注"待确认"。
- [x] 卫生类索引与类型问题按上述清单修正。

## 6. 建议处理顺序

```text
R2-01、R2-04（纯 DDL 缺陷，2026-07-21 已修复）
→ R2-05、R2-06、R2-08、R2-09、R2-11、R2-12（无需产品决策的修复与对齐，2026-07-21 已完成）
→ R2-02（2026-07-21 已决策：培训直投直播本期不做，按方案 A 移除 LIVE）
→ R2-03（2026-07-21 已决策：证书按简单规则——规则挂业务定义、证书挂执行实例、撤销后先逻辑删除再人工重发）
→ R2-07（2026-07-21 已决策：方案 A 通用导入批次/错误表，建表总数 131 → 130）
→ R2-10（2026-07-21 已决策：方案 A 独立 `course_type` 字段）
→ 重新执行 MySQL 空库建表验证（连同第一轮问题六遗留的验证一起做）
→ 再次执行 OpenSpec Review
```

R2-02、R2-03 会改变表结构或唯一键，建议在生成任何 API Contract 或 migration 之前完成确认。

## 7. 库上验证记录（2026-07-21）

环境：本地 Docker MySQL 8.0.45，临时库（utf8mb4_general_ci），验证后已删除。

1. **建表**：按 `backend/sql/schema-system.sql` → `ddl/00`～`06` 顺序执行，8 个脚本零报错；共创建 143 张表（129 张 `sts_*` +
   14 张 `us_sys_*`，即 130 张建表 DDL 加 13 张既有系统表），与 `table-catalog.md` 口径一致。
2. **唯一性用例**（预期冲突全部触发、预期成功全部通过）：

- R2-01：同一试卷版本插入同一题目的两个版本，第二条命中 `uk_version_question`。
- R2-04：同一题目版本、空位、答案组仅 `sort_order` 不同，第二条命中 `uk_version_blank_answer`。
- R2-06：同报名同令牌命中 `uk_enrollment_trace_deleted`；不同报名使用相同令牌成功写入。
- R2-03：同持有人、来源、模板第二张证书命中 `uk_holder_source_deleted`；将旧证置为 REVOKED 并逻辑删除后重新签发成功。
- 考试人员：同（考试、用户、上下文）第二条命中 `uk_exam_user_context_deleted`。
- R2-07：公共导入批次和逐行错误正常写入。

3. **EXPLAIN 抽查**（全部 `ref` 命中目标索引）：`sts_course_item.idx_asset`、`sts_question_asset.idx_asset`、
   `sts_course_item_material_rule.idx_aux_audio_asset`、`sts_training_offline_score.idx_import_batch`。

本验证覆盖第一轮问题六遗留的"MySQL 空库建表、唯一性用例和 `EXPLAIN`"事项；正式 migration 阶段仍须按 Testcontainers 流程在
CI 重复验证。

## 8. 评审确认表

| 编号    | 决策项                    | 负责人 | 结论                                              | 确认日期       | 影响文件                                                      |
|-------|------------------------|-----|-------------------------------------------------|------------|-----------------------------------------------------------|
| R2-01 | 试卷题目唯一键改为按 question_id | 需求方 | 已修复（库上验证待执行）                                    | 2026-07-21 | design、ddl/03、table-catalog                               |
| R2-02 | 培训 LIVE 内容删除或补执行通道     | 需求方 | 已确认：本期不做培训直投直播，按方案 A 移除 LIVE                    | 2026-07-21 | design、proposal、ddl/05、table-catalog                      |
| R2-03 | 证书来源粒度与撤销后重发规则         | 需求方 | 已确认：简单规则（规则挂业务定义、证书挂执行实例、撤销后先逻辑删除再人工重发）         | 2026-07-21 | design、proposal、ddl/06                                    |
| R2-04 | 填空答案唯一键剔除 sort_order   | 需求方 | 已修复（库上验证待执行）                                    | 2026-07-21 | design、ddl/03                                             |
| R2-05 | business_type 枚举补齐     | 需求方 | 已修复（库上验证待执行）                                    | 2026-07-21 | design、ddl/01                                             |
| R2-06 | 幂等令牌唯一键限定作用域           | 需求方 | 已修复（库上验证待执行）                                    | 2026-07-21 | design、ddl/02、ddl/04                                      |
| R2-07 | 导入批次模型统一或明确差异          | 需求方 | 已确认：方案 A 通用导入批次/错误表，删除两张专用表                     | 2026-07-21 | design、proposal、ddl/01、ddl/03、ddl/05、table-catalog、README |
| R2-08 | 素材反向引用索引补齐             | 需求方 | 已修复（库上验证待执行）                                    | 2026-07-21 | design、ddl/02、ddl/03                                      |
| R2-09 | plan_content_id 语义边界说明 | 需求方 | 已修复                                             | 2026-07-21 | design                                                    |
| R2-10 | 开课选修/必修表达方式            | 需求方 | 已确认：方案 A 独立 `course_type` 字段（REQUIRED/ELECTIVE） | 2026-07-21 | design、proposal、ddl/02、需求字典（编码回填待产品更新）                    |
| R2-11 | C 类版本表状态与审计对齐          | 需求方 | 已修复（补发布留痕并明确 C 类受控例外）                           | 2026-07-21 | design、ddl/03、ddl/06                                      |
| R2-12 | 文档漂移与卫生修正              | 需求方 | 已修复（库上验证待执行）                                    | 2026-07-21 | design、design-review-issues、ddl/01/02/04/05/06            |

## 9. 本文完成条件

1. 全部 12 项问题具有明确负责人、结论和确认日期。
2. 直接修复类问题的 DDL 与 `design.md`、`table-catalog.md` 同步完成。
3. 需产品确认类问题的结论回写 `design.md` 第 25 节。
4. 修复后的 DDL 重新执行本地 MySQL 空库建表、唯一性用例和 `EXPLAIN` 验证（含第一轮问题六遗留验证）。

2026-07-21，上述条件全部满足，需求方确认设计评审通过，本清单与第一轮清单一并关闭；后续实施按 `tasks.md` 逐 Task 确认执行。
