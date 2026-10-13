# QA：统一课程源文件下载模式

日期：2026-08-14  
协作身份：`git user.name=lims`（仅后端与 OpenAPI）  
OpenSpec：`2026-08-14-fix-course-source-download-mode`

## 验收范围

- 学员课程详情与实际附件下载统一使用课程既有枚举 `NONE`、`ALL`、`PARTIAL`。
- `NONE` 禁止下载；`ALL`、`PARTIAL` 进入冻结附件归属及附件有效性校验。
- 清理后端成功测试夹具与学员课程 OpenAPI 中虚构的 `ALLOW` 值。
- 保持报名本人、报名/开课状态、冻结附件归属、素材、源文件和存储校验不变。

不包含前端页面、Mock、联调、数据库结构、存量数据或课程版本快照 schema 变更。

## TDD 证据

- Red：先把成功夹具改为真实的 `ALL`、`PARTIAL`，目标 23 个用例运行结果为 9 failure、1 error；成功路径被旧实现以
  `SOURCE_DOWNLOAD_DISABLED` / 5087 拒绝，后续附件状态分支也被错误课程门禁提前截断。
- Green：增加共享 `CourseSourceDownloadPolicy`，仅允许 `ALL`、`PARTIAL` 通过课程级门禁；目标 23/23 通过。
- Refactor：两处下载判断复用同一策略，并补显式空值失败关闭，未改动后续授权顺序。
- 按人工要求未增加 `ALLOW` 专项回归用例；错误夹具和契约值直接清理。

## 功能与安全验收

| 验收项                     | 结果 | 证据                                                                      |
|-------------------------|----|-------------------------------------------------------------------------|
| `ALL` 详情状态与实际下载         | 通过 | `LearnerCourseResourceAssemblerTest`、`LearnerCourseDownloadServiceTest` |
| `PARTIAL` 冻结清单附件状态与实际下载 | 通过 | 同上成功路径同时覆盖 `ALL/PARTIAL`                                                |
| `NONE` 继续拒绝下载           | 通过 | `sourceDownloadDisabled` 用例                                             |
| 冻结归属与附件失效分支不被提前截断       | 通过 | 5088、5089、素材状态及不可用原因用例                                                  |
| 空值和未知值失败关闭              | 通过 | 共享允许集合白名单及显式 null 保护                                                    |
| OpenAPI 与创建/更新枚举一致      | 通过 | 学员课程 schema 与示例统一为 `NONE/ALL/PARTIAL`                                   |

## 实际测试

### 目标零 DB

```text
cd backend
mvn -pl sts-service-admin -am test \
  -Dtest=LearnerCourseResourceAssemblerTest,LearnerCourseDownloadServiceTest,CourseLearningServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：`Tests run: 23, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。

### 全量零 DB

```text
cd backend
mvn -pl sts-service-admin -am test
```

结果：`Tests run: 609, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。

### 带 DB

未运行。本 Change 未修改 Entity、Mapper、自定义 SQL、schema、索引、逻辑删除、分页、乐观锁或持久化事务，按 Admin 两层测试规则
无需机械重复零 DB 行为。

### 文档与规格

```text
jq empty docs/api/course.openapi.json
openspec validate 2026-08-14-fix-course-source-download-mode --strict
git diff --check
```

结果：全部通过；后端生产代码、测试和课程 OpenAPI 中已无 `sourceDownloadMode=ALLOW` 或 `DOWNLOAD_ALLOW`。

## 双轴 Review

- 规格符合性：通过。严格实现已确认 Task；`NONE/ALL/PARTIAL` 与课程创建、更新、快照、详情和下载接口一致，未扩大冻结附件授权边界。
- 代码质量：通过。两处判断复用共享白名单，空值失败关闭；未发现越界改动、无关重构、新依赖、权限变化、数据库变化或前端改动。

## 结论

Task 1 的后端、OpenAPI、测试、规格和双轴 Review 全部通过，可以交付。

