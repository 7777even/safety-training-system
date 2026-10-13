# 系统用户不可见 Unicode 输入校验 QA

日期：2026-08-13  
Owner：lims  
OpenSpec：`2026-08-13-reject-invisible-user-input`

## 范围

- 用户创建的全部可写字符串字段拒绝不可见 Unicode 控制、格式、私用、未分配及异常空白字符。
- 用户更新的可写字符串资料执行相同校验。
- 创建及重置密码只接受 64 位小写十六进制 SHA-256 摘要。
- 非法请求返回 HTTP 400、业务码 1001，且不进入 Service；不自动清洗、不处理历史数据。

## TDD 证据

- 首轮 Controller/Web Slice 目标测试共 23 个用例，新增 5 个行为断言按预期失败：不可见 `userCode`、不可见
  `jobTitle`、密码 BOM、非法摘要和 400/1001 外壳均被旧实现放行。
- Validator 测试在实现前因 `NoInvisibleCharacters` 约束尚不存在而测试编译失败。
- 完成最小实现后，Validator、Controller 和 Web Slice 目标测试 29/29 通过。

## 已通过验证

-
`mvn -pl sts-service-admin -Dtest=NoInvisibleCharactersTest,SysUserControllerTest,SysUserControllerWebSliceTest -Dsurefire.failIfNoSpecifiedTests=false test`
：29/29 通过。
- `mvn -pl sts-service-admin -am test`：609/609 通过，0 failure、0 error、0 skipped。
- Validator 覆盖 `Cc/Cf/Co/Cn`、异常 Unicode 空白、补充平面格式字符及正常中文、Emoji、组合字符和普通半角空格。
- DTO 元数据测试覆盖创建 7 个字段、更新 5 个字段和重置密码字段；密码正则固定为 `^[a-f0-9]{64}$`。
- 创建、更新、重置密码的契约级测试均验证 HTTP 400、业务码 1001，且 Service 未调用。
- `jq empty docs/api/system-admin.openapi.json`、`openspec validate 2026-08-13-reject-invisible-user-input` 和
  `git diff --check` 通过。

## 未运行项

- 未运行 MySQL IT。本 Change 不修改 Entity、Mapper、自定义 SQL、schema、索引或持久化事务，非法输入在 Controller
  参数校验阶段拒绝，不存在需由真库证明的持久化语义。

## 双轴 Review

- 规格符合性：通过。实现字段、禁止字符类别、正常文本边界、密码格式、错误响应和 OpenAPI 与已确认 Task 一致。
- 代码质量：通过。校验集中为可复用 Jakarta Validation 约束并按 Unicode code point 执行；未发现重复扫描逻辑、越界重构、
  新生产依赖、权限变化、数据库变化或前端改动。

## 当前结论

Task 验收标准、目标测试、Admin 零 DB 全量回归、OpenAPI、OpenSpec 和双轴 Review 均通过，可以关闭并归档 Change。
