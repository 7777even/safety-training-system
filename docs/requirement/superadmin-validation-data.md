# superAdmin 业务验证样例数据

本文只提供一套可重复录入的测试数据，不直接写入数据库。录入前请确认当前环境是开发或测试环境。

## 1. 创建企业租户

在“系统管理 → 机构组织 → 租户管理”创建：

| 字段      | 示例值                 |
|---------|---------------------|
| 企业编码    | `SAFE_ACME`         |
| 租户名称    | 安全生产示范企业            |
| 租户类型    | 企业租户`ENTERPRISE`    |
| 管理员账号   | `safeAdmin`         |
| 管理员名称   | 安全培训管理员             |
| 管理员初始密码 | `Safe@2026!`        |
| 状态      | 正常                  |
| 说明      | 用于课程、考试和培训闭环验收的企业租户 |

创建成功后记录返回的企业租户 ID。后续标签、岗位、部门和角色页面均选择这个租户，不手工填写 ID。

## 2. 标签

在“机构组织 → 标签管理”新增：

| 标签名称 | 说明             |
|------|----------------|
| 新员工  | 入职一年内人员        |
| 特种作业 | 需要特种作业培训和复审的人员 |
| 重点岗位 | 企业安全生产重点岗位人员   |
| 管理人员 | 企业管理和安全管理人员    |
| 外包人员 | 外部协作或承包商人员     |

## 3. 岗位

| 岗位编码                 | 岗位名称   | 说明               |
|----------------------|--------|------------------|
| `SAFETY_MANAGER`     | 安全管理人员 | 负责企业安全培训组织和结果确认  |
| `WORKSHOP_MANAGER`   | 车间负责人  | 负责车间人员培训和完成情况跟进  |
| `EQUIPMENT_OPERATOR` | 设备操作员  | 设备操作和特种作业培训对象    |
| `CONTRACTOR`         | 外包作业人员 | 外包作业人员培训对象       |
| `TRAINER`            | 企业培训教师 | 负责课程维护、考试批阅和结果发布 |

## 4. 部门

租户创建后后端会生成根部门。以该根部门为父级创建：

| 部门编码         | 部门名称  | 部门类型 | 负责人     |
|--------------|-------|------|---------|
| `SAFETY`     | 安全管理部 | 部门   | 安全培训管理员 |
| `PRODUCTION` | 生产运营部 | 部门   | 车间负责人   |
| `EQUIPMENT`  | 设备工程部 | 部门   | 设备负责人   |
| `CONTRACTOR` | 外协管理部 | 部门   | 外协管理员   |

以“生产运营部”为父级继续创建：

| 部门编码          | 部门名称 | 负责人   |
|---------------|------|-------|
| `ASSEMBLY`    | 装配车间 | 车间负责人 |
| `MAINTENANCE` | 维修班组 | 设备负责人 |

## 5. 用户

用户创建时从部门树、角色、标签和岗位选择项中选择，不输入任何 ID。密码由页面按现有登录约定处理。

| 账号             | 姓名      | 部门    | 成员类型     | 邮箱                         | 职务      | 标签        | 主岗位    |
|----------------|---------|-------|----------|----------------------------|---------|-----------|--------|
| `safeAdmin`    | 安全培训管理员 | 安全管理部 | INTERNAL | `safe.admin@example.com`   | 安全培训管理员 | 管理人员、重点岗位 | 安全管理人员 |
| `trainer01`    | 林晓峰     | 安全管理部 | INTERNAL | `trainer01@example.com`    | 企业培训教师  | 管理人员      | 企业培训教师 |
| `manager01`    | 周建国     | 装配车间  | INTERNAL | `manager01@example.com`    | 车间负责人   | 管理人员、重点岗位 | 车间负责人  |
| `operator01`   | 赵强      | 装配车间  | INTERNAL | `operator01@example.com`   | 设备操作员   | 新员工、特种作业  | 设备操作员  |
| `operator02`   | 陈海      | 维修班组  | INTERNAL | `operator02@example.com`   | 维修操作员   | 特种作业      | 设备操作员  |
| `contractor01` | 王磊      | 外协管理部 | EXTERNAL | `contractor01@example.com` | 外包作业人员  | 外包人员、特种作业 | 外包作业人员 |

