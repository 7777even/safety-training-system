# Design：认证、组织与权限前端基础能力

## 系统设计

PC Web 在现有 Vue Router 和 Pinia 基础上新增登录页、门户布局和后台布局。登录成功后保存 `token`，调用
`/api/system/users/profile` 初始化 `userInfo`、`roles`、`perms` 与 `menus`；请求层统一以 `Authorization: Bearer {token}`
发送后续受保护请求。路由守卫仅负责跳转体验和入口展示，不能替代后端鉴权。

PC 门户使用顶部导航与居中卡片布局；管理后台使用 240px 左侧导航、顶部工具栏和自适应工作区。二者均遵循
`docs/design/web-design-spec.md`、`docs/design/design-system.md` 的深海蓝、白色卡片、8px
圆角和间距规范。附件仅作为登录、门户和后台的信息层次参考，不继承其红色视觉。

移动端使用 uni-app 页面与 Pinia，不使用 Vue Router 或 Element Plus。移动登录页采用窄屏表单、44px 主按钮和安全区布局；移动端登录的首期目标是
H5 会话基础，原生 App 与企业微信具体登录能力标记为暂定。

## 认证与访问控制

| 场景          | 前端行为                                                                                | 契约依赖                          |
| ------------- | --------------------------------------------------------------------------------------- | --------------------------------- |
| PC / 移动登录 | 账号密码校验后以`identityType=local` 调用登录；密码按 Contract 传递 SHA-256 64 位摘要 | `POST /api/auth/login`          |
| 会话初始化    | 使用 token 获取当前用户资料，并写入基础用户 Store                                       | `GET /api/system/users/profile` |
| 受保护请求    | 请求拦截器添加`Authorization: Bearer {token}`                                         | OpenAPI`BearerAuth`             |
| 后台入口      | 仅在`perms` / `menus` 返回允许时显示；无入口权限时跳回门户                          | 当前用户资料`perms`、`menus`  |
| 退出登录      | 调用退出接口并清空会话，跳转登录页                                                      | `POST /api/auth/logout`         |

密码摘要使用运行环境现有的 Web Crypto 能力生成；不新增未经确认的第三方依赖，也不发送明文密码。若原生 App
运行环境无法提供该能力，保留为暂定而不降级为明文传输。

## 路由与菜单

PC 路由按以下边界组织：

- `/login`：登录页；登录成功后按用户可见入口跳转门户或后台。
- `/portal`：普通用户门户静态首页；仅展示当前 Change 允许的静态概览与管理后台快捷入口。
- `/admin`：后台首页概览；其下为“课程分类、资源管理、课程学习、考试中心、系统设置”。前四项仅建立导航与范围内占位页，不实现业务能力。
- `/admin/system-settings/*`：用户、角色、菜单、组织、字典五个管理页面。

后台侧栏的一级菜单固定为“首页概览、课程分类、资源管理、课程学习、考试中心、系统设置”。“系统设置”展开为用户管理、角色管理、菜单管理、组织管理、字典管理。菜单显示同时受后端返回的菜单/权限结果约束；本
Change 不硬编码管理员身份。

## 管理页面与 API 映射

| 页面     | 主要能力                                                       | API                                                                                                                                                                                                        |
| -------- | -------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 用户管理 | 分页筛选、新增、编辑、删除、重置密码、部门与角色选择           | `/api/system/users`、`/api/system/users/{userId}`、`/api/system/users/{userId}/reset-password`、`/api/system/depts/tree`、`/api/system/roles`                                                    |
| 角色管理 | 分页筛选、新增、查看/编辑、删除、菜单树授权                    | `/api/system/roles`、`/api/system/roles/{roleId}`、`/api/system/menus/tree`                                                                                                                          |
| 菜单管理 | 菜单树筛选、新增、编辑、删除、单项/批量可见性调整              | `/api/system/menus/tree`、`/api/system/menus`、`/api/system/menus/{menuId}`、`/api/system/menus/{menuId}/visibility`、`/api/system/menus/batch-visibility`                                       |
| 组织管理 | 部门树筛选、新增、编辑、删除                                   | `/api/system/depts/tree`、`/api/system/depts`、`/api/system/depts/{deptId}`                                                                                                                          |
| 字典管理 | 字典类型和字典数据的分页、筛选、新增、编辑、删除；按类型读取值 | `/api/system/dict/types`、`/api/system/dict/types/{dictTypeId}`、`/api/system/dict/data`、`/api/system/dict/data/{dictDataId}`、`/api/system/dict/data/{dictType}`、`/api/system/dict/catalog` |

所有列表都使用 Contract 定义的 `pageNum`、`pageSize`、`query.*` 筛选字段和 `ResultEnvelope` / `PageResult` 响应结构。写操作带上
Contract 要求的 `version` 时使用乐观锁版本，不新增前端字段或错误码。删除操作使用 Element Plus 二次确认；后端返回的业务错误由请求层统一提示。

## 菜单权限与动态路由补充

`GET /api/system/users/profile` 返回的 `menus` 是当前用户唯一的 PC
路由、菜单和按钮权限来源。前端不维护门户、后台或系统设置的路径常量、静态子路由及权限映射；后端仍负责真实资源授权。

