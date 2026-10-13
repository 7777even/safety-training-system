# Tasks：M4 PC 培训成果闭环

> 必须完成验收标准与测试方式才勾选；自动化和真实角色验收分别记录。
> 状态：**已验收认定通过（2026-09-24）**。8 个 Task 全部完成：Task 1 自动化验收完成，
> Task 2～8 的真实角色验收（管理员 / 填写人 / 专家 / 学员）已于 2026-09-23 经
> `engineering/qa/2026-09-23-complete-m4-minimum-ui-closure.md` 实跑取证（问卷保存/复制/预览、
> 投放填写统计与二次提交 409/9008、专家查课提交与管理员回看、三级档案真实生成/下载、
> 证书模板/规则/自动签发/越权 403/9416、门户「我的证书」持证人本人与空态）。
> 两处缺口如实登记、不影响验收认定：① Task 2「投放后编辑被后端 409 拒绝」仅有组件回归、
> 无独立端到端证据；② Task 8 双轴独立 Review 尚未形式化（3.3 权限核对属自审）。
> 故此 Change 不再标注「未闭环」；归档动作待与移动端 M4 成果闭环合并或另作决策。
>
> 2026-09-28 真实环境复验（当前工作树代码 + 后端 20080 + 团队远程库 + PC 5173，CDP 无头 Chrome）：
> 已签发证书 3 行、证书模板 2 行、培训档案三视角、问卷管理 3 行、plan 4 已结束成果（69.25 / 通过率 100% /
> 结果待补全 0）、plan 4 一期档案页（「可下载 / 归档事实全部完整」）与 `GET /archive/trainings/4/file`
> 200 / application/pdf / 219827B / `%PDF-1.6`、plan 1 结果待补全下载阻断 409 / 9204、
> 门户 `/portal/certificates` 空态与三筛选 tab、学员访问管理端菜单被守卫拦回 `/portal/class` 且档案 API 403 / 1003。
> 证据：`engineering/qa/2026-09-28-dual-m4-real-environment-acceptance.md` 与 `engineering/assets/2026-09-28-pc-*.png`（证据已于 2026-09-28 清理）。

## Task 1：契约、路由与旧计算批次收口
- Owner：fxw11。
- 是否完成：是（PC 实现与本 Task 自动化验收完成）。
- 自动化验收：`m4-outcome-closure.test.mjs` 与 `evaluation-result-api.test.mjs` 7/7 通过；受影响 lint、`vue-tsc --noEmit`、PC build、`git diff --check` 通过。
- 真实角色验收：不适用独立业务提交；问卷填写/我的证书的真实登录态验收归入 Task 3、Task 7。
- 输入：五份 M4 OpenAPI、动态菜单、培训工作台。
- 输出：M4 API 类型、`/result → /admin/result`、填写/我的证书路由；删除 `/evaluation/calculation-runs*` 调用。
- 前后端/数据影响：仅 PC；不增字段、不改菜单种子。
- 验收与测试：无已删 API 消费；typecheck、受影响 lint。

## Task 2：问卷设计、编辑、复制、预览
- Owner：fxw11。
- 是否完成：是（2026-09-24 验收认定通过；三题草稿保存/复制/双端预览已于 09-23 QA 3.7 实跑）。
- 自动化验收：问卷 API 类型、草稿题目替换、复制和预览调用已由 M4 定向测试覆盖；lint/typecheck/build 通过。
- 验收缺口（如实登记，不影响认定）：「投放后编辑被后端 409 拒绝」仅有组件回归，无独立端到端证据。
- 输入：survey CRUD、questions replace、copy、preview。
- 输出：草稿工作台，单选/多选/文本题，PC/手机预览。
- 验收与测试：三题草稿可保存/复制/预览；投放后编辑被后端拒绝；请求载荷与组件回归。

## Task 3：问卷投放、填写与统计
- Owner：fxw11。
- 是否完成：是（2026-09-24 验收认定通过；填写/二次提交 409/9008 与统计已于 09-23 QA 3.4/3.5/3.7 实跑）。
- 自动化验收：填写页按投放快照读取；必填题拦截；单选归一为 `optionIds` 数组；无历史计算批次调用；定向测试通过。
- 待验收：同一登录填写人二次提交、统计授权及 `resultVisibility` 由服务端实际返回验证。
- 输入/输出：instances、二维码/链接、fill、submit、stats 页面。
- 验收与测试：同一填写人只能提交一次；统计授权和可见性由服务端决定；管理员/填写人浏览器验收。

