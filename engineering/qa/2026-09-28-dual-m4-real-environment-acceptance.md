# M4 双端真实环境验收（PC + 移动端）

关联 Change：`2026-09-21-implement-m4-pc-outcome-closure`、`2026-09-24-close-mobile-learner-outcome-loop`、
`2026-09-28-improve-m4-certificate-and-mobile-archive`。记录时间：2026-09-28。

> **证据清理说明（2026-09-28 补记）**：本文表格中引用的 `2026-09-28-*.png` 截图（位于 `engineering/assets/`）
> 已随该目录一并清理，只有 `certificate-bg/` 两张证书底图保留。下文截图名一律视为「当时的取证文件名」，
> 不再随仓库分发；表格中的实测文字、HTTP 状态与错误码等事实不受影响。

## 验收环境（真实登录态）

- 后端：本地 `sts-service-admin`（当前工作树代码，`spring-boot:run -Dspring-boot.run.profiles=vpn`，端口 20080），
  数据源为团队远程库 `19.191.192.146:8045/sts`、Redis `19.191.192.146:16379`（探测三端口全通）。
- 前端：PC `vite` dev（5173，`/api` → `127.0.0.1:20080`）、移动端 uni H5 dev（5174，同代理），
  均使用**临时** `vite.qa.config.ts`（验收后删除，不入库）。
- 浏览器：无头 Chrome 153，CDP 驱动 `Runtime.evaluate` 取 DOM 事实 + `Page.captureScreenshot` 取证；
  移动端视口 390×844，PC 视口 1500×950。
- 账号：`DEMO/superAdmin`（超管，role 1 管理员）与 `DEMO/m4-learner-01`（role 3 学员，密码本轮开通为非生产口令）。
- 登录口令按前端约定提交 `SHA-256(明文)` 的 64 位小写十六进制；`Bearer <token>` 携带令牌。

## 一、移动端 M4 成果闭环

`2026-09-24-close-mobile-learner-outcome-loop` Task 1～4 的真实登录态纵向验收。

| # | 验收点 | 结果 | 事实证据 |
|---|---|---|---|
| 1 | 个人中心出现「我的成果」卡与两个入口 | 通过 | `entry-row` 文本 = `我的证书 查看已签发的培训证书` / `我的培训档案 按类型汇总培训结果`；截图 `2026-09-28-mobile-super-02-profile.png` |
| 2 | 「我的证书」列表按登录人收口 | 通过 | superAdmin 仅 1 张（`certificateId=7`，库中另有 5/6 属 user 2 未返回）；学员 `m4-learner-01` 返回 `list: [], total: 0` |
| 3 | 证书卡来源中文 + 编号 + 签发时间 | 通过 | 渲染文本：`培训证书` / `测试培训计划` / `签发于 2026-09-24` / `编号 CERT-893DC2DD7AFF477F`；截图 `...-03-certificates.png` |
| 4 | 在线查看走受控读取 | 通过 | 预览层出现，图片 `naturalWidth×naturalHeight = 1400×990`（真实 PNG 渲染，非占位）；截图 `...-04-cert-preview.png` |
| 5 | 下载走受控端点并带鉴权 | 通过 | 点击后捕获 `GET /api/certificate/my-certificates/7/file`，`Authorization: Bearer …`，**200 / image/png** |
| 6 | 「我的培训档案」按培训类型分组统计 | 通过 | 1 组：`测试培训类型 / 共 4 期`，组内 4 条任务；截图 `...-05-archive.png` |
| 7 | 结果中文与状态呈现 | 通过 | 已结束 3 条显示 `合格`；进行中 1 条（plan 3 `RUNNING`）**不显示结果 chip**；截图同 6 |
| 8 | 期次详情跳转 | 通过 | 点击首条 → `/#/pages/training/detail?trainingParticipantId=7`，页面显示「培训计划已结束，仅可查看结果」+ 6 个内容项与成绩；截图 `...-06-after-enter.png` |
| 9 | 学员空态 | 通过 | 证书页 `暂未获得证书`；档案页 `暂无培训档案`；截图 `...-learner-03-certificates.png`、`...-learner-05-archive.png` |
| 10 | 越权拒绝 | 通过 | 学员令牌读 `cert{7}/file` → **HTTP 403 / code 9416**「只能查看本人证书」 |
| 11 | 无票拒绝 | 通过 | 同端点无令牌 → HTTP 401 / code 1002 |

**对 09-28 Change QA「尚待真实验收」的回应**：该 QA 记「四期均显示旧『未分类』，不能仅据截图断定四期未配置培训类型」——
本轮实测四期全部显示 `测试培训类型`（`trainingTypeName` 回填生效），且图片、下载、越权、期次跳转四项均已取得真实登录态证据。

## 二、PC 端 M4 培训成果闭环（复验）

在 09-23/09-24 已有证据基础上，对当前工作树代码做真实环境复验。

