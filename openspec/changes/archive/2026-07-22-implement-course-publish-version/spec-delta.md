# Spec Delta：课程发布版本

> 与 `specs/course-publish-version/spec.md` 等价摘要，满足仓库 AGENTS 对 `spec-delta.md` 的归档要求。

## 新增需求

- 正式发布必须生成不可变 `sts_course_version`（递增 `version_no` + 完整快照 JSON + SHA-256）。
- 相同快照哈希的重复发布必须幂等，不产生新版本行。
- 发布前校验章节、ASSET 素材可用性、LIVE 场次完整性。
- 版本仅提供列表/详情只读查询；禁止更新与删除。
- 支持下线为 `OFFLINE`，保留历史版本与 `current_version_id`。
- 发布后草稿表仍可编辑；再发布在内容变化时产生新版本。

## 非目标

- 开课绑定、报名、学习事实；素材上传转码；前端页面。
