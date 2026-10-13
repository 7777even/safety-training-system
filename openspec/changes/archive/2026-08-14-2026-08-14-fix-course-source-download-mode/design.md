# Design：统一课程源文件下载模式

## 1. 问题定位

课程定义侧的合法值由 `CourseCreateRequest`、`CourseUpdateRequest`、数据库注释和 OpenAPI 共同约束为：

```text
NONE / ALL / PARTIAL
```

学员下载链路却在两处使用了虚构值 `ALLOW`：

- `LearnerCourseResourceAssembler`：只有 `ALLOW` 才生成可下载状态和受控下载地址。
- `LearnerCourseDownloadService`：只有 `ALLOW` 才继续执行冻结附件归属与文件校验。

既有零 DB 测试和学员响应 OpenAPI 示例也使用 `ALLOW`，使错误实现形成了自洽但脱离真实输入的假绿闭环。

## 2. 统一判断规则

课程级门禁统一为：

| `sourceDownloadMode` | 课程级结果    | 后续处理                                        |
|----------------------|----------|---------------------------------------------|
| `NONE`               | 禁止       | 详情返回 `SOURCE_DOWNLOAD_DISABLED`；文件接口返回 5087 |
| `ALL`                | 允许进入附件校验 | 继续校验冻结附件归属、启用状态、当前素材、源文件和存储                 |
| `PARTIAL`            | 允许进入附件校验 | 仅冻结 `downloadAssets` 清单内且启用的附件可继续下载         |
| 空值或未知值               | 禁止       | 失败关闭，行为与 `NONE` 相同                          |

`ALL` 与 `PARTIAL` 在本次修复中的共同点是都不能被课程级门禁误拦截；具体文件仍必须属于开课冻结版本的
`downloadAssets`。这是现有课程版本快照能够稳定证明的授权边界。

本 Change 不扩展 `ALL` 的素材收集范围。若需要让 `ALL` 自动包含课程内容项、辅助材料等当前未进入
`downloadAssets` 的素材，必须先明确素材范围和重复/停用规则，再创建独立 Change。

## 3. 实现方式

在课程下载服务包内使用同一语义的允许集合：

```text
DOWNLOAD_ENABLED_MODES = { ALL, PARTIAL }
```

详情组装器和实际下载服务都以集合包含关系判断，避免再次把单一字符串常量误当作课程枚举。未知值不在集合中，自然失败关闭。

不新增公共生产依赖，不修改 Entity、Mapper、自定义 SQL 或数据库事务。

## 4. 测试策略

按 Red → Green → Refactor 执行：

1. 先把成功夹具由非法 `ALLOW` 改为 `ALL`，并新增 `PARTIAL` 成功用例，观察详情状态或下载服务测试失败。
2. 保留 `NONE` 禁止用例；未知值继续由允许集合的失败关闭实现保证，不新增 `ALLOW` 专项回归用例。
3. 最小修改两处生产判断使测试通过。
4. 运行 Admin 模块受影响零 DB 测试及全量零 DB 测试。

本 Change 不涉及 Entity、Mapper、自定义 SQL、schema、索引、逻辑删除、分页、乐观锁或真实持久化事务，默认不运行带 DB IT。

## 5. API Contract

`docs/api/course.openapi.json` 中课程创建、更新 schema 已正确声明 `NONE/ALL/PARTIAL`，保持不变。

修正学员课程响应 schema：

- 枚举改为 `NONE`、`ALL`、`PARTIAL`。
- 中文说明明确三种取值与失败关闭语义。
- 示例不再使用 `ALLOW`。

接口 URL、HTTP Method、响应字段结构、错误码和 `x-permission` 均不改变。

## 6. 风险与取舍

- 修复后，原本被错误拦截的 `ALL`、`PARTIAL` 课程附件恢复可下载，属于既有配置语义的恢复，不是权限扩大。
- 继续强制冻结附件归属校验，不能仅凭 `ALL` 下载任意素材。
- 不兼容虚构的 `ALLOW` 值；课程创建接口从未接受该值，测试夹具中的错误值直接清理。
