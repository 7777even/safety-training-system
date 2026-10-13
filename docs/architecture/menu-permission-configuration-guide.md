# 菜单与角色权限配置指南

> 适用范围：PC 端「系统管理 → 机构组织 → 角色管理」的角色菜单勾选、学员端/教师端/管理端权限落地。
> 关联文档：`docs/architecture/button-permission.md`（按钮权限架构）、`docs/requirement/role-workflows.md`（角色与任务归属口径）、`docs/requirement/user-roles.md`。
> 数据源：`backend/sts-service-admin-init/src/main/resources/data/menus.json`（233 个菜单节点，初始化幂等插入；2026-09-23 新增 M4「培训成果」子树 25 个节点与门户「我的证书」#9751，见 §5 与 §7）。
> 配套矩阵：`docs/architecture/menu-role-matrix.csv`（可直接照着逐行勾选）。

---

## 一、三条结论（先看这里）

1. **菜单权限只能决定"接口级权限（1003 权限不足）"，无法决定"数据归属校验"。**
   `无权访问该报名学习数据`（业务码 5081）是**归属校验**，由 enrollment.userId 与当前登录人比对得出，**给角色配任何菜单都不会消失**。这是设计口径，不是缺陷。
2. **权限不做父子继承。** 角色的权限集合 = 被勾选节点自身的 `perms` 字符串集合。**只勾"学习中心/考试中心"这类父菜单、不勾下面的按钮，权限集合几乎是空的**，接口必然大面积 403。
3. **门户子树不完整**：`门户 → 培训中心` 以及其下的按钮节点**根本不存在 `training:learner:task`**，因此无论怎么勾，学员端的培训中心链路（列表/详情/在线考试/线下场次）都会 403。这是必须补菜单节点的硬缺口。

---

## 二、权限是怎么生效的（四条链路 + 证据）

### 链路 1：权限集合只来自"被绑定节点的 perms"

`SysMenuMapper.selectPermsByUserId`：

```sql
SELECT DISTINCT m.perms
FROM us_sys_menu m
INNER JOIN us_sys_role_menu rm ON m.menu_id = rm.menu_id
INNER JOIN us_sys_user_role ur ON rm.role_id = ur.role_id
WHERE ur.user_id = ? AND m.perms IS NOT NULL AND m.perms <> '' AND m.is_visible = 1
```

证据：`backend/sts-service-admin/src/main/java/com/sts/admin/mapper/SysMenuMapper.java:14-28`

- 只有**显式写进 `us_sys_role_menu` 的 menu_id** 才贡献权限码。
- **父节点不会把子节点的 perms 带下来**（没有递归，取的是每个节点自己的 perms）。
- 结论：勾到哪个节点，只得到那个节点写的权限码。**必须勾到叶子按钮**。

### 链路 2：侧边栏/路由来自"被绑定节点 + 向上补齐的父级"

`selectAccessibleMenusByUserId` 用 `WITH RECURSIVE` 把授权节点及其**祖先**查出来（`SysMenuMapper.java:39-65`）。所以勾了一个叶子按钮，它的父菜单也会出现在导航里；反之只勾父菜单，不会出现子页面。

### 链路 3：前端根据 `userInfo.menus` 动态注册路由，portal 内部页另有一道权限闸门

`frontend/pc-web/src/router/admin-dynamic-routes.ts`

- `synchronizeMenuRoutes()`（:696）用 profile 返回的 `menus` + `perms` 生成运行时路由。
- **门户工作流内部页**（`PORTAL_INTERNAL_ROUTE_DEFINITIONS`，:218-307）注册条件是"来源菜单已授权 **且** 持有声明的权限码"：

| 内部页 | 路径 | 依赖权限码 |
|---|---|---|
| 课程学习 / 课程考核作答 / 课程考核结果 | `/portal/class/learning/...` | `asset:asset:preview`（**仅声明**，例外见下） |
| 考生须知 | `/portal/exams/:examId/notice/:examCandidateId` | `exam:attempt:start` |
| 考试作答 / 考试结果 | `/portal/exams/:examId/attempt|result/...` | `exam:learner:attempt:view` |
| 培训详情 / 培训考试（须知/作答/结果）/ 线下场次 | `/portal/training/...` | `training:learner:task` |