建议先创建 `safeAdmin`、`trainer01`、`manager01`，完成 M1 后再补充外包人员和更多学员。

## 6. 角色

| 角色编码                | 角色名称    | 级别      | 建议权限范围                      |
|---------------------|---------|---------|-----------------------------|
| `SAFE_TENANT_ADMIN` | 企业租户管理员 | ADMIN   | 当前企业的用户、部门、标签、岗位、角色、素材和课程管理 |
| `SAFE_TRAINER`      | 企业培训教师  | TEACHER | 课程维护、开课、考试批阅和成绩发布           |
| `SAFE_MANAGER`      | 部门负责人   | TEACHER | 查看本部门培训进度和学习结果              |
| `SAFE_LEARNER`      | 企业学员    | STUDENT | 学习中心、考试中心、培训中心的本人任务         |

### 6.1 用户与角色对应关系

创建角色后，按以下关系给用户分配角色。一个用户可以有多个角色，但本套验收数据先保持一人一个业务角色，便于判断权限边界：

| 用户账号           | 分配角色                | 主要验证目的                         |
|----------------|---------------------|--------------------------------|
| `safeAdmin`    | `SAFE_TENANT_ADMIN` | 企业租户内的用户、部门、标签、岗位、角色和素材基础管理    |
| `trainer01`    | `SAFE_TRAINER`      | 课程、素材、开课、题库/试卷和考试批阅等教师端操作      |
| `manager01`    | `SAFE_MANAGER`      | 部门负责人查看本部门学习、考试和培训结果，不允许维护课程配置 |
| `operator01`   | `SAFE_LEARNER`      | 学习中心、考试中心、培训中心的本人任务和学习记录       |
| `operator02`   | `SAFE_LEARNER`      | 特种作业人员的学员端学习和考试验证              |
| `contractor01` | `SAFE_LEARNER`      | 外部成员的租户内学习任务和权限隔离验证            |

`safeAdmin` 是企业租户管理员，不应配置“租户管理”菜单；租户创建、停用/启用和跨租户查看只由平台 `superAdmin` 操作。

### 6.2 角色菜单配置

角色编辑页面按菜单树勾选以下叶子权限。父级菜单只负责显示和展开，实际授权以叶子权限为准；不要把菜单 ID 写入本文档。

#### `SAFE_TENANT_ADMIN`：企业租户管理员

| 菜单          | 建议勾选权限                                                                                                 |
|-------------|--------------------------------------------------------------------------------------------------------|
| 系统管理 → 用户管理 | `sys:user:list`、`sys:user:add`、`sys:user:edit`、`sys:user:resetPwd`；删除用户仅在需要验证删除流程时勾选 `sys:user:delete` |
| 系统管理 → 部门管理 | `sys:dept:list`、`sys:dept:add`、`sys:dept:edit`、`sys:dept:delete`                                       |
| 系统管理 → 标签管理 | `org:tag:list`、`org:tag:add`、`org:tag:edit`、`org:tag:delete`                                           |
| 系统管理 → 岗位管理 | `org:position:list`、`org:position:add`、`org:position:edit`、`org:position:delete`                       |
| 系统管理 → 角色管理 | `sys:role:list`、`sys:role:add`、`sys:role:edit`、`sys:role:query`；删除角色按需勾选 `sys:role:delete`             |
| 应用中心 → 素材库  | `asset:asset:list`、`asset:asset:add`、`asset:asset:edit`、`asset:asset:delete`、`asset:job:list`          |
| 公共支撑 → 人员选择 | `common:person:list`                                                                                   |
| 公共支撑 → 资源授权 | `common:grant:list`、`common:grant:add`、`common:grant:edit`、`common:grant:delete`                       |

