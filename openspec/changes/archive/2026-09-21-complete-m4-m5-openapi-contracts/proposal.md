# Proposal：补齐 M4/M5 OpenAPI 机器可读契约

> 状态：已确认并完成 Task 1，待归档确认；2026-09-21 人工确认按既有 M4 规格删除证书公开模型中的隐藏字段。
>
> Owner：lims
>
> 风险级别：L4（修复唯一机器可读 API Contract）

## 背景

2026-09-20 的 `ded1a807`（M4 最小化重构）与 `ad0da04`（M5 最小化重构）已经调整后端公开能力并同步部分
OpenAPI，但复核发现契约尚未完整表达当前 Controller、DTO、VO 与鉴权事实：

1. `certificate.openapi.json` 遗漏 4 个仍公开的模板/规则接口，请求和响应大量使用无字段 `object` 或无类型
   `ResultEnvelope.data`。
2. `evaluation-result.openapi.json` 在删除人工评价创建/提交和综合计算批次 API 后，仍保留对应的失效 Schema 与响应组件。
3. `survey.openapi.json` 的填写、提交和按可见性查看统计三个登录态接口缺少 `x-permission`。
4. `ops.openapi.json` 与 `regulation.openapi.json` 虽已同步路径，但新工作台、学习统计、预警和下钻响应只在 example 中展示字段，
   `data` 没有机器可读类型；既有监管下钻也未完整引用具体分页或 VO Schema。
5. 部分受影响 operation 缺少标准 400/401/403/404/409 响应，无法完整表达现有失败语义。

`docs/api/*.openapi.json` 是前端和其他调用方的唯一机器可读契约。仅有示例而没有字段 Schema，会使类型生成、字段校验和权限审计失去依据。

## 覆盖功能项及原文语义

本 Change 不新增或改变业务能力，只修复以下已确认功能项的契约表达：

| 功能项      | 本次保持不变并准确表达的原文语义               |
|----------|--------------------------------|
| 1、3、7    | 园区监管工作台、数据中心、学习情况统计及真实导出。      |
| 2        | 培训计划学时不足自动预警、查看、下钻与导出。         |
| 8        | 企业管理员工作台的权限化入口、真实概览和分类待办。      |
| 19、50    | 证书模板、规则、自动签发、管理员查询下载及学员本人查看下载。 |
| 35、42    | 问卷投放、登录填写、提交及按可见性查看统计。         |
| 39、40、43 | 自动综合结果、已结束培训结果及专家查课；不恢复人工计算批次。 |

功能项语义继续以已归档的 M4/M5 最小闭环 Change 为准；本 Change 不重新定义这些业务规则。

## 目标

1. 使受影响 OpenAPI 的 URL、Method、Controller tag、权限元数据与当前后端逐项一致。
2. 使用具体 DTO、VO、分页、列表、ID、void 和 binary Schema 表达请求与响应，不用 example 替代类型定义。
3. 清理已删除公开能力留下的不可达 Schema、响应组件和说明。
4. 为每个 operation 和字段补齐中文说明、业务示例及实际可能发生的标准错误响应。
5. 建立可重复的 Controller-operation、Schema 字段、权限和 `$ref` 定向检查证据。

## 非目标

- 不新增、删除或修改任何运行时 Controller 路径、Method、DTO、VO、权限或业务错误码。
- 不改变问卷统计的条件可见性，不创建新权限码。
- 不恢复人工评价创建/提交、综合计算批次、证书撤销/重发或 M5 已删除能力。
- 不修改数据库、migration、init SQL、业务代码或测试代码。
- 不修改前端、Mock、页面或联调逻辑；仅后端，前端以修复后的 OpenAPI 为准另开。
- 不顺带整理本次五份契约之外的历史文档问题。

## 影响文件

- `docs/api/certificate.openapi.json`
- `docs/api/evaluation-result.openapi.json`
- `docs/api/survey.openapi.json`
- `docs/api/ops.openapi.json`
- `docs/api/regulation.openapi.json`
- `backend/sts-service-admin/src/main/java/com/sts/certificate/dto/CertificateIssueRuleCreateRequest.java`
- `backend/sts-service-admin/src/main/java/com/sts/certificate/vo/CertificateVO.java`
- 证书公开模型定向测试
- 本 Change、QA/Retro 与进度记录

`archive.openapi.json`、`training.openapi.json`、`org.openapi.json`、`regulator.openapi.json` 仅参与差异复核；若没有新差异则不修改。

## 人工确认关卡

确认本 Change 表示接受：

1. 本次只校正文档对当前实现的表达，运行时行为以当前 Controller/DTO/VO/权限校验为事实来源。
2. 证书契约补登记当前已经公开但遗漏的 4 个接口，不视为新增 API。
3. 问卷三个无 `@SaCheckPermission`、但要求登录态的接口统一使用 `x-permission: authenticated`；统计接口的条件可见性继续写入
   description，不新增复合权限码。
4. 删除的评价/计算批次组件从 OpenAPI 清除，不恢复对应业务能力。
5. M5 所有当前公开 JSON 响应必须引用具体业务 Schema；example 只用于示例，不能作为唯一字段定义。

## 实施中发现的需人工决策项

当前 `CertificateIssueRuleCreateRequest` 仍接收并由 Service 写入 `conditionValue`、`collectionInstruction`，
`CertificateVO` 仍返回 `revokedTime`。已确认 M4 Design §8.5 与 inventory 将复杂条件值、领取说明、撤销时间列为从业务 API
删除且当前不读、不写、不展示。二者不能同时作为契约真相源。

2026-09-21 人工确认采用方向 1：扩大本 Change，删除上述 DTO/VO 字段及对应公开写入/返回行为，并补定向 TDD；数据库列与历史值继续保留，Entity
与 Mapper 不删除这些兼容字段。
