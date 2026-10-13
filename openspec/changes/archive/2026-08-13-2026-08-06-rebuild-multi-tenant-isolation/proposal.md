# Proposal：重构为多租户隔离体系

## 背景

现有机构模型把平台、园区、企业、培训机构塞进一棵 `sts_org_organization` 树，配合六类角色数据范围
（`ALL`/`ORGANIZATION`/`DEPT`/`DEPT_AND_CHILDREN`/`CUSTOM`/`SELF`）表达可见性。这套设计混合了机构、部门和个人三个维度，
业务查询无法统一执行，实际落地情况是：

- `DefaultOrganizationService.getDefaultOrganizationId()` 把 `DEFAULT` 占位机构 ID 硬编码返回，全后端 **75 处** 调用，
  素材、课程、报名、学习进度、工作台、统计、导出全部归属同一个占位机构——**租户隔离事实上没有实现**。
- 58 个 Controller 中只有 `OrganizationServiceImpl` 与 `ExamAttemptQueryServiceImpl` 两处调用
  `DataScopeService.resolve()`，
  其余业务查询没有任何数据范围过滤。
- 知识点、题库分类、档案导出任务、证书签发规则等接口直接以 `@RequestParam Long organizationId` 接收机构 ID，
  由客户端指定归属，构成越权读取通道。
- `DataScopeServiceImpl` 的 `ORGANIZATION` 分支取 `role.getOrganizationId()`（角色所属机构）而非用户所属机构，语义错误。
- `us_sys_user` 没有机构列，用户归属只能经 `dept_id → us_sys_dept.organization_id` 间接推导。

人工已确认改为多租户体系：**每个企业是一个租户，租户内资源完全隔离**；另有一个特殊的监管平台租户，
它不承载课程、考试、培训等业务概念，但可以查看全部企业租户的数据。原有机构层级设计整体废弃。

## 目标

1. 将机构树收敛为扁平租户模型：租户即企业，另设监管平台租户一类。
2. 在数据访问层建立强制租户隔离，使业务代码无需逐处手写机构过滤条件即可保证不跨租户。
3. 让监管平台租户可以按租户查看其它企业的统计与业务明细，且不破坏普通租户之间的隔离。
4. 建立以企业编码为入口的登录租户识别，使登录账号在租户内唯一。
5. 删除机构层级、六类角色数据范围、监管关系表和默认机构占位等已废弃结构与代码。

## 用户价值

- 各企业的课程、考试、素材、培训、档案、证书、人员和部门互不可见，满足多企业共用一套系统的基本前提。
- 监管平台可以横向掌握所有企业的培训与考试执行情况，并下钻到具体业务记录进行核查。
- 企业内部账号命名不再受其它企业占用影响，开户不需要人工协调账号前缀。
- 隔离由框架统一注入，新增业务域不会因为漏写过滤条件而产生越权。

## 功能范围

- 租户主数据：租户 CRUD、启用停用、企业编码唯一性、监管平台租户的特殊约束。
- 租户上下文：登录态解析当前租户、请求级上下文传递、监管平台按目标租户切换视角。
- 强制隔离：数据访问层统一注入租户条件，明确参与隔离的表、豁免表和子表归属校验规则。
- 登录与账号：企业编码 + 工号 + 密码登录，认证凭证标识改为租户内唯一的复合值。
- 用户与组织：`us_sys_user` 增加租户列并吸收机构成员档案字段，删除 `sts_org_member`；
  账号、部门、角色的租户内唯一性与一致性校验。标签与岗位保留并改为直接关联用户。
- 监管平台能力：跨租户统计汇总、按租户下钻明细的权限判定与审计。
- 废弃清理：机构层级、六类数据范围、`us_sys_role_dept`、`sts_org_regulatory_relation`、`DefaultOrganizationService`
  及其 75 处调用点。
- 存量处理：清空业务数据、重建租户与种子数据的 migration。
- 同步 `docs/api/` 契约、开发期 schema、自动化测试与工程记录。

## 非目标范围

- 不修改任何前端页面、Mock、联调代码或 UI；前端以 `docs/api/*.openapi.json` 为准另开。
- 不实现分库分表或按租户物理隔离数据库；本期为单库共享表 + 租户列隔离。
- 不实现一人同时任职多个租户、租户间账号迁移或运行时切换所属租户。
- 不实现租户级计费、配额、限流或独立域名部署。
- 不新增监管平台的预警、处置等业务能力；本期只解决可见性，规则类能力仍按路线图阶段 9 另行确认。
- 不改变部门树、岗位、标签、参与范围中 `DEPT` 等与角色数据范围无关的业务枚举。

## 影响范围

- `database/migration/`、`backend/sql/` 全部 schema 文件
- `backend/sts-service-admin` 全部 16 个业务域、58 个 Controller 及认证、缓存、数据访问配置
- `backend/sts-service-admin-init` 种子数据
- `docs/api/` 全部受影响 Contract
- `openspec/changes/2026-08-06-enforce-organization-hierarchy-data-scope`（方向作废，需废止或删除）
- 相关 QA、Retro 和进度记录

## 破坏性变化

- `sts_org_organization` 删除 `parent_organization_id`，`org_type` 由四值收敛为 `ENTERPRISE` / `REGULATOR`。
- 删除 `sts_org_regulatory_relation` 表。
- 删除 `us_sys_role.data_scope` 字段与 `us_sys_role_dept` 表；角色 API 删除 `dataScope` 与 `customDepts`。
- `us_sys_user` 新增 `organization_id`；唯一键由 `(user_code, deleted)` 改为 `(organization_id, user_code, deleted)`。
- 登录请求新增企业编码参数；`us_sys_auth_identity` 中 `local` 凭证的 `identifier` 由 `user_code`
  改为 `{orgCode}:{userCode}`。
- 删除 `sts_org_member` 表与机构成员相关接口；档案字段并入 `us_sys_user`，`employee_no` 由 `user_code` 取代；
  标签与岗位关联表外键由 `org_member_id` 改为 `user_id`。
- 以 `@RequestParam organizationId` 接收机构 ID 的接口删除该参数，归属一律由服务端租户上下文解析。
- 清空全部业务数据并删除 `DEFAULT` 占位机构；存量业务记录不迁移。
