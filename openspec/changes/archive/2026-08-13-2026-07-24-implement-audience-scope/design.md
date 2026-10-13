## 1. 依据

沿用已评审 DDL：`sts_common_audience` / `_rule` / `_member`（`01_org_common.sql`）。

解析规则（已确认）：

1. `INCLUDE` 并集后，减去 `EXCLUDE` 命中用户；**排除优先于包含**。
2. `subject_type`：`ALL`（`subject_id=0`）、`DEPT`、`TAG`、`POSITION`、`USER`、`AUDIENCE`（嵌套引用另一范围的当前有效包含集）。
3. `resolve_mode=DYNAMIC`：每次预览/准入实时按规则计算；不强制写 `audience_member`。
4. `resolve_mode=FROZEN`：调用冻结接口时将当时解析结果写入 `audience_member`；之后准入只读成员表，直至显式重新冻结。
5. `audience_kind=TEMPLATE`：`business_type/business_id` 可空；`BOUND`：二者必填，且同机构同业务同 purpose 唯一。
6. 人员选择仅限目标机构内启用成员对应的用户；不跨机构圈人（跨机构由 BASE-04 授权场景处理）。

## 2. 数据

正式 migration 创建三表，字段/索引与已评审 DDL 一致。开发期快照：`backend/sql/schema-common.sql`，IT 装载。

## 3. 包与错误码

- 包：`com.sts.commonbiz.audience`（避免与 `com.sts.common` 工具包冲突）或 `com.sts.audience`。选定：`com.sts.audience`。
- 错误码段：`4100-4199`（audience，落在 org 扩展 4000 段内细分）。

## 4. API（摘要）

| 能力           | 路径                                            | 权限                     |
|--------------|-----------------------------------------------|------------------------|
| 范围分页/详情/CRUD | `/common/audiences`                           | `common:audience:*`    |
| 替换规则         | `PUT /common/audiences/{id}/rules`            | `common:audience:edit` |
| 预览解析         | `POST /common/audiences/{id}/preview`         | `common:audience:list` |
| 冻结/重冻结       | `POST /common/audiences/{id}/freeze`          | `common:audience:edit` |
| 冻结成员分页       | `GET /common/audiences/{id}/members`          | `common:audience:list` |
| 是否包含用户       | `GET /common/audiences/{id}/contains?userId=` | `common:audience:list` |
| 统一人员选择       | `GET /common/person-picker`                   | `common:person:list`   |

## 5. 课程接入

`CourseEnrollmentService`：开课 `audienceId` 非空时，自报名与指派前调用
`AudienceResolveService.contains(audienceId, userId)`；不通过则业务错误。无 `audienceId` 时保持现有行为（兼容存量）。

## 6. 风险与取舍

- `AUDIENCE` 嵌套限制深度（默认 ≤3），防环。
- `DEPT` 含子部门：按部门树 `ancestors`/`parent` 展开（与系统部门表一致）。
- FROZEN 范围编辑规则后不自动失效成员；必须再次 freeze 才更新名单（在 description 与 API 标明）。
