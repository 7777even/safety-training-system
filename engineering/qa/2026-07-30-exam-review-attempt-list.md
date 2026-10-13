# QA：考试批阅任务与作答成绩分页

日期：2026-07-30  
Owner：lims  
OpenSpec：`2026-07-30-add-exam-review-attempt-list-apis`

## 验收范围

- 教师本人批阅任务分页与批阅人隔离。
- 管理端考试作答成绩分页与机构数据范围。
- 批阅状态、成绩发布状态、学员展示信息和分页参数。
- `docs/api/exam.openapi.json` 契约。

## TDD 记录

Red：先新增 `ExamAttemptListControllerTest`、`ExamAttemptQueryServiceImplTest`，首次编译因查询 DTO、VO、Mapper 和 Service
尚不存在而失败。  
Green：补充最小 Controller、Service、Mapper 聚合 SQL、DTO/VO 和 OpenAPI 后，目标测试通过。

## 验证结果

1. 目标零 DB：
  - 命令：
    `mvn -pl sts-service-admin -am -Dtest=ExamAttemptListControllerTest,ExamAttemptQueryServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false -DargLine=-javaagent:/home/ss/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.8/byte-buddy-agent-1.17.8.jar test`
  - 结果：5 tests，0 failure，0 error。
2. Admin 全量零 DB：
  - 命令：
    `mvn -pl sts-service-admin -am -DargLine=-javaagent:/home/ss/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.8/byte-buddy-agent-1.17.8.jar test`
  - 结果：384 tests，0 failure，0 error。
3. 编译打包：
  - 命令：`mvn -pl sts-service-admin -am -DskipTests package`
  - 结果：BUILD SUCCESS。
4. MySQL IT：
  - 命令：
    `mvn -pl sts-service-admin -am -Pintegration-test -Dtest=ExamAttemptQueryMapperIT -Dsurefire.failIfNoSpecifiedTests=false test`
  - 结果：环境阻塞。Testcontainers 报告没有可用 Docker environment，MySQL 容器未启动；不能以零 DB 结果替代。
5. 契约与文档：
  - `jq empty docs/api/exam.openapi.json`：通过。
  - `openspec validate 2026-07-30-add-exam-review-attempt-list-apis --strict`：通过。
  - `git diff --check`：通过。

## Review

- 规格符合性：通过。两个分页接口、字段、权限复用、机构范围与无数据库变更均在确认范围内。
- 代码质量：通过。查询使用数据库分页和聚合 SQL，无列表 N+1；教师查询强制当前登录 reviewer；管理查询复用数据范围并对空机构范围安全回退。
- 未决验证：需在具备 Docker 的环境重跑 `ExamAttemptQueryMapperIT`。
