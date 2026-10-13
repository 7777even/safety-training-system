# 前端协作规范

## 前端多端结构

- `pc-web/`：Vue 3 + TypeScript + Vite，用于统一门户、用户中心、管理后台。
- `mobile-app/`：uni-app + Vue 3 + TypeScript，用于移动 H5、App、企业微信入口。
- `shared/`：跨端稳定共享的类型、请求模型、常量与纯工具；不得包含特定端路由、UI 或运行时依赖。

## Frontend Package Manager

- 前端包统一由 `frontend/` 内的 pnpm workspace 管理，workspace 范围为其直属应用包。
- `shared/` 是 workspace package，包名固定为 `@safety/shared`；PC Web 和移动端引用它时必须使用 `workspace:*`。
- 依赖安装、升级、移除和脚本执行只能使用 pnpm，禁止在前端目录混用 npm 或 yarn。
- `frontend/package.json` 提供跨应用开发、构建、lint 和 typecheck 入口；应用内脚本仅负责自身执行。
- 遇到 `package-lock.json` 或 `yarn.lock`，必须先提示并得到确认后才能删除；唯一锁文件为 `frontend/pnpm-lock.yaml`。

## 职责边界

PC Web 负责桌面工作流；移动端独立适配移动任务、触控和窄屏信息层级，禁止复制 PC 页面结构。

## shared 共享规则

仅提取至少两个端确实需要且语义一致的内容；共享类型必须来自已确认 API Contract，且 `shared/` 不得反向依赖两个应用。

## API 调用规则

所有请求必须以 `docs/api/` 的已确认 Contract 为依据，统一封装鉴权、错误处理与类型转换；不得拼接未定义字段；禁止：前端自行设计接口。
契约本身的编写与补齐必须遵循 `docs/architecture/openapi-contract-writing-guide.md`；发现缺口时先按该规范更新 OpenAPI，再改前端类型与 Mock。

## 页面生成核心要求

* PC Web 页面严格使用 Element Plus 官方组件；移动端不得使用 Element Plus。
* 采用 Vue3 setup + TS 标准语法编写，代码添加必要业务注释，保障代码可读性，组件命名采用 PascalCase，工具函数命名采用 camelCase，禁止使用魔法值；
* 自动生成 scoped 隔离基础样式，保障页面基础布局正常渲染；
* 生成的 Vue 页面文件统一输出至对应业务模块 views/pages目录；
* 页面生成需保持项目风格统一性，统一页面布局结构、组件使用规范、交互逻辑风格及视觉呈现，确保不同页面间操作体验连贯、代码可读性强、风格统一无差异，智脑体需按照规范化描述输出符合项目风格的代码。

## UI Design Rules

- 前端页面必须遵循 [清爽现代风样式基准](../docs/design/clean-modern-style-baseline.md)、已确认的 Figma Design System 与 `docs/design/` 设计规范。
- 禁止自行创造与既有设计系统不一致的页面风格。
- 新增页面必须优先复用已有组件规范；新增组件规则需先同步设计系统。
- PC Web 和 Mobile 必须保持语义颜色、字体层级、状态表达的一致性，并分别适配桌面信息密度与移动触控场景。
- 页面开发前必须确认对应 Figma 页面和设计状态；设计中的接口示例仍须以 `docs/api/` 已确认契约为准。
- 门户、后台、详情页及移动端的样式基准均为“清爽现代风”；不得使用拟态极简主题，课程封面渐变不得扩展为系统级视觉语言。
- `/portal` 与 `/admin` 必须先按页面归属选择对应 UI 规则：门户遵循 `docs/design/pc-web-ui-standard.md` 的“前台门户”章节，后台管理遵循其“后台管理”章节，禁止交叉复用页面容器、筛选工具栏和导航结构。
- 门户学习类页面必须以既有 `/portal/class` 为视觉基准，使用门户横幅和任务卡片；不得使用 `admin-*` 容器、后台筛选表单、表格工作区、后台侧栏、面包屑或 tabs-card。后台管理页面也不得使用门户学习卡片替代管理列表。

## PC 后台表单与列表体验规则

