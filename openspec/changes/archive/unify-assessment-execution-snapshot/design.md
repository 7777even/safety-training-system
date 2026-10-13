# Design：统一考核执行快照与规范化 Hash

## Context

现有 `AssessmentExecution` 把来源、资源、时长、分数、题序、计分规则分散在普通列，把附加规则放在
`policy_snapshot_json`。课程执行的 `snapshot_hash` 只覆盖写入前的规则 JSON 原文；读取时 MySQL JSON
已规范化文本，因此完整性判断依赖数据库输出格式，而不是业务内容。

统一校验是在培训闭环中引入的，但课程分支为了沿用旧 hash 特判为原文 SHA-256，形成三类问题：

- 正常 JSON 经过 MySQL 往返后可能误报 `7063`；
- 课程 hash 没有覆盖资源、时长、分数等完整冻结事实；
- 课程、独立考试和培训使用不同的 hash 语义，测试难以证明一致性。

## Decisions

### D1：单一完整快照是冻结事实唯一权威来源

`sts_assessment_execution` 使用：

```text
snapshot_schema_version  int       NOT NULL
snapshot_json            json      NOT NULL
snapshot_hash            char(64)  NOT NULL
```

删除以下旧快照字段：

```text
resource_name_snapshot
duration_seconds_snapshot
total_score_snapshot
pass_score_snapshot
question_order_mode_snapshot
multi_select_scoring_snapshot
policy_snapshot_json
```

保留 `organization_id`、来源 ID、`assessment_kind`、`resource_type/resource_id` 和 `execution_status`
作为查询、关联和索引投影。业务执行值必须从解码后的快照读取，不得继续把投影列当作另一套冻结事实。

### D2：快照使用固定强类型结构

新增 `AssessmentExecutionSnapshot`，初始 `schemaVersion=1`。顶层至少包含：

```text
schemaVersion
organizationId
source { type, businessId, examId, courseOfferingId, courseVersionId, courseItemId }
assessmentKind
resource { type, id, name }
rules {
  durationSeconds, totalScore, passScore,
  questionOrderMode, multiSelectScoring,
  policy
}
```

金额/分数类小数在快照中使用去除无意义尾零的十进制字符串；时长、次数和数量使用整数。`policy`
允许结构化扩展，但不得放入来源、资源或基础规则的重复字段。培训冻结题池只在快照中保存池 hash 和抽题规则，题池明细继续由关系表承载。

所有属性、Codec 和关键业务方法添加必要中文注释。

### D3：规范化算法与存储文本无关

新增唯一 `CanonicalJsonHasher`：

1. 把强类型快照转换为 JSON 树；
2. 对象字段按稳定 Unicode 顺序递归排列；
3. 数组严格保持业务顺序；
4. 小数由 DTO 预先转换为规范十进制字符串；
5. 使用固定 ObjectMapper 配置输出无缩进 UTF-8；
6. 对规范字节计算 SHA-256，小写十六进制输出 64 位摘要。

写入和读取必须调用同一方法。禁止 hash 原始请求字符串、Jackson 首次输出文本、数据库返回文本或 `Map.of()` 迭代结果。

### D4：统一 Codec 是唯一创建和读取入口

新增 `AssessmentExecutionSnapshotCodec`：

- `encode(snapshot)`：校验 v1 完整性，生成普通 JSON、规范化 hash 和 schema version；
- `decodeAndVerify(entity)`：解析 JSON、检查 schema version、重算 hash、核对查询投影，然后返回强类型快照；
- hash 缺失、JSON 不合法、版本未知、hash 不一致或投影不一致统一返回 HTTP 400 / `7063`，不得继续创建 Candidate/Attempt；
- 日志可记录执行 ID 和失败类别，不记录答案、完整策略或其他敏感快照内容。

课程、独立考试和培训的构建器只负责形成强类型快照，禁止自行序列化或计算 hash。

### D5：查询投影必须与快照一致

Codec 读取时核对：

```text
organization_id
source_type/source_business_id
exam_id
course_offering_id/course_version_id/course_item_id
assessment_kind
resource_type/resource_id
snapshot_schema_version
```

任一不一致均按损坏处理。这样查询仍可使用普通列和索引，但执行语义只来自经过 hash 校验的快照。

### D6：执行快照不可原地刷新

执行对象一旦生成即不可修改 `snapshot_json`、`snapshot_hash` 或相关投影。发布前需要改变规则时，删除尚未被使用的执行对象后重新生成；已有
Candidate、Attempt、Reviewer、结果或冻结题池时禁止刷新。独立考试当前的原地 `refreshStandalone` 必须改为满足上述门禁的替换/重建语义。

`execution_status` 属于运行状态，不放入不可变快照；其合法状态迁移继续由现有业务服务管理。

### D7：不兼容旧数据，迁移不静默清理

Admin migration 和 Admin-init 直接形成最终结构，不提供旧字段双读或历史回填。migration 在存在旧
`AssessmentExecution` 行时必须明确失败，提示整体重建开发数据库；不得在 Flyway 中级联删除 Candidate、Attempt、答案、批阅或课程结果。

开发数据库重建后，课程开课、考试发布和培训生成流程按现有来源重新创建 v1 执行快照。任何需要保留既有执行事实的环境不得部署本
Change。

### D8：HTTP 契约保持入口稳定

现有课程、考试和培训开考请求及成功响应不增加快照内部字段。`7063`
的契约描述统一为“考核执行快照缺失、无法解析、版本不支持、摘要不一致、投影不一致，或冻结题目资源不可执行”，具体中文消息可以细分原因。

## 测试策略

按 Red → Green → Refactor 实施：

1. Codec 单元测试覆盖稳定键序、嵌套对象、数组顺序、十进制字符串、未知版本、篡改和投影不一致。
2. 课程题库、课程测验、独立考试、独立练习、培训考试和培训练习分别覆盖构建与开考。
3. MySQL IT 必须真实执行“写入 JSON → 重新查询 → 校验 → 开考”，不得用内存字符串替代。
4. MySQL IT 直接改动 JSON 内容但不改 hash，断言 `7063` 且 Candidate/Attempt 零新增。
5. Schema IT 校验 migration 与 Admin-init 最终结构一致，旧七个快照字段不存在。
6. 回归现有来源隔离、幂等令牌、题库抽题、试卷冻结、课程结果和培训投影测试。

## Risks / Trade-offs

- 完整 JSON 比分散字段更集中，但不能直接对所有内部规则建索引；当前只有来源、资源和状态需要索引，已由投影列覆盖。
- 投影产生有限重复，但 Codec 会校验一致性，避免出现两个业务真相源。
- 不兼容迁移使部署要求更严格；以 migration 明确失败替代静默破坏数据。
- 快照 schema 将长期演进；未知版本必须拒绝执行，新增版本须有独立解析和测试，禁止猜测。

## Open Questions

无。开发期不保留旧数据、使用完整版本化 JSON 快照、所有来源统一规范化 hash 已由本 Change 的人工确认一并确认。
