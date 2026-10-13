# 首页 UI 密度收敛与「我的证书」入口授权收敛（PC + H5）

> 日期：2026-10-09　范围：`frontend/pc-web/src/views/portal/home/index.vue`、`styles/portal.less`、
> `views/user-center/{UserCenterHome.vue,profile/index.vue}`、`frontend/mobile-app/src/pages/{index/index.vue,messages/index.vue}`、
> `styles/index.less`。均为前端展示层改动，未改接口、未改权限数据（仅做一次性可回滚的对照实验）。

## 一、用户反馈与根因

| 反馈 | 根因（代码事实） |
|---|---|
| 卡片按钮好长 | PC 首页把 `ElButton` 直接放在 `.portal-list-card__content` 里，该容器是 `flex-direction: column` ⇒ 按钮被 `align-items: stretch` **拉满整卡宽**（占比 0.9）。其余门户页（课程/考试/消息/档案）都把操作包在 `.portal-list-card__actions` 里，只有首页漏了。H5 首页同理：`<button>` 直接放在 `.learner-card` 下被拉成整卡宽。 |
| 好多卡片、排版过多 | 首页卡片继承目录卡的 `min-height: 190px`，内容只有「标题/状态/截止」却被撑高；网格 `minmax(280px,1fr)` 在 1920 宽下排出 **6 列**，同一屏 18~20 张近乎一样的空卡。 |
| 未分配「我的证书」菜单仍有入口 | 首页页头写死三个 `ElButton`（消息中心/我的证书/刷新任务），完全没看菜单授权。实测 `DEMO/admin`（user 2，角色 1）门户菜单只有 门户/首页/学习中心/考试中心/教学辅助，**没有 9751「我的证书」**（`us_sys_role_menu` 里 9751 只给了角色 2/3/6），点进去会被路由守卫送回默认页。 |

## 二、改动

### 1. 首页密度（PC）
- 卡片改**横向待办行卡** `.home-task-card`：左侧「标题 + 状态 chip + 类型·截止」，右侧唯一主操作；
  `min-height: 0` + `flex-direction: row` + `.portal-list-card__actions { margin-top: 0; padding-top: 0 }`。
- 网格 `repeat(auto-fill, minmax(400px, 1fr))`、页内节奏 `gap: 20px`、加 `.portal-section-head`（学习任务 · 共 N 条）。
- 状态用全局 `.portal-chip` 分色（已完成=绿 / 未完成=橙 / 已结束等=灰），类型与截止合一行；无入口时提示留在左列。
- 页头动作改走统一口径 `.portal-heading__actions`：跨模块入口用 `.portal-entry-link`（消息中心 / 我的证书），本页刷新用 `ElButton`。

### 2. 门户页头入口胶囊可读性（全局 `styles/portal.less`）
`.portal-entry-link` 原为「白底 20% + 白字」，而页头右侧同时叠了 `::before` 斜向柔光带与 `::after` 右上光晕，
**被提亮到接近白色 ⇒ 白字对比度不足**（课程中心同样问题，只是没人反馈）。改为**品牌色实心胶囊 + 白色内描边**，
`height:32px` / `radius:999px` 几何与胶囊形状不变（5 处用法均在渐变页头内）。

### 3. 首页密度（H5）
- hero 补 `learner-hero__deco` + `__eyebrow`（与消息/档案页同源）；消息中心与刷新任务由整宽按钮改为
  `.learner-toolbar` 内两枚胶囊。
- 分类筛选改用全局 `.segmented-filter`（与学习/考试页统一），本页按触控基准 `min-height: 88rpx`。
- 卡片改「标题 + 状态 badge」一行、类型·截止一行、`.learner-actions` 内紧凑主按钮。
- 新增全局 `.learner-card__head` + `.learner-badge(--success/--warning/--plain)`（消息页同步复用，删掉页内 `.message-badge`）。

### 4. 「我的证书」入口按菜单授权收敛（PC 三处）
判定统一为 `getAuthorizedPortalNavigation(userInfo).some(path === '/portal/certificates')`，
与顶部门户导航、路由守卫同源，不新增权限码：

| 位置 | 改动 |
|---|---|
| `/portal/home` 页头 | `v-if="canOpenCertificates"` |
| `/user-center`（个人学习中心）二级导航 | `visibleEntries` 计算属性过滤 |
| `/user-center/profile` 页头 | `v-if="canOpenCertificates"` |

