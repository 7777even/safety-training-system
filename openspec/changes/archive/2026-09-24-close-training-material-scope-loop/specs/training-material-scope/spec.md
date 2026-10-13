## ADDED Requirements

### Requirement: 培训资料类型必须收敛为四类

系统 MUST 将培训资料类型收敛为 `GENERAL`（一般资料）、`IMAGE`（图片）、`VIDEO`（视频）、`EVALUATION`（评价佐证）。
系统 MUST NOT 再接受 `REWARD_PENALTY` 作为资料类型，新增或编辑提交该类型时 MUST 拒绝且不写入任何资料记录。

#### Scenario: 提交已收口的资料类型

- **GIVEN** 一个 `RUNNING` 培训计划与具备 `training:attachment:add` 权限的管理员
- **WHEN** 管理员提交 `attachmentType` 为 `REWARD_PENALTY` 的资料
- **THEN** 服务端拒绝请求并返回「培训资料类型无效」
- **AND** 资料列表不新增任何记录

#### Scenario: 四类资料正常维护

- **GIVEN** 一个 `RUNNING` 培训计划
- **WHEN** 管理员分别维护 `GENERAL`、`IMAGE`、`VIDEO`、`EVALUATION` 四类资料
- **THEN** 四类资料均可保存、回读、编辑与删除

### Requirement: 奖惩佐证必须由奖惩记录承载

系统 MUST 将奖惩佐证收敛为奖惩记录自身的佐证文件这一条通道。
培训资料能力 MUST NOT 提供奖惩佐证类型，奖惩佐证的受控读取 MUST 经奖惩记录定位，MUST NOT 复制文件或新增存储。

#### Scenario: 佐证只走奖惩记录

- **GIVEN** 一个 `RUNNING` 培训计划与一名本计划参训人
- **WHEN** 管理员在奖惩记录上关联佐证文件
- **THEN** 佐证随奖惩记录保存并可受控回读预览
- **AND** 该文件不出现在培训资料列表中

### Requirement: 档案与学员侧只采信启用资料

培训资料的 `status` 为 `1` 表示启用、`0` 表示停用。系统 MUST 在档案三视角查询、归档素材登记与学员侧读取中
只取 `status = 1` 的资料；影像记录、附件清单与资料相关统计 MUST 使用同一过滤条件。
管理端工作台 MUST 继续展示停用资料并允许改回启用，停用资料 MUST NOT 因本口径被删除。

#### Scenario: 停用资料退出档案事实

- **GIVEN** 一个已结束培训的档案中存在一条启用资料
- **WHEN** 管理员在计划工作台把该资料停用
- **THEN** 档案的影像记录或附件清单不再包含该资料
- **AND** 影像、附件与资料合计统计同步减少
- **AND** 工作台仍可见该资料并可将它改回启用

#### Scenario: 重新启用恢复档案事实

- **GIVEN** 一条已被停用的培训资料
- **WHEN** 管理员把它改回启用
- **THEN** 档案三视角与资料相关统计重新包含该资料
