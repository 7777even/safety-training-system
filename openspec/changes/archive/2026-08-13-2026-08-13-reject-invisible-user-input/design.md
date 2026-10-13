# Design：拒绝用户资料中的不可见 Unicode 字符

## Context

`POST /api/system/users`、`PUT /api/system/users/{userId}` 和
`PUT /api/system/users/{userId}/reset-password` 使用 Jakarta Validation，但现有 DTO 没有不可见 Unicode 约束。`userCode`
还会参与租户内唯一性查询与 `{orgCode}:{userCode}` 凭证标识拼装，因此不能采用静默 `trim` 或删除字符的方式修复，否则校验、落库和登录可能使用不同文本。

当前 Git 身份为 `lims`，本 Change 只实施后端、`docs/api/system-admin.openapi.json` 及后端工程记录。

## 校验口径

输入字符串只要包含以下任一代码点即判为非法，不做自动清洗：

1. Unicode General Category 为 `Cc`（控制）、`Cf`（格式）、`Cs`（代理）、`Co`（私用）或 `Cn`（未分配）的字符。
2. 除普通半角空格 `U+0020` 之外的 Unicode 空白/分隔字符，包括不换行空格、行分隔符和段落分隔符。

该口径覆盖但不限于：

- `U+200B` ZERO WIDTH SPACE。
- `U+200C` ZERO WIDTH NON-JOINER。
- `U+200D` ZERO WIDTH JOINER。
- `U+2060` WORD JOINER。
- `U+FEFF` ZERO WIDTH NO-BREAK SPACE/BOM。
- `U+200E/U+200F` 及 `U+202A`～`U+202E`、`U+2066`～`U+2069` 双向文本控制符。

普通汉字、拉丁字母、数字、标点、Emoji、组合附加符号和 `U+0020` 不因本规则被拒绝。字段原有的非空、邮箱、枚举等约束继续生效。

## 适用字段

### UserCreateRequest

- `userCode`
- `userName`
- `phone`
- `password`
- `memberType`
- `email`
- `jobTitle`

### UserUpdateRequest

- `userName`
- `phone`
- `memberType`
- `email`
- `jobTitle`

### ResetPasswordRequest

- `password`

`password` 在不可见字符约束之外，必须匹配 `^[a-f0-9]{64}$`。这是落实既有 OpenAPI Contract，不改变调用方应提交 SHA-256
摘要的语义。

## 技术设计

1. 在 Admin 模块建立 Jakarta Validation 自定义约束注解及 `ConstraintValidator`，按 Unicode code point 遍历，避免按 UTF-16
   `char` 判断时遗漏补充平面字符。
2. `null` 交由 `@NotBlank`、`@Email` 等字段原有约束处理；不可见字符 Validator 对 `null` 返回通过，避免重复职责。
3. DTO 字段使用同一约束注解，不在 Controller 或 Service 重复手写扫描逻辑。
4. 参数错误继续由现有 `HttpApiExceptionHandler` 转换为 HTTP 400、业务码 1001；错误信息指出对应字段不得包含不可见或控制字符。
5. 不自动 `trim`、Unicode normalize 或删除字符。调用方必须显式修正输入，后端保存的值与通过校验的值完全一致。

## API Contract

更新 `docs/api/system-admin.openapi.json`：

- 创建、更新、重置密码 operation 的 description 明确不可见字符拒绝规则和 400/1001。
- 对上述字符串 property 补充中文校验说明。
- `password` 保持 `pattern=^[a-f0-9]{64}$`、`minLength=64`、`maxLength=64` 和 `writeOnly=true`。
- 不新增 URL、HTTP Method、权限或业务错误码。

## 数据与权限影响

- 不修改 Entity、Mapper、自定义 SQL、schema 或索引，不创建 migration。
- 不改变 `sys:user:add`、`sys:user:edit`、`sys:user:resetPwd` 权限。
- 非法请求在 Service 调用前失败，不产生用户、凭证、角色、标签或岗位写入。

## TDD 与测试设计

### 零 DB

1. 先扩展 `SysUserControllerTest`，分别提交在 `userCode`、`userName`、可选资料和密码中含 `U+200B`/格式控制字符的请求，观察现有实现仍进入
   Service 或返回非预期结果。
2. 扩展 `SysUserControllerWebSliceTest`，验证非法创建/更新/重置密码返回 HTTP 400、业务码 1001，且 Service 未调用。
3. 增加 Validator 单元测试，覆盖 `Cc`、`Cf`、异常 Unicode 空白、补充平面异常代码点，以及正常中文、Emoji、组合字符和普通半角空格。
4. 覆盖密码不是 64 位小写十六进制、混入零宽字符和合法摘要三类行为。

### 带 DB

本 Change 不修改 Entity、Mapper、SQL、schema、索引或持久化事务，非法输入在 Controller 参数校验阶段被拒绝；默认不新增或运行机械重复的数据库
IT。若实现需要改变持久化路径，必须暂停并升级设计。

## 风险与取舍

- 不可见字符不存在完全稳定的视觉定义。本 Change 使用明确的 Unicode 类别与分隔规则，避免依赖字体或渲染环境。
- 私用字符可能在特定字体中可见，但用于账号与人员基础资料会降低跨系统可辨认性，因此默认拒绝。
- 不做自动清洗，避免用户输入在无感知情况下变成另一个账号或姓名。
- 历史脏数据不在本 Change 内自动修改，避免凭证标识和外部引用失配。

## 待人工确认

1. 同意适用字段覆盖用户创建/更新的全部可写字符串，而不仅是 `userCode`、`userName` 和 `password`。
2. 同意拒绝 Unicode `C*` 类别及除 `U+0020` 外的 Unicode 空白/分隔字符，同时保留普通半角空格。
3. 同意密码严格执行既有 64 位小写 SHA-256 十六进制契约。
4. 同意不自动清洗且不处理历史数据；历史脏数据治理另开 Change。

