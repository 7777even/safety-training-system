# 素材库前端能力

## ADDED Requirements

### Requirement: 前端必须遵循现有上传契约

前端 MUST 根据文件大小选择现有单文件或 multipart 接口，且不得新增或修改后端接口字段。

#### Scenario: multipart 恢复

- **WHEN** 初始化响应包含已上传分片序号但不包含历史 ETag
- **THEN** 前端重新上传这些分片获取新的 ETag，再按现有 complete 契约提交

### Requirement: 素材预览必须统一路由

前端 MUST 使用统一查看器消费后端返回的格式和受控地址。

#### Scenario: 不支持的文件

- **WHEN** 后端返回无法在线预览的格式
- **THEN** 前端显示明确提示并保留下载或打开方式
