# Design：培训标准基础规则唯一性与学员详情契约修正

## 1. 基础规则唯一性

`sts_training_standard_rule.rule_type` 中：

| ruleType                      | 基数             |
|-------------------------------|----------------|
| `TOTAL_HOURS`                 | 同一标准最多 1 条有效记录 |
| `ONLINE_RATIO`                | 同一标准最多 1 条有效记录 |
| `COURSE` / `EXAM` / `OFFLINE` | 允许多条           |

「有效记录」指 `deleted = 0` 的行，**不论 `status` 是否启用**。禁用行仍占名额，避免一条启用、一条禁用的双值。

不新增数据库唯一索引：同列还要容纳多条 `COURSE`/`EXAM`/`OFFLINE`，部分唯一索引会扩大本次数据变更。唯一性由服务层保证。

## 2. 校验入口

### 替换规则 `PUT /api/training/standards/{id}/rules`

在草稿校验、乐观锁更新和软删旧规则之前，扫描请求 `rules`：

- `rules` 为 `null` 或空数组：视为清空，允许。
- 请求中 `TOTAL_HOURS` 出现超过一次：HTTP 409，业务码 `8016`，消息「总学时规则只能配置一条」。
- 请求中 `ONLINE_RATIO` 出现超过一次：同一码，消息「线上学时占比规则只能配置一条」。
- 两种同时重复时，先报告 `TOTAL_HOURS`。

失败不得消耗乐观锁版本，不得删除既有规则。`@Transactional` 回滚作为兜底。

### 发布标准 `POST /api/training/standards/{id}/publish`

草稿检查通过后、将状态改为 `PUBLISHED` 之前，读取该标准当前有效规则做同样计数。用于拦住绕过替换接口或历史脏数据。重复时 409/
`8016`，标准保持草稿。

## 3. 错误码

| 码      | HTTP | 场景                                  |
|--------|------|-------------------------------------|
| `8016` | 409  | `TOTAL_HOURS` 或 `ONLINE_RATIO` 超过一条 |

登记到 `TrainingBusinessErrorCodes` 与 Conflict 说明。权限、路径不变。

## 4. 学员详情契约

Java 已返回：

- `participant`：`TrainingParticipantVO`
- `plan`：`TrainingPlanVO`（`BeanUtils.copyProperties`，含 `publishStatus`）

OpenAPI 误写成 `type: string`。本次改为 `$ref` 上述 VO。

`TrainingPlanVO.publishStatus` 纳入 `required`。该字段管理端详情同样始终返回；学员详情接口因可见性门禁，实际只会出现
`PUBLISHED` / `RUNNING` / `ENDED`。

学员详情不装配 `stages`；`evaluationForms` 使用 VO 默认空数组。契约 description 写明，避免调用方当成管理端完整详情。不另造精简
VO，以免改变已经返回的 JSON 形状。

成功 example 必须是嵌套对象，禁止 `"participant": "示例值"`。

## 5. 测试

零 DB：

- `replaceRules` 重复 `TOTAL_HOURS` / `ONLINE_RATIO` 返回 409/`8016`，不更新、不删规则。
- 各一条加多条 `COURSE` 允许。
- `publish` 遇到库中重复基础规则返回 409/`8016`，保持草稿。
- 学员详情继续断言 `participant`/`plan` 为对象且 `publishStatus` 有值。

不改 Entity/Mapper/SQL，不跑 IT。

## 6. 前后端影响

仅后端；前端以 OpenAPI 为准另开。
