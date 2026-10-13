# Design：机构主数据管理

## 1. 依据与边界

- 数据模型：`openspec/changes/archive/2026-07-22-design-safety-training-mysql-schema/`。
- 现有表：`backend/sql/init/schema-org.sql`；后续结构变化通过 `backend/sql/migration/` 交付 Admin 增量。
- 现有成员与数据范围：`openspec/changes/2026-07-24-implement-org-member-data-scope/`。
- API 规范：`docs/architecture/openapi-contract-writing-guide.md`、`docs/architecture/api-standard.md`。
- Git 身份为 `lims`，本 Change 仅实施后端、OpenAPI 和相关工程文档；前端另开。

`sts_org_organization` 是机构主数据聚合根。园区、企业和培训机构不是与“机构”并列的模型，而是由 `org_type` 区分的机构类型：

```text
机构 Organization
├── PLATFORM：平台运营主体
├── PARK：园区或监管主体
├── ENTERPRISE：企业
└── TRAINING_INSTITUTION：培训机构
```

部门 `us_sys_dept`、成员 `sts_org_member`、监管关系、资源授权和业务聚合保存机构 ID，不复制机构名称作为当前主数据。
需要历史不可变性的考试人员、培训参与和档案继续使用既有快照字段，不因机构名称修改而重写历史快照。

## 2. 现有数据模型

本 Change 复用既有 `sts_org_organization`，不修改表结构：

| 字段                       | 语义                                            |
|--------------------------|-----------------------------------------------|
| `organization_id`        | 机构主键                                          |
| `org_code`               | 全局稳定编码，逻辑删除维度内唯一                              |
| `org_name`               | 机构显示名称                                        |
| `org_type`               | PLATFORM/PARK/ENTERPRISE/TRAINING_INSTITUTION |
| `parent_organization_id` | 可选上级机构                                        |
| `contact_user_id`        | 可选联系人用户                                       |
| `status`                 | 0 禁用，1 正常                                     |
| `description`            | 机构说明                                          |
| 审计、`deleted`、`version`   | 审计、逻辑删除和乐观锁                                   |

`DEFAULT` 是课程阶段产生的历史兼容记录，不代表真实企业。它继续承载已有历史归属，但不能作为新机构、监管关系、跨机构授权和计划参与机构
的可选项。

## 3. 领域规则

### 3.1 编码与类型

- `org_code` 必填、去除首尾空白后保存，格式暂定为大写字母、数字、下划线和短横线，长度 2～64。
- 编码创建后不可修改，避免外部对接和审计引用漂移。
- `org_name` 必填，长度不超过 100；名称允许修改。
- 类型只允许 `PLATFORM`、`PARK`、`ENTERPRISE`、`TRAINING_INSTITUTION`。
- 类型创建后不可修改，避免已经形成的监管、授权和业务职责被隐式改变；确需纠正时由人工数据治理流程另开 Change。

### 3.2 层级

- `parent_organization_id` 可为空；非空时父机构必须存在且启用。
- 机构不能以自身或其后代作为上级，更新上级时必须检测完整祖先链并拒绝成环。
- 本期不根据父子类型组合推导监管关系；园区监管企业必须继续使用 `sts_org_regulatory_relation` 明确表达。
- 移动机构上级不会自动修改部门、成员、监管关系或资源授权。

### 3.3 联系人

- `contact_user_id` 可为空。
- 非空时用户必须存在且未删除。
- 暂定：联系人可以在创建机构后再绑定；联系人是否必须已经是该机构成员不作强制，因为机构需要先创建，成员才能归属机构。
- 更新联系人时，如果该用户已经有主部门或机构成员记录，则必须与目标机构一致；尚未分配组织的有效用户允许作为建档联系人。

### 3.4 状态与引用保护

