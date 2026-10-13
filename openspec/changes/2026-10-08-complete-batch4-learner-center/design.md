# 已确认设计

## T1 首页与站内消息
首页从本人报名、考试/测试、培训任务读取，保持原领域动作和可见性。消息包含业务引用，不接收任意跳转URL。课程上下架发给已有报名/关注学员，任务发给实际人员。新消息与业务同事务；提交后才能在线可见。消息和接收人使用sts_common_message/recipient，租户隔离和唯一事件键保证幂等。

考试开考提醒；未完成且有截止时间的课程、考试、培训在0<剩余时间<=24小时窗口提醒一次，包括首次扫描已进入窗口。取消、结束和完成任务不提醒。当前有效分配任务补一次通知，不重建历史状态通知。

SSE使用Spring MVC与Authorization流式请求；3秒按在线接收人分批查询持久化消息计数，变化时发送messages事件，两端重新读取消息与首页；不以自增ID假定事务提交顺序。心跳与重连补偿，不增加依赖。异步/定时读取显式绑定租户；鉴权过期关闭连接。失效业务仍保留消息，跳转重新鉴权。首页一次聚合已安排的考核（含独立练习），原考核中心范围不变；CONTINUE携带本人attempt进入原作答页。

### T1验收发现：本人考试须知API补充已确认

现有PC/H5须知读取GET /api/exam/exams/{examId}，要求exam:exam:list管理权限；普通学员START跳转不能闭环。2026-10-08用户在本会话明确确认补充本人须知接口并继续完成第四批；原管理接口权限不变。

新增GET /api/exam/learner/exams/{examId}/notice，沿用exam:learner:task:list。身份从会话获取，以本机构、本人考生及对应独立考核执行关系校验；不存在/取消/无本人关系拒绝读取。仅返回examId、examName、examType、openStartTime、openEndTime、durationSeconds、passRatio、passScore、isNoticeEnabled、noticeContent，禁止题目/答案、候选人名单及管理字段。两端须知替换管理读取；同步exam OpenAPI、DTO/VO、Mock、本人/非本人/跨机构测试及真实普通学员START验收。

## T2 本人资料与改密
仅姓名/手机号/邮箱可写，版本冲突保护；专用VO返回本人真实手机号，管理端脱敏不变。改密旧/新密码均为SHA-256摘要，服务端复用BCrypt，禁止记录密码或摘要，提交成功撤销账号会话。沿用superAdmin保护和无local凭证错误。历史身份快照不随改名更新。

## T3 历史与更正
统一本人课程、正式考试、随堂测试、个人练习/模拟、培训历史，不使用有效任务过滤抹掉结束或退出记录，按原结果可见策略裁剪。更正只追加档案展示记录，原事实/答卷/冻结结果/文件/证书不变。保存字段前后值、理由、操作者、时间、版本；禁止覆盖与并发旧版本。

专用archive:history:correct和archive:history:correction:list不自动授权现有角色，同机构并要求原记录读取权限。学员只能看本人历史及留痕。展示明确区分原始值与更正值，不重新计算正式结果。

## 契约与数据同步
新增learner契约：首页/tasks、messages、messages/stream、history及history/{recordType}/{recordId}。system-admin新增GET/PUT users/my-profile、PUT users/my-password；archive新增POST/GET persons/{userId}/history/{recordType}/{recordId}/corrections。均在/api下。分页沿用PageRequest/PageResult，本人接口不接受userId。字段中文描述、examples、分组、权限、错误码与代码一致。

每个Task独立同步init、增量Flyway、schema-version、Entity与契约；不执行生产数据库操作。

## 2026-10-09 首页培训跳转缺陷修复

首页任务、消息与历史已有本人培训引用，PC内部培训路由不能仅依赖培训中心菜单。对已获首页菜单及既有 `training:learner:task` 权限的账号，在缺少培训中心菜单时注册隐藏的培训列表、详情、须知、作答、结果及线下场次入口，复用原页面与本人接口。已有培训菜单保留原路由，顶部导航不增加菜单，无本人培训权限不注册；切换账号沿用动态路由清理。不改变API、数据库或后端权限。


2026-10-09 消息考试入口修正：PC/H5 复用既有本人 `/api/exam/learner/tasks`，按考试人员ID+考试ID匹配消息，显示线上/线下/专项练习/题库练习类型及当前可用动作。线下明确不支持在线答题，成绩未开放时禁用“待出成绩”，仅 VIEW_RESULT 且存在作答ID时开放“查看成绩”；未知类型、未知动作或不可见记录禁用。原持久化正文不改写，点击继续通过当前业务接口重新鉴权；没有 API/数据库变更。
