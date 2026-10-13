# PC Web UI 标准

本文档是安全教育培训系统 PC Web 的长期界面标准。基础 token 可共享，但门户和后台管理是两套不同的信息架构与页面语言：开发前必须先按路由归类为 `/portal` 或 `/admin`，再遵循对应章节；不得把后台工作台组件直接用于门户，也不得把门户学习卡片直接用于后台管理。

门户与后台壳层遵循同一套基础视觉 token，但必须按各自的信息架构使用对应的布局容器，禁止跨场景混用。

## 1. 基础密度

| 项目         | 标准                                  |
| ------------ | ------------------------------------- |
| 页面背景     | `var(--color-bg-page)`              |
| 卡片背景     | `var(--color-bg-surface)`           |
| 正文         | 14px，`var(--color-text-regular)`   |
| 辅助文字     | 12px，`var(--color-text-secondary)` |
| 页面标题     | 18px，600                             |
| 卡片标题     | 16px，600                             |
| 控件高度     | 32px                                  |
| 工作区外间距 | 16px                                  |
| 卡片间距     | 12px                                  |
| 卡片内边距   | 16px                                  |
| 控件间距     | 8px                                   |

## 2. 后台管理壳层（仅 `/admin`）

- 顶部工具栏固定为 64px，品牌、模块导航和用户菜单垂直居中。
- 后台左侧菜单默认 168px，折叠为 48px；菜单按 12px 基础左内边距 + 每级 14px 递进缩进，最深层级的三级标题也须保留不小于 24px 的右侧留白；tabs-card 与折叠状态保存在 `sessionStorage`，关闭浏览器标签页后自动清空并恢复默认值。
- 工作区使用 `min-height: 0`，页面本身不产生长滚动条；表格内容在表格区域内滚动。
- 后台内容顺序固定为：tabs-card → breadcrumb → 页面内容。
- 不再使用旧的 `page-header` 作为页面标题容器。
- 后台概览页是唯一的轻量壳层例外：隐藏侧栏、面包屑和 tabs-card，只保留顶部工具栏与概览内容。

## 3. 前台门户（仅 `/portal`）

门户面向学员完成学习、考试和培训任务，基准页面为 `/portal/class` 的学习中心；它不是后台管理系统的简化版。

1. 使用门户已有的品牌栏和业务导航，不使用后台侧栏、`tabs-card`、面包屑或 `admin-*` 页面容器。
2. 学员任务中心（课程、考试、培训）使用学习中心式页头：32px 内边距、`var(--radius-card)` 圆角、`linear-gradient(120deg, #155eef, #4f8bff)` 蓝色横幅；眉标、标题和说明使用白色层级，右侧仅放刷新等轻量操作。
3. 任务使用网格卡片呈现：`repeat(auto-fill, minmax(280px, 1fr))`、18px 间距、卡片最小高度 190px。卡片顶部为标题、来源/副标题和状态标签，正文只呈现学习者需要的关键信息，底部放主要操作。
4. 门户任务页不得默认放置后台式 `ElForm inline` 筛选区、批量操作工具栏或表格。契约支持的筛选和分页可以保留在请求层；只有已确认存在学员检索场景时，才在门户语境中单独设计轻量入口。
5. 门户的分页、空状态、错误反馈和 Element Plus 基础控件仍使用共享 token 与中文文案，但应自然位于内容区或卡片网格之后，不模拟后台工作区的固定表格布局。
6. 同一类门户页面优先复用已存在的门户页面结构和 scoped 样式。考试中心必须以学习中心的横幅与卡片语言为准；考试作答、结果等详情页保持同一门户页头和清爽内容层级。

### 3.1 门户列表公共样式实现约定

课程、考试和培训列表页统一使用全局 `portal-list-*` class，公共视觉规则只维护在 `frontend/pc-web/src/styles/portal.less`：

