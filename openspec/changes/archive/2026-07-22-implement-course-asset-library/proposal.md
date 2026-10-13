## Why

课程编排与开课已可引用素材，但素材库仍缺上传入库、处理状态机与删除保护的完整管理能力。按 `course-module-task-plan` 步骤 1
补齐，使 ASSET 内容项能基于真实素材流转，而非仅靠手工插库。

## What Changes

- 素材 CRUD（机构内）、分页查询、详情。
- 创建时按来源绑定已上传 `fileInfoId` / 外链 / 富文本；写入后进入处理状态机。
- 转码任务表落地工作流：**推荐默认**为本地存储 + 内置桩转码（无外部供应商）。
- 删除前经课程侧引用检查拦截；处理中不可用于课程（已有 `AssetReferenceService`）。
- 权限、OpenAPI；前端不在本协作范围。

## Capabilities

### New Capabilities

- `course-asset-library`: 素材库管理、处理状态与转码任务（桩）、删除保护。

### Modified Capabilities

- （无）

## Impact

- `com.sts.asset.*`、复用 `StorageService`/`SysFileInfo`、`CourseAssetReferenceService`。
- 表已存在（`V20260722020000`）；本 Change 以能力实现为主，必要时补索引/字段 migration。
- 非目标：真实云转码供应商、HLS/预览派生生产级质量、题目模块引用检查（可预留扩展点）。