- 唯一静态路由为 `/login`。登录成功、会话恢复和用户切换后，前端按 `menus` 的可见顺序注册路由，并打开第一个可解析菜单页面。
- 每个非按钮菜单直接使用其返回的 `path` 作为 Vue Router 路径、`menuName` 作为标题、`sortOrder` 作为导航顺序；`F` 节点不注册页面路由。
- 顶层无子菜单页面的 `component` 非空时，按约定解析 `src/views/<component>.vue`；为空或组件文件不存在时，按 `path` 解析
  `src/views/<path>/index.vue`。每个顶层 `/admin` 菜单使用统一 `AdminLayout` 作为后台壳，并在侧栏作为一级菜单展示；其后代
  `component` 解析为 `src/views/<component>.vue`；若接口返回的是 `system/...` 相对组件路径，则按目录约定补为
  `admin/system/...`，若已是 `admin/system/...` 则直接解析。空组件的目录菜单使用 `RouterView`
  承载子路由。这些均是菜单层级与目录约定，不维护单个菜单名称、路径或组件映射表。无法解析到文件的叶子菜单跳过。
- 门户页顶部导航只显示 profile 中非 `/admin` 的顶层可解析菜单，不为学习、考试或证书保留静态入口。
- 当前接口误将门户配置为 `/dashboard` 时，前端仅将该值临时转换为实际门户 `/portal`；后端改为 `/portal` 后自动按普通目录约定解析
  `src/views/portal/index.vue`。此兼容不适用于其他菜单路径。
- 路由 `meta` 由前端根据既有 `menuName`、`menuId`、`menuType` 和 `icon` 生成 `title`、`requiresAuth`、`menuId`、`menuType` 和
  `icon`，当前 API 无需新增 `meta` 字段。
- 所有操作按钮仅递归读取用户 `menus` 中可见 `F` 节点的 `perms`；不再依赖 profile 顶层 `perms` 或预设的页面查询权限标识。
- 同一浏览器会话切换用户或退出登录时，前端必须移除上一用户注册的动态路由后再同步新用户菜单，避免权限入口残留。

### 后台模块导航补充

后台导航以 profile `menus` 中路径属于 `/admin` 的顶层 `D` 节点为模块来源。模块的顺序、名称、图标和可见性完全由接口返回的
`sortOrder`、`menuName`、`icon` 和 `isVisible` 决定，前端不得按“首页概览”“业务中心”或“系统管理”等名称维护分支。

- 所有可解析的后台 `D` 节点在统一后台顶栏横向展示，当前模块以主色下边线和主色文字标识。点击模块时，若模块自身有独立组件且没有可解析子页面，则进入模块自身路径；若模块存在可解析的
  `M` 或 `C` 后代，则进入其按排序找到的首个页面叶子节点。
- 无可解析 `M` 或 `C` 后代的 `D` 节点是独立页面：仅显示顶栏和内容工作区，模块自身组件通过该 `D` 路由的默认子路由渲染，不显示左侧导航。
- 存在可解析后代的 `D` 节点显示左侧导航。`M` 节点作为可展开目录，`C` 节点作为可点击页面；`F`
  节点只用于按钮权限，不出现在左侧导航也不注册页面。左侧栏标题显示当前模块名称。
- 所有后台 `D` 路由继续由 `AdminLayout` 承载，因此访问 `/admin/...` 的任意已授权叶子路径时，顶栏、当前模块与左侧导航都会保留；有后代模块的根
  URL 自动重定向到首个页面叶子，避免出现空白工作区。空组件目录节点由 `RouterView` 承载其子页面；无组件且无可解析子页面的节点不生成入口。
- 当前业务中心的实际菜单必须补齐为可解析叶子菜单后才会显示左侧入口：例如“课程管理（M）→ 课程分类（C，
  `admin/BusinessCenter/class/classify/index`）”及“培训管理（M）→ 培训计划（C，`admin/BusinessCenter/training/plan/index`
  ）”。这只是后端菜单配置约定，不属于前端硬编码映射。

## 组件、状态与错误处理

- `shared` 只放置跨端真实共用的认证模型、API 响应模型、路由/权限常量和纯 SHA-256 工具；不得放入端特定页面、路由或 UI。
- PC API 模块承载认证和系统设置的接口调用；页面只调用 API 模块，不直接拼装请求。
- PC 用户 Store 只保存 token、用户资料和角色；权限 Store 只维护可展示路由或菜单。移动用户 Store 保持同等会话语义。
- PC 列表使用 Element Plus 的表格、分页、表单、抽屉或弹窗；移动端不使用 Element Plus。
- 登录失败、无权限、加载失败与空数据分别以可读文本呈现；移动端不单靠颜色表达状态。

## 数据范围、风险与取舍

本 Change 的“数据范围”只建立授权消费边界：前端读取后端返回的权限信息决定入口显示，后端负责所有资源和数据的真实范围校验。
`system-admin.openapi.json` 未定义角色数据范围字段或保存接口，因此不提供数据范围配置表单、不伪造请求字段，也不将前端路由守卫视为安全边界。

门户和后台参考图中出现的课程、资源、学习、考试统计仅作为布局参考。本 Change 不请求、展示或模拟这些业务数据，以避免越过已明确的非目标范围。

## 测试设计

- 单元测试：覆盖认证请求参数与摘要格式、令牌注入、会话清理、权限入口判定和 API 参数转换。
- 组件测试：覆盖登录成功/失败、无后台权限跳转、后台菜单层级，以及五个管理页的加载、空态、写操作确认和 API 错误提示。
- 构建与静态检查：运行 `pnpm build:pc`、`pnpm build:mobile`、`pnpm lint` 和 `pnpm typecheck`。
- 联调：在已可用的后端环境，使用 Contract 示例账号验证 PC 与移动 H5 登录，以及系统设置写操作的请求结构、令牌和乐观锁字段。
