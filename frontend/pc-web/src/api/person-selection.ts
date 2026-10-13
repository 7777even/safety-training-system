/** 统一人员选择类型，与人员选择 OpenAPI Contract 保持一致。 */
export type PersonSelectionType = 'ALL' | 'DEPT' | 'USER' | 'POSITION' | 'GROUP';

/** 业务接口提交的人员选择条件，由后端在本次操作中展开为具体用户。 */
export interface PersonSelectionRequest {
  selectionType: PersonSelectionType;
  subjectIds: number[];
}