## 三、证据

### 3.1 真实渲染几何（脚本 `.workbuddy/tmp/qa-home-ui/{pc-home-shots,h5-home-shots}.mjs`）

| 视口 | 列数 | 卡片宽/高 | 主操作按钮宽 | 按钮宽/卡宽 | 横溢 | 页头入口链接 |
|---|---|---|---|---|---|---|
| PC 1920×1080 | 4 | 462 / **110**（改前 190 最小高、按钮满宽） | 88 | **0.19**（改前 ≈0.9） | 无 | 仅「消息中心」 |
| PC 1440×900 | 3 | 460 / 110 | 88 | 0.19 | 无 | 仅「消息中心」 |
| H5 390×844 | — | 357 / 128（最高 149） | 87 | **0.24** | 无（scrollWidth == clientWidth == 390） | — |

其余断言：`tabs=5`、`sectionHeads=1`、`chips=20`、`emptyState=0`、H5 `heroDeco=1`、`eyebrow=MY LEARNING`、
`filterItems=5`、`filterItemH=58px`、`filterActive=1`、状态 badge 颜色实测四色（蓝/绿/灰/橙）。

### 3.2 「我的证书」入口双向对照（`gate-positive-control.sh`，含回滚）

```
role1 菜单总数(补前)=218     已补 9751 行数=1
--- 授权态 --- hasCertificatesMenu= true
   浏览器: entryLinks=["消息中心","我的证书"]  certLinks=1
role1 菜单总数(回滚后)=218   残留9751=0   ROLLBACK_OK
--- 未授权态 --- hasCertificatesMenu= false
   浏览器: entryLinks=["消息中心"]            certLinks=0
```
接口侧只读证据：`DEMO/admin`（user 2）门户菜单 = 门户 / 首页 / 学习中心 / 考试中心 / 教学辅助（`/portal/certificates` 缺失）。

### 3.3 静态校验

- `vue-tsc --noEmit`：pc-web **exit 0**、mobile-app **exit 0**（顺序执行）。
- ESLint（改动文件，`--max-warnings=0`）：PC 3 文件 **exit 0**、H5 2 文件 **exit 0**。
- 契约测试：`clean-modern-style-tokens` **6/6**（改前 4/6）、`learner-home-message` **6/6**。

### 3.4 零新增失败（HEAD 基线对照，detached worktree + node_modules 目录联接）

- 受影响用例集（读本轮改动文件的 5 个测试文件）：基线 18 例 6 失败 → 本轮 **23 例 4 失败**，失败用例名**逐个为基线子集**。
- 全量 PC：基线 **462 例 / 398 过 / 64 失败** → 本轮 **469 例 / 407 过 / 62 失败**；
  `comm` 逐条比对：**新增失败 0 条**，基线失败中消失 2 条（`PC Web 核心壳层使用语义 Token…`、
  `移动端基础布局、登录页和首页遵循留白、卡片与触控基准` —— 新首页样式正好满足这两条既有断言）。
- 移动端：47 例 4 失败（素材预览/课程作答/考试页），4 条读到的文件全部 `= HEAD`，且**没有任何移动端测试读本轮改动文件**。
- 基线工作树与目录联接已清理（先删联接再删 worktree，`frontend/node_modules/esbuild` 仍在）。

## 四、说明与遗留

1. 首页任务卡**没有使用 `.portal-cover` 封面信息条**，是刻意的密度取舍：待办清单需要的是「一眼扫完 20 条」，
   封面条会把卡片高度翻倍。若产品更看重与课程/消息页的封面语言完全一致，可换成 `portal-cover` 版本（已备好结构）。
2. `.portal-entry-link` 由白底半透明改为品牌色实心，属**全局**口径变化（课程中心、考试中心同款入口一并变清晰）；
   几何未动，`baseline.mjs` 的页头几何（x/w/radius/padding）不受影响。
3. 本轮验收期间工作树**有并发会话在写**（`frontend/pc-web/src/router/admin-dynamic-routes.ts`、
   `mobile-app/src/pages/messages/index.vue` 的考试信息逻辑）。上述证据均取自本轮改动自身的文件；
   移动消息页的徽标类名替换与其新增逻辑不冲突，但两者尚未合并验证，交付时需一并复跑。
