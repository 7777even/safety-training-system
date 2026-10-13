# Design：证书模板设计与签发规则管理闭环

## 1. 真相源与边界

优先级依次为：

1. 本 Change 经人工确认后的 Proposal、Design、Tasks 与 Spec Delta；
2. `openspec/specs/m4-minimum-closure/spec.md` 的证书最小闭环；
3. 功能项 19 原文语义；
4. `docs/api/certificate.openapi.json`；
5. 当前证书、课程、考试和培训实现。

本 Change 只完成证书模板设计与签发配置，不修改档案和签到，不恢复已删除证书能力。

## 2. 模板聚合与版本状态

### 2.1 聚合边界

模板详情由以下现有表聚合：

- `sts_certificate_template`：稳定身份、名称、启用状态、乐观锁版本；
- `sts_certificate_template_version`：底图、画布尺寸、版本状态；
- `sts_certificate_template_element`：文字/变量、坐标和字体样式。

不新增表或列。模板版本表已有 `DRAFT/PUBLISHED/ARCHIVED`，本 Change 激活其既有状态语义。

### 2.2 版本规则

1. `current_version_no` 只指向当前 PUBLISHED 版本；没有已发布版本时为 0。
2. 每个模板最多存在一个 DRAFT，其 `version_no = current_version_no + 1`。
3. PUT 保存设计时：
  - 没有 DRAFT：创建下一版本 DRAFT；
  - 已有 DRAFT：更新该版本并替换其元素；
  - 不修改 PUBLISHED/ARCHIVED 版本和元素。
4. 发布时先以发布请求内容保存/覆盖 DRAFT，再在同一事务中：
  - 将旧当前 PUBLISHED 标为 ARCHIVED；
  - 将 DRAFT 标为 PUBLISHED并记录发布人、发布时间；
  - 更新模板 `current_version_no`；
  - 已被证书引用的历史版本仍可按 ID 读取和渲染。
5. 模板元数据和设计草稿在同一事务更新；模板 `version` 不一致时返回 409，避免静默覆盖。

### 2.3 详情模型

`CertificateTemplateVO` 增加：

- `description`；
- `currentPublishedVersion`：当前发布版本详情，可空；
- `draftVersion`：当前草稿详情，可空。

版本详情包含 `templateVersionId/versionNo/backgroundFileId/widthPx/heightPx/publishedStatus/versionNote/elements`；元素包含
`templateElementId/elementType/variableCode/staticContent/xPosition/yPosition/width/height/fontFamily/fontSize/fontColor/sortOrder/status`。

### 2.4 更新模型与校验

`CertificateTemplateUpdateRequest` 保留模板 `version/templateName/status/description`，并增加必填设计内容：

- `backgroundFileId`、`widthPx`、`heightPx`、`versionNote`、`elements`。

校验规则：

- 画布宽高为正数；坐标、排序为非负数；字号为正数；颜色为 `#RRGGBB`；
- 公开元素类型仅 `TEXT/VARIABLE`；
- TEXT 必须有 `staticContent`；VARIABLE 必须使用支持的变量编码；
- 支持变量为 `CERT_NO/HOLDER_NAME/SOURCE_TYPE/ISSUED_TIME`；
- `backgroundFileId` 必须指向当前可读取且能由 ImageIO 解码的图片。

错误使用证书域业务码登记在 OpenAPI；请求字段基础校验返回 400，状态/并发冲突返回 409。

## 3. 模板删除

DELETE 在同一租户内检查未删除的 `sts_certificate_issue_rule`。存在任何规则引用时返回 HTTP 409，不删除模板；否则逻辑删除模板稳定身份。
历史版本和元素不做物理删除，避免破坏已签发证书快照。

## 4. PNG 渲染

1. 渲染器通过 `backgroundFileId` 读取真实对象并解码图片，缩放到模板画布。
2. TEXT 绘制 `staticContent`；VARIABLE 从签发上下文解析。
3. 应用 `fontFamily/fontSize/fontColor/xPosition/yPosition`；缺省字体使用 Java 逻辑无衬线字体。
4. `width/height` 暂只作为设计工作台回显和边界校验，不实现自动换行、裁剪或图片元素叠加。
5. 底图不存在或不可解码时，保存/发布失败；不得回退为默认米色画布并伪装成功。
6. 预览与正式签发复用同一渲染路径，保证结果一致。

## 5. 签发规则管理

### 5.1 API

在既有列表、创建、删除基础上新增：

