# 第四批提交前快速验证

## 后续：PC首页培训任务无法进入（2026-10-09）

- 根因：培训流程路由依赖培训中心菜单；已有首页任务及本人培训权限、但没有培训中心菜单的账号未注册 `/portal/training/:trainingParticipantId`。
- 修复：在首页菜单下按既有 `training:learner:task` 补注册隐藏培训列表及五个流程页，已有培训菜单不覆盖，无权限账号不注册，切换账号清理原入口。首页、消息、历史继续使用同一业务引用，未修改API/数据库/后端权限。
- Red：`pnpm exec node --test tests/learner-training-routing.test.mjs` 重现同样的Vue Router无匹配警告并失败；Green：该测试与首页消息、历史测试共11项通过，PC类型检查与路由文件lint通过。
- 独立双轴Review均通过；真实本机5173→IDEA20080，DEMO/admin顶部无培训菜单：点击首页“测试培训计划”的进入任务成功打开 `/portal/training/2`，显示已结束状态与三项内容；返回 `/portal/training`、再次查看完成情况、刷新详情均通过，控制台培训路由警告为零。
- 截图：`.workbuddy/tmp/batch4-evidence/pc-training-route-fixed.png`。本次修复尚未提交，保留用户其它工作区改动。

日期：2026-10-09；身份：fxw11；关联 Change：`2026-10-08-complete-batch4-learner-center`。

用户已确认本批补齐完成并要求提交。核对当前实现、OpenSpec、迁移、契约与后续验收记录后，47/52/61 登记完成，50 仅非微信子项完成，微信随46；本次不扩大功能范围。

## 本轮重新执行

- 后端12个受影响测试类共39项，零失败、零错误、零跳过，`BUILD SUCCESS`。为避免IDE与Maven污染同一个target，复制当前backend源码到 `.workbuddy/tmp/batch4-backend-verify`，执行 `mvn -pl sts-service-admin -am test -Dtest=<上述受影响类> -Dsurefire.failIfNoSpecifiedTests=false`。
- PC/H5 `pnpm exec vue-tsc --noEmit` 均 exit 0；shared 类型检查 `pnpm exec tsc -p ../shared/tsconfig.json --noEmit` exit 0。
- `pnpm exec node --test tests/learner-home-message.test.mjs tests/my-profile-password.test.mjs tests/learner-history.test.mjs`：12/12通过。
- 更正弹窗错误提示定向 ESLint通过；四份受影响OpenAPI与初始化menus JSON解析通过；OpenSpec strict与`git diff --check`通过。
- 双轴只读复核：规格轴无阻断；质量轴发现更正弹窗外错误被遮罩隐藏，已补回弹窗内Alert，新增回归先失败再通过。仅修复提示位置，不修改权限或更正规则。

## 已有证据与当前环境边界

- 已有真实HTTP终检16/16、截止提醒4/4、培训取消及失效打开12/12、课程下架9/9，更正写入17/17，改密HTTP链路10/10。证据脚本、日志与PC/H5渲染报告位于 `.workbuddy/tmp/qa-batch4/`，归纳见两份同日最终QA记录。它们是既有验收证据，不是本轮重新执行结果。
- 检查已有Surefire报告：MyProfileIT、LearnerHistoryIT、LearnerHistoryMigrationIT、LearnerMessageIT、LearnerMessageMigrationIT及目标FlywayMigrationIT均零失败。与早期Docker初始化失败分开记录，不用旧失败覆盖后来成功，也未重新运行全部MySQL IT。
- 本轮尝试 `node .workbuddy/tmp/qa-batch4/t-final-smoke.mjs`，因本机20080未启动，登录请求 `ECONNREFUSED`，未进入接口断言。本轮未重启用户IDE、未写共享数据库，不能声称再次完成在线联调。
- 改密10项是HTTP行为验证，PC/H5资料、消息、历史另有真实渲染取证，不能将其描述为本轮人工页面改密操作。
- 已知既有问题保留：开发库operator01手机号存为脱敏字符串；既有全量菜单/前端测试漂移。未纳入本次修复。

## 提交范围

后端与契约、PC/H5及shared、OpenSpec/QA/Retro/进度与唯一清单按职责分组。提交期间复查发现本地IDE profile与两端代理已与HEAD一致，故没有配置差异可另行提交，未重写它们。临时脚本、构建产物、验收截图不纳入Git。第三批QA的已有改动保留未提交。


## 2026-10-09 消息考试类型与线下入口回归

- 症状：消息仅显示“考试”，线下消息点击后才出现泛化的无入口提示。PC/H5现在显示真实考试类型；线下提示不支持在线答题，等待成绩时禁用按钮，成绩可查看后仅开放查看成绩。
- 复用真实本人考试任务接口 `/api/exam/learner/tasks`，覆盖ONLINE/OFFLINE/SPECIAL_PRACTICE/BANK_PRACTICE；不使用只包含两种正式考试的assessment-tasks，也不使用Mock。双重业务引用匹配，点击时仍重新鉴权；无权限或未知信息不开放入口。
- Red：两端考试信息提供方回归在修正前失败；Green：message-exams与learner-home-message共12/12通过，覆盖线下禁止START/CONTINUE/WAIT_REVIEW、已发布结果、缺作答ID、未知类型/动作、两类练习及跨页匹配/SSE补读。PC/H5/shared类型检查、两端定向lint通过。
- 双轴Review先发现两类练习遗漏和未知类型未禁用，均已修复并独立复核通过。
- 真实本机PC5173/H55174连接IDEA20080，DEMO/admin消息5条：2条线上、2条线下、1条专项练习，类型正确；线上/专项入口可用，线下均“待出成绩”，H5 DOM两个disabled=true。未启动答题或改动业务数据；线下已发布结果与题库练习消息仅自动化覆盖，本轮未造真实样本。
- 截图：`.workbuddy/tmp/batch4-evidence/pc-message-offline-exam-fixed.png`、`h5-message-offline-exam-fixed.png`。本轮尚未提交。