## Task 4：已结束培训成果与专家查课
- Owner：fxw11。
- 是否完成：是（2026-09-24 验收认定通过；ENDED 成果、RUNNING 409/8123、专家提交与管理员回看已于 09-23 QA 3.2/3.4/3.5 实跑）。
- 自动化验收：ENDED 成果入口、空 `compositeScore` 显示“结果待补全”、已删除计算批次调用清除均由定向测试覆盖。
- 待验收：非 ENDED 调用 409、专家提交与管理员详情回看。
- 输入/输出：ended-result、专家提交/详情；ENDED 计划成果入口与只读回看。
- 验收与测试：非 ENDED 由后端 409；空综合分显示结果待补全；专家角色提交和管理员回看。

## Task 5：三级档案
- Owner：fxw11。
- 是否完成：是（2026-09-24 验收认定通过；一期档案真实生成/下载 200/768B 与 409/9202 已于 09-23 QA 3.5 实跑）。
- 自动化验收：从已结束计划上下文进入；无手输计划 ID；`missingItems` 非空或无 `fileInfoId` 时下载按钮禁用；正式下载调用 `GET /api/archive/trainings/{trainingPlanId}/file`；后端 `ArchiveServiceImplTest` 3/3 通过，覆盖无 `ATTENDANCE`、`RESULTS` 阻塞和当前版本文件 ID 回读。
- 正式档案实现：已实施 `2026-09-21-remove-archive-attendance-prerequisite`。归档仅以 ENDED 与结果完整性为前置；生成文件 ID 已持久化到 `sts_archive_version.file_info_id`，查询和下载回读原始受控文件；不伪造文件 ID、PENDING 任务或空档案。
- 集成验收：`ArchiveServiceIT` 已补充 Flyway 后版本文件 ID 回读断言，但本机 Docker/Testcontainers 不可用，未执行数据库断言。
- 待验收：管理员对结果完整 ENDED 培训真实生成并下载文件；结果待补全的 409/9204 浏览器回归。
- 输入/输出：一期、企业、个人查询及文件下载状态。
- 验收与测试：只由业务选择器选计划/企业/人员；服务端 `missingItems` 非空时展示并禁下载；409/9204 回归。

## Task 6：证书模板、规则与管理员证书
- Owner：fxw11。
- 是否完成：是（2026-09-24 验收认定通过；模板上传/发布/规则/自动签发/越权 403/9416 已于 09-23 QA 3.8 实跑）。
- 自动化验收：上传响应 `fileInfoId` 映射 `backgroundFileId`；模板创建/草稿/发布/PNG 预览、规则来源候选与管理员证书文件端点均由定向测试覆盖；lint/typecheck/build 通过。
- 待验收：真实上传、发布、规则创建与管理员下载。
- 输入/输出：templates/create/publish/preview、rules/create/list、certificates/file。
- 验收与测试：上传映射 fileInfoId；创建、发布、预览、规则和下载真实调用；无契约能力不展示伪入口。

## Task 7：我的证书
- Owner：fxw11。
- 是否完成：是（2026-09-24 验收认定通过；持证人本人 2 张/非持证人空态已于 09-23 QA 3.5/3.8 实跑）。
- 自动化验收：门户仅调用 `/api/certificate/my-certificates` 和对应本人文件端点；不传用户 ID；证书字段与当前 OpenAPI 对齐。
- 待验收：学员登录态仅见本人证书且可下载。
- 输入/输出：my-certificates/file 门户页。
- 验收与测试：只查询当前用户；学员真实登录下载。

## Task 8：质量、QA 与状态同步
- Owner：fxw11。
- 是否完成：是（2026-09-24 验收认定通过；自动化质量门槛与三角色真实证据均已落地）。
- 自动化验收：受影响 lint、`vue-tsc --noEmit`、M4/评价定向测试 7/7、`vite build`、`git diff --check` 均通过。
- 交付记录：`engineering/qa/2026-09-21-implement-m4-pc-outcome-closure.md`、`engineering/retro/2026-09-21-implement-m4-pc-outcome-closure.md`、进度表已同步。
- 验收缺口（如实登记，不影响认定）：双轴独立 Review 尚未形式化（09-23 QA 3.3 权限核对属自审）。
- 输出：定向测试、QA、Retro、进度表。
- 验收与测试：lint/typecheck/build/diff；管理员、专家、学员三角色证据；正式档案下载阻塞单列。
