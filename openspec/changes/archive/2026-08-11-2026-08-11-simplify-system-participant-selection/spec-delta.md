# Spec Delta：全系统简化参与人员选择

## ADDED Requirements

### Requirement: 部门筛选最终绑定具体人员

系统 MUST 只使用 ALL、DEPT 或 USER 作为当次人员选择条件，并 MUST 将选择结果解析为当前机构启用用户 ID。业务记录 MUST 绑定具体用户，
不得长期保存部门、标签、岗位、包含/排除或嵌套范围规则。

#### Scenario: 按部门选择人员

- **WHEN** 管理员选择一个或多个部门并确认
- **THEN** 系统解析这些部门及下级部门中的当前机构启用用户
- **AND** 目标业务逐人保存具体 userId

#### Scenario: 人员后续调岗

- **WHEN** 已绑定人员之后变更部门
- **THEN** 既有报名、考生、参训或问卷参与记录不自动增删

### Requirement: 各业务使用自己的人员事实

课程、考试、培训和问卷 MUST 分别使用报名、考生、参训人和问卷参与人作为唯一参与事实，不得依赖公共 audience 成员表。

#### Scenario: 指派课程人员

- **WHEN** 管理员向 ASSIGNED 开课确认人员选择
- **THEN** 系统逐人创建来源为 ASSIGN 的课程报名

#### Scenario: 分配考试培训问卷人员

- **WHEN** 管理员在考试、培训或问卷中确认人员选择
- **THEN** 系统逐人创建对应领域人员记录
- **AND** 重复人员按领域唯一键幂等处理

### Requirement: 开放课程使用具体资格白名单

OPEN 开课 MUST 明确使用当前机构全员或具体用户白名单；部门选择 MUST 在保存时展开为具体白名单用户。

#### Scenario: 白名单用户自主报名

- **WHEN** 当前用户位于已发布 OPEN 开课的具体资格白名单
- **THEN** 系统允许其查看并自主报名

#### Scenario: 非白名单用户报名

- **WHEN** SELECTED 开课的非白名单用户尝试报名
- **THEN** 系统不返回该开课并拒绝直接报名请求

### Requirement: 公共参与范围完整退役

系统 MUST 删除公共参与范围管理、解析、冻结、成员检查能力以及 `common:audience:*` 权限和三张 audience 表。

#### Scenario: 调用旧参与范围接口

- **WHEN** 调用方访问 `/api/common/audiences/**`
- **THEN** 系统不存在对应路由

#### Scenario: 执行退役 migration

- **WHEN** migration 在旧结构上执行
- **THEN** 系统先删除业务引用列和菜单权限
- **AND** 再按 member、rule、audience 顺序删除三张表

### Requirement: 问卷只允许已绑定人员提交

问卷投放 MUST 以 `sts_survey_participant` 作为参与资格，不得在提交时为未绑定用户隐式创建参与人。

#### Scenario: 未绑定用户提交问卷

- **WHEN** 当前用户没有该问卷实例的参与人记录
- **THEN** 系统拒绝提交

#### Scenario: 已绑定用户提交问卷

- **WHEN** 当前用户具有有效参与人记录且尚未提交
- **THEN** 系统保存答卷并更新该参与人状态

