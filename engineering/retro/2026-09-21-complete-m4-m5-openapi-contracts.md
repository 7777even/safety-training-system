# Retro：补齐 M4/M5 OpenAPI 机器可读契约

## 做得好的地方

- 先按 Controller 建立 operation 基线，再检查 DTO/VO 字段和权限，避免把“JSON 可解析”误当成契约完整。
- 明确区分 example 与 Schema：保留业务示例，同时让 `data` 引用具体分页、列表、标量或 VO 类型。
- 清理评价结果组件时按当前 path 的可达引用复核，没有恢复已删除的人工计算批次能力。
- 发现当前 DTO/VO 与已确认 M4 规格冲突后暂停并取得人工决策，再按 Red → Green 删除公开字段，没有通过文档掩盖实现偏差。

## 发现的问题

- M4/M5 原同步主要关注 path 增删，未把 Controller-operation、DTO/VO-Schema、权限和 `$ref` 当作同一验收集合。
- `ResultEnvelope.data` 无类型但 example 很完整，人工阅读看似可用，代码生成器实际上无法得到业务类型。
- 已删除 path 后没有同步清理仅由旧 path 使用的 Schema/response，导致契约继续暴露失效概念。
- 登录即可访问的接口缺少统一的 `x-permission: authenticated` 表达，权限审计无法区分“公开”与“需登录”。
- 数据库列被标记“当前不读、不写、不展示”时，只保留 Entity/DDL 不足以完成收口，还必须同步删除公开 DTO/VO 字段。

## 后续约束

- 以后修改 Controller 时，验收必须同时比较 URL/Method、Controller tag、权限、请求 DTO、响应 VO、错误码和 example。
- 成功响应不得只引用无类型 `ResultEnvelope`；必须在 operation 或具体 Result Schema 中明确 `data` 类型。
- 删除 operation 时同步删除不可达 request/query/VO/page/result/response，并执行悬空与不可达组件检查。
- 无 `@SaCheckPermission` 但依赖登录态的接口统一使用 `x-permission: authenticated`，不得虚构功能权限码。
- 隐藏数据库列时增加公开 DTO/VO 字段边界测试，确保兼容存储不会重新泄露为产品能力。

