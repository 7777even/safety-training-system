# Design：M4/M5 OpenAPI 契约完整性修复

## 1. 边界与真相源

本 Change 的真相源顺序为：

1. 已确认并归档的 M4/M5 最小闭环 OpenSpec；
2. 当前 Controller 的路径、Method 与 `@SaCheckPermission`；
3. 当前 DTO/VO、分页包装和统一异常映射；
4. `docs/architecture/openapi-contract-writing-guide.md`。

发现运行时实现与已确认业务规格冲突时立即暂停，不通过修改 OpenAPI 掩盖冲突。本次不改运行时。

2026-09-21 实施中发现证书公开 DTO/VO 仍包含已确认隐藏字段。人工确认后，本 Change 对该句形成唯一例外：只删除
`CertificateIssueRuleCreateRequest.conditionValue/collectionInstruction` 与 `CertificateVO.revokedTime`，使运行时公开模型重新符合既有
M4 规格；不扩大到其他业务逻辑。

## 2. 差异基线

### 2.1 M4

| 契约                               | 当前状态                                                | 修复设计                                           |
|----------------------------------|-----------------------------------------------------|------------------------------------------------|
| `certificate.openapi.json`       | Controller 14 个 operation，契约 10 个；请求/响应多为无字段 object | 补齐 4 个路径，并为全部 14 个 operation 建立具体请求/响应 Schema  |
| `evaluation-result.openapi.json` | 当前公开 3 个 operation 已登记，但保留已删除计算批次及通用评价写入组件          | 删除仅服务已删除路径且不可从现有 operation 到达的 Schema/response |
| `survey.openapi.json`            | 14 个 operation 路径已对齐；3 个登录态接口缺 `x-permission`       | 填写、提交、统计统一声明 `authenticated`，保留统计条件可见性说明       |

`archive.openapi.json` 与 `training.openapi.json` 的本次新增路径已经对齐，仅复核不改写。

### 2.2 M5

| 契约                        | 当前状态                                               | 修复设计                                      |
|---------------------------|----------------------------------------------------|-------------------------------------------|
| `ops.openapi.json`        | 1 个 operation 路径对齐；工作台 data 无具体类型                  | 新增工作台、快捷入口、待办分类及 Result 包装 Schema         |
| `regulation.openapi.json` | 15 个 operation 路径对齐；JSON 响应普遍只引用无类型 ResultEnvelope | 为工作台、学习统计/下钻、预警、培训计划/参训人/结果及分页建立具体 Schema |

`org.openapi.json` 的机构类型与请求/响应已经同步，`regulator.openapi.json` 已正确移除旧聚合路径；仅复核不改写。

## 3. 契约表达规则

### 3.1 Controller 与 operation

- 根级 tag 和 operation tag 使用真实 Controller 简单类名。
- 路径保留 `/api` 前缀，Method 与映射注解逐项一致。
- 每个 operation 均包含 `summary`、`description`、`security`、`x-permission` 和成功 example。
- `@SaCheckPermission` 单值按原权限码登记；OR/AND 组合按代码真实语义写入并在 description 解释。
- 仅要求登录态且没有功能权限注解的接口使用 `x-permission: authenticated`。

### 3.2 请求与响应

- 请求体引用具体 `*Request` Schema；DTO 的校验约束映射到 `required`、枚举、格式和可空性。
- 返回对象引用具体 `*VO` Schema；分页使用 `PageResultXxxVO`，列表显式声明 item 类型。
- 创建 ID、void、标量计数和 binary 下载分别使用明确包装或媒体类型。
- `ResultEnvelope.data` 不承担具体业务类型；operation 使用 `allOf` 或具体 Result 包装覆盖 data。
- 每个 Schema 和 property 使用中文 `description`，标量和枚举提供业务 example。

### 3.3 错误响应

- 所有受保护接口至少登记 401、403。
- 参数/请求体校验接口登记 400；资源定位接口登记 404；状态或幂等冲突接口登记 409。
- description 只登记当前代码实际可能产生的业务错误码，不编造新错误码。
- 失败响应统一引用带 `application/json` 和 `ResultEnvelope` 的公共 response。

