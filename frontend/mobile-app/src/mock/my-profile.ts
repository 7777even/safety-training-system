import type { MyProfile } from '@shared/types/my-profile';
/** 契约样例供页面测试；运行接口始终使用真实本人服务。 */
export const myProfileFixture: MyProfile = {
  "userCode": "learner",
  "userName": "张三",
  "phone": "13800000007",
  "email": "learner@example.test",
  "organizationId": 2,
  "orgName": "示例企业",
  "deptId": 1,
  "deptName": "安全部门",
  "memberType": "INTERNAL",
  "roles": [
    "learner"
  ],
  "version": 3,
  "hasLocalCredential": true,
  "passwordChangeAllowed": true
};