> 例外：`/portal/class` 来源的内部页**豁免权限检查**，只要「学习中心」菜单已授权即注册（见 `registerAuthorizedPortalInternalRoutes` 的 `inheritsCourseMenuAccess`）。所以课程学习页能否打开只取决于**学习中心菜单是否勾选**。

### 链路 4：后端鉴权 = Sa-Token `@SaCheckPermission`

- 全局拦截器只做 `StpUtil.checkLogin()`（`SaTokenConfig.java:58`），登录即通过。
- 接口级权限写在 Controller 方法上，如 `@SaCheckPermission("exam:learner:task:list")`。
- 权限码集合由 `StpInterfaceImpl.getPermissionList` 提供；**仅当 `userCode` 严格等于 `superAdmin` 时返回 `["*"]` 全放行**（`StpInterfaceImpl.java:30-32`）。
- 不匹配 → HTTP 403 + 业务码 **1003「权限不足: xxx」**。

### 附：两个容易误判的点

- **角色级别（`role_level` = ADMIN/TEACHER/STUDENT）不参与任何鉴权**，只做取值范围校验后入库（`SysRoleServiceImpl.java:175-183`）。它只是分类标签，**配置它不会带来任何权限**。
- **修改角色菜单后会清空全部用户的权限缓存**（`SysRoleServiceImpl.java:117` 调 `userCacheService.evictAllPerms()`），后端侧不需要手工清缓存；但**前端 `menus/perms` 是登录时下发的，必须让该账号重新登录**才能拿到新菜单。

---

## 三、报错对照表：哪种错误该动什么

| 现象 / 业务码 | HTTP | 含义 | 能不能靠配菜单解决 |
|---|---|---|---|
| `1003 权限不足: xxx` | 403 | `@SaCheckPermission` 未通过 | ✅ **能**。勾上对应叶子按钮节点即可 |
| `5081 无权访问该报名学习数据` | 403 | 报名人 ≠ 当前登录人 | ❌ **不能**，归属校验 |
| `5081 无权访问课程报名` | 403 | 报名人不符，**或** learningStatus 不在 NOT_STARTED/LEARNING/COMPLETED | ❌ 不能（数据/状态问题） |
| `5073 无权退选该报名` | 403 | 退选时报名人不符 | ❌ 不能 |
| `7042 无权访问该作答尝试` | 403 | 作答不属于当前考生 | ❌ 不能 |
| `8150 只能查看当前登录学员的…` | 403 | 培训参训人不符 | ❌ 不能 |
| `8155 场次对当前学员不可见` | 404 | 非本人培训场次 | ❌ 不能 |
| 页面能进但菜单不显示 | — | `us_sys_role_menu` 无绑定，或前端未重新登录 | ✅ 能 |
| 点进去 404 / 空白页 | — | 内部页依赖的权限码缺失（链路 3） | ✅ 能 |

**归因方法**：看响应体的 `code`。`1003` → 配菜单；`50xx / 7xxx / 8xxx` → 数据归属，配菜单无效。

---

## 四、为什么"配了菜单还是报无权访问该报名学习数据"

这不是配置问题。项目自身的设计口径（`docs/requirement/role-workflows.md:34-41`）：

> 角色分两层：系统 RBAC 角色（`us_sys_role` + `us_sys_role_menu`）回答"**能不能操作**"；业务任务归属（`sts_course_enrollment.user_id`、`sts_exam_candidate.user_id`、`sts_training_participant.user_id` 等）回答"**这件事归谁**"。
> **即使拥有菜单权限，教师也只能处理被分配的任务，学员只能访问本人执行链。**

代码上的三处独立实现（都拿 `enrollment.userId` 对比 `StpUtil.getLoginIdAsLong()`）：

| 位置 | 触发文案 |
|---|---|
| `course/service/CourseLearningServiceImpl.java:954-958` | 无权访问该报名学习数据（调用点 :268/:458/:495/:510，即"我的任务/我的详情/学习会话上报/提交评价"） |
| `course/service/LearnerCourseDownloadService.java:50-52` | 无权访问该报名学习数据（课程附件下载） |
| `course/service/CourseAssessmentAttemptService.java:78-81` | 无权访问课程报名（开始考核/作答/交卷） |

