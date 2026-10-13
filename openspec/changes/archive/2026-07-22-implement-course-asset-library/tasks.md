# Tasks：素材库与转码

## Task 1：素材 CRUD、状态机、桩转码与删除保护

- 状态：已实施
- Owner：AI
- 输入：design 推荐默认；已有表与 AssetReferenceService、CourseAssetReferenceService、Storage/SysFileInfo
- 输出：AssetService/Controller、TranscodeStub、权限、OpenAPI、单测
- 验收标准：
  - [x] UPLOAD/EXTERNAL/CREATED 创建校验正确
  - [x] AUDIO/VIDEO 产生任务并桩完成 → SUCCEEDED
  - [x] PROCESSING 不可引用；被课程引用不可删
  - [x] 列表/详情/更新/删除 API 与 `asset:asset:*` 权限
- 测试方式：`AssetServiceImplTest` 等
