# Design：培训计划初培 / 复审性质与学时判定

## 1. 领域语义

本设计将两个容易混淆的概念分开：

| 概念   | 字段               | 示例                 | 职责                       |
|------|------------------|--------------------|--------------------------|
| 培训类型 | `trainingTypeId` | 高压电工作业、熔化焊接与热切割作业  | 表达“培训什么”，维护初培 / 复审两套学时基线 |
| 培训性质 | `trainingNature` | `INITIAL`、`REVIEW` | 表达“本次按哪一种培训要求执行”         |

稳定枚举：

- `INITIAL`：初培。
- `REVIEW`：复审。
- `UNSPECIFIED`：仅用于迁移前历史计划，禁止出现在新建 / 修改请求中，禁止用于新计划发布。

不使用“初审”，因为初审表达首次审核而非首次培训；不另设“复训”，避免与功能项“复审”形成两套产品语义。若产品后续确认复训与复审是两个
独立业务阶段，必须另建 OpenSpec Change 扩展枚举及学时模型。

## 2. 计划生命周期

1. 创建草稿时必须提交 `trainingNature=INITIAL|REVIEW`。
2. 草稿修改可以改变培训性质；修改仍受现有乐观锁控制。
3. 发布预检根据计划培训性质选择培训类型对应学时，并校验计划内容要求学时合计。
4. 发布成功后培训性质、适用最低学时、类型 / 标准 / 模板及内容结构一起不可变。
5. 已发布计划继续使用冻结值；培训类型或标准后来修改不回写、不重新解释历史计划。

培训模板不保存培训性质。原因是功能项只要求模板按工种或培训类型制定，同一模板可以作为初培或复审计划的编排起点；实例化后的计划必须明确性质，
并根据相应最低学时调整内容后才能发布。

## 3. 学时规则

### 3.1 培训类型基线

设计划内容中所有启用内容的 `requiredHours` 合计为 `planTotalHours`：

```text
typeRequiredHours =
  INITIAL -> trainingType.initialHours
  REVIEW  -> trainingType.reviewHours
```

培训类型创建和修改时，`initialHours`、`reviewHours`
均为必填正数。历史培训类型若缺少对应值，不猜测默认学时；使用该类型的新计划在预检 / 发布时失败，
直到类型配置被人工补齐。

### 3.2 与培训标准的关系

培训类型学时是功能项规定的基线。若计划选择了已发布培训标准，则所有启用的 `TOTAL_HOURS` 规则只能追加更严格要求：

```text
standardRequiredHours = max(所有启用的 TOTAL_HOURS.requiredValue)
applicableRequiredHours = max(typeRequiredHours, standardRequiredHours)
```

未选择培训标准时，`applicableRequiredHours = typeRequiredHours`。没有 `TOTAL_HOURS` 规则时，不额外增加标准学时要求。由此保证培训标准不会无意降低培训类型的
初培 / 复审基线。

发布条件：

```text
planTotalHours >= applicableRequiredHours
```

不满足时沿用培训发布学时问题码 `8121`，消息必须包含培训性质、实际计划学时和最低要求学时。线上占比仍同时校验标准
`ONLINE_RATIO` 与培训类型 `onlineMaxRatio`，不因本次变更改变。

计划学时属于编排合规规则，不是个人实学时门槛；个人结果仍按既有必修逐内容正式结论产生。

## 4. 数据模型

`sts_training_plan` 新增：

| 列                           | 类型             | 约束         | 说明                                     |
|-----------------------------|----------------|------------|----------------------------------------|
| `training_nature`           | `varchar(16)`  | `NOT NULL` | `INITIAL/REVIEW`；迁移历史值可为 `UNSPECIFIED` |
| `applicable_required_hours` | `decimal(8,2)` | 可空         | 发布时冻结的适用最低学时；草稿及无法判定的历史计划为空            |

`training_nature` 是需要筛选、统计和稳定解释的核心业务维度，必须使用关系型列，不能只放在 JSON。`applicable_required_hours`
是发布时形成的合规事实，
单独持久化便于监管检测和证据生成，同时写入发布快照用于完整性校验。

数据库资产必须同时更新：

1. `schema-training.sql` 中的最终完整表结构。
2. 新增 Admin `V<yyyyMMddHHmmss>__add_training_plan_nature.sql`，禁止修改既有 migration。
3. `sts.init.schema-version` 更新为新 migration 版本。

升级 migration 先增加可空 `training_nature`，将全部既有计划明确写为 `UNSPECIFIED`，再改为 `NOT NULL`
；不得依据计划名称、类型学时、标准或内容合计猜测。
`applicable_required_hours` 不回填。最终 init SQL 不为 `training_nature` 设置默认值，避免绕过应用层必填约束。

## 5. 发布快照

新发布计划的 `trainingTypeSnapshotJson` 升级为可识别的 v2 结构，至少包含：

```json
{
  "schemaVersion": 2,
  "trainingTypeId": 1,
  "typeCode": "SO_ELEC_HV",
  "typeName": "高压电工作业",
  "trainingNature": "INITIAL",
  "initialHours": 80,
  "reviewHours": 24,
  "applicableRequiredHours": 80,
  "onlineMaxRatio": 0.3
}
```

