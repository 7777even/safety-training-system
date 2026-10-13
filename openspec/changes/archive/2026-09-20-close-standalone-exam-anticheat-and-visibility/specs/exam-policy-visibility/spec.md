## ADDED Requirements

### Requirement: 延期考试策略列必须明确标注为暂时隐藏

当前中山 50 最小闭环不启用的考试策略 MUST 保留数据库列及历史值，并通过列 COMMENT 明确其原用途、当前不读取/不写入/不展示以及重新启用必须新建并确认
OpenSpec Change。

#### Scenario: 检查延期策略数据库资产

- **WHEN** 从最新 init SQL 初始化数据库或在既有数据库执行本次 migration
- **THEN** 11 个延期策略列继续存在且类型和数据不变
- **AND** 每个列 COMMENT 使用统一“【暂时隐藏】”格式
- **AND** 水印、防作弊总开关、端侧、补考及结果可见性列不受影响
