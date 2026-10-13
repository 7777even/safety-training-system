# Design：课程发布版本

## Context

- 前置 Change（已归档）：`implement-course-config-authoring` 已交付草稿态课程编排。
- 表结构唯一依据：归档数据模型 DDL `sts_course_version`（C 类，写入即发布，不可变）。
- 步进依据：`docs/requirement/course-module-task-plan.md` 步骤 4。
- 模块边界：继续在 `com.sts.course.*` / `sts-service-admin`，不新建服务。

## Goals / Non-Goals

**Goals:**

- 正式发布生成不可变版本快照（JSON + SHA-256 哈希）。
- 更新课程 `current_version_id`、`publish_status=PUBLISHED`、`published_time`。
- 版本列表/详情只读；相同内容重复发布幂等。
- 支持将已发布课程下线为 `OFFLINE`（保留历史版本）。

**Non-Goals:**

- 开课绑定版本、报名、学习进度（步骤 5）。
- 素材上传/转码全能力（步骤 1）；发布时仅复用 `AssetReferenceService`。
- 前端页面。
- 发布后回滚删除版本行。

## Decisions

### D1：快照范围与结构

快照 MUST 包含发布瞬间的完整可学习结构：

```json
{
  "schemaVersion": 1,
  "course": { /* 课程基本信息字段，不含乐观锁工作态噪声可选保留 version */ },
  "categoryIds": [1, 2],
  "chapters": [
    {
      "chapterId": 1,
      "chapterName": "...",
      "sortOrder": 0,
      "status": 1,
      "items": [
        {
          "courseItemId": 10,
          "itemType": "ASSET",
          "itemTitle": "...",
          "assetId": 5,
          "isRequired": 1,
          "minimumStudySeconds": 0,
          "sortOrder": 0,
          "status": 1,
          "materialRule": { /* 生效规则：CUSTOM 行或按机构设置计算的 DEFAULT */ },
          "liveSession": null
        }
      ]
    }
  ],
  "downloadAssets": [{ "assetId": 5, "sortOrder": 0, "status": 1 }]
}
```

- 序列化：Jackson 稳定字段顺序（`ObjectMapper` + 有序 Map / JavaBean 固定字段序），UTF-8 JSON 紧凑或统一 pretty 二选一，*
  *全项目固定一种**（采用无多余空白的 compact JSON）。
- `snapshot_hash`：对 `content_snapshot_json` 字节做 SHA-256，小写十六进制。

### D2：幂等

同一课程再次发布时：

1. 组装快照并算哈希。
2. 若 `current_version_id` 指向版本的 `snapshot_hash` 与本次相同 → **不插入新行**，返回已有 `courseVersionId`（仍要求请求携带正确课程
   `version` 乐观锁，或跳过课程行更新）。
3. 否则插入 `version_no = max+1`，更新课程行。

### D3：发布校验

- 课程属于默认机构且存在。
- 至少 1 个章节。
- 每个 ASSET 内容项：`assetId` 非空且 `requireUsableAsset` 通过。
- LIVE 内容项：须存在直播场次行。
- 禁用类型内容项若库中意外存在 → 拒绝发布（5032）。

### D4：下线

- `PUT .../offline`：`publish_status=OFFLINE`，不清空 `current_version_id`，不删版本。
- 下线后草稿仍可编辑；再次发布产生新版本并回到 `PUBLISHED`。

### D5：API 与权限

| 接口                                              | 权限                      |
|-------------------------------------------------|-------------------------|
| `POST /course/courses/{id}/publish`             | `course:course:publish` |
| `GET /course/courses/{id}/versions`             | `course:course:list`    |
| `GET /course/courses/{id}/versions/{versionId}` | `course:course:list`    |
| `PUT /course/courses/{id}/offline`              | `course:course:publish` |

错误码（课程段扩展）：

- `5051` 无可发布内容（无章节）
- `5052` 发布校验失败（素材/直播不完整等，消息区分）
- `5053` 版本不存在
- `2040` 乐观锁冲突

`published_by`：取当前登录用户 ID（Sa-Token）；无登录上下文的集成测试可注入固定 ID。

### D6：审计类别

`sts_course_version` 为 C 类：仅 `create_by`/`create_time` + 发布字段；无 `update_*`/`deleted`/`version`。Service 禁止
update/delete。

## Risks / Trade-offs

- [快照过大] → 本阶段课程体量可控；后续可评估压缩或外置对象存储。
- [JSON 字段顺序导致哈希不稳] → 固定 schema + compact 序列化 + 单测锁定样例哈希。
- [发布瞬间草稿被并发修改] → 课程行乐观锁；发布事务内读快照。
- [开课表未建，无“已被开课引用不可改版本”] → 版本本身不可变已满足；开课侧守卫留步骤 5。

## Migration Plan

1. `V20260722040000__create_course_version_table.sql`（对齐已评审 DDL）。
2. 同步 `backend/sql/schema-course.sql`。
3. 回滚：停用发布接口；表可保留（只追加）。

## Open Questions

- 无（步骤 4 无产品阻塞项；存储/转码属步骤 1，本 Change 不涉及）。