学员侧的**列表接口本身是按登录人过滤的**（`pageMyEnrollments` 用 `.eq(userId, StpUtil.getLoginIdAsLong())`，`CourseLearningServiceImpl.java:76-78`；VO 的 `enrollmentId` 就是 `course_enrollment_id`，:187），所以正常从"学习中心"点进去不会触发。

**会触发的场景**（请按此自查）：

1. 用**别人的 enrollmentId** 直链/书签打开 `/portal/class/learning/{enrollmentId}`（例如从管理端报名列表里复制的链接、或从另一个账号会话里复制的 URL）。
2. 用一个"既不是报名人、也不是考生"的账号访问培训/考试任务。
3. 报名或考生数据本身的 `user_id` 与登录账号不是同一人（数据问题：需查 `sts_course_enrollment.user_id` 与 `us_sys_user.user_id` 是否一致）。
4. 报名状态为 `EXPIRED` / `WITHDRAWN` 时点进课程考核（`CourseAssessmentAttemptService:79` 的状态白名单）。

> 若确认是"自己报名自己的课、正常路径"也报 5081，那是**数据问题**，需要按第 3 条核对 `user_id`——不能靠改菜单解决，标记为需人工决策。

---

## 五、完整菜单树（233 节点）

数据源 `menus.json`，`menu_id` 为**固定业务 ID**（前端路由与权限点依赖其稳定）。反引号内为 `perms`。

- **门户** #1
  - 首页 #9524
  - 学习中心 #9525
    - 素材预览 `asset:asset:preview` #9554
    - 素材列表 `asset:asset:list` #9555
  - 考试中心 `exam:learner:task:list` #9526
    - 开考 `exam:attempt:start` #9557
    - 学员作答详情 `exam:learner:attempt:view` #9558
    - 学员作答 `exam:attempt:answer` #9559
    - 学员交卷 `exam:attempt:submit` #9560
    - 考试信息 `exam:exam:list` #9563
  - 培训中心 #9527 ← ⚠️ 无子节点、无权限码
  - 我的证书 `training:learner:task` #9751 `/portal/certificates`（2026-09-23 新增，M4 学员端入口；旧手工节点 #9601 已随遗留段软删）
  - 教学辅助 #9528 ← 占位页，无接口
