# QA：学员课程简介与受控附件下载

日期：2026-08-13  
协作身份：`git user.name=lims`（仅后端与 OpenAPI）

## 验收范围

- 扩展 `GET /api/course/learning/enrollments/{enrollmentId}`，聚合冻结课程简介与附件元数据。
- 新增 `GET /api/course/learning/enrollments/{enrollmentId}/download-assets/{assetId}/file`。
- 报名本人、报名/开课状态、冻结课程下载模式、冻结附件归属、当前机构素材和源文件校验。
- 受控文件流的安全文件名、MIME、完整请求、单 Range、404 与 416。
- `docs/api/course.openapi.json` 的真实 Controller tag、字段、示例、权限和 5087～5089 错误语义。

不包含前端页面、Mock、联调、数据库结构或课程版本快照 schema 变更。

## TDD 证据

- Red：新增测试首次因 `CourseLearningCourseVO`、`LearnerCourseDownloadAssetVO`、资源组装器、下载 Service/Controller 和
  `AssetMediaAccessService.sourceFile` 尚不存在而在 `testCompile` 失败。
- Green：先以最小实现使 32 个目标测试通过；Review 修正存储能力、错误示例和状态分支后，最终目标测试 40 个通过。
- Refactor：附件素材与文件各执行一次批量查询并按冻结快照原序组装；下载底层增加原始源文件二次关联校验。

## 功能与安全验收

| 验收项                    | 结果 | 证据                                                                     |
|------------------------|----|------------------------------------------------------------------------|
| 本人详情聚合冻结课程简介           | 通过 | `CourseLearningServiceImplTest`、`CourseLearningControllerWebSliceTest` |
| 冻结附件保序及当前素材/文件补充       | 通过 | `LearnerCourseResourceAssemblerTest`                                   |
| 稳定不可用原因及非 MinIO 不伪造地址  | 通过 | 下载模式、附件/素材状态、处理状态、来源、文件和存储能力测试                                         |
| 非本人短路且不读取资源            | 通过 | `LearnerCourseDownloadServiceTest`                                     |
| 冻结附件归属与学习状态            | 通过 | 5087、5088 分支测试                                                         |
| 原始源文件二次关联与存储 404       | 通过 | `AssetMediaAccessServiceSourceFileTest`                                |
| 文件流 200/206/416 与安全文件名 | 通过 | `CourseLearningDownloadControllerTest`                                 |
| 真实 403/404 Result 错误外壳 | 通过 | `CourseLearningDownloadControllerWebSliceTest`                         |
| 不授予后台课程/素材权限           | 通过 | Controller 无管理端权限注解；OpenAPI `x-permission=authenticated`               |

## 实际测试

### 目标零 DB

```text
cd backend
mvn -pl sts-service-admin -am \
  -Dtest=CourseLearningServiceImplTest,CourseLearnerReadingServiceImplTest,\
CourseLearningControllerWebSliceTest,LearnerCourseResourceAssemblerTest,\
LearnerCourseDownloadServiceTest,CourseLearningDownloadControllerTest,\
CourseLearningDownloadControllerWebSliceTest,AssetMediaAccessServiceSourceFileTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：`Tests run: 40, Failures: 0, Errors: 0, Skipped: 0`。

### 全量零 DB

```text
cd backend
mvn -pl sts-service-admin -am test
```

结果：`Tests run: 585, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。

### 带 DB

未运行。原因：本 Change 未修改 Entity、Mapper、自定义 SQL、schema、索引、逻辑删除、分页、乐观锁或真实持久化事务；按 Admin
两层测试规则不机械重复零 DB 行为。

### 文档与规格

```text
jq empty docs/api/course.openapi.json
openspec validate 2026-08-13-add-learner-course-detail-download
git diff --check
```

结果：全部通过。

## 双轴 Review

- 规格符合性：通过。冻结事实、当前事实、`assetId` 标识、本人边界、存储状态、OpenAPI 和 lims 范围均符合已确认 Change。
- 代码质量：通过。租户隔离、授权顺序、二次文件关联、TOCTOU 缓解、批量查询、Range、文件名和异常边界未发现剩余重要问题。

## 结论

Task 1 的后端、OpenAPI、测试和 Review 全部通过，可交付前端按 `docs/api/course.openapi.json` 另开实施。

