# 门户首页：卡片尺寸统一 + 返回首页不重复加载（PC）

日期：2026-10-09
范围：`frontend/pc-web`（未改后端、未改移动端、未改路由注册）
触发：用户反馈「课程学习/培训的卡片大小和全部任务不一样」「点任务跳转后再返回首页不要又刷新」

## 一、结论

| 问题 | 根因 | 处置 | 实测 |
|---|---|---|---|
| 各页签卡片尺寸不一致 | `.portal-list-grid` 的 `min-height: 220px` 叠加默认 `align-content: normal`（=stretch），内容不足一行高的页签被**整行拉伸到 220px** | 首页网格 `min-height: 0; align-content: start; grid-auto-rows: 110px`，标题两行截断 | 5 个页签卡片全部 **462×110**（改前 220 / 86~114） |
| 返回首页又刷新一次 | 组件级 `KeepAlive` 在本项目路由结构下**保不住页面实例**（见第四节），每次返回都重新挂载并请求 | 改为**会话内快照缓存**：同账号命中快照即原样铺回筛选/页码/列表，不请求、不闪加载态 | 返回首页 **0 次列表请求、0 帧加载遮罩**，页签/页码/条数/卡片高完全一致 |

## 二、卡片尺寸统一

### 2.1 改前实测（真实渲染 1920）

| 页签 | 条目 | 网格行数 | 卡片高 |
|---|---|---|---|
| 全部任务 | 20 | 5 | 86~114px |
| 课程学习 | 4 | 1 | **220px** |
| 考试 | 10 | 3 | 86~114px |
| 随堂测试 | 5 | 2 | 103px |
| 培训 | 1 | 1 | **220px** |

根因链：`.portal-list-grid{min-height:220px}` + `align-content:normal` → 网格高度被撑到 220px →
`align-content: stretch` 把唯一的 auto 行拉到 220px → 单行卡片变形；多行页签天然超过 220px 所以看不出问题。

### 2.2 改动（`views/portal/home/index.vue`）

- 网格：`min-height: 0; align-content: start; grid-auto-rows: 110px`（固定行高，卡片尺寸与页签、条目数、文案长度解耦）。
- 标题：`-webkit-line-clamp: 2` + `line-clamp: 2`（沿用 `views/portal/class/index.vue` 既有写法），最多两行，不会撑破固定行高。
- 无入口的任务：把原来独立成行的提示文案「当前尚无可进入的入口…」收进操作位的静态胶囊 `.home-task-card__idle`（`portal-chip--plain`，全称保留在 `title` 提示里），卡片恒定「标题 + 状态 / 类型·截止」两行。

### 2.3 改后实测（同脚本同视口）

| 页签 | 条目 | 卡片宽×高 | 行数 | 按钮占卡宽 |
|---|---|---|---|---|
| 全部任务 | 20 | 462×110 | 5 | 0.19 |
| 课程学习 | 4 | 462×110 | 1 | 0.19 |
| 考试 | 10 | 462×110 | 3 | 0.19 |
| 随堂测试 | 5 | 462×110 | 2 | 0.19 |
| 培训 | 1 | 462×110 | 1 | 0.19 |

`gridAutoRows=110px / gridMinHeight=0px / gridAlignContent=start` 五个页签一致。

## 三、返回首页不重复加载

### 3.1 做法：会话内快照（`stores/modules/learner-home.ts`）

- 每次成功读取列表后写入快照：`{ owner, kind, page, rows, total, loadedAt }`。
- 首页挂载时若 `reusableBy(owner)` 命中（同账号、已读过一次）→ 直接把 `kind/page/rows/total` 铺回，**不发起请求**。
- 快照按 `userCode` 归属，换账号登录不会串数据；在线消息事件与「刷新任务」仍走真实读取。
- 顺带修掉一个可见闪烁：在线消息触发的补读改为**静默**（`load(true)` 用 `refreshing` 而不占用 `busy`），
  不再每次都闪一次加载遮罩（原先 SSE 事件会把首页列表盖成灰色重载态）。

### 3.2 实测（真实渲染 + 请求计数 + 遮罩采样）

