# QA：课程模块 PC 管理端前端

## 范围

OpenSpec Change：`2026-07-22-implement-course-admin-frontend`。

本次覆盖 Task 1～3：素材库、课程分类与机构设置、课程中心、课程编排、发布版本、开课、报名和管理员学习详情。学员端、课程授权、考试类内容项以及素材预览/下载不在本次范围。

## 需求与契约检查

- `FR-ENT-CRS-001～002`：素材分页筛选、元数据维护、删除保护、失败转码重试和转码任务查询已覆盖。
- `FR-ENT-CRS-003～004`：分类维护、机构默认设置、课程草稿列表和基本信息维护已覆盖。
- `FR-ENT-CRS-005～010`：按现有 Contract 覆盖根级章节、`ASSET`/`LIVE` 内容项、材料规则、下载素材、发布版本、开课、报名和管理员学习详情。
- API 请求字段、方法、路径、乐观锁 `version` 和权限点均按 `docs/api/asset.openapi.json`、`docs/api/course.openapi.json` 实现。
- 规格符合性独立 Review：通过。
- 代码质量独立 Review：通过；开课页面超长模板已按组件层级格式化。

## 验证结果

- `node --test frontend/pc-web/tests/task1-asset-management.test.mjs frontend/pc-web/tests/course-foundation-management.test.mjs frontend/pc-web/tests/course-operations-ui.test.mjs frontend/pc-web/src/api/course-operations.test.mjs`：通过，17 项测试全部成功。
- `pnpm --filter @safety-training/pc-web lint`：通过。
- `pnpm --filter @safety-training/pc-web build`：通过。
- `git diff --check`：通过，无空白错误。
- `pnpm --filter @safety-training/pc-web typecheck`：未全量通过；仅命中本次变更前已存在的 `src/views/admin/system/dict/index.vue` 第 307、366 行参数 `page` 隐式 `any`，本次未越界修改。

## 已知限制

- 素材播放、预览和下载地址尚无正式 Contract，因此页面未提供这些入口。
- 学员课程列表、学习内容读取、课程授权和考试类内容项等待后续 Contract/OpenSpec。
- 业务菜单由管理员手动配置；本次不创建兼容历史 `component` 字符串的薄入口组件。

## 2026-07-23 前端 Mock 补充验证

- 素材、课程基础、课程编排、版本、开课、报名和管理员学习详情默认切换为契约内存 Mock；`VITE_USE_API_MOCK=false` 可恢复真实请求分支。
- Mock 覆盖素材可用性与动态引用删除保护、分类子节点删除保护、乐观锁、课程发布幂等、下线重发、开课冻结版本及合法状态流转。
- `node --test` 课程与素材定向回归：32 项全部通过。
- `pnpm --filter @safety-training/pc-web lint`：通过。
- `pnpm --filter @safety-training/pc-web build`：通过。
- Mock/API 文件 Prettier 检查与 `git diff --check`：通过。
- 规格符合性和代码质量独立 Review：通过。
- 全量 `typecheck` 仍仅被未纳入本次变更的 `src/views/admin/system/dict/index.vue` 第 307、366 行参数 `page` 隐式 `any` 阻塞。