- **模块首页概览** #9529
- **应用中心** #9530
  - 素材库 `asset:asset:list` #401
    - 素材查询 `asset:asset:list` #1401 / 素材新增 `asset:asset:add` #1402 / 素材编辑 `asset:asset:edit` #1403 / 素材删除 `asset:asset:delete` #1404 / 转码任务查询 `asset:job:list` #1405
  - 课程管理 #300
    - 课程分类 `course:category:list` #301 → 查询#1301 / 新增#1302 / 编辑#1303 / 删除#1304
    - 课程中心 `course:course:list` #9531 → 课程 查询#1321/新增#1322/编辑#1323/删除#1324/发布 `course:course:publish` #1333；章节 `course:chapter:*` #1325-1328；内容项 `course:item:*` #1329-1332；开课 `course:offering:*` #9539-9543；报名 `course:enrollment:list|assign|remove` #9545-9547；学习详情 `course:learning:view` #9548；人员选择 `common:person:list` #9553；部门查询 `sys:dept:list` #9556
  - 考试管理 #700
    - 题库中心 `question:question:list` #603 → `question:knowledge|bank|question:list|add|edit|delete` #6100-6111、`question:question:publish` #6112、`question:import` #6121、`question:user-state:edit` #6122
    - 试卷管理 `paper:paper:list` #604 → 查询#6113/新增#6114/编辑#6115/删除#6116/发布 `paper:publish` #6123
    - 考试管理 `exam:exam:list` #605 → 查询#6117/新增#6118/编辑#6119/删除#6120/发布 `exam:publish` #6124/开考 `exam:attempt:start` #6125/作答查询 `exam:attempt:list` #6126/作答 `exam:attempt:answer` #6127/交卷 `exam:attempt:submit` #6128/批阅 `exam:review` #6129/成绩发布 `exam:score:publish` #6130/学员考试任务 `exam:learner:task:list` #6131/学员作答详情 `exam:learner:attempt:view` #6132
    - 成绩批阅 #9561（无权限码）、成绩管理 #9562（无权限码）
  - 培训管理 #800
    - 培训类型 `training:type:list` #701 → 查询#7101/新增#7102/修改#7103/删除#7104
    - 培训标准 `training:standard:list` #702 → 查询#7201/新增#7202/修改#7203/删除#7204/发布#7205
    - 培训模板 `training:template:list` #703 → 查询#7301/新增#7302/修改#7303/删除#7304/发布#7305
    - 评价表单 `evaluation:form:list` #704 → 表单 查询#7401/新增#7402/修改#7403/删除#7404/发布#7405；记录 `evaluation:record:list` #8201 / `evaluation:record:add` #8203 / `evaluation:record:submit` #8202；计算 `evaluation:calculation:calculate` #8301 / `evaluation:calculation:list` #9536 / `evaluation:calculation:confirm` #8302
    - 培训计划 `training:plan:list` #705 → 查询#7501/新增#7502/修改#7503/删除#7504/发布#7505；参训人 `training:participant:list` #7506 / `training:participant:assign` #7507；`training:offline-score:edit` #7510 / `training:offline-score:list` #7519；附件 `training:attachment:list|add|edit|delete` #7511/#7514/#7515/#7516；奖惩 `training:reward:list|add|edit|delete` #7512/#7522/#7523/#7524；教师任务 `training:teacher:task` #7513；**学员任务 `training:learner:task` #7518**；结果 `training:result:list` #7520 / `training:result:synchronize` #7521
  - 培训成果 #9700 `/result`（2026-09-23 新增，M4 最小界面闭环）
    - 问卷管理 `survey:survey:list` #9701 → 问卷查询 `survey:survey:list` #9710 / 问卷新增 `survey:survey:add` #9711 / 问卷编辑 `survey:survey:edit` #9712 / 问卷删除 `survey:survey:delete` #9713 / 投放查询 `survey:instance:list` #9714 / 投放新增 `survey:instance:add` #9715
    - 培训档案 `archive:training:list` #9702 → 档案查询 `archive:training:list` #9720 / 档案更新 `archive:training:create` #9721
    - 证书管理 #9703（无权限码，功能权限在三个叶子页上）→ 证书模板 `certificate:template:list` #9704（查询#9730/新增 `certificate:template:add` #9731/编辑 `certificate:template:edit` #9732/删除 `certificate:template:delete` #9733/发布 `certificate:template:publish` #9734）、签发规则 `certificate:rule:list` #9705（查询#9740/新增 `certificate:rule:add` #9741/编辑 `certificate:rule:edit` #9742/删除 `certificate:rule:delete` #9743）、已签发证书 `certificate:cert:list` #9706（查询与预览下载 `certificate:cert:list` #9750）
- **系统管理** #200
  - 机构组织 #500 → 机构管理 `system:tenant:list` #9532（查询#1381/新增#1382/编辑#1383）、标签管理 `org:tag:*` #9534+#1511-1514、岗位管理 `org:position:*` #9535+#1521-1524、部门管理 `sys:dept:list` #203（`sys:dept:*` #1021-1024 及缓存/参数/在线/日志/文件等 #1051-1104、#9537、#9538）、用户管理 `sys:user:list` #201（#1001-1005）、角色管理 `sys:role:list` #202（#1011-1015）
  - 菜单管理 `sys:menu:list` #204（#1031-1034）
  - 字典管理 `sys:dict:list` #205（#1041-1044）
  - 文件操作权限 #9549 → `sys:file:list` #9550 / `sys:file:upload` #9551 / `sys:file:delete` #9552

> 完整 233 行（含层级、menuId、权限码、路由、三角色勾选列）见 `docs/architecture/menu-role-matrix.csv`。⚠️ 该 CSV 尚未回填 2026-09-23 新增的 26 个节点（M4 #9700～#9750 共 25 个 + 门户「我的证书」#9751），补录前请以 §5 为准。