| 步骤 | 结果 |
|---|---|
| 切「考试」页签 | 1 次 `query.kind=FORMAL_EXAM` 请求（用户显式操作，应有请求） |
| 点任务进详情 | `/portal/exams/32/notice/31` |
| 顶部导航回首页 | **0 次**列表请求（`backUrls=[]`）、**0 帧**加载遮罩；页签仍为「考试」、10 条、卡片 110px、分区头「共 10 条」 |
| 对照：整页刷新 | 2 次请求（证明计数器确实在数；其中 1 次是在线消息触发的静默补读） |
| 边界：从「个人学习中心」壳层回首页 | 切回后 **150ms 采样**已有 20 张卡片且无加载态 → 走的是快照；期间 1 次 `kind=ALL` 是静默补读（无遮罩） |
| 页签切换 | 仍按筛选请求 1 次 `query.kind=TRAINING`（快照没有把筛选冻住） |

## 四、为什么组件级 KeepAlive 在这里用不了（记录，避免重复踩）

试过 `PortalLayout` 内 `<RouterView v-slot="{Component}"><KeepAlive :include>…`，两条都失败：

1. `admin-dynamic-routes.ts:504` 对**有子菜单的菜单节点**用 `component: component ?? RouterView` 包装，
   门户页因此在路由记录里还套了一层由「门户模块」菜单生成的 `RouterView`。
   组件树实测：`PortalLayout → RouterView → KeepAlive → RouterView(菜单壳) → PortalHomePage`。
   `include` 按组件名匹配只能匹配到那层壳（`cache` 恒为 0），**按名白名单无效**。
2. 改成按路径进缓存后，壳确实被缓存复用了（`cache={'/portal/home' => RouterView}`，实例 uid 不变），
   但壳内部的 `RouterView` 会随路由变化重渲染，把里面的页面实例**卸载重建**（返回后 `PortalHomePage` uid 由 34 变 112，DOM 标记丢失）。
   → 只要被缓存的子树里含路由级 `RouterView`，页面实例就保不住。

要让组件级缓存真正生效，需要去掉那层菜单壳（把门户模块的子路由直接挂到布局路由下），属于路由注册层改动，
本轮未动（同一文件有并发会话在改）。当前用数据级快照已达到「不重复请求 + 不闪加载态 + 状态还原」。

> 定位脚本（一次性诊断，留在 `.workbuddy/tmp/qa-home-ui/`）：
> `diag-names.mjs` 打印组件链与 KeepAlive 缓存表 → `diag-keepalive.mjs` 跨导航比对实例 uid / 缓存键与类型
> → `diag-cache-entry.mjs` 直读缓存条目的 `type/key/component/el`。

## 五、验证与命令

```
# 静态
cd frontend/pc-web && node ../node_modules/vue-tsc/bin/vue-tsc.js --noEmit          # exit 0
node ../node_modules/eslint/bin/eslint.js src/views/portal/home/index.vue src/stores/modules/learner-home.ts --max-warnings=0   # exit 0

# 契约（新增 1 例：命中快照不再请求接口且还原筛选）
node --test tests/learner-home-message.test.mjs        # 7/7 pass（原 6 例 + 新增 1 例）
node --test tests/*.test.mjs                           # 470/408/62

# 真实渲染（脚本在 .workbuddy/tmp/qa-home-ui/）
QA_TAG=final node tabs-geometry.mjs    # 逐页签卡片几何 + 截图（等 kind 对应接口返回后再量）
node snapshot-probe.mjs                # 返回首页请求数 / 遮罩帧数 / 状态还原
```

零新增失败（HEAD 工作树基线 `462/398/64` → `470/408/62`，按用例名 `comm` 比对：**新增 0 条**，
消失 2 条为本次会话早先修好的既有样式断言）。

## 六、改动文件

| 文件 | 改动 |
|---|---|
| `frontend/pc-web/src/views/portal/home/index.vue` | 网格/行高/标题截断、无入口胶囊、静默补读、快照还原与写入 |
| `frontend/pc-web/src/stores/modules/learner-home.ts` | 新增：首页列表会话快照 |
| `frontend/pc-web/tests/learner-home-message.test.mjs` | 注入 `useLearnerHomeStore` 桩 + 新增快照命中用例 |

`src/layouts/PortalLayout.vue` 与 `tests/portal-layout-routing.test.mjs` **已回退到 HEAD**（KeepAlive 试验未保留）。
