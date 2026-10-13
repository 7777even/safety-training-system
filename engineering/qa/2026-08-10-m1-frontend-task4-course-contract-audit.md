# QA：M1 前端 Task 4 课程契约复核与端到端验收准备

日期：2026-08-10
Change：`2026-07-24-implement-m1-frontend-foundation-course` Task 4

## 结论

Task 4 暂不能关闭。课程页面的静态契约复核、定向回归、类型检查与生产构建通过；但当前没有可访问的后端联调环境和角色测试账号，不能把
Mock 或构建结果作为端到端验收证据。

## 本次 OpenAPI 复核与修正

- 以 `docs/api/course.openapi.json` 的 `ItemCreateRequest`、`ItemUpdateRequest` 为准，内容项补齐 `QUESTION_BANK`、`EXAM`、
  `PRACTICE` 三种考试类类型；分别只查询 `BANK_PRACTICE`、`ONLINE`、`SPECIAL_PRACTICE` 的已发布启用考试资源。
- 保持 `assetId` 与 `examId` 的互斥关系；新增时校验素材或对应考试资源，编辑时仅提交 Contract 允许变更的字段。
- 直播 `teacherUserId` 改由统一人员选择器写入；`audienceId` 改由机构选择加参与范围选择器写入；材料规则 `auxAudioAssetId`
  改由仅音频的素材选择器写入，不允许手输全局 ID。
- 章节新建和编辑改为弹窗；下载资料保存和重新打开后均展示已选资源名称，避免不知道当前资料集合的状态。
- 课程、章节、内容项、材料规则、直播、下载资料、发布版本、开课、报名、学习详情、学习会话、退选和评价的现有 API 层路径、请求外壳与
  `course.openapi.json` 逐项复核；本轮发现并修正的缺口仅为上述内容项扩展和关联选择交互。

## 自动化验证

- `node --test tests/course-operations-ui.test.mjs`：9/9 通过，覆盖新增内容项类型、考试类型过滤、关联选择器、章节弹窗和下载资料回显。
- `pnpm --filter @safety-training/pc-web exec vue-tsc --noEmit --ignoreDeprecations 5.0`：通过。常规
  `pnpm --filter @safety-training/pc-web typecheck` 被仓库 `tsconfig.json` 的 `ignoreDeprecations: "6.0"` 与当前
  TypeScript 版本不兼容阻断（TS5103）；未修改全局配置。
- `pnpm --filter @safety-training/pc-web build`：通过。
- `git diff --check`：通过。

## 真实联调阻塞

- 前端 `.env`、`.env.development`、`.env.production` 均为 `VITE_USE_API_MOCK=false`，生产路径已指向真实 API。
- 本机仅检测到 Vite 服务；只读访问 `http://localhost/api/course/categories/tree` 返回 HTTP 404，未发现后端联调服务。
- 缺少可登录的管理员、讲师、学员账号及其角色/组织数据，无法核验 `Result` 外壳、鉴权、`x-permission`、乐观锁、分页、状态流转和本人数据边界。

## 关闭前所需最小证据

1. 可访问的后端 API 地址，以及管理员、讲师、学员三类测试账号。
2. 管理员完成参与范围配置、课程/章节/内容项/直播/下载资料、发布、开课与人员报名；讲师和学员分别登录。
3. 学员 PC 和移动端完成任务读取、会话上报、退选（允许时）和评价；确认服务端学习事实与管理员学习详情一致。
4. 逐接口记录真实请求、响应、错误码和版本冲突结果；成功后删除已联调接口的 Mock 分支，再更新 Task 4 为完成。
