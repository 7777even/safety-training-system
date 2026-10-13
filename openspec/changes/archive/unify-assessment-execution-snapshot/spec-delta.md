# Spec Delta：统一考核执行快照

## ADDED Requirements

### Requirement: 考核执行必须使用完整版本化快照

每个课程、独立考试/练习和培训考核执行 MUST 保存一个完整、版本化的 `snapshot_json`。该文档 MUST 是来源、资源和执行规则的唯一冻结事实来源，MUST
NOT 再由多个普通快照列与规则 JSON 共同组成隐式快照。

#### Scenario: 生成课程题库执行

- **GIVEN** 已发布开课的课程版本包含题库内容项
- **WHEN** 系统生成课程考核执行
- **THEN** 系统写入包含来源、题库和全部执行规则的 v1 完整快照
- **AND** 普通来源与资源列只作为查询投影

#### Scenario: 生成其他来源执行

- **GIVEN** 系统生成课程测验、独立考试/练习或培训考核执行
- **WHEN** 执行对象持久化
- **THEN** 所有来源使用相同快照结构、Codec 和版本规则
- **AND** 不存在按来源选择不同 hash 算法的分支

### Requirement: 快照摘要必须基于规范化 JSON 语义

系统 MUST 对解析并规范化后的 UTF-8 JSON 计算 SHA-256。规范化 MUST 稳定排序对象键、保持数组业务顺序并统一小数表达，MUST NOT
对写入前文本、数据库返回文本或 Map 迭代结果直接计算摘要。

#### Scenario: MySQL 规范化 JSON 文本

- **GIVEN** 合法执行快照已经写入 MySQL JSON 列
- **AND** MySQL 改变了对象键展示顺序、空白或等价数字文本
- **WHEN** 系统重新读取并校验执行快照
- **THEN** 规范化摘要与写入时一致
- **AND** 系统不得返回 `7063`

#### Scenario: 快照业务内容被修改

- **GIVEN** 已保存执行快照及摘要
- **WHEN** 来源、资源或任一执行规则被修改但摘要未同步改变
- **THEN** 系统返回 HTTP 400 / `7063`
- **AND** 不创建 Candidate、Attempt、答案或结果事实

### Requirement: 查询投影必须与已验证快照一致

系统 MAY 保留来源、资源和状态普通列用于关联与索引，但所有冻结业务值 MUST 从已验证快照读取。来源与资源投影 MUST
在读取时与快照核对，不能形成第二套业务真相源。

#### Scenario: 查询投影与快照冲突

- **GIVEN** 快照 hash 本身合法
- **AND** 普通 `resource_id` 或来源 ID 与快照内容不一致
- **WHEN** 系统读取该执行对象
- **THEN** 系统返回 `7063`
- **AND** 不按普通列或快照任一方猜测继续执行

### Requirement: 考核执行快照必须不可原地修改

系统 MUST 把已创建的执行快照视为不可变事实。规则或资源需要变化时，只能在没有
Candidate、Attempt、Reviewer、结果或冻结题池的前提下重建执行；存在任一执行事实时 MUST 拒绝刷新。

#### Scenario: 已有作答后刷新执行

- **GIVEN** 一个执行对象已经存在 Candidate 或 Attempt
- **WHEN** 发布流程尝试刷新其快照或查询投影
- **THEN** 系统拒绝修改
- **AND** 已有作答继续引用原执行事实

### Requirement: 开发期最终模型不得兼容旧执行快照

系统 MUST 只读写本 Change 定义的最终快照模型，MUST NOT 实现旧字段双读、旧 hash 回退或历史数据猜测转换。数据库迁移 MUST NOT
静默删除已有执行事实。

#### Scenario: 非空旧执行库应用迁移

- **GIVEN** 数据库仍存在旧结构的 AssessmentExecution 行
- **WHEN** 应用最终模型 migration
- **THEN** migration 明确失败并提示重建开发数据库
- **AND** 不自动删除 Candidate、Attempt、答案、批阅或结果

#### Scenario: 空开发库建立最终结构

- **GIVEN** 开发数据库没有旧考核执行事实
- **WHEN** 执行 Admin migration 或 Admin-init
- **THEN** 两条路径建立一致的最终表结构
- **AND** 新生成执行只包含 v1 完整快照模型

## MODIFIED Requirements

### Requirement: 考核执行必须保存明确业务来源

每个 Candidate、Attempt、批阅和成绩事实 MUST 归属于唯一考核执行对象。执行对象 MUST 在完整版本化快照中冻结来源类型、来源业务
ID、资源类型、资源 ID 和执行规则，并使用经过规范化的 SHA-256 校验完整性。查询投影 MUST 与快照一致，MUST NOT 通过空字段、名称、
`examType` 或未校验的普通列猜测来源。

#### Scenario: 从课程入口开始考核

- **GIVEN** 当前学员拥有有效课程报名且对应执行快照完整
- **WHEN** 学员从课程内容项开始题库练习或测验
- **THEN** 系统从已验证快照派生资源和规则并创建作答
- **AND** MySQL 对 JSON 的物理规范化不影响完整性判断

#### Scenario: 执行快照不可验证

- **GIVEN** 执行快照缺失、无法解析、版本未知、摘要不一致或查询投影不一致
- **WHEN** 任一课程、独立考试或培训入口尝试开始作答
- **THEN** 系统返回稳定业务错误 `7063`
- **AND** 不回退实时题库、试卷、课程版本、考试任务或培训计划重新拼装快照

## REMOVED Requirements

### Requirement: 分散快照列与规则 JSON 共同组成考核执行快照

移除通过 `resource_name_snapshot`、时长/分数/题序/计分规则快照列和 `policy_snapshot_json`
共同表达冻结执行的模型。移除原因是该模型存在多个事实来源，且规则摘要依赖 MySQL JSON 的物理文本表示，无法稳定完成完整性校验。
