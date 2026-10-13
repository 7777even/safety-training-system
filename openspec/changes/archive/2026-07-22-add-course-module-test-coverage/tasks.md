# Tasks：课程域测试覆盖补齐

## Task 1：零 DB 单测与 Web 切片

- 状态：已实施
- Owner：AI
- 输出：缺失 ServiceImplTest + ControllerWebSliceTest
- 验收标准：
  - [x] CourseDownloadAssetServiceImplTest、CourseAssetReferenceServiceImplTest
  - [x] CoursePublish/Offering/Enrollment/Learning/DownloadAsset/AssetReference Controller WebSlice
  - [x] AssetControllerWebSliceTest（或并入 AssetService 已有覆盖时至少 Controller 包装）
  - [x] `mvn -pl sts-service-admin test` 相关用例通过
- 测试方式：零 DB

## Task 2：带 DB 集成测试（Testcontainers）

- 状态：已实施
- Owner：AI
- 输出：MapperIT（必要时薄服务 IT）
- 验收标准：
  - [x] CourseVersionMapperIT（uk_course_version）
  - [x] CourseLiveSessionMapperIT（uk_course_item_deleted 1:1）
  - [x] CourseDownloadAssetMapperIT（uk_course_asset_deleted + 逻辑删除复用）
  - [x] CourseOfferingMapperIT（uk_org_offering_code_deleted）
  - [x] CourseEnrollmentMapperIT（uk_offering_user_deleted）
  - [x] CourseStudySessionMapperIT（uk_enrollment_trace_deleted）
  - [x] AssetMapperIT（uk_org_asset_code_deleted）
  - [x] `mvn -pl sts-service-admin -Pintegration-test verify` 新增 IT 通过（需 Docker）
- 测试方式：真 MySQL

## Task 3：对齐系统管理测试标准（Persistence + Mapper 深度 + 逻辑删除）

- 状态：已实施
- Owner：AI
- 输入：系统管理样例 `MenuCrudPersistenceIT`、`LogicDeleteServiceIT`、`SysUserMapperIT`（往返/分页/乐观锁）
- 输出：与系统管理同等层次的课程/素材 IT
- 验收标准：
  - [x] `CourseMapperIT`（或并列类）补齐：insert 往返、分页拦截器、乐观锁 0 行 / `updateByIdWithVersionCheck` 409（对齐
    SysUserMapperIT）
  - [x] `CourseLogicDeleteServiceIT`：`removeById` → `deleted=主键`，`selectById` 不可见，软删后编码可复用（对齐
    LogicDeleteServiceIT）
  - [x] `CourseCategoryCrudPersistenceIT`：Service 真库新增/乐观锁更新/逻辑删除 + 有子分类删除护栏（对齐
    MenuCrudPersistenceIT）
  - [x] `CoursePublishPersistenceIT`：真库首次发布写 version、幂等不插第二行、内容变更后 version_no 递增
  - [x] `CourseOfferingPublishPersistenceIT`：真库发布开课冻结 `course_version_id`；无课程版本时拒绝
  - [x] `AssetMapperIT` 补齐：insert 往返 + 乐观锁（若尚未覆盖）
  - [x] `mvn -pl sts-service-admin -Pintegration-test verify` 新增用例通过
- 测试方式：真 MySQL + 真实 Service（TestConfiguration 装配）
