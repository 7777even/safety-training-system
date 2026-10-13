# M4 PC 培训成果闭环 QA

日期：2026-09-21  
Change：`2026-09-21-implement-m4-pc-outcome-closure`

## 已核验范围

- 问卷：草稿设计、题目整体替换、复制、PC/手机预览、投放、填写、提交和统计均调用 `survey.openapi.json` 定义端点；填写页使用投放快照，并在前端校验必填题，将单选归一为 `optionIds` 数组。
- 成果：已结束培训从计划工作台进入只读正式结果；空综合分显示“结果不完整”，没有恢复已删除的计算批次调用。
- 专家查课：沿用计划工作台既有专家提交和管理员只读回看入口，评价人与可见性仍由服务端控制。
- 档案：已结束计划进入一期一档；归档只以结果完整性为前置，不再要求出勤。生成文件的真实 `fileInfoId` 持久化在当前归档版本，查询与下载端点回读该 ID；PC 在 `missingItems` 非空或没有 `fileInfoId` 时禁用下载，否则调用正式档案文件端点。个人/企业查询端点已经在 API 层保留，但当前菜单没有超出契约的全量列表入口。
- 证书：管理员页面已覆盖模板创建、统一上传组件返回的 `fileInfoId` 到 `backgroundFileId` 映射、草稿保存、发布、服务端 PNG 预览、来源候选选择的自动签发规则创建，以及已签发证书下载；门户仅使用 `my-certificates` 与本人文件端点。

## 自动化证据

- `pnpm exec eslint src/api/m4-outcome.ts src/views/admin/result src/views/pages/SurveyFillPage.vue src/views/portal/certificates/index.vue --fix`：通过。
- `pnpm run typecheck`：通过。
- `node --test tests/m4-outcome-closure.test.mjs tests/evaluation-result-api.test.mjs`：7/7 通过。
- `pnpm run build`：通过；仅有既有大 chunk 提示和依赖 `util`/`buffer` 浏览器外置提示，无构建失败。
- `git diff --check`：通过。
- `mvn -pl sts-service-admin "-Dtest=ArchiveServiceImplTest" test`：3/3 通过（移除伪出勤前置、结果不完整仍阻塞、当前归档版本文件 ID 回读）。
- `mvn -pl sts-service-admin "-Dtest=ArchiveServiceImplTest,ArchiveServiceIT" test`：单测 3/3 通过；`ArchiveServiceIT` 未启动数据库断言，本机 Docker/Testcontainers 不可用。

## 未替代为已完成的真实验收

- 管理员：三题问卷保存、复制、预览、投放、统计授权；模板上传、发布、PNG 预览、规则创建和管理员下载。
- 专家：执行中计划提交查课记录，管理员读取详情；非 ENDED 成果接口 409。
- 学员：同一投放仅能提交一次、本人证书查询和下载。
- 档案：管理员真实登录态仍须验证结果完整计划的生成与下载，以及结果不完整时的 409/9204；本机 Docker/Testcontainers 不可用，Flyway 数据库迁移和真实对象存储下载尚未在本轮环境完成。
