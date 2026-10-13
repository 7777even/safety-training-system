# 全局 PC Web UI 统一 QA

## 范围

- 后台 `AdminLayout`：侧栏折叠、tabs-card、面包屑、工作区高度。
- 门户 `PortalLayout`：顶部工具栏、内容宽度、背景、间距和控件密度。
- 后台分页器、分页表格高度、树表高度和列表操作栏规范。
- 长期标准文档 `docs/design/pc-web-ui-standard.md`。

## 自动化验证

| 命令 | 结果 |
| --- | --- |
| `node --test tests/pc-web-ui-standard.test.mjs` | 4/4 通过 |
| `pnpm exec vue-tsc --noEmit --ignoreDeprecations 5.0` | 通过 |
| `pnpm build` | 通过 |
| `git diff --check` | 通过 |

## 视觉验证

已启动本地 PC Web 预览并确认登录页可加载。后台页面需要有效的后端登录会话；当前本地 API 返回 401，未能进入授权后台页面完成交互截图验证，故本项标记为“环境受限”，不以登录页验证替代后台视觉验收。

## Review 结论

- 规格符合性：通过。实现范围对应已确认 Change，未修改 API、权限或数据库。
- 代码质量：通过。复用现有 Element Plus、动态菜单和全局 token；未引入生产依赖。
- 环境限制：后台真实视觉交互仍需在可登录环境补验。

## Follow-up 验收

- tabs-card 已增加左右滑动按钮，隐藏原生横向滚动条，点击按钮按方向平滑滑动；按钮根据滚动位置自动禁用。
- `node --test tests/pc-web-ui-standard.test.mjs`：8/8 通过。
- `pnpm build`：通过。

- 概览页已切换为轻量壳层，不显示侧栏、面包屑和 tabs-card。
- tabs-card 已实现单 tab 不可关闭、多 tab 任意关闭；关闭按钮默认隐藏，悬浮时带边框和动画显示。
- 课程中心、素材库、课程分类、题库、试卷、考试结果/批阅及培训管理列表页已接入 `admin-business-page` 公共结构样式。
- `node --test tests/pc-web-ui-standard.test.mjs`：7/7 通过。
- `pnpm exec vue-tsc --noEmit --ignoreDeprecations 5.0`：通过。
- `pnpm exec eslint`（受影响前端文件）：无错误；仓库原有 Vue 模板格式产生 38 条 warning，因 `--max-warnings=0` 返回非零，未在本次 UI 统一中顺带格式化无关模板。
- `pnpm build`：通过。
- `git diff --check`：通过；仅有仓库现有的 LF/CRLF 提示。
