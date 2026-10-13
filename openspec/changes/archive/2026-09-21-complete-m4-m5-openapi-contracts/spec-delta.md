# Spec Delta：M4/M5 OpenAPI 契约完整性

## ADDED Requirements

### Requirement：当前公开 API SHALL 具有完整机器可读契约

#### Scenario：Controller 已公开但 OpenAPI 遗漏路径

- **GIVEN** 当前后端 Controller 已公开一个 M4/M5 接口
- **WHEN** 调用方读取对应 `docs/api/*.openapi.json`
- **THEN** SHALL 找到相同 URL 与 Method 的 operation
- **AND** operation SHALL 使用真实 Controller tag、权限元数据、请求/响应 Schema、示例和错误响应
- **AND** SHALL NOT 把运行时既有接口误当作新增业务能力。

#### Scenario：业务对象通过统一外壳返回

- **GIVEN** 接口返回 DTO、VO、列表、分页、标量、ID 或 void
- **WHEN** OpenAPI 表达成功响应
- **THEN** `data` SHALL 具有对应的具体机器可读类型
- **AND** example SHALL 只作为具体 Schema 的示例，SHALL NOT 替代 Schema 定义。

### Requirement：已删除公开能力 SHALL 从 OpenAPI 完整移除

#### Scenario：M4/M5 最小化重构删除接口

- **GIVEN** 已确认规格删除人工评价写入、综合计算批次或其他超范围公开能力
- **WHEN** 检查对应 OpenAPI
- **THEN** path、仅服务该 path 的 request/query/VO/page/result/response 组件 SHALL 全部不存在
- **AND** SHALL 保留当前公开接口仍真实引用的公共组件。

### Requirement：登录态接口 SHALL 明确表达认证要求

#### Scenario：接口要求登录但没有功能权限注解

- **GIVEN** 当前 Controller 从登录态派生用户身份且没有 `@SaCheckPermission`
- **WHEN** OpenAPI 表达该 operation
- **THEN** SHALL 声明 Bearer security 与 `x-permission: authenticated`
- **AND** 条件可见性 SHALL 写入 description
- **AND** SHALL NOT 为文档完整性虚构新的功能权限码。

### Requirement：证书隐藏字段 SHALL NOT 继续通过公开模型读写

#### Scenario：创建证书签发规则

- **GIVEN** 复杂条件值和领取说明已由确认的 M4 最小闭环隐藏
- **WHEN** 调用方提交证书签发规则创建请求
- **THEN** 公开请求模型 SHALL NOT 声明或写入 `conditionValue`、`collectionInstruction`
- **AND** 数据库兼容列及历史值 SHALL 保留。

#### Scenario：查询已签发证书

- **GIVEN** 撤销能力已从 M4 最小闭环删除
- **WHEN** 管理员或学员查询证书
- **THEN** 公开响应模型 SHALL NOT 返回 `revokedTime`
- **AND** 数据库兼容列及历史值 SHALL 保留。

## MODIFIED Requirements

### Requirement：M4/M5 后端契约交付 SHALL 可直接供调用方生成类型

M4/M5 的“前端以 OpenAPI 为准另开”要求修改为：对应 OpenAPI 必须先完整描述当前 Controller、DTO、VO、权限和错误响应；只有
example、
没有具体业务 Schema 的契约不视为完成。

## REMOVED Requirements

无。本 Change 不移除业务需求，只清理已删除能力遗留的契约组件。
