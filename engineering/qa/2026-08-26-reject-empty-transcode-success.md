# 素材转码空产物成功修复 QA（2026-08-26）

OpenSpec Change：`2026-08-26-reject-empty-transcode-success` Task 1。Owner：lims。仅后端；未修改前端、权限或数据库结构；
`asset.openapi.json` 同步新增 `PROVIDER_DISABLED` 失败码说明，不改变接口结构。

## 验收结果

| 验收项           | 结果 | 证据                                                                                                    |
|---------------|----|-------------------------------------------------------------------------------------------------------|
| Stub 不再假返回成功  | 通过 | `StubTranscodeProvider` 直接抛出 `PROVIDER_DISABLED`，原因说明当前环境未启用真实转码；Processor 测试验证输出错误日志并调用失败落库，不调用成功提交。 |
| 配置未启用与执行异常可区分 | 通过 | Stub 使用 `PROVIDER_DISABLED`；真实 Provider 空产物/缺产物使用 `TRANSCODE_ERROR`；OpenAPI 已同步语义。                    |
| 空视频产物拒绝成功     | 通过 | `TranscodeResultServiceTest.rejectsVideoSuccessWithoutAnyArtifacts`。                                  |
| 缺 HLS 或封面拒绝成功 | 通过 | 两个缺失类型测试验证在文件、派生和终态写入前失败。                                                                             |
| 完整视频结果保持成功    | 通过 | HLS + THUMBNAIL 测试验证两条派生登记及 Job/Asset 成功提交。                                                           |
| 既有失败竞态不回归     | 通过 | 原有“超时不得覆盖已成功任务”和“赢得失败转换后更新素材”测试保留并通过。                                                                 |

## TDD 记录

- Red：结果护栏新增后 3 个用例失败，旧实现会继续调用成功提交；stub 编排用例证明其仍返回空结果；失败码区分测试随后证明旧实现仍使用通用
  `TRANSCODE_ERROR`。
- Green：在统一结果提交边界校验视频派生类型；stub 改为直接抛出 `PROVIDER_DISABLED` 和明确中文原因。
- Refactor：仅提取必需类型常量与校验方法，恢复并保留原有竞态测试，没有重构 Provider、调度器或存储链路。

## 实际验证

```text
mvn -pl sts-service-admin -am -Dtest=TranscodeResultServiceTest,TranscodeJobProcessorTest -Dsurefire.failIfNoSpecifiedTests=false test
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0

mvn -pl sts-service-admin -am test
Tests run: 691, Failures: 0, Errors: 0, Skipped: 1
```

跳过项为既有 qpdf 条件测试。本 Change 未修改 Entity、Mapper、自定义 SQL、schema、索引、逻辑删除、分页、乐观锁或真实持久化事务，未运行
MySQL IT。

## Review

- 规格符合性：通过。空产物和不完整视频不再提交成功；异常由后台边界记录日志和失败终态。
- 代码质量：通过。校验先于任何结果落库；错误原因不包含对象路径或凭据；无越界修改、无新增依赖、无 API 结构/数据库/前端变化。
- 线上历史数据：未处理；任务 27、28、30 等既有伪成功记录仍需开发人员决定是否人工修正或重新创建任务。
