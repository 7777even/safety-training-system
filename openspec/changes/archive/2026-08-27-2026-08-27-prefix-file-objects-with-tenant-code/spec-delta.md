# Spec Delta：文件对象统一增加租户编码根目录

## ADDED Requirements

### Requirement: 所有新文件对象必须按机构编码分配根目录

系统 MUST 使用当前租户不可变的机构编码作为所有新文件对象键的首个路径段，并在该租户根目录内保持既有业务目录和命名规则。机构编码
MUST
由服务端租户上下文解析，MUST NOT 接受调用方指定或覆盖。

#### Scenario: 两个机构上传文件

- **WHEN** 两个不同机构分别上传或生成文件对象
- **THEN** 每个新对象分别写入本机构编码对应的根目录
- **AND** 两个机构的对象键不会共享同一个根目录

#### Scenario: 生成不同类型的新对象

- **WHEN** 当前机构上传素材源文件、课程封面、通用文件或生成转码派生文件
- **THEN** 对象键分别以 `{ORG_CODE}/source/`、`{ORG_CODE}/course-cover/`、`{ORG_CODE}/{原业务目录}/` 或
  `{ORG_CODE}/derivative/` 开头
- **AND** 各业务目录内部的年月、资源 ID、任务 ID 和文件命名规则保持不变

#### Scenario: 租户编码不可可靠解析

- **WHEN** 写入文件时租户上下文缺失、机构不存在或机构编码不符合既有安全格式
- **THEN** 系统拒绝对象写入
- **AND** 不得退化为无租户前缀、默认租户前缀或由客户端提供的前缀

### Requirement: 历史无租户前缀对象必须保持兼容

系统 MUST 将数据库和任务中已经保存的完整对象键作为历史读取与清理的真相源。系统 MUST NOT 为历史键自动增加租户前缀，MUST NOT
迁移、重命名、复制、删除或批量改写历史对象及引用。

#### Scenario: 读取历史源文件或课程封面

- **WHEN** 素材或课程仍关联历史 `source/...`、`course-cover/...` 或其他无租户前缀对象键
- **THEN** 系统按原始保存键读取或下载文件
- **AND** 不修改数据库引用或对象键

#### Scenario: 播放历史派生文件

- **WHEN** 已完成素材仍关联历史 `derivative/...` manifest 和 segment
- **THEN** 系统以已登记 manifest 为锚点读取同一历史目录下的派生文件
- **AND** 不把当前机构编码重复拼接到历史路径

#### Scenario: 读取新派生文件

- **WHEN** 已完成素材关联 `{ORG_CODE}/derivative/...` manifest
- **THEN** 系统从该已登记目录读取对应 segment 或预览文件
- **AND** 不依赖客户端传递机构编码

## MODIFIED Requirements

### Requirement: 课程封面对象键使用租户根目录和年月层级

系统 MUST 将新课程封面写入 `{ORG_CODE}/course-cover/{yyyy}/{MM}/{uuid}.{ext}`。历史
`course-cover/{yyyy}/{MM}/{uuid}.{ext}` 继续按原键读取；`source`、`derivative` 和其他业务目录只增加机构编码根目录，不改变其既有内部层级。

#### Scenario: 当前机构上传新课程封面

- **WHEN** 当前机构上传合法课程封面
- **THEN** 新对象首段为该机构不可变编码，其后为 `course-cover/{yyyy}/{MM}/{uuid}.{ext}`
- **AND** 不增加日目录，不影响历史课程封面引用

