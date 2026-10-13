# Spec Delta：拒绝用户资料中的不可见 Unicode 字符

## 新增

### Requirement：系统用户文本输入必须可辨认

系统必须在创建和更新用户时拒绝不可见 Unicode 控制、格式及异常分隔字符，不得自动删除或替换后继续保存。

#### Scenario：拒绝用户编码中的零宽字符

- **GIVEN** 管理员具有用户新增权限
- **WHEN** 创建请求的 `userCode` 包含 `U+200B` 或其他已禁止的不可见字符
- **THEN** 系统返回 HTTP 400、业务码 1001
- **AND** 不执行用户查重、用户写入或凭证写入

#### Scenario：拒绝用户姓名和可选资料中的不可见字符

- **GIVEN** 创建或更新请求的 `userName`、`phone`、`email`、`jobTitle` 或其他已纳入字段包含禁止字符
- **WHEN** 请求进入参数校验
- **THEN** 系统返回 HTTP 400、业务码 1001
- **AND** 不调用用户 Service

#### Scenario：允许正常可见文本

- **GIVEN** 用户资料包含正常中文、拉丁字母、数字、标点、Emoji、组合字符或普通半角空格
- **WHEN** 请求进入不可见字符校验
- **THEN** 本规则不拒绝请求
- **AND** 字段原有非空、邮箱、枚举及其他业务校验继续执行

### Requirement：用户密码摘要必须符合既有契约

系统必须在创建用户和重置密码时只接受 64 位小写十六进制 SHA-256 摘要，并拒绝任何不可见字符。

#### Scenario：拒绝混入不可见字符的密码摘要

- **GIVEN** 密码字符串肉眼呈现为摘要，但其中混入零宽或控制字符
- **WHEN** 管理员创建用户或重置密码
- **THEN** 系统返回 HTTP 400、业务码 1001
- **AND** 不执行 BCrypt 编码或凭证写入

#### Scenario：接受合法密码摘要

- **GIVEN** 密码是 64 位小写十六进制字符串
- **WHEN** 管理员创建用户或重置密码
- **THEN** 参数校验通过
- **AND** Service 继续按既有 BCrypt(SHA-256(原始密码)) 语义处理

## 修改

- `POST /api/system/users` 的全部可写字符串字段增加不可见 Unicode 校验，`password` 同时落实既有摘要格式校验。
- `PUT /api/system/users/{userId}` 的全部可写字符串字段增加相同校验。
- `PUT /api/system/users/{userId}/reset-password` 的 `password` 增加相同不可见字符与摘要格式校验。

## 不变

- 用户接口 URL、HTTP Method、响应结构、权限码和统一参数错误码不变。
- 用户编码仍按租户内唯一，凭证标识仍为 `{orgCode}:{userCode}`。
- 密码仍由调用方提交 SHA-256 摘要，服务端继续 BCrypt 后保存。
- 不修改数据库结构、历史用户数据或登录认证规则。