---

## 六、按角色的勾选清单

### 6.1 学员角色（可确定，依据已实施的学员端链路）

**勾选范围：`门户` 全子树（13 个节点）**，即 `门户` 一级模块 + 首页 + 学习中心（含素材预览、素材列表）+ 考试中心（含开考、学员作答详情、学员作答、学员交卷、考试信息）+ 培训中心 + 教学辅助。

其余三个模块（模块首页概览 / 应用中心 / 系统管理）**全部不勾**。

对应获得的权限码（共 8 个）：

```
asset:asset:preview
asset:asset:list
exam:learner:task:list
exam:attempt:start
exam:learner:attempt:view
exam:attempt:answer
exam:attempt:submit
exam:exam:list
```

⚠️ **缺 1 个**：`training:learner:task`（培训中心链路必需），菜单树里没有对应节点 → 见第七节，需先补节点。

**勾选后的预期行为**

| 学员端页面 | 依赖 | 结果 |
|---|---|---|
| 首页 | 首页菜单 | ✅ |
| 学习中心（我的课程/可报名开课） | 学习中心菜单 | ✅（这些接口本身无权限注解，只校验登录与归属） |
| 课程学习 / 课程考核作答 / 考核结果 | 学习中心菜单 | ✅（豁免权限检查） |
| 考试中心列表 / 考试信息 | 考试中心菜单 + 考试信息 | ✅ |
| 考试须知 / 作答 / 结果 | 开考 + 学员作答详情（+作答/交卷） | ✅ |
| 培训中心 / 培训详情 / 培训考试 / 线下场次 | 培训中心菜单 + **`training:learner:task`** | ❌ 当前必然 403 |

### 6.2 教师角色（**建议值，需人工确认**）

依据 `docs/requirement/role-workflows.md` §6：教师不是独立 RBAC 角色，而是被业务表指派的任务归属人（直播讲师 / 线下讲师 / 线下评分人 / 阅卷人 / 评价人）。因此菜单应只覆盖"接任务、执行任务"所需的页面：

| 勾选位置 | 节点 |
|---|---|
| 门户 | 按需（教师本人也要学习/考试就全勾） |
| 应用中心 → 考试管理 → 考试管理 `exam:exam:list` #605 | 作答查询 `exam:attempt:list` #6126、**批阅 `exam:review` #6129** |
| 应用中心 → 考试管理 → 成绩批阅 #9561 / 成绩管理 #9562 | 入口路由（权限码在考试管理的按钮上，见备注） |
| 应用中心 → 培训管理 → 培训计划 `training:plan:list` #705 | **教师任务 `training:teacher:task` #7513**、参训人管理 #7506、线下成绩 `training:offline-score:edit` #7510 / `training:offline-score:list` #7519 |
| 应用中心 → 培训管理 → 评价表单 `evaluation:form:list` #704 | 记录查询 `evaluation:record:list` #8201、**记录提交 `evaluation:record:submit` #8202** |

⚠️ 注意：`成绩批阅`/`成绩管理` 两个菜单节点自身**没有权限码**，其功能权限（`exam:review`、`exam:score:publish`）挂在 `考试管理 #605` 的按钮上——所以两个都要勾，只勾前者会看到菜单但进去无权限。

⚠️ `线下教学任务` 页（`/admin/business-center/training/teacher-tasks/:offlineSessionId`）的 `sourcePath` 在菜单树中**不存在**，教师无法通过菜单进入该页 → 见第七节缺口。

### 6.3 管理员角色

初始化脚本已把 `admin` 角色与**全部 233 个菜单**绑定（`SeedDataFillerService.java:278-298`），无需手工配置。
⚠️ 但**已部署库**不会因 init 重跑而更新：`DatabaseSchemaInitializer.initializeEmptyDatabase()` 对非空库直接抛错并阻断 `fillSeedData()`。已部署库新增节点必须走增量迁移并同步授权，例如 `V20260923110000__sync_m4_result_menus.sql`（幂等插节点 + 向 `admin` 授予新节点）。若新建自定义管理角色，勾选 `应用中心 + 系统管理 + 模块首页概览` 全树即可；建议同时为账号保留 `superAdmin` 用于兜底排障。

