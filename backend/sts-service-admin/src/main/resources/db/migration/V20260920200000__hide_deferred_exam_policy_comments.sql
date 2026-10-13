-- 中山50最小闭环不启用的考试策略仅保留兼容列和历史值，统一标注为暂时隐藏。

ALTER TABLE `sts_exam_policy`
  MODIFY COLUMN `switch_limit` int DEFAULT NULL COMMENT '【暂时隐藏】原用于：切屏次数阈值；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `is_split_screen_force_submit` tinyint NOT NULL COMMENT '【暂时隐藏】原用于：检测到分屏时强制交卷；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `is_copy_refresh_devtool_disabled` tinyint NOT NULL COMMENT '【暂时隐藏】原用于：禁止选择复制、刷新及开发者工具；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `inactivity_seconds` int DEFAULT NULL COMMENT '【暂时隐藏】原用于：无操作判定秒数；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `inactivity_countdown_seconds` int DEFAULT NULL COMMENT '【暂时隐藏】原用于：无操作强制交卷倒计时秒数；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `minimum_answer_seconds` int DEFAULT NULL COMMENT '【暂时隐藏】原用于：最短作答秒数；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `pass_point` decimal (10, 2) NOT NULL COMMENT '【暂时隐藏】原用于：合格积分；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `fail_point` decimal (10, 2) NOT NULL COMMENT '【暂时隐藏】原用于：不合格积分；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `absent_point` decimal (10, 2) NOT NULL COMMENT '【暂时隐藏】原用于：缺考积分；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `late_forbid_seconds` int NOT NULL COMMENT '【暂时隐藏】原用于：迟到禁考秒数；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `is_ranking_enabled` tinyint NOT NULL COMMENT '【暂时隐藏】原用于：考试排行榜展示；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change';
