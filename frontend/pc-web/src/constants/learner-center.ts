/** 本批个人学习入口，路由常量集中管理。 */
export const LEARNER_CENTER_ROUTES = { home: '/user-center', practices: '/user-center/practices', wrongs: '/user-center/wrong-questions', attempt: '/user-center/practice', exams: '/user-center/exams', courses: '/user-center/courses', favorites: '/user-center/favorites', teachers: '/user-center/teachers' };
export const PRACTICE_MODES = [{ label: '练习模式', value: 'PRACTICE' }, { label: '模拟模式', value: 'SIMULATION' }] as const;
export const PRACTICE_FILTERS = [{ label: '全部题目', value: 'ALL' }, { label: '未做题目', value: 'UNANSWERED' }, { label: '错题', value: 'WRONG' }, { label: '收藏题目', value: 'FAVORITE' }];

/** 我的考试状态来自聚合契约，不新增原领域状态。 */
export const PERSONAL_ASSESSMENT_STATUSES = [{ label: '全部', value: 'ALL' }, { label: '未完成', value: 'UNFINISHED' }, { label: '进行中', value: 'IN_PROGRESS' }, { label: '已结束', value: 'ENDED' }];
