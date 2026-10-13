# 培训编排前端联动 QA

- 日期：2026-09-03
- OpenSpec：`2026-09-03-link-training-composition-frontend`
- 范围：PC 管理端培训标准、模板、计划和评价表单版本的编排联动；不含后端、数据库、移动端。

## 验收结果

- TDD：先新增 `training-composition-linkage.test.mjs`，首次运行 3/3 失败；审查补充“新建计划保存阶段、清空模板、类型基线、来源展示”断言后再次失败，实现收口后通过。
- 定向回归：`node --test frontend/pc-web/tests/training-composition-linkage.test.mjs frontend/pc-web/tests/training-workspace-ui.test.mjs` 中本 Change 的 3 项通过；既有 `training-workspace-ui.test.mjs` 的“规则面板 card-header 样式”断言失败，已确认 `HEAD` 即缺少该样式，非本 Change 引入，未越界修改。
- 类型检查：`pnpm --filter pc-web typecheck` 通过。
- ESLint：本 Change 的 API、工具、标准/模板/计划工作台和步骤组件定向检查通过。
- 构建：默认 `pnpm --filter pc-web build` 因既有 `dist/assets` 被占用报 `EBUSY`；使用独立输出目录执行 `pnpm --filter pc-web exec vite build --outDir .vite-verify` 通过。
- 差异检查：`git diff --check` 通过。
- 双轴 Review：规格符合性与代码质量复审均通过；审查发现的模板阶段保存、清空模板、类型基线、模板来源和比例单位问题均已回归验证。

## 已知限制

- 未提供真实登录态接口环境，未执行人工联调；页面仍以既有 OpenAPI 详情、分页、阶段整体替换、预检和评价版本接口为唯一输入。
- 前端只能限制选择并显示差异，无法强制后端跨类型/标准/模板一致性、模板复制原子性、标准指定资源强校验和评价来源强校验；这些后端缺口未在本 Change 修改。

## Task 4：计划内容执行配置

- TDD：先新增“四类内容可维护发布所需执行配置”源码行为测试，首次因 API 类型缺少 `assessmentMode` 和 `executionConfig` 失败；实现后该用例通过。
- 契约覆盖：课程完成规则、考试模式、题库练习时长/及格线/次数/抽题规则、线下教师/地点/授课时间/签到窗口/签退/及格线均可维护；日期时间使用 `YYYY-MM-DDTHH:mm:ss`。
- 交互检查：弹窗编辑使用深拷贝草稿，取消不写回；内容类型变化同时清空旧资源、考试模式和执行配置；内容行显示“已配置/待配置”。
- 定向测试：`node --test --test-name-pattern="四类内容" tests/training-composition-linkage.test.mjs` 通过（1/1）。
- 类型与规范：PC Web `typecheck` 通过；API、计划工作台和 `PlanStagesPanel.vue` 定向 ESLint 通过；`git diff --check` 无错误。
- 构建：`pnpm --filter @safety-training/pc-web build` 通过；仅保留既有浏览器兼容性和大分块警告。
- 回归：两个培训工作台测试文件共 20 项，18 项通过；2 项既有失败均位于培训标准规则面板（缺“规则依据”文案及 `.card-header` 样式断言），与本 Task 未触碰的文件一致，未越界修复。
- 双轴自审：规格符合性通过；代码质量通过。受侧线程限制未调用 Review 子 Agent，改由当前线程按 OpenSpec、OpenAPI 和分层前端规范逐项核对。
- 未执行：无真实登录态接口环境，保存回读和发布预检请由需求方按手工测试数据完成联调验收。

## Task 5：统一模板执行配置并支持计划完整复用

- 模板阶段内容新增与计划一致的执行配置入口；课程完成规则、考试模式、题库练习参数/抽题规则、线下教师地点时间范围等字段使用同一契约模型。
- 模板详情加载和计划模板带入均深拷贝 `assessmentMode`、`executionConfig`，计划草稿修改不会改变已发布模板数据。
- 定向 parity 测试：`node --test --test-name-pattern="模板与计划阶段内容共享" tests/training-composition-linkage.test.mjs` 通过（1/1）。
- 类型检查：`pnpm --filter @safety-training/pc-web typecheck` 通过。
- ESLint：计划/模板工作台、共享配置弹窗及相关 API 定向检查通过。
- 构建：`pnpm --filter @safety-training/pc-web build` 通过；仅保留既有兼容性和大分块警告。
- 差异检查：`git diff --check` 通过。

## Task 6：计划参训人员批量绑定与发布前提示

- TDD：先新增“培训计划发布前必须支持批量绑定参训人员并提示前置要求”源码行为测试，首次因缺少 `bindTrainingPlanParticipantSelection`、`PersonSelectionDialog` 和发布前提示失败；实现后该用例通过。
- 契约覆盖：新增前端封装调用既有 `POST /api/training/plans/{trainingPlanId}/participants/bind-selection`，请求体为 `{ selection: { selectionType, subjectIds } }`，未修改后端和 OpenAPI。
- 交互检查：人员与执行步骤中参训人员面板先于发布执行面板；草稿计划可打开统一人员选择弹窗按全部人员、部门或指定人员绑定；发布前参训人数为 0 时先提示“发布前请先配置至少一名参训人员”并停留当前步骤。
- 定向测试：`node --test --test-name-pattern="批量绑定参训人员" frontend/pc-web/tests/training-composition-linkage.test.mjs` 通过（1/1）；`node --test frontend/pc-web/tests/training-composition-linkage.test.mjs` 通过（7/7）。
- 类型与规范：`pnpm --filter pc-web typecheck` 通过；计划 API、工作台、执行组件和联动测试定向 ESLint 通过；`git diff --check` 无错误，仅有 Windows LF/CRLF 提示。
- 构建：`pnpm --filter pc-web exec vite build --outDir .vite-verify` 通过；仅保留既有浏览器兼容性和大分块警告。
- 未执行：无真实登录态接口环境，`bind-selection` 的 ALL/DEPT/USER 实际展开和发布预检人数字段请在手工验收时联调确认。
