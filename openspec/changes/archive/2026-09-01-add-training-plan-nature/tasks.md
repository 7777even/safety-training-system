# Tasks：培训计划初培 / 复审性质与学时判定

> 状态：已完成（2026-09-01）。Owner：lims。全部 Task 仅后端；前端以 OpenAPI 为准另开。

## Task 1：完成培训计划初培 / 复审选择与发布学时闭环

- 状态：已完成（2026-09-01）
- Owner：lims
- 输入：功能项 5；既有培训类型初培 / 复审学时；培训计划、标准、内容与发布快照；本 Change Design 第 1～6、8～10 节。
- 输出：计划培训性质、适用最低学时、发布预检 / 冻结、历史兼容和完整 `training.openapi.json` 契约。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：既有培训类型 CRUD、培训计划 CRUD / page / detail / precheck / publish；不新增路径或权限。
- 数据影响：`sts_training_plan` 增加 `training_nature`、`applicable_required_hours`；同步 init SQL、新 Admin Versioned
  Migration、Entity、`sts.init.schema-version`；历史计划迁移为 `UNSPECIFIED`，不修改既有 V 文件。
- 验收标准：
  - [X] 创建和修改计划必须提交 `INITIAL` 或 `REVIEW`；非法值按 400/1001 返回且不进入 Service。
  - [X] 培训类型创建 / 修改必须提供正数 `initialHours`、`reviewHours`，所有契约文案统一为“初培 / 复审”。
  - [X] 初培计划读取 `initialHours`，复审计划读取 `reviewHours`；对应值缺失或非法时以 8129 阻止发布。
  - [X] 适用最低学时取培训类型对应基线与全部启用 `TOTAL_HOURS` 规则的最大值，标准不得降低类型基线。
  - [X] 计划总要求学时不足时以 8121 阻止发布；线上占比与个人结果既有口径不变。
  - [X] 发布原子冻结 `trainingNature`、`applicableRequiredHours`、v2 类型快照和 hash；来源后续修改不改变历史计划。
  - [X] 新请求不接受 `UNSPECIFIED`；历史计划可返回和筛选 `UNSPECIFIED`，且不会被系统猜测为初培 / 复审。
  - [X] `training.openapi.json` 同步请求、响应、查询、枚举、字段说明、example 和 8129 错误语义。
  - [X] init 与 migration 结构一致；历史回填、非空约束、Entity 映射和分页筛选通过真实 MySQL IT。
- 测试方式：严格 TDD；先观察 Controller / Service / 发布校验和 Mapper IT 失败，再做最小实现；运行目标零 DB、受影响 Admin 零
  DB、
  培训计划 MySQL IT、Flyway / Admin-init IT、OpenAPI 校验、OpenSpec strict 和 `git diff --check`。

## Task 2：完成初培 / 复审计划学时监管预警闭环

- 状态：已完成（2026-09-01）
- Owner：lims
- 输入：功能项 2；Task 1 已发布计划的冻结培训性质与适用最低学时；既有 `PLAN_TOTAL_HOURS` 预警规则、条件、证据和幂等检测。
- 输出：以计划冻结值为基线的学时预警和可追溯证据；历史计划兼容检测。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：既有 warning rule / detect / warning detail API；原则上不新增路径、请求字段或权限，只同步证据 JSON 语义和
  operation
  description；若实现发现必须新增结构化响应字段，立即暂停并取得人工确认。
- 数据影响：无新表或预警表字段；证据 JSON 增加稳定字段。若实现确认需要关系型预警字段，属于数据库范围变化，必须暂停并修订已确认
  Change。
- 验收标准：
  - [X] 新发布计划的 `PLAN_TOTAL_HOURS` 检测读取 `applicableRequiredHours`，不读取培训类型 / 标准当前值重算历史计划。
  - [X] 规则条件阈值只能通过 `max(planFrozen, conditionThreshold)` 收紧要求，不能降低计划冻结基线。
  - [X] 预警证据记录培训性质、计划总学时、冻结最低学时、条件阈值、最终阈值和阈值来源。
  - [X] 同一培训类型的初培和复审计划使用各自冻结学时，检测结果和证据可明确区分。
  - [X] 历史 `UNSPECIFIED` 计划不猜测初培 / 复审，沿用标准 TOTAL_HOURS 或条件阈值并标记 `LEGACY_FALLBACK`。
  - [X] 重复检测继续遵守既有 dedup 幂等规则；`PLAN_ONLINE_RATIO` 行为不变。
  - [X] 必要的 `regulation.openapi.json` 描述和 example 与实际证据语义一致。
  - [X] 培训计划创建→初培 / 复审发布→监管检测的真实 MySQL 目标验收通过。
- 测试方式：严格 TDD；先覆盖冻结阈值、条件收紧、来源修改、初培 / 复审差异、legacy fallback 和检测幂等失败测试，再完成最小实现；运行
  regulation 目标零 DB、相关 MySQL IT、Admin 受影响测试、OpenAPI / OpenSpec 校验和 `git diff --check`。

## 完成门槛

两个 Task 分别完成目标测试和受影响模块验证后，独立执行：

1. 规格符合性 Review：严格对应功能项 2、5、本 Change 和 API Contract。
2. 代码质量 Review：检查历史兼容、发布原子性、快照一致性、越界改动、测试遗漏和无关重构。
3. 更新本 `tasks.md`，再更新进度表中的状态、负责人、日期、验收证据、阻塞项和行更新时间。
4. 同步 QA 与 Retro；全部通过后才能关闭本 Change。
