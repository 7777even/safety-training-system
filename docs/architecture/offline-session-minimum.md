# 线下场次最小设计

> 已确认执行口径。签到 HTTP、签到字段、签到表和签到权限均删除。实施见
> `openspec/changes/simplify-offline-session-to-score/` 与 `openspec/changes/remove-offline-sign-in-from-plan-apis/`。
> 覆盖功能项 33 线下句、34、37 本场成绩、48/56 学员可见地点与本人分数。
> 路线图阶段 5 签到/签退/缺勤/附件/奖惩不作为本期约束。

## 功能项最小集合

| 功能项          | 本期做                 | 不做              |
|--------------|---------------------|-----------------|
| 33           | 阶段可加线下；公示地点；成绩能写入系统 | 签到；成绩文件批量导入     |
| 34           | 计划可编排线下内容（已有）       | —               |
| 37           | 教师看见本场每人分数          | 课堂试卷、统计图表       |
| 36 / 41 / 44 | —                   | 影像、奖惩、档案签到、线下图表 |
| 48 / 56      | 学员能打开线下项，看见地点和本人分数  | 学员自助签到          |

## 目标模型

```text
计划 OFFLINE 内容（名称、地点、时间、教师、及格线）
        │ 发布后生成一场
        ▼
   场次（通知：名称、地点、起止、教师）
        │
        └── 每人至多一条成绩
                └── 投影只读这条成绩
```

教师：打开本场 → 计划参训人名单 → 每人录一条分。  
学员：地点/时间 + 自己的分。  
完成：已评分且分数达标 → `COMPLETED`；低于及格线 → `FAILED`；没分 → `PENDING`。

## 去掉的原口径

结果链路不再使用：

- 出勤是完成前提
- PRESENT / LATE / ABSENT / LEAVE、签到窗口、`SIGN_IN_OUT`
- 确认签到才计学时
- 种签到行、教师「应到/已确认」人数
- 场次状态机、多成绩项无序 `LIMIT 1`
- 线下获得学时 = 场次时长或进出时间差

`sts_training_attendance`、签到实体/Mapper/Service、场次签到字段及签到权限全部删除。线下场次表继续承载教师、地点、起止时间和状态，成绩表继续承载教师录入结果。

学时：发布仍用课程与线下内容的 `requiredHours`。线下获得学时：`COMPLETED` 时为该内容 `requiredHours`×3600 秒，未评分或
`FAILED` 为 0。

## 接口

- `GET` 教师任务列表/详情：通知 + 参训名单 + 分数 + 场次乐观锁 `version`（供 start/complete/cancel 回传）。
  `training:teacher:task`。
- `POST /training/offline-scores`：一场一人一行，再提交更新。
- `GET /training/learner/offline-sessions/{id}`：地点、时间、本人分数、完成态。无出勤字段。

删除：`GET/POST /training/attendance`、`POST /training/attendance/{id}/confirm`。

## 非目标

签到能力、Excel 导入、图表、附件、奖惩、前端。场次状态机见 `openspec/changes/add-offline-session-state-machine/`。
