# 2026-09-29 三效日报（项目进展口径）

> 生成时间：2026-09-29 17:35
> 代码快照：`main` @ `8d6774cd`（= `origin/main`，今日提交均已推送）
> 口径：按 Change / 功能项列今日产出、状态、缺口与待决策项。Owner 均为 fxw11。

## 零、一句话结论

今日完成 **10 个提交**（81 个文件变更、+1789 / −148），**已推送 `origin/main`**；交付「学员端课程评价闭环」与「一期档案收录专家查课」两个功能单元，归档 2 个已完成 Change（归档目录 168 → 170），新增 L3/L4 业务需求清单（87 功能项 × Change 覆盖矩阵）。

**另有一件不在提交里但在推进的事**：M5 PC 监管闭环（Change `2026-09-29-close-m5-pc-regulation`）Task 1～3 于今日 15:45 后由另一会话实施，代码与静态验证完成、**尚未提交**，真实登录验收未做 —— **M5 不得按前后端闭环标记完成**。

## 一、今日产出（按功能单元）

### 1.1 学员端课程评价闭环 — `feat(course)` `687d473c`（31 个文件）

补全「提交评价 / 本人回看 / 同次开课学员评价列表」三件事，PC 学习页与移动端课程详情同步接入。

- 后端：`CourseLearningController/Service/Impl` 新增评价列表读取，新增 `CourseEvaluationListItemVO`，`docs/api/course.openapi.json` 同步；补 `CourseEvaluationMapperIT` 与 3 个单测。
- 前端：`shared` 课程学习类型、双端 `api|mock/learner-course.ts`、PC `portal/class/index.vue` + `learning.vue`、移动 `pages/course/detail.vue` + `index.vue`、双端 Node 用例。
- Change：`2026-09-29-show-peer-course-evaluations`（新建，含 proposal/design/tasks/spec-delta）+ QA + retro；顺带补 `2026-07-24-implement-m1-frontend-foundation-course/tasks.md` 的 Task 3 记录。
- **未拆分的理由（重要）**：「课程评价提交/本人回看」与「同次开课评价列表」在 `course/detail.vue`、`portal/class/learning.vue`、双端 `api|mock/learner-course.ts` 的**同一个 hunk 内交缠**，按 hunk 拆会产出「tab 存在但列表函数未定义」的不可编译中间态，故合并为一个提交并在 commit body 说明。**本轮零 hunksplit。**

### 1.2 一期档案收录专家查课 + 培训结束后回看 — `feat(archive)` `ab52754c`（17 个文件）

- 后端：`ArchiveServiceImpl` / `ArchivePdfRenderer` / `ArchiveViewVO` 收录专家逐项查课意见，新生成的档案 PDF 含专家事实；补 `ArchiveServiceImplTest` 与 IT 配置。
- 前端：PC `PlanArchivePanel.vue`（档案页展示）、`PlanExecutionPanel.vue` + `PlanExpertInspectionExecutionPanel.vue`（`ENDED` 计划只读回看）。
- Change：`2026-09-29-review-expert-inspections-in-archive` + QA + retro。个人/企业档案可见范围未扩大。

### 1.3 课程考核结果分页改用显式查询 DTO — `fix(course)` `7dd44dde`（4 个文件）

新增 `CourseAssessmentResultQuery`，替换 `PageRequest<Void>`，并补泛型回归断言。对齐 2026-08-26「不新增 `PageRequest<Void>`」的约定。

### 1.4 成绩已发布但学员不可见 → 改为可回看答题情况 — `fix(portal)` `7223550b`（5 个文件）

PC `portal/exams/index.vue`、`portal/exams/result.vue`，移动 `pages/exam/index.vue`、`result.vue`，管理端发布提示同步。

### 1.5 培训列表按计划阶段解释未形成的结果 — `fix(portal)` `68eeb1c7`（4 个文件）

PC `portal/training/index.vue` + 移动 `pages/training/index.vue`，结果未形成时按计划阶段给出解释而非空白；双端用例同步。

### 1.6 PC 布局修正 — `fix(pc-web)` `7018e904`（6 个文件）

工作区横向滚动条、侧栏滚动、表格列宽、标签可读性。

- **根因（公共类，非页面自身）**：`styles/admin-workspace.less` 的 `.ws-hero::after` 装饰光晕 `position:absolute; top:-52px; right:-34px` **向右溢出卡片 34px**；`.page` 是 `overflow:auto` 滚动容器，绝对定位子元素同样计入横向可滚动区域。
- 处置：裁剪收进公共类 `.ws-hero { overflow: hidden; }`。动手前逐个确认全仓 5 处 `ws-hero` 都有裁剪祖先（`overflow:auto/hidden`）⇒ **全站零视觉变化**；页面级覆盖已全部撤掉。
- 约定已写入 `CONVENTIONS.md` 第一节。

