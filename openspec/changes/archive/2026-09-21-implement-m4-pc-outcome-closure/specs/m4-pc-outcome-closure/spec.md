## ADDED Requirements

### Requirement: PC 成果入口只消费已确认的 M4 契约

系统 SHALL 基于当前 OpenAPI 提供管理员问卷、结束成果、专家查课、档案、证书和学员本人证书流程。系统 SHALL 展示服务端返回的缺失事实、权限错误和状态，系统 SHALL NOT 用默认值、Mock 文件或占位交付宣称完成。

#### Scenario: 档案下载状态由服务端事实决定

- **WHEN** 管理员从已结束培训进入档案页面
- **THEN** 系统展示服务端 `missingItems` 和 `fileInfoId`
- **AND** 缺失事实非空或文件 ID 为空时禁用下载
- **AND** 条件满足时调用既有正式文件下载端点