## 4. 分文件设计

### 4.1 certificate

补登记当前既有路径：

- `GET /api/certificate/templates/{certificateTemplateId}`
- `PUT /api/certificate/templates/{certificateTemplateId}`
- `DELETE /api/certificate/templates/{certificateTemplateId}`
- `DELETE /api/certificate/rules/{certificateRuleId}`

并对齐 `CertificateTemplate*Request/VO`、`CertificateIssueRule*Request/VO`、`CertificateQuery`、`CertificateVO`、分页结果、
模板预览和证书文件 binary 响应。已删除的手工签发、撤销、重发路径不得恢复。

公开模型同步收口：

- `CertificateIssueRuleCreateRequest` 不再接收复杂条件值 `conditionValue` 和领取说明 `collectionInstruction`；
- `CertificateVO` 不再返回撤销时间 `revokedTime`；
- `CertificateIssueRule`、`Certificate` Entity 与数据库历史列继续保留，当前业务不读、不写、不展示；
- 删除 DTO/VO 字段后，现有 `BeanUtils.copyProperties` 自然停止写入隐藏字段，不新增替代默认值或兼容写入。

### 4.2 evaluation-result

保留且完整表达当前三个 operation：专家查课提交、计划专家查课摘要、评价记录读取。删除仅服务以下已删除公开能力的组件：

- 通用评价记录创建/提交；
- 综合计算批次 calculate/confirm/page/source；
- 对应 request、query、VO、page/result/response 包装。

被当前专家查课或评价记录读取真实引用的公共评价项 Schema 必须保留。

### 4.3 survey

不改路径、请求或响应字段。为 `fill`、`submit`、`stats` 增加 `x-permission: authenticated`。`stats` description 保留管理员权限或
填写人提交后可见的服务层判定，避免把条件可见性误写为单个功能权限。

### 4.4 ops

为 `OpsWorkbenchSummaryVO`、`OpsWorkbenchShortcutVO`、`OpsTodoCategoryVO` 及具体 Result 包装建立 Schema；成功响应引用具体包装，
并补 401、403。字段顺序与 Java VO 一致。

### 4.5 regulation

按当前 Controller 和实际复用 VO 建模：

- `RegulationWorkbenchVO` 及培训类型分布、特种作业、机构摘要、快捷入口；
- `LearningStatsVO`、`LearningLearnerRowVO`、`LearningOfferingRowVO`；
- `WarningQuery`、`WarningVO`、未读数量、分页及 void 包装；
- `TrainingPlanQuery/VO`、`TrainingParticipantVO`、`TrainingParticipantResultsVO` 及列表/分页包装；
- 三类 CSV binary 下载响应。

查询参数必须与 Spring 实际绑定一致；不复制或发明培训域字段。跨域 VO 的字段以当前 Java 类型为准，并在 regulation 契约内提供完整定义，
避免跨文件 `$ref`。

## 5. 验证设计

本 Change 仅涉及上述三个公开字段的最小运行时模型收口，不执行无关 Maven 全量测试。定向验证包括：

1. 五份修改契约及全部 `docs/api/*.openapi.json` JSON 解析。
2. 受影响 Controller 与 OpenAPI operation 集合双向差异为 0。
3. 本次具体 DTO/VO 与 Schema property 集合、字段顺序和类型逐项比对。
4. 所有 `$ref` 可解析；已删除评价/计算组件不再存在。
5. operation 的 tag、summary、description、example、`x-permission` 与标准错误响应检查。
6. `openspec validate 2026-09-21-complete-m4-m5-openapi-contracts --strict`。
7. `git diff --check`。
8. 证书公开模型测试先失败后通过，证明请求不再声明隐藏写入字段、响应不再声明撤销时间字段。

完成后执行规格符合性与契约质量双轴 Review，并形成 QA/Retro；进度表只追加“契约修复”证据，不改变 M4/M5 业务完成口径。
