# 设计

## 策略与快照

`ExamPolicy` 新增 `isAnticheatEnabled`，默认关闭。发布考试时该字段与现有水印字段一起写入执行策略快照；学员作答详情从冻结快照返回两个只读开关，避免发布后编辑考试改变进行中的作答规则。

## 答题端

PC 与移动端仅在 `isAnticheatEnabled=1` 时注册或触发切离、复制/剪贴板与右键事件审计。事件继续使用既有反作弊事件接口与 `RECORD` 动作，不触发强制交卷。两端在 `isWatermarkEnabled=1` 时使用当前登录用户信息生成重复背景水印，不携带正确答案、成绩或其他敏感信息。

## 管理端行为证据

批阅页不再将可空的 `evidenceFileId` 裸值作为一列。页面解析既有 `clientPayloadJson` 中的 `source`，将窗口失焦、页面隐藏、复制、剪切、粘贴及右键菜单映射为可读行为证据；“查看详情”展示来源、原始载荷和附件关联状态。未新增截图、录像或文件采集能力，真实附件存在时仅标识其 ID。

## 策略可见性

- `isScoreVisible=0`：隐藏正式成绩与及格结论。
- `answerDetailMode=SUBMITTED`：交卷后返回标准答案和逐题评分；`PUBLISHED`：成绩发布后返回；`NEVER`：始终裁剪。
- `isAnalysisVisible=0`：即使答案详情可见也裁剪解析；为 1 时才返回并渲染解析。

## 数据与契约

新增 `sts_exam_policy.is_anticheat_enabled` 非空列，默认 0；同步完整 init SQL、Flyway、实体、DTO、VO、OpenAPI、执行快照和培训考试快照适配。已有记录按关闭处理。

### 延期策略列标注

`sts_exam_policy` 中以下列保留，不删除列、不清理历史值，但数据库 COMMENT 统一标注为“暂时隐藏”：

- `switch_limit`
- `is_split_screen_force_submit`
- `is_copy_refresh_devtool_disabled`
- `inactivity_seconds`
- `inactivity_countdown_seconds`
- `minimum_answer_seconds`
- `pass_point`
- `fail_point`
- `absent_point`
- `late_forbid_seconds`
- `is_ranking_enabled`

注释格式为：`【暂时隐藏】原用于：<原用途>；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change`。同步最新
init SQL、新增 Admin Flyway migration，并递增 `sts.init.schema-version`。本任务不删除既有 Java/API 兼容字段。
