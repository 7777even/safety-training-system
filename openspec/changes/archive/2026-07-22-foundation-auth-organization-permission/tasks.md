# Tasks：认证、组织与权限前端基础能力

## Task 1：完成跨端本地登录与会话基础

- 状态：已完成
- Owner：前端开发
- 输入：`/api/auth/login`、`/api/system/users/profile`、`/api/auth/logout` 契约，现有两端请求层与用户 Store。
- 输出：PC 登录页、移动 H5 登录页、SHA-256 登录参数、令牌注入、个人资料初始化、退出登录和基础路由/页面跳转。
- 前后端影响：仅前端调用既有认证接口；不改后端、数据库或契约。
- API 依赖：`POST /api/auth/login`、`GET /api/system/users/profile`、`POST /api/auth/logout`。
- 数据影响：浏览器或应用本地会话仅保存 token 与用户基础资料；不新增服务端数据。
- 验收标准：登录请求符合 `local` 与 SHA-256 摘要契约；受保护请求带 Bearer token；成功、失败、退出和未登录跳转均可验证；移动端保持独立触控布局。
- 测试方式：认证与 Store 单元测试、PC/移动构建与手工联调。

## Task 2：完成 PC 门户、后台框架与权限入口

- 状态：已完成
- Owner：前端开发
- 输入：`docs/design/`、附件参考图、当前用户资料中的 `perms` 与 `menus`。
- 输出：深海蓝风格的 PC 登录/门户/后台布局、门户静态概览、后台一级菜单及“系统设置”二级菜单、无业务能力的一级菜单占位页。
- 前后端影响：仅前端路由、布局与入口展示；不调用课程、考试或培训 API。
- API 依赖：`GET /api/system/users/profile` 返回的 `perms`、`menus`。
- 数据影响：无。
- 验收标准：门户使用顶部导航和居中内容；后台为 240px 左侧导航与自适应工作区；一级菜单和系统设置子菜单名称正确；后台入口不会向无权限用户展示；视觉符合
  `docs/design/`。
- 测试方式：路由/权限组件测试、桌面断点手工视觉检查、PC 构建。

## Task 3：完成系统设置—用户管理

- 状态：已完成
- Owner：前端开发
- 输入：用户、部门树和角色分页 API Contract。
- 输出：用户筛选分页、创建、编辑、删除、重置密码页面及对应 API 模块和类型。
- 前后端影响：仅前端调用现有用户管理接口；不改数据模型。
- API 依赖：`GET/POST /api/system/users`、`PUT/DELETE /api/system/users/{userId}`、
  `PUT /api/system/users/{userId}/reset-password`、`GET /api/system/depts/tree`、`GET /api/system/roles`。
- 数据影响：通过既有接口读写用户、角色关联；不新增数据库迁移。
- 验收标准：筛选字段、分页参数、表单字段和版本号完全符合 Contract；删除与重置密码有二次确认；请求失败可读提示；不硬编码角色语义。
- 测试方式：API 参数单元测试、页面组件测试、后端环境联调。

## Task 4：完成系统设置—角色管理

- 状态：已完成
- Owner：前端开发
- 输入：角色 CRUD 与菜单树 API Contract。
- 输出：角色分页、创建、详情、编辑、删除和菜单树授权页面及 API 模块和类型。
- 前后端影响：仅前端调用既有角色和菜单接口；不改变后端权限模型。
- API 依赖：`GET/POST /api/system/roles`、`GET/PUT/DELETE /api/system/roles/{roleId}`、`GET /api/system/menus/tree`。
- 数据影响：通过既有接口读写角色和角色菜单关联；不新增数据库迁移。
- 验收标准：角色表单仅以 `menuIds` 保存菜单授权；编辑传递 Contract 要求的 `version`；删除经二次确认；不自行配置数据范围字段。
- 测试方式：API 参数与权限入口单元测试、角色页面组件测试、后端环境联调。

## Task 5：完成系统设置—菜单管理

- 状态：已完成
- Owner：前端开发
- 输入：菜单树、菜单 CRUD 和菜单可见性 API Contract。
- 输出：菜单树筛选、新增、编辑、删除、单项可见性调整和批量可见性调整页面及 API 模块和类型。
- 前后端影响：仅前端调用既有菜单接口；不改变后端权限模型。
- API 依赖：`GET /api/system/menus/tree`、`POST /api/system/menus`、`PUT/DELETE /api/system/menus/{menuId}`、
  `PUT /api/system/menus/{menuId}/visibility`、`PUT /api/system/menus/batch-visibility`。
- 数据影响：通过既有接口读写菜单；不新增数据库迁移。
- 验收标准：菜单树筛选和表单字段符合 Contract；编辑传递 `version`；单项与批量可见性调用各自接口；所有破坏性操作经二次确认。
- 测试方式：API 参数与权限入口单元测试、菜单页面组件测试、后端环境联调。

## Task 6：完成系统设置—组织管理

