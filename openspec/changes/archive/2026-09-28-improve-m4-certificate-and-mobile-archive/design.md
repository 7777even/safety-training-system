# Design：来源识别与展示

1. `CertificateVO.sourceName` 为可空展示字段。列表按本页 `sourceType + sourceId` 批量读取课程、考试和培训计划名称；来源已删除或不可见时为空，前端退回「类型 + 来源 ID」。来源名称为当前主数据名称，后续改名会影响列表显示，证书 PNG 原件保持签发时文件不变。
2. 双端使用既有 `/api/certificate/my-certificates/{id}/file` 受控获取 PNG 缩略图；加载失败显示本地占位，不使用裸 `fileInfoId` 或可公开访问地址。仅加载当前可见页；PC 释放 Blob URL，移动端 H5 释放可撤销的临时 URL。
3. 移动端以来源名称为主标题、类型和签发日期为辅助信息、编号为次要信息，保留在线查看与下载操作。
4. 培训档案继续按类型分组；`trainingTypeName` 为空时分组名改为「类型名称待补全」。`publishStatus=RUNNING` 时只显示进行状态；`ENDED` 后才显示正式结果，`INCOMPLETE` 解释为「结果待补全」。不改后端结果计算。

既有移动端 Change 对 PC 页面、类型兜底和结果展示的限制，以本 Change 的上述已确认目标为准；其它权限边界不变。