- `GET /api/certificate/rules/{certificateRuleId}`，权限 `certificate:rule:list`；
- `PUT /api/certificate/rules/{certificateRuleId}`，权限 `certificate:rule:edit`；
- `GET /api/certificate/rule-source-candidates`，权限 `certificate:rule:list`。

规则更新请求包含 `version/ruleName/certificateTemplateId/sourceType/sourceId/conditionType/isAutoIssue/status`。规则 VO
补齐
`isAutoIssue/version/certificateTemplateName/sourceName`，用于列表、详情和可靠回显。

### 5.2 关联校验

创建和更新规则共同校验：

1. 模板属于当前租户、状态启用且存在当前 PUBLISHED 版本；
2. `sourceType` 仅允许 `COURSE/EXAM/TRAINING`；
3. 来源属于当前租户且满足候选状态；
4. `conditionType` 使用既有最小闭环值 `PASS`；
5. 同租户同 `sourceType + sourceId + certificateTemplateId` 仍由唯一键和服务错误共同保证；
6. 更新使用实体乐观锁 `version`，并发冲突返回 409。

规则状态禁用只阻止后续自动签发，不撤销或修改历史证书。

## 6. 统一来源候选

### 6.1 请求

`GET /api/certificate/rule-source-candidates` 使用标准分页参数，并要求 `query.sourceType`；可选 `query.keyword`
。一次请求只查一个来源类型，避免把三个领域的独立分页结果伪装成全局准确分页。

### 6.2 响应

返回 `PageResult<CertificateRuleSourceCandidateVO>`：

- `sourceType`、`sourceId`；
- `sourceCode`、`sourceName`；
- `publishStatus`。

### 6.3 候选状态

- COURSE：课程 `publishStatus=PUBLISHED`；
- EXAM：理论考试且 `publishStatus=PUBLISHED`，不返回练习或线下结果定义；
- TRAINING：培训计划 `publishStatus in (PUBLISHED,RUNNING)`；
- 结束、取消、归档和草稿来源不作为新规则候选，但既有规则详情仍尽力解析名称并原样返回 ID。

所有查询由租户插件限制当前租户，Service 不接受客户端机构 ID。

## 7. 权限与数据库资产

新增 `certificate:rule:edit`：

- Controller 使用 `@SaCheckPermission("certificate:rule:edit")`；
- `menus-back.json` 在证书管理下增加“规则编辑”功能节点；
- 新增不可变 Flyway migration 为既有数据库幂等补充权限节点；
- 更新 `sts.init.schema-version`；
- 不新增角色默认授权，不修改生产数据。

除权限种子外，不修改表、索引、字段或约束。

## 8. 错误语义

保留现有错误码，并新增不冲突的证书域错误码，实施时以代码和 OpenAPI 同步为准：

- 模板不存在、规则不存在、重复规则沿用当前语义；
- 模板被规则引用：409；
- 草稿/背景/元素不合法：400 或 409（按请求错误与状态冲突区分）；
- 模板未发布或来源不可绑定：400；
- 模板或规则乐观锁冲突：409。

不得复用当前已占用的 9412～9417 证书签发/文件错误码表达新错误。

## 9. 测试设计

### 9.1 零 DB Red → Green

- Controller：新增规则详情、更新、来源候选路径和权限委托；模板详情/更新请求契约。
- 模板 Service：详情聚合、草稿创建/覆盖、发布晋级、引用删除拒绝、并发冲突。
- 规则 Service：详情、更新、模板/来源校验、重复与并发冲突。
- 渲染器：真实背景像素、变量替换、坐标/字体颜色，坏图片拒绝。

### 9.2 MySQL IT

本 Change 涉及真实多表事务、逻辑删除、唯一键、乐观锁、租户过滤和权限 migration，必须补/跑：

- 模板草稿覆盖与发布后历史版本不可变；
- 模板引用删除拒绝；
- 规则更新唯一键与乐观锁；
- 三类候选的租户与状态过滤；
- Flyway migration 与 Admin-init 基线一致。

### 9.3 契约与关闭

- Controller-operation 与 `certificate.openapi.json` 零差异；
- DTO/VO 与 Schema 字段零差异；
- OpenAPI 元数据、中文说明、example、权限、错误码与 `$ref` 检查通过；
- `openspec validate --strict`、`git diff --check`；
- 规格符合性和代码质量双轴 Review；
- 更新 Task、进度表、QA、Retro。