---

## 七、菜单缺口清单（不补节点就永远配不出来）

### P0（直接影响学员端培训中心，必须补）

| 要补的节点 | 父节点 | 类型 | perms |
|---|---|---|---|
| 学员任务（建议命名「培训任务」） | `培训中心` #9527 | F | `training:learner:task` |

现状：`training:learner:task` 只作为 `应用中心 → 培训管理 → 培训计划` 下的 #7518 存在。若为了给学员这个权限而勾选 #7518，会把整个「应用中心 / 培训管理 / 培训计划」菜单带进学员的侧边栏（链路 2 向上补齐父级），**这是当前唯一的绕行办法，但不合规**。

补齐方式（二选一）：

1. **界面补**（推荐，不改代码）：`系统管理 → 菜单管理` 新增按钮节点，父级选「培训中心」，权限字符串填 `training:learner:task`。
2. **种子补**：在 `menus.json` 的 9527 下新增一个 `"type": "F"` 节点（`id` 取一个未占用的固定值），重跑 init（按 `menu_id` 幂等补插，不覆盖已有节点；现有节点不会被更新）。

> 该改动涉及权限模型，实施前需人工确认；如走种子文件修改，需按项目规则同步 OpenSpec。

### P1（当前模块未开放，先记录）

`培训中心` 与 `教学辅助` 之外，后端还有一批 `@SaCheckPermission` 权限码在菜单树里没有任何节点，因此**无法**通过角色界面授权。

**2026-09-23 更新**：原清单（42 行）中的**档案/证书/问卷 17 个权限码已补齐节点**（`#9700`～`#9750`，见 §5），已可正常授权；剩余缺口**以持续门禁测试 `MenuPermissionConsistencyTest` 的 `elements not found` 为权威口径**（当前为 7 个 regulation/ops 权限码，属延期模块）。下表为 2026-09-17 的历史清单，保留用于追溯，**不得再作为授权依据**：

```
archive:export:create            archive:export:list
archive:training:create          archive:training:list
certificate:cert:issue           certificate:cert:list
certificate:cert:reissue         certificate:cert:revoke
certificate:rule:add             certificate:rule:delete
certificate:rule:list            certificate:template:add
certificate:template:delete      certificate:template:edit
certificate:template:list        certificate:template:publish
ops:export:create                ops:export:list
ops:stats:drilldown              ops:stats:view
ops:workbench:enterprise         ops:workbench:learner
ops:workbench:teacher            regulation:overview:view
regulation:rule:add              regulation:rule:delete
regulation:rule:edit             regulation:rule:list
regulation:warning:action        regulation:warning:detect
regulation:warning:list
regulator:aggregate:view         survey:instance:add
survey:instance:bind             survey:instance:list
survey:instance:stats            survey:instance:submit
survey:survey:add                survey:survey:delete
survey:survey:edit               survey:survey:list
survey:survey:publish
```

> `regulator:aggregate:view` 还被前端路由（`AdminInternalRouteDefinitions` 的企业汇总页）引用，同样缺节点。这些模块开放时，必须先把菜单节点补齐，否则前端页面能写、权限给不了。

**已补齐（2026-09-23，`2026-09-23-complete-m4-minimum-ui-closure`）**：`archive:training:create`、`archive:training:list`、`certificate:cert:list`、`certificate:rule:add|delete|list`、`certificate:template:add|delete|edit|list|publish`、`survey:instance:add|list`、`survey:survey:add|delete|edit|list` 共 17 个，节点见 §5「培训成果」子树。

