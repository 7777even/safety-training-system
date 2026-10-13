# Tasks：机构主数据管理

> 状态：待人工确认。确认前不得修改业务代码、API Contract、权限种子或数据库。
>
> Owner：lims

## Task 1：完成机构登记与查询能力

- 状态：进行中（2026-07-27）
- Owner：lims
- 输入：已评审机构数据模型、既有 `sts_org_organization` 表、确认后的 proposal/design、OpenAPI 编写规范
- 输出：机构分页、详情、创建能力；`OrganizationController`/Service/Mapper/DTO/VO；`docs/api/org.openapi.json`
  机构契约；机构查询和新增权限点
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：新增 `GET /org/organizations`、`GET /org/organizations/{id}`、`POST /org/organizations`
- 数据影响：复用现有机构表，不新增表、不迁移历史数据；新增真实机构记录
- 验收标准：
  - [ ] 园区、企业、培训机构和平台记录可按合法类型创建，编码全局稳定唯一
  - [ ] `ALL` 或 bypass 范围可查询全部机构并在具有 add 权限时创建机构
  - [ ] ORGANIZATION 及更窄范围只能查询有效机构成员所属主机构
  - [ ] 列表可按关键字、类型、上级和状态分页筛选，默认不返回 `DEFAULT`
  - [ ] 详情与列表只返回契约声明字段，不泄漏内部信息
  - [ ] 父机构与联系人均执行存在性、状态和归属校验
  - [ ] `org:organization:list/add` 权限由 Controller 真实执行
  - [ ] OpenAPI 满足 Controller tag、接口/字段中文说明、example、错误码及 `x-permission`
- 测试方式：先观察 Controller/Service 零 DB 测试失败，再实现通过；执行受影响零 DB 测试；
  `OrganizationMapperIT` 验证真实分页、唯一约束和审计填充
- 当前验证（2026-07-27）：
  - Red：目标测试首次因 Organization Controller/Service/DTO 尚不存在而编译失败，已观察预期失败
  - Green：`OrganizationControllerTest` 2 个、`OrganizationServiceImplTest` 4 个，共 6 个零 DB 用例通过
  - IT：`OrganizationMapperIT` 已覆盖字段/审计、唯一约束和真实分页；运行时因本机无
    `/var/run/docker.sock`，Testcontainers 无法找到 Docker，尚未形成通过证据
  - Review：规格范围和代码质量轻量自审无越界实现；因必需 IT 未通过，Task 保持“进行中”

## Task 2：完成机构维护、层级和状态保护能力

- 状态：待确认
- Owner：lims
- 输入：Task 1；已确认的 ALL/本机构维护边界、机构编码/类型不可变规则和 `DEFAULT` 处理规则
- 输出：机构编辑、上级调整、启停用；循环检测；乐观锁；默认机构只读保护；跨域引用保护；编辑权限点和 OpenAPI
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：新增 `PUT /org/organizations/{id}`、`PUT /org/organizations/{id}/status`
- 数据影响：修改机构名称、上级、联系人、描述和状态；不开放删除，不改表结构
- 验收标准：
  - [ ] 编码和机构类型创建后不可修改
  - [ ] ALL 或 bypass 范围可调整非默认机构的名称、联系人、描述、上级和状态
  - [ ] 本机构及更窄范围只能修改本机构名称、联系人和描述；修改上级或状态返回 403
  - [ ] 机构不得挂到自身或后代节点，父机构无效时拒绝更新
  - [ ] 更新和状态变更使用 `version`，并发冲突返回 HTTP 409
  - [ ] `DEFAULT` 机构所有修改均被拒绝
  - [ ] 存在有效部门、成员、监管/授权关系或未结束业务实例时不得禁用
  - [ ] 禁用机构不能再用于新建部门、成员、监管关系、授权或业务聚合
  - [ ] 不根据角色名称、级别或机构类型产生隐含权限
- 测试方式：先观察 Service 护栏测试失败，再实现通过；执行零 DB 测试；IT 覆盖真实乐观锁、层级查询和状态更新

## Task 3：完成基础能力验收与文档闭环

- 状态：待确认
- Owner：lims
- 输入：Task 1～2；OpenSpec 验收标准；路线图和进度表
- 输出：完整目标测试、双轴 Review、QA、Retro、OpenSpec Task 状态及路线图/进度表验收证据
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：校验 `docs/api/org.openapi.json` 与实现一致
- 数据影响：验证现有 migration 顺序和 schema 真相源，不创建第二份 DDL
- 验收标准：
  - [ ] 零 DB 与相关 IT 全部通过，实际命令和用例结果记录在 QA
  - [ ] 从“创建园区/企业 → 建部门 → 建成员 → 建监管关系/授权”的后端纵向链路可验证
  - [ ] 规格符合性 Review 与代码质量 Review 结论均为“通过”
  - [ ] 路线图、进度表、OpenSpec 和 API Contract 状态一致
  - [ ] Retro 记录本次“表存在不等于业务能力完成”的原因和防复发措施
- 测试方式：
  - `mvn -pl sts-service-admin -am test`
  - `mvn -pl sts-service-admin -am -Pintegration-test test`
  - `openspec validate 2026-07-27-implement-organization-master-data`
  - `git diff --check`
