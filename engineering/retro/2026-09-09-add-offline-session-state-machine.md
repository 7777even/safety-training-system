# Retro：线下场次受控状态机

## 做得好的地方

- 状态只走三个动作，PUT 不再能改 `sessionStatus`。
- 录分与教师动作共用同一套状态，避免再出现「有字段无状态机」。

## 问题

- 生成后未 start 则无法录分，前端必须先调 start。
- OpenAPI 原先写成 RUNNING/ENDED，与代码 IN_PROGRESS/COMPLETED 不一致，本次对齐。

## 原因

场次状态以前只是可写字段。

## 改进方案

前端对接 start/complete/cancel；监管下钻与交卷空白答案另开，不塞进状态机。