- 状态：已完成
- Owner：前端开发
- 输入：部门树与部门 CRUD API Contract。
- 输出：组织树查询、筛选、新增、编辑、删除页面及 API 模块和类型。
- 前后端影响：仅前端调用现有部门接口；不改组织数据结构。
- API 依赖：`GET /api/system/depts/tree`、`POST /api/system/depts`、`PUT/DELETE /api/system/depts/{deptId}`。
- 数据影响：通过既有接口读写部门；不新增数据库迁移。
- 验收标准：树形展示与筛选符合 Contract；父级、状态、排序、负责人和电话字段按接口定义提交；编辑使用版本号；删除有二次确认。
- 测试方式：API 参数单元测试、组织页面组件测试、后端环境联调。

## Task 7：完成系统设置—字典管理

- 状态：已完成
- Owner：前端开发
- 输入：字典类型、字典数据、目录及按类型查询 API Contract。
- 输出：字典类型与字典数据的筛选分页、创建、编辑、删除页面，以及类型目录和按类型数据的复用能力。
- 前后端影响：仅前端调用现有字典接口；不改数据模型。
- API 依赖：`/api/system/dict/types`、`/api/system/dict/types/{dictTypeId}`、`/api/system/dict/data`、
  `/api/system/dict/data/{dictDataId}`、`/api/system/dict/catalog`、`/api/system/dict/data/{dictType}`。
- 数据影响：通过既有接口读写字典类型与数据；不新增数据库迁移。
- 验收标准：类型和数据查询使用 Contract 的分页/筛选参数；写操作传递 `version`；删除有二次确认；字典目录和按类型读取不臆造字段。
- 测试方式：API 参数单元测试、字典页面组件测试、后端环境联调。

## Task 8：完成测试、Review 与过程记录

- 状态：已完成
- Owner：前端开发
- 输入：Task 1 至 Task 7 的实现、OpenAPI Contract、设计规范。
- 输出：自动化测试结果、QA 记录、Code Review 结论、任务状态更新与 Retro 记录。
- 前后端影响：无新增接口或数据影响。
- API 依赖：Task 1 至 Task 7 的所有已确认接口。
- 数据影响：无；联调仅使用开发环境可控数据。
- 验收标准：PC/移动构建、lint、typecheck 通过；测试覆盖认证、权限展示和五个系统设置页关键交互；实现不包含非目标业务；Review
  无阻断问题。
- 测试方式：根目录质量命令、组件/单元测试、手工 Contract 联调与 Review。

## Task 9：完成 PC 菜单动态路由与按钮权限展示

- 状态：已完成
- Owner：前端开发
- 输入：`GET /api/system/users/profile` 返回的 `menus`、`perms`，以及当前 `views/admin` 组件目录。
- 输出：仅保留登录静态路由；以 profile `menus` 的 `path`、`component`、`menuName` 和 `sortOrder` 注册门户及后台动态路由；每个顶层
  `/admin` 菜单均使用后台框架并在侧栏作为一级菜单展示；门户导航仅展示 profile 授权菜单；兼容当前接口误配的 `/dashboard`
  门户路径；按菜单 `F` 节点展示系统设置操作按钮。
- 前后端影响：仅消费既有 profile 接口；不修改后端菜单数据、接口 Contract 或数据库。
- API 依赖：`GET /api/system/users/profile`。
- 数据影响：浏览器运行时动态注册和移除路由；不写入服务端数据。
- 验收标准：除 `/login` 外不保留静态业务路由或本地菜单路径映射；菜单返回的 `path` 直接成为路由路径，当前 `/dashboard`
  临时转换为门户 `/portal`；空 `component` 依据路径目录约定解析页面；未知组件不生成路由；登录按菜单顺序进入首个可解析页面；按钮权限只读取
  `menus` 中的可见 `F` 节点；用户切换或退出不会保留上一用户的路由入口。
- 测试方式：菜单与权限静态扫描、路由守卫检查、`pnpm lint`、`pnpm typecheck`、`pnpm build:pc` 与后端环境联调。

## Task 10：完成后台模块顶栏与按需侧栏导航

- 状态：已完成
- Owner：前端开发
- 输入：`GET /api/system/users/profile` 返回的 `menus`，其中顶层 `D` 节点表示后台模块，`M` 表示目录，`C` 表示页面，`F`
  表示按钮权限。
- 输出：后台顶部模块导航、当前模块按需左侧子菜单、模块默认叶子页跳转，以及可直接访问的后台模块与叶子路由。
- 前后端影响：仅调整 PC 动态路由解析与 `AdminLayout`；不修改接口、后端菜单数据或数据库。
- API 依赖：`GET /api/system/users/profile`。
- 数据影响：无；仍只在浏览器运行时注册用户授权的动态路由。
- 验收标准：顶栏只显示可见的顶层后台 `D` 菜单；独立 `D` 页面不显示侧栏；存在可解析 `M/C`
  后代的模块显示当前模块侧栏，点击模块进入首个叶子页；直接访问任意已授权 `/admin/...`
  路径仍保留后台框架；所有菜单名称、路径、排序和组件解析均取自接口字段，不建立名称或路径映射。
- 测试方式：已完成动态菜单静态检查、路由守卫检查、`pnpm --filter @safety-training/pc-web typecheck`、
  `pnpm --filter @safety-training/pc-web lint` 和 `pnpm build:pc`；待后端按确认层级补齐业务中心 `C` 页面菜单后进行联调。
