# QA：学员课程入口与学习任务读取接口

日期：2026-07-27  
协作身份：`git user.name=lims`（仅后端与 OpenAPI）

## 验收范围

- `GET /api/course/learning/my-enrollments`
- `GET /api/course/learning/offerings`
- `GET /api/course/learning/enrollments/{enrollmentId}/tasks`
- 当前机构隔离、当前登录人报名隔离、参与范围、自助报名资格、冻结课程版本和内容项进度组装。
- `docs/api/course.openapi.json` 的路径、参数、响应字段、错误码、示例和 Controller tag。

不包含前端页面、Mock、UI、前端联调、报名容量、素材转码、播放/预览地址和章节顺序解锁。

## TDD 证据

### Task 1

- Red：新增课程入口测试首次因 Learner Query/VO 不存在而在 testCompile 失败。
- Green：实现“我的课程”和“可报名课程”读取后，目标零 DB 11 个通过。
- IT：`CourseEnrollmentMapperIT` 增加本人及学习状态真实分页隔离用例。

### Task 2

- Red：新增学习任务测试首次因 `LearnerCourseTasksVO` 和服务方法不存在而在 testCompile 失败。
- Green：实现冻结快照与内容项进度左连接后，目标零 DB 15 个通过。
- IT：报名和课程版本 Mapper 真库用例通过。

## 功能验收

| 验收项          | 结果 | 证据                                                   |
|--------------|----|------------------------------------------------------|
| 我的课程只查询本人    | 通过 | 查询强制当前机构和 `StpUtil.getLoginIdAsLong()`，无 `userId` 入参 |
| 我的课程分页和状态过滤  | 通过 | Controller/Service 测试；`CourseEnrollmentMapperIT`     |
| 课程展示字段读取冻结版本 | 通过 | `CourseLearnerReadingServiceImplTest`                |
| 可报名开课过滤      | 通过 | 发布、启用、有效期、OPEN/LINK、自助允许、参与范围和已有报名过滤测试               |
| 不虚构报名余量      | 通过 | 契约只返回 `enrollmentAvailability=AVAILABLE`             |
| 学习任务仅本人访问    | 通过 | 非本人返回业务码 5081，且不继续读取开课                               |
| 章节和内容项来自冻结快照 | 通过 | 冻结 JSON 组装测试；快照异常使用 5083                             |
| 无进度任务仍返回     | 通过 | 默认 `NOT_STARTED`、`studySeconds=0`                    |
| 可学习状态        | 通过 | 停用内容和过期报名测试；取消/退选规则由同一状态映射实现                         |
| 必要引用         | 通过 | 素材 ID、考试 ID、直播冻结引用进入独立 VO                            |

## 实际测试

### 全量零 DB

```text
cd backend
mvn -pl sts-service-admin -am test
```

结果：`Tests run: 370, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。

### 完整带 DB

```text
cd backend
mvn -pl sts-service-admin -am -Pintegration-test test
```

结果：`Tests run: 85, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。  
环境：Testcontainers MySQL 8，Docker 可用。

### 文档与规格

```text
jq empty docs/api/course.openapi.json
openspec validate 2026-07-27-add-learner-course-reading-apis
git diff --check
```

结果：全部通过。

## 双轴 Review

### 规格符合性

结论：通过。

- 只实现已确认的三个学员读取接口，没有修改报名、退选、学习完成或评价规则。
- 不接受外部 `userId`，本人边界和参与范围由后端执行。
- 展示结构来自冻结版本，未回退读取可变课程草稿。
- 未修改数据库、权限模型或前端。

### 代码质量

结论：通过。

- Controller 仅负责参数接收和委托；规则集中在 Service。
- Query/VO 与管理端模型分离，不暴露 Entity。
- 可报名列表在资格过滤后分页，`total` 与实际可报名集合一致。
- 删除了 Review 中发现的不必要 O(n²) 版本查找。
- 无新增依赖、无无关重构、无数据库 migration。

## 结论

三个学员课程读取接口的后端实现、OpenAPI、测试和 Review 均通过，可交付前端按
`docs/api/course.openapi.json` 实施。COURSE-07 的前端页面仍由非 `lims` 协作方另开。