1. 页面根节点使用 `portal-list-page`，页头使用 `portal-list-page__heading` 和 `portal-list-page__eyebrow`。
2. 状态筛选使用 `portal-list-page__tabs`、`portal-list-page__tab` 及 `portal-list-page__tab--active`。
3. 列表网格使用 `portal-list-grid`，卡片使用 `portal-list-card`；卡片内部的标题、元信息和操作区分别使用 `portal-list-card__header`、`portal-list-card__meta` 和 `portal-list-card__actions`。
4. 空数据或加载失败的内容区使用 `portal-list-state`，分页使用 `portal-list-page__pagination`。
5. 页面 scoped Less 只保留封面、业务详情、抽屉和页面专属状态等差异化样式，不重复定义页头、tabs、网格、卡片基础尺寸和公共响应式规则。

## 4. 后台管理列表页（仅 `/admin`）

### 4.1 业务列表页标准

后台课程管理中心作为业务列表页基准，统一使用以下结构：

1. 第一个 `ElCard`：筛选表单。
2. 第二个 `ElCard`：`card-header` 放列表说明和新增、导入、导出等操作；正文为表格；底部为分页器。

列表页不再写页面级标题：`admin-business-page__header` 只保留全局定义以兼容历史页面，课程、考试、培训、成果等列表页一律不写页面标题；列表名称与主操作统一落在列表卡的 `admin-business-page__table-heading` 内，左标题、右操作两端对齐。

全部的列表页面必须复用 `admin-business-page`、`admin-business-page__filters` 和 `admin-business-page__table` 公共样式。

标准结构如下：

1. 第一个 `ElCard`：筛选表单，使用 `ElForm inline`。
2. 第二个 `ElCard`：`card-header` 放列表说明和新增、导入、导出等按钮；正文放 `ElTable`；底部放 `ElPagination`。
3. 分页器固定使用：`total, sizes, prev, pager, next`。
4. 表格操作栏使用 `ElButton link type="primary"`；仅删除使用 `ElButton link type="danger"`。

#### 4.1.1 带页签的列表页

同一页面需要按视角切换列表时（如问卷管理的“问卷 / 投放管理”、培训档案的“一期一档 / 企业档案 / 个人档案”），页签统一使用全局 `admin-page-tabs`，页签内容仍复用上面的列表卡骨架：

1. `ElTabs` 直接作为 `admin-business-page`（列表页）或 `admin-detail-page`（查询页）的子元素，不再包在业务卡片里。
2. 页签是本页主体内容时追加 `admin-page-tabs--fill`：页签条固定高度，内容区占满剩余高度，页签内的列表卡随卡片贴底。
3. 每个页签内部按“筛选卡（`admin-business-page__filters`）→ 内容卡（`admin-business-page__table`）”排列；主操作按钮放在内容卡标题行 `admin-business-page__table-heading` 右侧，不再放页面标题行。
4. 页签内的内容卡统一占满剩余高度：列表卡由表体滚动；详情卡（如培训档案的一期档案）复用 `admin-business-page__table` 的卡身与卡头，并让 `ElCard` 正文滚动，页面自身不出现滚动条。不占满高度的辅助卡（如问卷管理的投放表单卡）显式写 `flex: 0 0 auto`。
5. 页签的字号、字重与选中色只在 `.admin-page-tabs` 内定义一次，页面不得再写 `:deep(.el-tabs__*)` 覆盖。
6. 同一页面既可切换视角、又支持路由直达单条记录时（培训档案从培训计划工作台进入一期档案），直达态仍是详情页，保留 `admin-detail-page__header` 与返回入口，只把内容卡换成同一份详情组件。

全局常量位于 `frontend/pc-web/src/constants/ui.ts`：

- `LIST_PAGE_SIZES`

表格高度不再以 `LIST_TABLE_HEIGHT`、`TREE_TABLE_HEIGHT` 或页面内 `calc()` 单独设置。`admin-business-page__table` 通过弹性布局占满工作区剩余高度，`ElTable` 在该容器内滚动；树形表格与普通列表遵循同一规则。

