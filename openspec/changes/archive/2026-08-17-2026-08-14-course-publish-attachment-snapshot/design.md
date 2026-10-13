# 设计

## 后端快照

`DownloadAssetSnapshot` 增加 `assetName` 字段。发布快照时根据课程附件关联的 `assetId` 查询当前机构素材，保存素材名称、素材
ID、排序和状态。历史版本解析后直接使用快照内的名称，不读取当前素材名称覆盖历史数据。

## 发布前检查

发布版本页面加载课程附件关联和素材详情，展示素材名称、素材 ID、排序、启用状态。附件为空仅显示“未配置课程附件”，不阻止发布。

## 历史快照展示

解析 `contentSnapshotJson.downloadAssets`，展示快照保存的附件字段。兼容旧版本快照中没有 `assetName` 的情况，名称显示为“未保存名称”。

## 测试边界

- 后端 `CourseSnapshotAssembler.assembleAndValidate` 验证附件名称写入快照。
- 前端定向测试验证发布检查加载附件、空附件提示和历史快照附件展示契约。
- 不修改发布 API 请求结构。
