import type { LearnerHistory } from '@shared/types/learner-history';
/** 契约样例供组件测试，运行时不替代真实鉴权。 */
export const historyFixture: LearnerHistory = {
  "recordType": "COURSE",
  "recordId": 1,
  "title": "安全基础课程",
  "original": {
    "userName": "张三",
    "userCode": "learner",
    "occurredTime": "2026-10-08T12:00:00",
    "status": "COMPLETED",
    "score": null,
    "hours": 2,
    "scoreVisible": true
  },
  "current": {
    "userName": "张三",
    "userCode": "learner",
    "occurredTime": "2026-10-08T12:00:00",
    "status": "COMPLETED",
    "score": null,
    "hours": 2,
    "scoreVisible": true
  },
  "version": 0,
  "reference": {
    "kind": "COURSE",
    "resourceId": 1,
    "parentId": null,
    "itemId": null,
    "attemptId": null,
    "action": "VIEW_COURSE"
  },
  "corrections": []
};