- 新建机构状态只能为启用或禁用，默认启用。
- 被禁用机构不可用于新建部门、成员、监管关系、资源授权以及新增业务聚合；历史查询和快照仍可读取。
- 禁用前必须确认不存在有效部门、有效成员、有效监管关系、有效资源授权或未结束业务实例。引用范围至少覆盖课程、素材、考试、培训计划/
  执行、问卷、评价、档案、证书、运营任务和监管预警等当前正式表。
- 本期不开放删除接口。删除会影响大量逻辑引用和历史审计，后续如有数据清理需求必须创建独立 Change。
- `DEFAULT` 禁止修改编码、名称、类型、上级、联系人和状态，避免破坏历史兼容；只允许查询。

## 4. 权限与数据范围

新增平台级功能权限：

| 权限                      | 能力             |
|-------------------------|----------------|
| `org:organization:list` | 查询机构分页与详情      |
| `org:organization:add`  | 创建机构           |
| `org:organization:edit` | 修改机构基本信息、层级和状态 |

权限规则：

1. Controller 使用 `@SaCheckPermission` 做真实后端校验。
2. `superAdmin` 沿用既有精确账号编码旁路。
3. 其他账号只有被显式授予相应权限才能调用，不根据角色名称、`role_level` 或机构类型提供隐含特权。
4. 查询、创建和修改除校验功能权限外，还必须调用 `DataScopeService.resolve(userId, permissionCode)` 解析既有角色数据范围。
5. `bypass=true` 或 `all=true`：可以查询全部机构；具有 add 权限时可以创建机构；具有 edit 权限时可以完整维护非
   `DEFAULT` 机构，包括名称、联系人、描述、上级和状态。编码、类型创建后仍不可修改。
6. `ORGANIZATION`：只能查询 `organizationIds` 中的机构；具有 edit 权限时只允许修改机构名称、联系人和描述。
7. `DEPT`、`DEPT_AND_CHILDREN`、`CUSTOM`、`SELF`：机构记录无法按部门拆分，因此机构主数据查询退化到有效机构成员的
   `principalOrganizationId`；具有 edit 权限时同样只允许修改名称、联系人和描述。
8. 非 `all`、非 `bypass` 范围即使具有 add 权限也不能创建机构，返回 HTTP 403。
9. 园区可查看的被监管企业不能从父子层级或普通数据范围推导；监管业务继续通过 `sts_org_regulatory_relation` 查询。能查看机构基础资料
   不代表拥有该机构课程、考试、培训或档案数据权限。
10. 列表默认排除 `DEFAULT`；仅 `bypass=true` 或 `all=true` 且显式 `includePlaceholder=true` 时返回，用于运维排查，
    不用于业务选择。

## 5. API Contract

扩展 `docs/api/org.openapi.json`，新增 `OrganizationController` tag：

| Method | Path                                         | 说明      | 权限                      |
|--------|----------------------------------------------|---------|-------------------------|
| GET    | `/org/organizations`                         | 分页查询机构  | `org:organization:list` |
| GET    | `/org/organizations/{organizationId}`        | 查询机构详情  | `org:organization:list` |
| POST   | `/org/organizations`                         | 创建机构    | `org:organization:add`  |
| PUT    | `/org/organizations/{organizationId}`        | 修改机构    | `org:organization:edit` |
| PUT    | `/org/organizations/{organizationId}/status` | 启用或禁用机构 | `org:organization:edit` |

分页条件：关键字（编码/名称）、机构类型、上级机构、状态、`includePlaceholder`。响应包含版本号，更新和状态变更必须提交
`version`，冲突返回 HTTP 409，防止覆盖并发修改。

建议错误码：

| 业务码  | HTTP | 含义                   |
|------|------|----------------------|
| 4040 | 404  | 机构不存在                |
| 4041 | 409  | 机构编码已存在              |
| 4042 | 400  | 机构类型非法               |
| 4043 | 400  | 上级机构无效               |
| 4044 | 409  | 机构层级形成循环             |
| 4045 | 409  | 机构已被业务引用，不能禁用        |
| 4046 | 409  | 默认占位机构不允许修改          |
| 4047 | 400  | 联系人无效或归属冲突           |
| 4048 | 409  | 机构版本冲突               |
| 4049 | 403  | 当前数据范围不允许创建机构或修改受限字段 |