### 1.7【进行中 · 未提交】M5 PC 监管闭环 — Change `2026-09-29-close-m5-pc-regulation`

| Task | 状态 |
|---|---|
| Task 1 机构开通与 M5 入口对齐 | 代码完成，待 migration IT 与双角色真实验收 |
| Task 2 园区监管工作台与学习统计 | 代码完成，待监管真实登录与 CSV 验收 |
| Task 3 培训计划学时预警查看闭环 | 代码完成，待预警生成/查看/修正真实验收 |
| Task 4 企业管理员工作台 | 待实施 |
| Task 5 双轴 Review、QA 与 M5 状态收口 | 待实施 |

- 覆盖功能项 1、2、3、4、7、8。
- 产物体量：`migration V20260929120000__add_m5_pc_menus.sql`（新菜单 ID 9800/9810 段，软删旧 module 900/950）、`views/admin/regulation/` 4 个页面（workbench / learning / plans / warnings）、`api/regulation.ts`、删除旧 `regulator` 聚合入口与 `mock/organization.ts`；后端 `RegulationWorkbenchService`、`Org*`、`OpsWorkbenchServiceImpl` 与 3 份 OpenAPI。
- 未提交规模（17:35 快照）：**25 个 tracked 改动 + 13 个 untracked**。
- 进度表已把 M5 从「已完成」改为「**前端进行中**」，并注明「未完成前后端真实闭环，不得标记 M5 整体完成」。

## 二、今日提交清单（10 个，均已推送）

| 提交 | 文件 | 功能单元 |
|---|---:|---|
| `687d473c` | 31 | 学员端课程评价：提交入口 / 本人回看 / 同次开课列表 |
| `ab52754c` | 17 | 一期档案收录专家查课事实 + 培训结束后回看 |
| `7dd44dde` | 4 | 课程考核结果分页改用显式查询 DTO |
| `7223550b` | 5 | 成绩已发布但学员不可见 → 可回看答题情况 |
| `68eeb1c7` | 4 | 培训列表按计划阶段解释未形成的结果 |
| `7018e904` | 6 | PC 布局：横滚动条 / 侧栏滚动 / 表格列宽 / 标签可读性 |
| `a00a6fcb` | 1 | 进度表三条 09-29 增量记录 |
| `3d61dc9d` | 1 | 新增 L3/L4 业务需求清单 |
| `0e21c0b8` | 10 | 归档两个 09-29 Change + 订正专家查课验收勾选 |
| `8d6774cd` | 2 | 清单与进度表同步至归档后状态 |

## 三、验证覆盖程度（诚实说明）

**已实跑（真实输出）**

| 项 | 结果 |
|---|---|
| 后端定向单测（5 个类：course 学习/阅读、考核分页、archive） | **57/57 通过** |
| 后端编译真实性 | `target/classes` 14:32:46 晚于源码 14:32:33 ⇒ 确认按当前来源重编（`Nothing to compile` 假象已证伪） |
| PC `vue-tsc --noEmit` | 退出码 0 |
| PC 改动文件 ESLint | 与 HEAD 基线**同为 4 条** `vue/html-self-closing`（仅行号位移）⇒ 零新增 |
| 移动端 `vue-tsc --noEmit` / ESLint | 退出码 0 / 0 告警 |
| 双端 `node --test` | PC 25 例 24 过（1 例既有失败）、移动 25 例 22 过（3 例既有失败）；**HEAD 基线逐条对照 ⇒ 零新增失败** |
| OpenAPI ×2 `JSON.parse` / `openspec validate --strict` ×2 | 全部通过 |
| M5（另一会话）：OpenSpec strict、3 份 OpenAPI 可解析、`git diff --check`、PC 定向 Node 测试 2/2、typecheck、定向 ESLint、生产构建 | 通过 |
| M5（另一会话）：Admin 定向测试 | 22/22，快捷路径断言补充后复跑 4/4 |

**未验证（不得就此判通过）**

1. **两个已归档 Change 的真实登录态验收都没有证据**：peer 课程评价缺「两个同学员 + 另一开课学员」交叉验收；专家查课缺管理员核对已结束计划回看与更新后 PDF。QA 里明确记为缺口。
2. **移动端评价弹窗与 PC 学习页评价页签只有静态 + Mock 用例证据**，无真机/真实登录态证据。
3. **真实浏览器几何实测未做**：本机无头 Chrome 静默 no-op（`--dump-dom` 连 `data:` URL 都返回 0 字节），PC 布局结论依据溢出机制分析 + 构建。
4. **后端 IT（Testcontainers）未跑**：本机无 Docker。奖惩乐观锁 IT 同样待有 Docker 的环境复跑。
5. **M5 全部真实验收未做**：当前机器无 8080/8081/5173/5174 监听、无监管与新机构管理员登录态、Docker Desktop API 返回 500 ⇒ 开户登录、部门/用户/角色维护、跨租户写拒绝、CSV 一致性、预警幂等生成与修正后 ENDED 均未判通过；**菜单 migration 未在数据库实际执行验证**。

