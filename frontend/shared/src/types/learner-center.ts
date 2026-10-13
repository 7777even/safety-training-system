/** 学员端可进入的聚合动作，直接来自原考试领域的学员建议动作。 */
export type PersonalAssessmentAction = 'START' | 'CONTINUE' | 'WAIT_REVIEW' | 'VIEW_RESULT';
/** 第三批本人聚合考核接口类型，来自Exam OpenAPI。 */
export interface PersonalAssessmentTask {
  key: string; kind: 'FORMAL_EXAM' | 'COURSE_QUIZ'; title: string; sourceName: string;
  /** 仅正式考试给出；线下考试没有在线作答环节。课程测验为 null。 */
  examType: 'ONLINE' | 'OFFLINE' | null;
  status: 'UNFINISHED' | 'IN_PROGRESS' | 'ENDED'; available: boolean;
  /** 为空表示当前没有可进入的入口（例如线下考试尚未录入成绩）。 */
  action: PersonalAssessmentAction | null;
  candidateId: number | null; examId: number | null; enrollmentId: number | null; itemId: number | null; attemptId: number | null;
}
export interface PersonalAssessmentHistory { attemptId: number; status: string; submittedTime: string }
/** Course OpenAPI的本人课程收藏摘要与只读详情。 */
export interface PersonalFavoriteCourse { courseId: number; courseName: string; introduction: string | null; teachingObjective: string | null; enrollmentId: number | null; offeringId: number | null; available: boolean; favorite: boolean }
/** 同机构教师公开详情，故意不包含账号、手机号和权限。 */
export interface PersonalTeacher { teacherId: number; teacherName: string; available: boolean; followed: boolean }