OpenAPI 必须按 Controller 分类，每个接口具备 `summary`、`description`、参数/字段中文说明、请求及成功响应 example、
错误响应和 `x-permission`。

## 6. 后端设计

沿用现有单体模块边界：

- `com.sts.org.controller.OrganizationController`
- `com.sts.org.service.OrganizationService` / `OrganizationServiceImpl`
- `com.sts.org.mapper.OrganizationMapper`
- `com.sts.org.dto.*`、`com.sts.org.vo.OrganizationVO`

为避免无关搬迁，本 Change 暂不移动 `com.sts.course.entity.Organization`。机构 Mapper 可复用该 Entity；后续只有在确认领域边界重构时才独立
迁移实体包。

`OrganizationService` 负责：

- 唯一性、类型、联系人和父机构校验。
- 按当前操作权限解析 `DataScopeContext`，限制列表、详情、创建和编辑字段。
- 使用祖先链查询检测循环。
- 乐观锁更新。
- 状态变更和引用保护。
- `DEFAULT` 只读保护。

Controller 只负责参数接收、分页解析、鉴权和统一响应；Mapper 不向 Controller 泄漏。

## 7. TDD 与测试分层

### 零 DB

- `OrganizationControllerTest`：路径、分页默认值、筛选绑定、CRUD 委托、校验失败 400 且不进入 Service。
- `OrganizationControllerWebSliceTest`：统一 `Result`、HTTP 状态、参数错误和权限异常外壳。
- `OrganizationServiceImplTest`：ALL 创建与完整维护、本机构范围字段白名单、部门/本人范围退化到主机构、无范围拒绝、编码唯一、类型、
  父机构、循环、联系人、`DEFAULT` 保护、禁用引用保护、乐观锁冲突和关键未调用。

实现前先写失败测试并观察 Red，再以最少实现 Green，最后仅重构本 Task 产生的重复。

### 带 DB

`OrganizationMapperIT` 使用 `AbstractMysqlIntegrationTest` 和真实 schema，覆盖：

- Entity/列映射与审计填充。
- 编码唯一约束。
- 真实分页与条件过滤。
- 乐观锁冲突。
- 祖先链/后代查询。
- 机构状态更新及 `DEFAULT` 查询行为。

涉及真实 SQL 和乐观锁，必须执行：

```bash
cd backend
mvn -pl sts-service-admin -am test
mvn -pl sts-service-admin -am -Pintegration-test test
```

Docker 不可用时如实报告 IT 未执行，不得用零 DB 通过代替。

## 8. 实施顺序

1. 先扩展并校验 `docs/api/org.openapi.json`。
2. 按 TDD 实现机构查询、详情和创建。
3. 按 TDD 实现编辑、层级校验和联系人校验。
4. 按 TDD 实现状态变更、`DEFAULT` 保护和跨域引用保护。
5. 同步初始化权限、QA、Retro、OpenSpec Task、路线图和进度表。

## 9. 风险与取舍

- **跨域引用保护范围大。** 禁用检查必须覆盖当前正式业务表，不能只检查成员/部门；遗漏会让禁用机构继续产生业务数据。
- **默认机构历史债务。** 本 Change 阻止新增数据继续选择 `DEFAULT`，但不自动判断每条历史数据应迁往哪个真实机构。
- **功能权限与数据范围必须同时生效。** `ALL` 普通管理员可维护全部机构；本机构及更窄范围只能查看主机构并修改白名单字段，不能因为拥有
  edit 权限越权调整上级或状态。
- **实体包归属。** 暂时复用课程包中的 Organization 是最小改动；移动实体会扩大扫描、Mapper 和测试差异，不属于本 Task。
- **数据库结构。** 现有表足够支撑本期能力；如发现必须新增字段、索引或约束，应暂停并按 L4 取得人工确认后新增 migration。