不勾选：租户管理、菜单管理、字典管理、缓存管理、参数配置、在线用户和日志管理。这些属于平台级运维能力。

#### `SAFE_TRAINER`：企业培训教师

| 菜单          | 建议勾选权限                                                                                                                                                                                                                                                                                                                                                                                               |
|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 应用中心 → 素材库  | `asset:asset:list`、`asset:asset:add`、`asset:asset:edit`、`asset:job:list`；删除素材按需勾选 `asset:asset:delete`                                                                                                                                                                                                                                                                                               |
| 应用中心 → 课程管理 | `course:category:list`、`course:category:add`、`course:category:edit`、`course:category:delete`、`course:course:list`、`course:course:add`、`course:course:edit`、`course:chapter:list`、`course:chapter:add`、`course:chapter:edit`、`course:chapter:delete`、`course:item:list`、`course:item:add`、`course:item:edit`、`course:item:delete`、`course:course:publish` |
| 应用中心 → 开课管理 | `course:offering:list`、`course:offering:add`、`course:offering:edit`、`course:offering:publish`、`course:enrollment:list`、`course:enrollment:assign`、`course:enrollment:remove`、`course:learning:view`                                                                                                                                                                                                  |
| 应用中心 → 考试管理 | M2 联调时再配置题库、试卷和考试的查询、新增、编辑、发布、批阅、成绩发布权限                                                                                                                                                                                                                                                                                                                                                              |
| 应用中心 → 培训管理 | M1/M2 对应培训接口联调后，再按实际返回菜单配置培训类型、标准、模板和计划权限                                                                                                                                                                                                                                                                                                                                                            |

#### `SAFE_MANAGER`：部门负责人

只配置查看类菜单，不授予系统管理和课程维护权限：

- 课程：`course:course:list`、`course:offering:list`、`course:enrollment:list`、`course:learning:view`。
- 考试：M2 联调后配置考试任务、作答详情和成绩查询类权限，不配置题库维护、组卷、发布和批阅。
- 培训：M1/M2 联调后配置培训计划查询、参训人员查询、培训结果查询。

#### `SAFE_LEARNER`：企业学员

不配置后台管理菜单，仅保留门户中的本人业务入口：

- 学习中心：本人课程和学习任务。
- 考试中心：本人考试任务、作答、交卷和成绩查看。
- 培训中心：本人培训任务、签到/完成记录和结果查看。

不配置 `sys:*`、`org:*`、`asset:*`、课程维护、题库、组卷、考试发布、培训计划编排等管理权限。

### 6.3 监管角色（可选验收数据）

监管角色不属于 `SAFE_ACME` 企业租户。验证监管平台时使用后端初始化的 `REGULATOR` 租户账号，配置“监管平台 → 监管概览”只读权限，并确认账号
`orgType=REGULATOR`。监管账号查看企业明细时，前端只对 GET/HEAD/OPTIONS 请求追加 `X-Target-Tenant-Id`。

当前契约存在一个待联调项：后端初始化菜单使用 `regulation:overview:view`，而前端监管路由当前校验 `regulator:aggregate:view`
。在后端菜单权限统一前，不要将监管功能标记为已完成；应以实际登录后返回的权限集合和接口响应为准记录结果。

## 7. 推荐验证顺序

1. superAdmin 创建租户并记录租户编码 `SAFE_ACME`。
2. 使用 `SAFE_ACME / safeAdmin / Safe@2026!` 登录，确认租户上下文正确。
3. 创建标签、岗位和部门。
4. 创建教师、负责人和学员用户。
5. 创建角色并勾选菜单权限。
6. 用 `trainer01` 建立课程，用 `operator01` 验证学习，用 `manager01` 查看结果。
7. M2 完成后再使用同一批用户验证组卷、考试、批阅和成绩发布。