> ⚠️ **两个必须同时满足的条件**（缺一即「菜单存在但角色看不到」）：
> 1. 节点要进**已部署库**：`main.flyway.enabled=false` 时迁移不会自动执行，且 `sts-service-admin-init` 的 `DatabaseSchemaInitializer.initializeEmptyDatabase()` 对非空库会直接抛错并阻断 `fillSeedData()`，**重跑 init 无法给已部署库补节点**，只能人工执行增量迁移（如 `V20260923110000__sync_m4_result_menus.sql`、`V20260923120000__add_portal_my_certificates_menu.sql`）。
> 2. 节点要**绑定到角色**：新增节点不会自动进入已有角色的 `us_sys_role_menu`（`admin` 在 init 时绑定的是「当时的全部菜单」），迁移须显式授权，例如 `V20260923110000` 中的 `INSERT IGNORE INTO us_sys_role_menu ... WHERE role_code = 'admin'`。绑定后须让该账号**重新登录**才生效。
>
> 另需注意：除了种子与迁移，**真实库可能还存在绕过版本控制手工创建、且未登记进本指南的菜单节点**。部署前建议先比对 `us_sys_menu` 现存 `menu_id` 段与 `menus.json`，避免同一功能出现两套节点。
> 2026-09-23 实例：团队库曾存在 2026-09-21 手工创建的 9566～9605（含门户「我的证书」#9601），已由 `V20260923130000__soft_delete_legacy_m4_result_menus.sql` 按 `deleted = menu_id` 整段软删（32 条有效行、零角色绑定，不影响任何用户可见性）。

### P2（已知不完整但不阻塞）

- `门户 → 培训中心` #9527、`教学辅助` #9528 无子节点；`教学辅助` 目前是 `ElEmpty` 占位页，无接口。
- `菜单树里有后端未使用的权限码`：`asset:asset:preview`（仅被前端路由/按钮使用，后端无注解，**属于正常**）、`training:offline-score:edit`（后端实际校验的是 `training:teacher:task`/`training:offline-score:list` 一类，核对后再清理）。

---

## 八、操作步骤

1. 用 `superAdmin` 登录（权限安全、可兜底）。
2. （若需要 `training:learner:task`）先按第七节 P0 在「系统管理 → 菜单管理」补出按钮节点。
3. `系统管理 → 机构组织 → 角色管理` → 找到学员角色 → `编辑`。
4. 在「菜单权限」树中**逐级展开并勾选到叶子按钮**：
   - 只勾 `门户` 一级模块即可让 ElTree 级联勾选其全部后代；**但保存前必须肉眼确认叶子按钮处于勾选态**。
   - 反例（当前最常见错误）：只勾 `首页 / 学习中心 / 考试中心 / 培训中心 / 教学辅助` 而叶子按钮未勾 → 权限集合几乎为空。
5. `保存`。后端会 `delete + insert` 重写该角色的 `us_sys_role_menu`，并清空全部用户权限缓存。
   > ⚠️ **编辑对话框的勾选回显只认叶子节点**（`frontend/pc-web/src/utils/menu-tree.ts` 的 `getSelectedLeafMenuIds`）。若历史数据里只存了父节点 ID，重新打开对话框会**显示为未勾选**；此时若直接点保存，会把原有绑定**清空**。编辑前务必核对勾选态。
6. 让该角色下的用户**重新登录**（前端 `menus`/`perms` 在登录/取 profile 时下发，不重登不变）。
7. 验证：
   - 菜单是否出现（侧边栏/顶部导航）。
   - 调一个受限接口看是否还是 `1003`；若返回 `1003` 说明权限码没到位（回到第 4 步）。
   - 若返回 `5081/5073/7042/8150`，**不是权限问题**，按第四节排查数据归属。

---

## 九、一页速查

| 我想做的 | 该动哪里 |
|---|---|
| 让学员能看/答考试 | 勾「门户 → 考试中心」及其 5 个按钮 |
| 让学员能学课程 | 勾「门户 → 学习中心」（内部页豁免权限检查）+ 素材列表/素材预览 |
| 让学员能看培训任务 | 先补 `training:learner:task` 菜单节点，再勾上 |
| 让教师能阅卷 | 勾「应用中心 → 考试管理 → 考试管理」下的 `exam:review` 按钮 |
| 消除 `1003 权限不足` | 补勾对应权限码所在的叶子按钮 |
| 消除 `5081 无权访问该报名学习数据` | **改不了菜单**，检查登录账号是否为报名人本人 / `course_enrollment.user_id` 是否正确 |
| 改完不生效 | 让用户重新登录（后端缓存已自动清） |
| 找不到某个权限码 | 查第七节缺口清单——可能菜单树里根本没有节点 |