## 四、归档与规格状态

- 归档 2 个 Change（`openspec archive <change> --skip-specs -y`，经人工决策走**纯移动、delta 不并入基线**）：`2026-09-29-show-peer-course-evaluations`、`2026-09-29-review-expert-inspections-in-archive`。归档目录 **168 → 170**。
- 复验：`diff -rq` 备份 vs `openspec/specs` **完全一致**（零附带损伤）；`openspec list` → 仅剩 M5 1 个活动 Change；`openspec doctor` → `root: ok`；排除 `archive/` 扫 `docs/`、`engineering/` 的路径引用 **0 命中**；本次未产生残留 `.openspec-archive.lock`。
- **订正了一处矛盾状态**：专家查课 Change 的 `tasks.md` 里两条「管理员真实登录」被勾选，但 QA 明确写「未取得可用登录态」——已改回 `[ ]` 并注明未验证（归档时 CLI 如实报 `Task status: 2/4 tasks`）。
- **观察项（建议确认）**：`openspec/specs/m5-minimum-closure/spec.md` **基线文件被直接改动**（功能项 4「授权」口径确认写入 + 机构类型 Requirement 改写），而对应 Change **仍处活动状态**。这与本仓「delta 在归档时才合并进基线」的做法不一致，可能是本轮有意提前固化口径，也可能是漏走 Change 流程。

## 五、L3/L4 业务需求清单（今日新增产物）

产物：`docs/requirement/l3-l4-business-requirement-checklist.md`。L4 20 条（契约/权限/DB/架构/跨端协议，含 3 条未开始）、L3 六个业务域，外加**功能项 1～87 与 Change 的覆盖矩阵**（脚本扫 `openspec/changes/**` 中「功能项 N」显式引用）。

| 指标 | 数值 |
|---|---|
| 功能项总数 | 87（中山 1～50、裕龙 51～87） |
| 有 Change 显式引用 | **41**（中山 31/50 = 62%、**裕龙 10/37 = 27%**） |
| 无任何 Change 引用 | **46**（中山 19、裕龙 27） |
| 归档 Change 目录 / 活动 Change | 170 / 1（M5，进行中） |

裕龙 27 项无规格承诺，集中在学员档案、收藏关注错题、资源库、岗位培训矩阵、题库试卷管理、对外接口。

**口径局限（已写进清单）**：只识别「功能项 N」显式引用，「无引用」**≠** 无实现，只代表**无规格承诺**。

## 六、缺口与需人工决策

1. **两个已归档 Change 的真实登录态验收仍欠着**（peer 评价需两个同学员 + 另一开课学员；专家查课需管理员核对已结束计划回看与更新后 PDF）。补验写入各自 QA，**不回改已归档目录**。
2. **M5 Task 1～3 的真实登录验收与 migration IT 待补**，Task 4/5 待实施。默认口径：M5 在前端闭环 + 双角色真实登录通过前不标完成。
3. **裕龙 27 项无 Change 引用**属「不在本期范围」还是「漏开 Change」，需产品与开发负责人定口径。
4. **新端点仍用 `PageRequest<Void>`**：同课评价列表接口与 2026-08-26 约定不一致（同轮已把管理端那处改成 `CourseAssessmentResultQuery`）；当前解析器对 `Void.class` 显式返回 null，**功能可用**，是否统一到显式 DTO 待定。
5. **`composite_score` 无法写回 NULL 的既有缺陷未修**：`TrainingParticipant.compositeScore / resultStatus` 缺 `@TableField`，MP 默认 `NOT_NULL` 策略导致 `SET composite_score = NULL` 不进 SQL。建议补 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`。
6. **`archive/` 内 delta 是否并入 `openspec/specs/` 基线**（属规格基线变更，须人工确认）。
7. 功能项 45、46、87 是否本期做。

## 七、下一工作日建议

1. 先接 **M5 Task 4 企业管理员工作台**，再做 Task 5 双轴 Review 与状态收口；同时把 Task 1～3 的真实登录验收排上（需可用环境：后端 20080 + `vpn` profile + 团队远程库 + Redis，或本机 Docker 就绪以跑 migration IT）。
2. 补两个已归档 Change 的真实登录态验收，写进各自 QA。
3. 就第 2、3、4 项口径找产品/开发负责人拍板，口径定了才好排后续批次。
