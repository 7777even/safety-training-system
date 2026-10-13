## 1. 依据

已评审 DDL：`sts_org_regulatory_relation`、`sts_common_resource_org_grant`；design §5～6.1。

## 2. 业务规则（本期确认口径）

1. 资源所属机构可向其他机构授予 VIEW/USE/MANAGE；同一资源对同一被授权机构仅一条有效记录。
2. 等级：MANAGE 包含 USE、VIEW；USE 包含 VIEW。
3. 有效期：`valid_from`/`valid_to` 为空表示不限制对应端；当前时间须落在有效区间且 `status=1`。
4. 监管关系：独立记录监管机构与被监管机构；不自动等于资源授权。课程跨机构引用**必须**有资源授权；监管关系供后续培训/监管模块复用，本期提供
   CRUD + 查询。
5. 资源类型本期校验枚举含 COURSE；其他类型允许写入以便后续复用，课程接入只检查 COURSE。

## 3. API

| 能力          | 路径                           | 权限                  |
|-------------|------------------------------|---------------------|
| 监管关系        | `/org/regulatory-relations`  | `org:regulatory:*`  |
| 资源授权        | `/common/resource-grants`    | `common:grant:*`    |
| 校验（内部/可选调试） | Service 层；可选 `GET .../check` | `common:grant:list` |

## 4. 课程最小接入

`CourseService` 详情/编排引用：当请求机构 ≠ course.organizationId 时，要求 grantee 对 COURSE 资源具备至少
USE。本机构资源不受影响。列表默认仍按本机构过滤；跨机构目录检索本期不做完整选课大厅，只保证授权校验钩子可用。

## 5. 错误码

- 监管关系：`4200-4249`
- 资源授权：`4250-4299`
