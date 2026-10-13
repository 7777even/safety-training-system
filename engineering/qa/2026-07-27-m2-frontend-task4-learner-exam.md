# QA：M2 前端 Task 4 学员双端作答与结果

日期：2026-07-29
Change：`2026-07-27-implement-m2-frontend-question-exam` Task 4

## 验证结果

- TDD：先新增学员 API 和双端独立页面失败测试，因页面/API 不存在而失败；实现后 Node 定向测试 3/3 通过。
- Contract：任务读取走 `/api/exam/learner/tasks`，本人作答读取走 `/api/exam/learner/attempts/{examAttemptId}`
  ；开始、暂存、手动交卷和超时交卷均映射既有 `/api/exam/attempts/*` 路径。
- Mock：两端均保留 `VITE_USE_API_MOCK !== 'false'` 分支、`TODO replace real api` 标记和相同请求载荷的真实分支。
- 验证通过：PC/mobile typecheck、PC build、mobile `build:h5`、mobile lint、`git diff --check`。
- 已知非本 Task 项：PC 全量 lint 被既有 `AssetPreviewPanel.vue` 的一条 warning 阻断；本 Task 新增文件无 lint error。

## 双轴 Review

- 规格符合性：通过。PC 提供状态区、倒计时、提交、答题卡和题目工作区；移动端提供触控友好的任务、作答和结果页。暂存/交卷异常不伪造成功，交卷后禁用编辑；成绩和解析同时受
  `scoreVisible` 及 `PUBLISHED` 约束。
- 代码质量：通过。改动限定于共享纯类型、两端 API/Mock、学员页面、移动端页面注册、测试和过程记录；未新增依赖，全部新增业务入口有中文注释及
  scoped Less。

## 2026-07-29 门户路由与功能权限补充

- 路由：考试中心只保留 `/portal/exams` 一个门户 `C` 类型入口；作答、结果页作为内部路由注册，要求来源菜单存在且 profile 包含
  `exam:learner:attempt:view`。
- 按钮：任务页按 `exam:attempt:start`、`exam:learner:attempt:view` 控制开始与查看；作答页按 `exam:attempt:answer`、
  `exam:attempt:submit` 控制暂存与提交。
- TDD：新增门户内部路由与按钮权限测试，先观察失败，再实现后与既有学员考试测试共 4/4 通过。
- 定向验证：PC typecheck、门户考试相关源码 lint、PC build、`git diff --check` 通过。

## 未完成验收

真实后端、鉴权、服务端超时与成绩发布联调尚不可执行，保留至 Task 6 以 `VITE_USE_API_MOCK=false` 验收；Mock 不作为真实闭环证据。