所有列表分页都必须使用 `DEFAULT_LIST_PAGE_SIZE` 作为默认值、`LIST_PAGE_SIZES` 作为可选项，并使用 `v-model:page-size` 将选择结果回传到对应查询参数；后续只维护分页常量即可统一全局配置。

以指标、图表和明细呈现事实的统计与概览类页面不使用本节骨架，一律按第 7 节实现。

## 5. 后台管理导航（仅 `/admin`）

- 面包屑来自动态菜单路由的完整 `matched` 路径，示例：系统管理 / 机构组织 / 岗位管理。
- tabs-card 记录本次会话打开的页面，支持点击切换、关闭当前、关闭其他、关闭全部。
- 权限仍由服务端和现有授权菜单数据决定；UI 只负责展示和导航。

## 6. 后台管理详情、编辑与工作台页（仅 `/admin`）

详情、创建、编辑和工作台页面统一使用 `admin-detail-page`。页面标题使用 `admin-detail-page__header`：左侧为标题与说明，右侧仅放返回或主要操作；不再各自定义页面标题高度、字体和间距。

存在多个阶段的页面（如课程、试卷、培训标准、培训模板、评价表单）使用 `admin-detail-page__steps` 放置步骤导航，步骤下方使用 `admin-detail-page__surface` 和主 `ElCard` 作为工作区。步骤导航独占一行，主工作区占用剩余高度并在卡片内滚动。

多阶段工作台的 DOM 骨架固定为 `admin-detail-page` → `admin-detail-page__header` → `stage-nav admin-detail-page__steps` → 单个 `admin-detail-page__surface`。`admin-detail-page__surface` 只能由 workspace 页面使用一次；每个步骤业务组件只能以 `admin-detail-page__card` 作为主 `ElCard`，不得嵌套 `admin-detail-page__surface` 或覆盖其全局布局样式。步骤业务组件必须放在所属模块 `components/` 目录，workspace 只保留共享数据、权限、真实接口调用和事件编排，并通过 props / events 传入；禁止以默认或具名插槽把步骤业务模板放回 workspace。

无步骤的详情或创建页面使用 `admin-detail-page__card` 作为主内容卡：卡片占满工作区剩余高度，内容在卡内滚动，页面主体不产生长滚动条。

表单统一使用 `admin-detail-form`，结构必须与课程中心 `CourseBasicPanel.vue` 一致：`ElForm` 内使用 `ElRow :gutter="24"` 和 `ElCol :span="12"` 排列常规字段；题干、说明、富文本、文本域和可变列表使用独立整行表单项（`admin-detail-form__wide`）。常规控件宽度 320px，数字控件 220px；单列详情使用 `admin-detail-form--single`，最大内容宽度为 840px。禁止将普通 `ElInput`、`ElSelect` 拉满整个内容区域。

详情页提交、保存、取消和上一步/下一步操作统一使用 `sticky-actions`（兼容已有 `admin-detail-action-bar`），固定在内容卡底部右侧。带操作栏的详情卡结构固定为：标题卡头 → `admin-detail-page__form-scroll`（中间内容滚动）→ `sticky-actions`（底部操作）。禁止让 `sticky-actions` 与同一滚动容器内的展开表单内容重叠，或让内容增长后把操作栏挤出可视区域。页面局部结构确有特殊需要时可增加 scoped 样式，但不得覆盖上述通用宽度、高度和间距。

课程中心工作台、试卷工作台和考试工作台是多阶段页面的基准实现：三者均固定为“标题区 → 步骤区 → 主内容卡”的纵向结构。主内容卡内部必须固定为“卡片标题（如有）→ `admin-detail-page__form-scroll` → `sticky-actions`”，只有中间内容区可滚动；步骤切换、表单项增多或选项动态增加时，底部操作栏都必须始终可见且不遮挡表单。

## 7. 后台统计与概览页（仅 `/admin`）

