# Design：以既有培训结果作为正式档案前置

## 决策

### D1：归档只消费既有业务事实

归档输入保持为计划状态、参训人结果、计划/机构快照、评价、奖惩、证书和附件。`ATTENDANCE` 从归档必需事实集合中移除；不引入任何替代字段或默认值。

### D2：保留真实结果完整性阻塞

当计划为 `ENDED` 且任一参训人为 `INCOMPLETE` 时，归档查询应返回 `missingItems: ["RESULTS"]`，创建与文件下载继续返回 `409/9204`。当没有缺失项时，沿用既有归档版本、真实文件存储和受控下载实现。

### D3：归档版本持久化正式文件

归档生成后，将 `GeneratedFileStore#store` 返回的真实 `fileInfoId` 写入当前 `sts_archive_version.file_info_id`。查询档案时按计划和当前版本回读该字段，下载端点继续调用既有 `GeneratedFileStore#open`，不复制、重建或伪造文件。

### D4：契约和 PC 以服务端缺失项为准

`archive.openapi.json` 删除对“签到”等必需事实和 `ATTENDANCE` example 的承诺，保留 `missingItems` 通用语义及 9204。PC 不再将 `ATTENDANCE` 视为业务前置，仍在 `missingItems` 非空或无 `fileInfoId` 时禁用下载并显示服务端返回项。

## 数据与权限

- 新增 `sts_archive_version.file_info_id`，仅关联当前归档版本生成的受控文件；同步 Flyway migration、init schema 和 `ArchiveVersion` Entity。
- 无新增/变更权限、菜单、HTTP 路径、DTO 或 VO 字段。
- 延用 `archive:training:create`、`archive:training:list` 与既有租户隔离。

## 实施与测试顺序

1. Red：归档服务定向测试先断言完整结果的 ENDED 计划不含 `ATTENDANCE`、当前版本文件 ID 可回读；不完整结果仍为 9204/`RESULTS`。
2. Green：最小修改 `ArchiveServiceImpl` 的缺失项组装和归档版本文件 ID 持久化；同步 OpenAPI 与 PC 文案/断言。
3. IT：覆盖 Flyway 新列、归档版本文件 ID 回读、租户隔离和 ENDED 培训纵向归档。Docker 或真实对象存储不可用时如实记录，不能把单测或 mock IT 视为真实下载验收。

## 风险控制

- 不把“出勤”改名为其他隐藏前置；业务规则只以教师评分和既有结果投影为准。
- 不放宽 `ENDED`、结果完整性、权限或租户校验。
- 对已生成档案不重写历史版本；仅使此前被伪前置阻塞、且满足真实条件的计划能够首次归档。
