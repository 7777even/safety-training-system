# Spec Delta：培训计划初培 / 复审性质与学时判定

完整场景见 `specs/training-plan-nature/spec.md`。

## ADDED Requirements

1. 培训计划必须把培训类型与本次培训性质分离；新计划只能选择 `INITIAL`（初培）或 `REVIEW`（复审）。
2. 初培计划使用培训类型 `initialHours`，复审计划使用 `reviewHours`；缺失对应基线时不得发布。
3. 培训标准 `TOTAL_HOURS` 只能收紧培训类型基线，计划适用最低学时取两者最大值。
4. 发布必须冻结培训性质和适用最低学时，并纳入类型快照与既有来源快照 hash。
5. `PLAN_TOTAL_HOURS` 监管检测必须使用计划冻结最低学时，规则条件阈值只能收紧；证据必须记录性质、实际值、阈值和来源。
6. 历史计划不得被自动猜测为初培或复审；以 `UNSPECIFIED` 兼容读取并使用明确标记的旧预警回退口径。
7. init SQL、Versioned Migration、Entity 和 OpenAPI 必须同步，真实 MySQL 验证空库、升级库和初培 / 复审检测链路。

## MODIFIED Requirements

- 培训计划发布学时校验从“仅在选择培训标准时检查 TOTAL_HOURS”改为“始终检查培训性质对应的培训类型基线，并叠加不能降低该基线的
  培训标准要求”。
- 培训类型 `initialHours`、`reviewHours` 从可空输入改为新建 / 修改时必填正数；`reviewHours` 产品文案统一为“复审要求学时”。
- `PLAN_TOTAL_HOURS` 从优先读取当前培训标准改为优先读取计划发布时冻结的适用最低学时，避免来源修改后重算历史计划。

## REMOVED Requirements

无。

## 非目标

- 不增加“初审”或“复训”业务值。
- 不给模板绑定培训性质。
- 不改变个人实学时和人员通过 / 不通过判定。
- 不实施前端页面、Mock、联调或 UI；前端以 OpenAPI 为准另开。

