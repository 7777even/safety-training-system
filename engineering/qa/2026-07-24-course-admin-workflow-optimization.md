# 课程管理端工作流优化 QA

## 范围

- 课程中心列表与四阶段详情工作台。
- 基础信息、内容编排、发布版本和当前课程开课。
- 素材选择器、素材预览、统一文件上传和素材编辑抽屉。
- 全局开课管理、报名名单和管理员学习详情。

## 自动化验证

| 检查 | 结果 |
|---|---|
| 课程与素材定向 Node 测试 | 42/42 通过 |
| `pnpm --filter @safety-training/pc-web lint` | 通过 |
| `pnpm --filter @safety-training/pc-web typecheck` | 通过 |
| `pnpm --filter @safety-training/pc-web build` | 通过 |
| 全量 PC Node 测试 | 55/58 通过，3 项为既存非课程失败 |

全量测试既存失败：

- `clean-modern-style-tokens.test.mjs` 和 `portal-layout-routing.test.mjs` 依赖已不存在的 `frontend/pc-web/src/views/portal/index.vue`。
- `clean-modern-style-tokens.test.mjs` 的移动端首页仍缺少既定 `min-height: 88rpx` 样式。
- 上述文件均不在本次课程改动范围内。

## 浏览器验收

- 使用 Mock 数据检查 1440 × 1000 与 1100 × 900 桌面宽度。
- 验证内容工作台无水平溢出，右侧检查器和材料规则可读。
- 验证发布前检查、历史快照空状态和未发布课程开课禁用原因。
- 验证素材选择器只有一层弹窗，预览在同一弹窗内部。
- 验证统一上传的文件选择、进度、成功状态、移除入口和图片预览。
- 验收中发现并修复上传队列完成态不刷新的响应式问题。
- 详细视觉证据见 `engineering/qa/2026-07-24-course-admin-workflow-design-qa.md` 与 `engineering/qa/evidence/course-workflow-2026-07-24/`。

## Contract 与边界

- 没有新增后端 API、权限、数据库结构或业务状态机。
- 课程、素材和开课继续由现有 Mock/真实 API 切换层提供数据。
- 历史文件无 `previewUrl` 时按方案显示无法在线预览，不虚构文件详情接口。
- 菜单和动态路由未写入系统配置，等待开发人员按交付表手动配置。

## 双轴 Review 修复

- 规格符合性：阶段切换使用 `push` 保留浏览器历史，首次创建保存使用 `replace`；内容、发布、开课阶段按既有权限点拦截；历史失效素材不再回填；版本快照兼容 Mock 的平铺内容项。
- 代码质量：上传队列移除嵌套按钮；课程目录刷新时清除已删除章节缓存；补充上传失败、重试、移除和 Object URL 释放行为测试；视觉验收记录归档至 `engineering/qa/`。
- 目录迁移后同步修正培训计划既有占位页的课程分类导入路径，未新增兼容菜单组件。
- 最终独立复核结论：规格符合性通过，代码质量通过。

## 结论

课程交互优化 Task 1～3 的定向验收通过。