统计与概览类页面（考情分析、课程统计、线下课堂统计、班级统计、培训统计与记录、培训成果、监管工作台等以“指标 + 图表 + 明细”呈现事实的页面）与列表页、详情页并列，是第三种页面形态。这类页面的数据来自多个聚合接口，需要同时呈现概览结论和多个明细分组，因此**不得**复用列表骨架 `admin-business-page`：该骨架按“筛选卡 + 一张撑满剩余高度的列表卡”设计，多个卡片会被弹性布局挤压变形。概览信息块统一使用 `ws-` 前缀的全局类（维护在 `frontend/pc-web/src/styles/admin-workspace.less`），页面只提供数据与结构，不重复声明卡片、指标与留白。

### 7.1 页面骨架

固定为：

1. `admin-detail-page` 作为页面根节点。
2. `admin-detail-page__header`：左侧 `h1` 与业务口径说明，右侧只放返回按钮。
3. 第一个 `ElCard` 使用 `admin-business-page__filters`，筛选规格与列表页一致。
4. 第二个 `ElCard` 使用 `admin-detail-page__card`，是本页唯一的内容卡；卡片占满剩余高度、内部整体滚动，页面自身不产生长滚动条。
5. 内容卡内部按 `ws-hero` → `ws-metrics` → 若干 `ws-section` 的顺序纵向排列。**内容卡内不得再嵌套 `ElCard`**，每个业务分组一律用 `ws-section` 白卡表达，避免“卡片套卡片”。

### 7.2 概览 hero（`ws-hero`）

用一块 hero 交代“这是哪个资源的统计”，替代页面级标题。

1. 左侧 `ws-hero__main`：`ws-hero__eyebrow`（英文眉标）→ `ws-hero__title-row`（`ws-hero__title` + 身份 `ElTag`）→ `ws-hero__meta`（若干 `ws-hero__chip` 排列关键口径，末尾可跟一行说明）。
2. 右侧 `ws-hero__aside` 内放一个 `ws-hero__state`：`strong` 为主指标值、`small` 为指标名；只放一个主指标（完成率、平均分、总参训人数等），其余指标交给 `ws-metrics`。
3. hero 的渐变底、圆角与右上角装饰光晕由全局提供，页面不得自写背景或装饰元素。

### 7.3 指标卡（`ws-metrics` / `ws-metric`）

把表格里的关键数字提取为指标卡，使用统一网格与等宽数字。

1. 容器 `ws-metrics` 自适应列数；单卡 `ws-metric` = `ws-metric__label` + `ws-metric__value`。
2. 语义色只用四个修饰类：`ws-metric--accent`（本页主指标，如完成率）、`ws-metric--pass`（通过、达标）、`ws-metric--fail`（不通过）、`ws-metric--pending`（待处理、待补）。
3. **零值不染色**：待批阅、未发布成绩、缺考等计数型指标为 0 时保持中性样式，大于 0 才加 `--pending`；不得把整排指标都染成语义色，否则失去提示作用。
4. 缺失值统一显示 `—`，不得用 0 冒充。

### 7.4 分组白卡（`ws-section`）

每个业务分组一张白卡，标题左侧的品牌色短竖条由全局提供。

1. `ws-section__head` 左侧 `<div>` 内为标题（`h2` 或 `h3`）与口径说明 `<p>`；右侧操作区为第二个子元素：单行小字用全局 `ws-section__extra`，按钮组用一个 `flex: 0 0 auto` 的容器承载（现有页面以 scoped `section-actions` 实现），不得把按钮塞进标题行或改用 `margin-left: auto` 推到卡片最右。
2. 相邻白卡的间距由全局 `.ws-section + .ws-section` 提供，页面不得再写相邻选择器或外边距。
3. 图表统一使用 `src/components/statistics/StatisticsChart.vue`：该组件**自带完整 `ws-section`**（标题、口径说明、右侧“下载统计图”、图体与空态），页面直接摆放在内容卡里即可，不得再包一层 `ElCard` 或 `ws-section`；通过 `subtitle` 传筛选口径、`unit` 传单位、`kind` 选 `bar`/`pie`、`downloadable` 控制下载按钮。
4. 字段不多但密度高的信息（场次信息、课程汇总、计划身份）用 `ws-kv` 网格替代三列表格：`ws-kv__item`（`--span` 占整行）/ `ws-kv__label` / `ws-kv__value`（`--muted` 次要值、`--multiline` 保留换行）。

