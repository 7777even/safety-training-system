# Tasks：补齐 M4/M5 OpenAPI 机器可读契约

> 状态：已确认，Task 1 已完成。
>
> Owner：lims
>
> 执行限制：一次只实施一个已确认 Task。

## Task 1：闭合 M4/M5 现有 API 的机器可读契约

- **状态**：已完成（2026-09-21）
- **Owner**：lims
- **输入**：已确认 M4/M5 最小闭环规格；当前 Certificate、EvaluationResult、Survey、Ops、Regulation Controller；对应
  DTO/VO；OpenAPI 编写规范；2026-09-21 契约复核发现
- **输出**：修复后的 `certificate.openapi.json`、`evaluation-result.openapi.json`、`survey.openapi.json`、
  `ops.openapi.json`、`regulation.openapi.json`；删除证书公开模型三个隐藏字段；Controller-operation 与 Schema 字段差异检查证据；
  QA/Retro 与进度记录
- **前后端影响**：仅后端契约；前端以 OpenAPI 为准另开。不改前端、Mock、页面或调用代码
- **API 依赖**：不新增运行时路径；按已确认 M4 规格从签发规则创建请求删除 `conditionValue/collectionInstruction`，从证书响应删除
  `revokedTime`；不改变其他请求、响应、权限或错误码语义
- **数据影响**：无数据库、migration、init SQL 或历史数据影响
- **验收标准**：
  - [x] certificate 的 14 个 Controller operation 与 OpenAPI 一一对应，补齐遗漏的模板详情/更新/删除和规则删除接口
  - [x] certificate 的请求、VO、分页、ID、void 与 binary 响应均引用具体 Schema，不再使用无字段 object 或无类型 data 代替业务模型
  - [x] evaluation-result 仅保留当前公开能力可达的组件，人工评价写入和综合计算批次残留 Schema/response 清零
  - [x] survey 的 fill、submit、stats 均声明 `x-permission: authenticated`，条件统计可见性说明与当前实现一致
  - [x] ops 工作台响应与三个 Java VO 字段逐项一致，成功响应引用具体 Result Schema
  - [x] regulation 的 15 个 Controller operation 与 OpenAPI 一一对应；全部 JSON 业务响应引用具体 VO、列表或分页 Schema
  - [x] CSV 下载使用正确媒体类型和 binary Schema；标量计数、void、ID 均有明确类型
  - [x] 所有受影响 operation 具有 Controller tag、中文 summary/description、准确 `x-permission`、参数/请求/成功示例和实际错误响应
  - [x] 全部新增或修改 property 具有中文 description，枚举、可空性、required 与当前 Java/业务事实一致
  - [x] 五份契约悬空 `$ref` 为 0，受影响 DTO/VO 与 Schema 字段差异为 0
  - [x] 证书规则创建请求不再声明或写入 `conditionValue/collectionInstruction`，证书响应不再声明 `revokedTime`
  - [x] 证书隐藏字段冲突已取得人工决策：删除公开 DTO/VO 字段，数据库列与历史值保留
  - [x] 不修改未获确认的运行时代码、权限模型、数据库或前端，不恢复任何已删除能力
- **测试方式**：
  - JSON 解析五份目标契约及全量 `docs/api/*.openapi.json`
  - 自动或半自动比对 Controller 与 OpenAPI operation 集合
  - 定向比对 DTO/VO 与 Schema property、类型和顺序
  - 检查 `$ref`、Controller tag、summary、description、example、`x-permission` 和 400/401/403/404/409 响应
  - `openspec validate 2026-09-21-complete-m4-m5-openapi-contracts --strict`
  - `git diff --check`
  - 证书公开模型定向测试按 Red → Green 执行
  - 规格符合性与契约质量双轴 Review

### 当前验证（2026-09-21）

- 全量 `docs/api/*.openapi.json` JSON 解析通过。
- Controller-operation 双向比对通过：certificate 14、evaluation-result 3、survey 14、ops 1、regulation 15，差异均为 0。
- 目标 DTO/VO 与 Schema property 集合比对差异为 0；五份契约悬空 `$ref` 为 0。
- 失效的人工评价写入、综合计算批次 request/query/VO/page/result/response 组件为 0。
- operation 元数据、参数/请求/成功 example、401/403、字段 description 检查通过；占位字段说明和“示例值”清零。
- Red：`CertificateControllerTest.publicModels_excludeHiddenFields` 首次运行 4 例中 1 例按预期失败，命中请求仍声明
  `conditionValue/collectionInstruction`。
- Green：删除三个公开字段后，证书定向测试 `CertificateControllerTest`、`CertificateServiceImplTest`、
  `CertificateTemplateServiceImplTest` 共 7/7 通过；Admin 生产源码 1021 个文件编译通过。
- 全量 `docs/api/*.openapi.json` JSON 解析、五份契约结构检查、18 个目标 DTO/VO 字段集合比对通过。
- `openspec validate 2026-09-21-complete-m4-m5-openapi-contracts --strict` 与 `git diff --check` 通过。
- 未运行 MySQL IT：数据库结构、Entity、Mapper、SQL 和 migration 均未修改。

### Review

- **规格符合性**：通过。证书隐藏字段已按人工确认从公开 DTO/VO 与 OpenAPI 删除；Entity 与数据库历史列保留；未新增业务能力。
- **代码与契约质量**：通过。Controller 路径、DTO/VO 字段、认证/权限、成功/失败响应、example 与 `$ref` 均通过定向检查；
  定向测试覆盖公开模型边界。
- **遗留项**：无本 Task 内遗留；前端仍按修复后的 OpenAPI 另开消费与联调。
