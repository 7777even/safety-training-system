# QA：学员端考核执行快照异常同步

## 范围

- 依赖后端提交：`fdcfdf08ccd4bc95f592aba7173529b7d7a7daf9`。
- 覆盖：PC Web、移动端的课程考核和培训考核入口。
- 不覆盖：后端 migration、真实环境重建数据库和真实登录态验收。

## 自动化与静态验证

- 通过：PC `learner-course-learning.test.mjs` 与 `training-learner-api.test.mjs`，20 项通过。
- 通过：移动端以 `7063` 过滤的课程、培训入口测试，以及课程 Mock `7063` 运行时场景。
- 通过：PC Web、移动端 `vue-tsc --noEmit`。
- 通过：受影响 API、移动端页面和共享工具 ESLint；PC `learning.vue` 仅有既存的两条 `vue/html-self-closing` 警告（491、526 行），无 error，未在本 Task 中改动。
- 通过：三份 OpenAPI JSON 解析、`openspec validate 2026-09-15-sync-assessment-snapshot-frontend --strict`、`git diff --check`。

## 已知非回归项

移动端完整 `learner-course-learning.test.mjs` 有两条既有素材预览断言失败：测试仍期待 `audio-preview|audio-progress`，当前 `AssetPreview.vue` 已使用 HLS 统一音视频预览。该文件不在本 Task 修改范围；本次新增的 `7063` 与 Mock 状态机测试通过。

## 真实环境待验收

部署含 `fdcfdf08` 的后端及本次前端包后，以真实学员账号分别验证 PC、移动端：

1. 课程测验或题库练习返回 `7063` 时停留当前页并显示统一提示。
2. 培训 EXAM、QUESTION_BANK 返回 `7063` 时停留培训详情并显示统一提示。
3. 非 `7063`（尤其 `8167`）仍显示后端原始中文 message，成功开考与已有答卷跳转不变。