### 7.5 页签、下钻与返回入口

1. 需要按视角切换时（如培训统计与记录的“培训计划完成情况 / 全部课程培训记录”），按 4.1.1 使用全局 `admin-page-tabs`，直接作为 `admin-detail-page` 的子元素；页签是本页主体时追加 `admin-page-tabs--fill`。
2. 人员明细等下钻内容统一用对话框承载，对话框内的表格、分页与空状态规格与页面一致。
3. **从列表页跳转进入的统计页必须提供返回入口**：在 `admin-detail-page__header` 右侧放 `返回` 按钮（`ArrowLeft` 图标 + `$router.back()`），与详情页保持一致；不得依赖浏览器后退或面包屑代替。

### 7.6 分页

概览页内的所有表格（含下钻对话框内的表格）都必须使用 `DEFAULT_LIST_PAGE_SIZE` 作为默认值、`LIST_PAGE_SIZES` 作为可选项，并带 `v-model:page-size` 与 `total, sizes, prev, pager, next` 布局；不得使用简化分页器。

## 8. 后台管理组件规范（仅 `/admin`）

筛选区域内的查询、重置、导入和导出等操作按钮必须放入独立的 `ElFormItem`，不得直接作为 `ElForm` 子节点。筛选表单必须支持换行，操作按钮组紧随最后一个筛选项；空间不足时整组换行，禁止使用 `margin-left: auto` 将操作按钮推到卡片最右侧。

筛选卡只承载筛选项与筛选操作，不得放置跳转入口、统计入口、说明文字等其他内容。全局 `.el-card__body` 是 `flex-direction: column`，凡直接作为卡片子元素的按钮都会被 `align-items: stretch` 拉满整卡宽（1440 视口下实测约 1190px）并让文字居中，观感即“一条横贯卡片的孤立文字”。这类入口一律放进列表卡标题行的 `admin-business-page__table-actions`，与新增、导入、导出等操作同排右对齐，主操作放最右且保持唯一的 `type="primary"`；入口多于一个时用图标区分语义（如统计类用 `Histogram`、`TrendCharts`）。

tabs-card 超出可视区域时隐藏原生横向滚动条，左右显示滑动按钮；按钮根据当前滚动位置自动启用或禁用，并使用平滑滚动。

tabs-card 和侧栏折叠状态仅限当前登录会话：退出登录时必须同时清空 `safety-admin-tabs` 与 `safety-admin-sidebar-collapsed`，不同账号不得继承上一账号的界面状态。

字典管理不使用“字典类型 / 字典数据”的页内 Tabs。页面以 `GET /api/system/dict/catalog` 预览左侧字典目录，选中类型节点后在右侧维护该类型的数据；新增字典数据必须自动绑定当前节点，类型新增、编辑和删除入口保留在同一工作区内。

- Element Plus 组件使用全局 token，不在页面内覆盖颜色和圆角。
- Element Plus 必须使用简体中文 locale；程序化 `ElMessageBox` 的默认按钮固定为“确认”和“取消”，页面传入的“确认删除”等具体中文文案可覆盖默认值。
- 表单占位符、空状态、分页器、确认框和错误提示使用中文。
- 状态、结果、模式等枚举一律以中文标签呈现（`ElTag` + 中文映射函数），禁止把后端枚举原值直接渲染给用户（如 `NOT_STARTED`、`PASSED`、`COMPLETED`）；新增后端枚举时必须同步前端映射表，映射函数注释里标注来源枚举便于维护。
- 新增公共组件必须放入 `src/components`，页面特有样式使用 scoped Less。