- 任何全局主键或外键 ID 禁止由用户手工输入；必须使用已有业务选择组件，或使用对应 OpenAPI 列表/树接口驱动的 `ElSelect`、`ElTreeSelect`。机构统一使用 `OrganizationSelect`；上游选择变化时必须清空并重载已失效的下游选择。
- Element Plus 所有用户可见的标签、占位、空态、确认、错误及校验提示必须为中文；表单必填与格式规则必须显式提供中文 `message`，不得依赖组件默认校验文案。
- 图片和文件业务字段禁止输入文件 ID；必须使用统一上传组件调用 OpenAPI 上传接口，并将响应的 `data.fileInfoId` 映射到目标业务字段。需要编辑回显时，应通过契约已有的文件详情接口取得文件信息。
- Element Plus 全局组件必须使用中文语言包，分页器、空数据、确认框等系统文案不得显示英文。
- 列表分页的初始页面大小必须使用 `constants/pagination.ts` 的 `DEFAULT_LIST_PAGE_SIZE`，并将同一值直接传入分页接口的 `pageSize` 参数；仅供下拉选项加载的分页请求必须以独立常量命名，避免与列表分页混用。
- 查询区的 `ElSelect`、`ElTreeSelect` 必须设置明确宽度，避免在行内表单中收缩为图标宽度。
- 用户可编辑的 `ElInput`、`ElSelect`、`ElTreeSelect` 必须提供中文 `placeholder`；复杂或存在歧义的输入须补充中文 `tooltip` 或字段说明。
- API Contract 的枚举值用于 `ElSelect`、单选或状态筛选时，必须定义 `{ label: '中文文案', value: 'CONTRACT_ENUM' }` 常量映射；界面不得直接把英文枚举值作为可见选项文案。

## Mock First 规则

先契约、后 Mock、再接入真实接口。Mock 的字段、状态码和错误场景必须与契约一致，并在真实接口接入前后保持契约核对。

Mock必须：

标记：

TODO replace real api

接口完成：

必须删除Mock

## 权限显示规则

前端只根据权限数据控制菜单、入口和提示展示；隐藏入口不是权限控制，真实资源访问由后端校验。不得硬编码管理员身份。

## 接口边界

禁止前端自行定义接口、字段、错误码或权限语义。发现缺口时，先更新 OpenSpec 与 `docs/api/` 并确认，再更新 Mock 与调用代码。

## 目录与命名

- PC Web 使用 `api`、`assets`、`components`、`constants`、`layouts`、`libs`、`mock`、`router`、`stores`、`styles`、`types`、`utils`、`views`；门户、用户中心和后台页面分别位于 `views/portal`、`views/user-center`、`views/admin`。
- 移动端使用 `api`、`components`、`constants`、`libs`、`mock`、`pages`、`stores`、`styles`、`types`、`utils`；页面按移动任务放入相应 `pages` 子目录。
- `components` 与页面 Vue 文件使用 PascalCase，业务目录使用 kebab-case，工具函数使用 camelCase，类型/接口使用 PascalCase，常量使用 UPPER_SNAKE_CASE。
- 路由路径、状态码、角色码和分页大小不得散落在代码中；应放入应用或 shared 的 `constants`。角色语义必须来自已确认的 API Contract。

## Vue、样式与依赖

- PC Web 的 Vue 文件使用 Vue 3 Composition API、`<script setup lang="ts">` 与 scoped Less 样式；复杂模板逻辑应移至 computed 或函数。
- 两端统一使用 Less：全局样式只放变量、reset 与基础布局；页面样式必须使用 scoped Less，不得随意创建全局 class。
- PC Web 使用 Element Plus 的按需自动导入，禁止 `app.use(ElementPlus)`；图标从 `@element-plus/icons-vue` 按需导入。
- 移动端只使用 uni-app 能力，保留 H5、App、企业微信入口边界；具体 App/企业微信细节为“暂定”，不得提前实现。

## 基础设施边界

- `libs` 只存放无页面依赖的通用函数，函数必须显式声明返回类型；`types/index.ts` 汇总类型导出，公共 API 响应类型放入 `api.ts`。
- Request 只提供 baseURL、拦截/Token/统一错误处理基础能力，不得新增真实业务接口；移动端基于 `uni.request`，不得照搬 Axios。
- Pinia 用于跨组件复杂状态；user store 仅保留 token、userInfo、roles 基础结构。PC permission store 仅控制入口显示，后端负责真实鉴权。
- EventBus 只用于刷新、通知、布局等轻量事件，不传递业务状态；页面不得自行创建 mitt 实例。
- PC Web 路由组件必须动态 import，路由 name 使用 PascalCase、path 使用 kebab-case，meta 预留 title、requiresAuth、roles；移动端页面路由由 `pages.json` 维护，不套用 Vue Router。