若培训标准把最低学时提高到 96，则 `applicableRequiredHours=96`；标准规则本身继续保存在 `trainingStandardSnapshotJson`。
`sourceSnapshotHash` 继续覆盖类型、标准和模板快照，因此培训性质和最终适用学时进入既有完整性校验。历史 v1 快照保持原样并可读取。

## 6. API Contract

`docs/api/training.openapi.json` 同步：

- `TrainingPlanCreateRequest.trainingNature`：必填，只允许 `INITIAL/REVIEW`。
- `TrainingPlanUpdateRequest.trainingNature`：必填，只允许 `INITIAL/REVIEW`。
- `TrainingPlanQuery.trainingNature`：可选筛选，只允许 `INITIAL/REVIEW/UNSPECIFIED`。
- `TrainingPlanVO.trainingNature`：必返，允许 `INITIAL/REVIEW/UNSPECIFIED`，其中 `UNSPECIFIED` 仅表示迁移前历史计划。
- `TrainingPlanVO.applicableRequiredHours`：可空；仅新发布计划保证有值。
- 培训类型创建 / 修改请求把 `initialHours`、`reviewHours` 改为必填正数。
- 所有 `reviewHours` 中文描述统一为“复审要求学时”。
- 创建、修改、发布和预检 operation 更新 example、错误语义及兼容说明。

不新增 API 路径、不新增权限。Controller 分组、字段说明、请求 / 响应 example、错误码及 `x-permission` 继续遵循项目 OpenAPI 规范。

## 7. 监管预警

`PLAN_TOTAL_HOURS` 对新发布计划不得重新读取培训类型或培训标准当前值，而应读取计划冻结的 `applicableRequiredHours`：

```text
warningRequiredHours = max(plan.applicableRequiredHours, condition.thresholdValue)
```

其中规则条件阈值可以收紧监管要求，但不能降低计划发布时的初培 / 复审基线。预警证据快照增加：

- `trainingNature`
- `planTotalHours`
- `applicableRequiredHours`
- `conditionThreshold`
- `warningRequiredHours`
- `thresholdSource=PLAN_FROZEN|PLAN_FROZEN_AND_CONDITION|LEGACY_FALLBACK`

历史 `UNSPECIFIED` 计划没有冻结适用学时：为保持既有检测兼容，继续使用“培训标准 `TOTAL_HOURS`，否则规则条件阈值”的旧算法，并把证据标记为
`LEGACY_FALLBACK`；不得选择 `initialHours` 或 `reviewHours`。本 Change 不自动订正历史预警。

`PLAN_ONLINE_RATIO` 继续按既有规则运行，不在本次改变。

## 8. 错误与边界

- 创建 / 修改缺少或提交非法 `trainingNature`：HTTP 400，统一参数错误码 `1001`。
- 新计划发布时性质为 `UNSPECIFIED`，或对应初培 / 复审学时为空、非正数：HTTP 400，新增培训域业务码 `8129`。
- 计划总学时不足：发布预检返回 `8121`；直接发布沿用现有聚合失败机制且不写快照、不改变状态。
- 已发布计划修改培训性质：沿用 HTTP 409 / `8103`，不新增绕过入口。
- 历史 `UNSPECIFIED` 计划保持可查询；不能因读取旧数据发生反序列化或枚举异常。

## 9. 测试设计

严格按 TDD 实施。

零 DB：

1. Controller 创建 / 修改请求缺少或非法培训性质返回 400，且不进入 Service。
2. Service 保存和返回 `INITIAL/REVIEW`，查询条件正确透传。
3. 发布预检分别选择 `initialHours`、`reviewHours`；缺失对应学时失败。
4. 培训标准高于类型基线时取标准值，低于类型基线时不能降低基线。
5. 发布冻结 `trainingNature`、`applicableRequiredHours` 和 v2 类型快照；来源修改不改变已发布值。
6. 监管检测读取冻结值，规则条件只能收紧；历史计划走明确的 legacy fallback。

真实 MySQL IT：

1. init 与 migration 得到一致列、类型和约束，schema-version 正确。
2. 新建 / 更新 / 查询培训计划正确持久化培训性质，分页筛选准确。
3. migration 后历史计划为 `UNSPECIFIED` 且适用学时为空，不猜测业务值。
4. 初培与复审计划用同一培训类型发布时分别冻结不同最低学时，并由监管检测生成正确阈值证据。

交付验证按 Admin 两层测试规则执行：目标零 DB、受影响 Admin 零 DB、相关 MySQL IT、Admin-init IT、OpenAPI JSON / 规范检查、OpenSpec
strict、`git diff --check`，最后完成规格符合性和代码质量双轴 Review。

## 10. 风险与取舍

- `UNSPECIFIED` 只为避免伪造历史事实，不能成为新业务选项；前端后续应显示“历史未标明”，不能显示成初培或复审。
- 同时保存关系型冻结列和 JSON 快照存在少量重复，但分别服务查询 / 预警与完整审计；发布事务必须一次写入，测试校验二者一致。
- “标准只能收紧类型基线”是本设计的业务决策；若需求方希望标准能够覆盖并降低培训类型学时，必须在确认前修改本
  Change，不能在实现阶段临时改变。