| # | 页面 | 结果 | 事实证据 |
|---|---|---|---|
| 1 | `/admin/result/certificate/certificates` 已签发证书 | 通过 | 3 行（培训计划 / 课程 / 理论考试），`CERT-893DC2DD7AFF477F`…；`GET /api/certificate/certificates` 200 |
| 2 | `/admin/result/certificate/templates` 证书模板 | 通过 | 2 行：`CERT-TPL-M4-001 已发布 v3`、`测试模版 未发布`；`GET /api/certificate/templates` 200 |
| 3 | `/admin/result/archive` 培训档案 | 通过 | 「一期一档 / 企业档案 / 个人档案」三视角，已结束计划下拉 `GET /api/training/plans?query.publishStatus=ENDED` 200 |
| 4 | `/admin/result/survey` 问卷管理 | 通过 | 3 行（含 09-23 验收问卷）；`GET /api/survey/surveys` 200 |
| 5 | `/admin/business-center/training/plan/4/ended-result` 已结束成果 | 通过 | `69.25` 平均综合分、通过率 100%、学员行 67.5/70/70/69.25/合格；`GET /api/training/plans/4/ended-result` 200 |
| 6 | `/admin/business-center/training/plan/4/archive` 一期档案 | 通过 | 「可下载 / 归档事实全部完整」、培训内容 6 项、线上 0.08 / 线下 65 学时；截图 `2026-09-28-pc-admin-06-plan-archive-ui.png` |
| 7 | 一期档案文件 | 通过 | `GET /api/archive/trainings/4/file` → **200 / application/pdf / 219827B / `%PDF-1.6`** |
| 8 | 企业档案 / 个人档案 | 通过 | 企业档返回列表；`plan 1` 与 `user 1` 均带 `missingItems: ["RESULTS"]` |
| 9 | 结果不完整阻断下载 | 通过 | `GET /api/archive/trainings/1/file` → **409 / code 9204**「缺少归档必需事实: RESULTS」 |
| 10 | 门户 `/portal/certificates` 我的证书 | 通过 | 空态 `暂未获得证书` + 三筛选 tab（全部/课程证书/考试证书/培训成果证书）；截图 `2026-09-28-pc-learner-01-portal-certificates.png` |
| 11 | 学员访问管理端菜单被守卫拦截 | 通过 | 学员请求 `/admin/result/certificate/certificates` → 落回 `/portal/class`；截图 `...-learner-02-admin-blocked.png` |
| 12 | 学员访问管理端档案 API | 通过 | → **403 / code 1003**「权限不足: 无此权限：archive:training:list」 |

> 复验中 `/admin/result/certificate/templates` 首次访问出现空白页，经单页重跑确认为 **dev 首次按需编译超出采集窗口**的假阴性；
> 二次访问正常渲染 2 行模板且控制台零异常，**非产品缺陷**。

## 三、双端一致性观察（非缺陷，登记待确认）

- **结果不完整措辞**：PC 成果页统计维度为「结果不完整」（`结果不为「结果不完整」`），
  移动端档案 chip 为「结果待补全」。语义一致、措辞不同；属 09-28 Change Task 3 有意调整（该 Task 要求
  「结束后显示正式结果、进行中不显示结果不完整」），本轮**不按缺陷处理**，是否需要双端统一措辞待人工确认。
- **类型名称缺失兜底**：PC 语境为「未分类」，移动端为「类型名称待补全」（09-28 明确要求「不误称为未分类」）✓ 符合 Change 目标。
- **证书来源名称取当前主数据**：本轮 `sourceName` 显示为「测试培训计划」，与 09-28 QA 提示的「来源改名会改变列表标题、PNG 原件不变」一致。

## 四、未覆盖面（如实登记）

1. **移动端原生端（App / 小程序）未验证**：`openDocument` 分支、真机触控与窄屏仅经 H5 复现；本轮无真机环境。
2. **H5 下载未落盘取证**：无头浏览器不实际保存文件，本轮以「受控端点请求 200/image/png + `<a download>` 触发」作为等价证据；
   未在带下载目录的浏览器中核对落盘文件字节。
3. **移动端档案分组仅 1 个培训类型**：库内 5 个计划的 `training_type_id` 均为 28，故「按类型分组」只取得单组证据；
   多组排序逻辑仍仅由定向测试覆盖。
4. **问卷投放后二次提交 409/9008、专家查课提交**：本轮未复跑，沿用 `2026-09-23-complete-m4-minimum-ui-closure` 实跑证据。
5. **跨租户 / 停用态文件读取**：沿用接口层与 IT 证据，本轮未构造。

## 五、结论

- 移动端「我的证书」「我的培训档案」两条链路在真实学员/超管登录态下**端到端通过**，
  `2026-09-24-close-mobile-learner-outcome-loop` Task 4 视为完成。
- PC 端 M4 五个成果页面 + 一期档案页 + 三级档案端点 + 结果不完整阻断 + 越权拦截**复验通过**，
  `2026-09-21-implement-m4-pc-outcome-closure` 维持「已验收认定通过」。
- `2026-09-28-improve-m4-certificate-and-mobile-archive` 的「真实学员登录态验收」项**由本轮补齐**。
- 综合判定：**M4 双端闭环完成**，未覆盖面见第四节，不影响本次认定。
