# Design：素材库与转码（步骤 1）

## Context

- 表：`sts_asset_asset` / `sts_asset_derivative` / `sts_asset_transcode_job` 已建。
- 已有：Entity/DTO/VO 骨架、`AssetReferenceService`、课程引用统计。
- 存储：已有 `LocalStorageAdapter` + `SysFileInfoService.upload`。
- 阻塞项（存储/转码供应商）**按推荐默认关闭**。

## Goals / Non-Goals

**Goals:** 素材入库、状态机、桩转码任务、删除保护、列表详情 API。  
**Non-Goals:** 真实转码厂商、批量 ZIP 上传、前端页面、题目侧引用（可接口预留）。

## Decisions

### D1：存储（推荐默认）

- 文件先走现有 `/system/files` 上传拿 `fileInfoId`，再创建素材（`sourceType=UPLOAD`）。
- `EXTERNAL`：必填 `externalUrl`，`processStatus` 可直接 `SUCCEEDED`。
- `CREATED`：富文本，直接 `SUCCEEDED`。

### D2：处理状态机（推荐默认）

`UPLOADED → PROCESSING → SUCCEEDED | FAILED`

创建后：

| assetType                                  | 行为                                                                                                                       |
|--------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| DOCUMENT / IMAGE / RICH_TEXT / LINK / LIVE | 同步置 `SUCCEEDED`（可写空 derivative 可选）                                                                                       |
| AUDIO / VIDEO                              | 建 `sts_asset_transcode_job`（PENDING→RUNNING→SUCCEEDED），**桩实现同步完成**并置素材 `SUCCEEDED`；失败路径用显式 API 模拟 `FAILED`（管理端调试）或类型校验失败 |

课程引用：仅 `SUCCEEDED`（已实现）。

转码中：禁止更新关键字段（名称除外可改）、禁止删除、不可引用。

### D3：删除保护

删除前调用 `CourseAssetReferenceService.getReferences`；`referenceCount>0` → 拒绝 6023。  
（题目引用检查 TODO 扩展点，本 Change 返回 0。）

### D4：权限

`asset:asset:{list,add,edit,delete}`；转码任务列表 `asset:job:list`。

### D5：错误码

`6011/6012` 已有；新增 `6021` 校验失败、`6022` 状态不允许、`6023` 仍被引用、`6024` 任务不存在。

## Risks

- [桩转码非生产] → design/OpenAPI 标明 stub；替换为真实 Provider 时只换适配器。
- [SysFileInfo 错误码与课程冲突] → 素材域使用 60xx，不改文件服务。

## Open Questions

| 事项     | 状态                                    |
|--------|---------------------------------------|
| 文件存储   | **本地存储已有，关闭**                         |
| 转码供应商  | **内置桩，关闭**                            |
| 转码失败处理 | **记 FAILED + failureReason；可手动重试桩任务** |
