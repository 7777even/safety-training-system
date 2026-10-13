# 规格增量

## Requirement：独立考试防作弊开关 SHALL 控制事件审计

管理员可在独立考试策略中配置 `isAnticheatEnabled`。该值与水印配置一同冻结进考试执行快照，学员答题端不得读取发布后可变的策略。

### Scenario：开关关闭

- **WHEN** 学员在关闭防作弊的独立考试答题期间切离页面
- **THEN** 客户端 SHALL 不提交切离事件
- **AND** SHALL 不强制交卷。

### Scenario：开关开启

- **WHEN** 学员在启用防作弊的独立考试答题期间切离页面
- **THEN** 客户端 SHALL 追加审计事件，动作固定为 `RECORD`
- **AND** SHALL 不因该事件自动交卷。

### Scenario：剪贴板与右键操作

- **WHEN** 学员在启用防作弊的独立考试答题期间进行复制、剪切、粘贴或打开右键菜单
- **THEN** PC 与移动 H5 客户端 SHALL 阻止该操作，并追加类型为 `COPY` 的审计事件，动作固定为 `RECORD`
- **AND** 答题输入框 SHALL 保持可输入和可编辑。

## Requirement：独立考试水印 SHALL 在学员答题端生效

### Scenario：水印开启

- **WHEN** 冻结策略的 `isWatermarkEnabled` 为 1
- **THEN** PC 与移动答题页 SHALL 叠加当前学员身份水印。

## Requirement：管理端 SHALL 展示可读的反作弊行为证据

### Scenario：客户端事件无附件

- **WHEN** 防作弊事件仅含 `clientPayloadJson` 且 `evidenceFileId` 为空
- **THEN** 批阅页 SHALL 依据事件来源展示可读行为证据，并提供详情查看入口
- **AND** SHALL 不显示空白的“证据文件 ID”列。

### Scenario：事件关联真实附件

- **WHEN** 防作弊事件存在 `evidenceFileId`
- **THEN** 批阅页 SHALL 在行为证据详情中标识该附件 ID
- **AND** SHALL 不将客户端事件数据伪造为附件文件。

## Requirement：学员端 SHALL 分别消费考试结果可见性策略

### Scenario：交卷后查看答案详情

- **WHEN** `answerDetailMode=SUBMITTED` 且学员已交卷
- **THEN** 系统 SHALL 返回允许的正确答案与逐题评分，不以成绩发布为前置条件。

### Scenario：解析和成绩独立裁剪

- **WHEN** `isAnalysisVisible=0` 或 `isScoreVisible=0`
- **THEN** 系统 SHALL 分别裁剪解析或正式成绩，并且学员端不得渲染被裁剪内容。

## Requirement：延期考试策略列 SHALL 明确标注为暂时隐藏

当前中山 50 最小闭环不启用的考试策略 SHALL 保留数据库列及历史值，并通过列 COMMENT 明确其原用途、当前不读取/不写入/不展示以及重新启用必须新建并确认
OpenSpec Change。

### Scenario：检查延期策略数据库资产

- **WHEN** 从最新 init SQL 初始化数据库或在既有数据库执行本次 migration
- **THEN** 11 个延期策略列 SHALL 继续存在且类型和数据不变
- **AND** 每个列 COMMENT SHALL 使用统一“【暂时隐藏】”格式
- **AND** 水印、防作弊总开关、端侧、补考及结果可见性列 SHALL 不受影响。
