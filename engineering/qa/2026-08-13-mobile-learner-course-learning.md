# 移动端学员课程学习 QA 记录

## 范围

对应 OpenSpec Change：`2026-08-12-implement-mobile-learner-course-learning`。

本次覆盖 Task 3 的移动端学习会话、断点恢复和完成上报，以及 Task 4 的自动化验收证据整理。

## 自动化验证

执行目录：`frontend/mobile-app`

| 验证项 | 结果 |
| --- | --- |
| `node --test --test-timeout=5000 tests/learner-course-learning.test.mjs` | 通过，7/7 |
| `pnpm exec vue-tsc --noEmit` | 通过 |
| 目标文件 ESLint（`asset.ts`、`AssetPreview.vue`、`detail.vue`） | 通过 |
| `pnpm run build:h5` | 通过 |
| `git diff --check` | 通过 |

## 验收结论

- 移动端详情页进入内容项后创建学习会话，切换、页面离开和媒体结束会按现有 Contract 上报。
- 视频使用后端断点作为初始播放位置；音频使用 `InnerAudioContext` 恢复播放位置并上报进度。
- 每次上报使用递增的 `clientTraceId` 序号；上报失败不会阻塞继续学习。
- 浏览器视觉、真实接口联调、真实账号权限和断点回读尚未执行，原因是当前工作环境未提供可用的移动端浏览器/后端联调入口，保留为后续验收项。

## Review

- 规格符合性：通过。改动限定在当前 OpenSpec 的移动端 Task 3～4 范围，未新增 API、权限或数据库结构。
- 代码质量：通过。修复了 H5 `audio.css` 解析问题，并将视频事件读取集中到带类型边界的方法中；未发现本次变更引入的无用依赖或无关重构。
