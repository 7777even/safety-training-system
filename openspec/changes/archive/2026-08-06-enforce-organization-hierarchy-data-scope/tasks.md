# Tasks：收敛机构层级与机构树数据权限

> 状态：**已废止（2026-08-07），未实施**。被 2026-08-06-rebuild-multi-tenant-isolation 取代，见 proposal.md 顶部说明。
>
> Owner：lims

## Task 1：建立合法机构树与唯一所属机构

- 状态：待确认
- Owner：lims
- 输入：已确认的四类统一机构模型、固定父子组合、现有机构创建 API、机构成员模型和存量数据审计结果
- 输出：机构创建层级与权限校验；普通用户唯一所属机构；migration、schema、OpenAPI 与自动化测试
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：修改 `POST /api/org/organizations` 的父子类型、上级范围和错误契约；不新增前端接口
- 数据影响：调整 `sts_org_member` 当前归属唯一索引；保留 `DEFAULT` 历史例外；不自动猜测修复异常机构层级
- 验收标准：
  - [ ] 仅允许 PLATFORM 无上级、PARK→PLATFORM、ENTERPRISE→PARK、TRAINING_INSTITUTION→PARK/ENTERPRISE
  - [ ] `DEFAULT`、停用、已删除或范围外机构不能作为新机构上级
  - [ ] 普通用户只能有一个未删除所属机构，主部门和角色与该机构一致
  - [ ] `superAdmin` 可创建平台；普通用户只能在权限可见根节点内创建合法子机构
  - [ ] 非法父子组合、范围外上级和重复成员归属均返回稳定 HTTP 状态与业务码
  - [ ] `docs/api/org.openapi.json` 满足 Controller tag、中文字段说明、参数/请求/响应 example、错误码和 `x-permission`
- 测试方式：先增加失败的 Service/Controller 测试；最小实现通过；MySQL IT 覆盖唯一索引、真实层级和跨范围创建

## Task 2：将角色数据范围收敛为本平台、本园区、本机构

- 状态：待确认
- Owner：lims
- 输入：Task 1；现有角色 CRUD、六类 `data_scope`、`us_sys_role_dept`、DataScopeService
- 输出：三类数据范围配置与机构子树解析；存量迁移；删除 CUSTOM 结构和 Contract 字段
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：修改 `/api/system/roles` 创建、修改、分页和详情 Contract；`dataScope` 仅允许 PLATFORM/PARK/ORGANIZATION，删除
  `customDepts`
- 数据影响：ALL→PLATFORM，其余废弃范围→ORGANIZATION；删除 `us_sys_role_dept`；同步 schema 和种子
- 验收标准：
  - [ ] 本平台返回所属平台及全部后代，本园区返回所属园区及全部后代，本机构仅返回直接所属机构
  - [ ] 多角色仅合并授予当前功能权限的范围；无授权角色不得回退为本机构或全量
  - [ ] 缺少 PLATFORM/PARK 祖先时拒绝对应范围，`superAdmin` 全局旁路保持不变
  - [ ] 旧五类值不再被 API 接受，`customDepts`、相关 Java 类型/Mapper 和数据库表全部删除
  - [ ] migration 在异常存量数据上明确失败，在合法数据上可重复验证预期映射
  - [ ] `docs/api/system-admin.openapi.json` 与 Controller/DTO/VO 完全一致
- 测试方式：DataScopeService、SysRoleService、Controller/WebSlice 先 Red 后 Green；MySQL IT 覆盖 migration、递归机构树和真实约束

## Task 3：按机构树范围统一接入后端业务数据

- 状态：待确认
- Owner：lims
- 输入：Task 1～2；所有正式业务表、Service、Mapper、统计、导出和异步任务清单
- 输出：机构归属业务访问路径的数据范围接入；受影响 OpenAPI；完整回归、QA、Retro 和进度记录
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：不接受客户端传入可扩大范围的机构集合；如现有 Contract 描述旧数据范围则同步修改
- 数据影响：原则上不新增业务表字段；发现缺少稳定机构归属时暂停并提交人工决策，不按创建人当前部门猜测
- 验收标准：
  - [ ] 素材、课程、题库、试卷、考试、培训、评价、档案、证书、运营统计和监管数据均按解析机构集合过滤
  - [ ] 列表、详情、写入、删除、批量、统计、导出和异步任务不能通过 ID 或请求参数越权
  - [ ] 本人执行链、教师任务、监管关系、显式授权和发布快照等附加护栏未被机构树权限绕过
  - [ ] 统计先过滤事实再聚合；跨平台、跨园区和跨机构负例通过
  - [ ] 受影响零 DB 与 MySQL IT 全部通过，规格符合性和代码质量双轴 Review 结论均为“通过”
  - [ ] OpenAPI、tasks.md、QA、Retro 和进度表记录实际验收证据
- 测试方式：逐业务域 TDD；Admin 目标零 DB；涉及 Mapper/SQL/分页/统计/事务的目标 MySQL IT；OpenAPI 校验、
  `openspec validate --strict`、`git diff --check`

