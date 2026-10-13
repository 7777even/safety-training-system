# Tasks：补齐证书模板设计与签发规则管理闭环

> 状态：已于 2026-09-21 获得人工确认，Task 1～2 已完成。
>
> Owner：lims
>
> 执行限制：一次只实施一个已确认 Task；Task 1 验收后才能实施 Task 2。

## Task 1：完成模板设计草稿、发布与真实样式生成

- **状态**：已完成（2026-09-21）
- **Owner**：lims
- **输入**：功能项 19；本 Change Proposal/Design/Spec Delta；当前模板、版本、元素、预览和签发实现；既有文件存储能力。
- **输出**：可读取/保存/发布的模板设计聚合；安全删除；真实背景和文字变量样式渲染；同步后的 OpenAPI、测试与 Task 1 QA 证据。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：保持模板 GET/PUT/DELETE、publish、preview 路径；扩展模板 GET/PUT 模型和 publish 语义；不新增模板历史公开接口。
- **数据影响**：复用模板、版本、元素表；不改 DDL；DRAFT 可覆盖，PUBLISHED/ARCHIVED 不可变；删除为逻辑删除且保留历史版本/元素。
- **验收标准**：
  1. GET 同时返回模板元数据、当前发布版本和当前草稿及完整元素，跨租户 ID 返回不存在。
  2. PUT 使用模板乐观锁保存唯一下一版本 DRAFT，重复保存只替换草稿元素，不修改任何已发布历史版本。
  3. 发布请求原子保存并发布 DRAFT，旧当前版本转 ARCHIVED，`currentVersionNo` 指向新版本。
  4. TEXT/VARIABLE 的必填字段、变量编码、坐标、字号和颜色得到真实校验；公开请求不再接受 IMAGE。
  5. 背景文件必须真实存在且可解码；预览与签发 PNG 使用底图、坐标、字体、字号和颜色，不使用占位背景伪装成功。
  6. 有有效签发规则引用时删除模板返回 409 且不删除；无引用时正常逻辑删除。
  7. 模板 Controller、DTO/VO 与 `certificate.openapi.json` 路径、字段、权限、示例和错误码一致。
- **测试方式**：先扩展 Controller/Service/Renderer 测试并观察目标失败，再最小实现；运行证书模板相关零 DB 测试及模板/元素/规则
  Mapper、事务和租户相关 MySQL IT；执行契约检查、双轴 Review、`git diff --check`。

## Task 2：完成签发规则详情、更新与统一来源候选

- **状态**：已完成（2026-09-21）
- **Owner**：lims
- **输入**：Task 1 已发布模板事实；功能项 19；课程、理论考试、培训计划的租户与发布状态；当前规则创建/删除及自动签发实现。
- **输出**：规则详情/更新、增强后的列表回显、统一来源候选、创建/更新关联校验、新权限及 migration、完整 OpenAPI、QA/Retro 和进度记录。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：新增规则 GET/PUT 和统一候选 GET；规则 PUT 使用 `certificate:rule:edit`，详情/候选使用
  `certificate:rule:list`；保留现有列表、创建、删除路径。
- **数据影响**：不改证书业务表；`menus-back.json` 和新 Flyway migration 增加 `certificate:rule:edit` 功能权限，更新
  Admin-init schema version；不默认授予角色。
- **验收标准**：
  1. 规则详情和列表返回完整配置、`version`、模板名称和来源名称，可可靠回显。
  2. PUT 在当前租户内按乐观锁更新；不存在、重复和并发冲突分别返回明确 404/409。
  3. 创建和更新都只接受启用且已发布模板，以及当前租户内满足状态要求的 COURSE/EXAM/TRAINING 来源。
  4. 候选接口按必填 `sourceType` 和可选关键字分页，只返回当前租户可绑定来源；三类来源的 ID/编码/名称/状态语义明确。
  5. 既有规则的来源后续结束或不可绑定时，详情仍保留原 ID 并尽力返回名称；候选接口不把它作为新选择。
  6. `certificate:rule:edit` 在 Controller、OpenAPI、init 种子和 migration 一致且可授权。
  7. 自动签发继续按启用规则工作；规则禁用/编辑不修改、撤销或重发历史证书。
  8. Controller-operation、DTO/VO-Schema、权限、错误码、示例与 `$ref` 检查全部通过；相关零 DB、MySQL IT、Flyway IT、OpenSpec
     strict 和 `git diff --check` 通过。
- **测试方式**：先写规则 Controller/Service/候选查询失败测试，再最小实现；运行受影响证书、课程/考试/培训候选、Mapper/租户/唯一键/乐观锁及
  Flyway MySQL IT；完成规格符合性与代码质量双轴 Review，更新 tasks、进度表、QA 和 Retro。
