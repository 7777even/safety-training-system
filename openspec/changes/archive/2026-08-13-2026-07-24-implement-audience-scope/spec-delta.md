# Spec Delta：common-audience-scope

## ADDED Requirements

### Requirement: 参与范围定义与规则

系统必须支持按机构维护参与范围（TEMPLATE/BOUND），并维护 INCLUDE/EXCLUDE 规则；规则主体类型为
ALL、DEPT、TAG、POSITION、USER、AUDIENCE。

#### Scenario: 创建 BOUND 范围

- GIVEN 机构存在
- WHEN 管理员创建 BOUND 范围并指定 businessType/businessId/purpose
- THEN 持久化成功，同机构同业务同 purpose 不可重复有效记录

#### Scenario: 排除优先

- GIVEN 用户同时命中 INCLUDE 与 EXCLUDE
- WHEN 解析参与范围
- THEN 该用户最终为 EXCLUDE / 不在包含集

### Requirement: 动态与冻结解析

系统必须支持 DYNAMIC 实时解析与 FROZEN 冻结名单；冻结后准入读取 `sts_common_audience_member`，直至重新冻结。

#### Scenario: 冻结写入成员

- GIVEN FROZEN 模式范围已配置规则
- WHEN 调用冻结接口
- THEN 覆盖写入 audience_member，后续 contains 以成员表为准

### Requirement: 统一人员选择

系统必须提供机构内人员分页选择能力，支持关键词与部门/标签/岗位过滤。

#### Scenario: 按标签筛选

- GIVEN 成员绑定标签
- WHEN 人员选择传入 tagId
- THEN 仅返回该标签下启用成员对应用户

### Requirement: 开课准入校验

当开课绑定 audienceId 时，自报名与管理员指派必须校验用户处于包含结果中。

#### Scenario: 不在范围不可报名

- GIVEN 开课 audienceId 指向有效范围且用户不在包含集
- WHEN 自报名
- THEN 业务失败，不得创建报名记录
