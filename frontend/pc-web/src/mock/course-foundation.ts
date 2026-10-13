// TODO replace real api

/** Mock 统一响应外壳，字段与后端 ResultEnvelope 保持一致。 */
export interface CourseFoundationMockResponse<TData> {
  code: number;
  message: string;
  data: TData;
}

/** Mock 分类记录；children 仅在树查询响应中临时生成。 */
export interface CourseFoundationMockCategory {
  courseCategoryId: number;
  parentId: number;
  categoryName: string;
  description?: string;
  sortOrder?: number;
  status: 0 | 1;
  version: number;
  children?: CourseFoundationMockCategory[];
}

/** 分类树查询条件。 */
export interface CourseFoundationMockCategoryQuery {
  categoryName?: string;
  status?: 0 | 1;
}

/** 分类创建字段。 */
export interface CourseFoundationMockCategoryCreatePayload {
  parentId: number;
  categoryName: string;
  description?: string;
  sortOrder?: number;
  status?: 0 | 1;
}

/** 分类更新字段，version 用于模拟后端乐观锁。 */
export interface CourseFoundationMockCategoryUpdatePayload {
  parentId: number;
  categoryName: string;
  description?: string;
  sortOrder?: number;
  status: 0 | 1;
  version: number;
}

/** Mock 课程详情，字段与 CourseVO 保持一致。 */
export interface CourseFoundationMockCourse {
  courseId: number;
  courseCode: string;
  courseName: string;
  introduction?: string;
  teachingObjective?: string;
  syllabus?: string;
  teacherUserId?: number | null;
  assessmentMethod?: string;
  credit: number;
  pointMode: 'NONE' | 'PER_ITEM' | 'COURSE';
  pointValue: number;
  sourceDownloadMode: 'NONE' | 'ALL' | 'PARTIAL';
  isSpeedPlayAllowed: 0 | 1;
  isStudyAnticheatEnabled: 0 | 1;
  isStudyDescriptionEnabled: 0 | 1;
  isWatermarkEnabled: 0 | 1;
  currentVersionId?: number | null;
  coverFileId?: number | null;
  contentMode: 'SINGLE' | 'MULTI_CHAPTER';
  publishStatus: 'DRAFT' | 'PUBLISHED' | 'OFFLINE';
  publishedTime?: string | null;
  status: 0 | 1;
  version: number;
  categoryIds?: number[];
  updateTime?: string;
}

/** 创建课程字段。 */
export type CourseFoundationMockCourseCreatePayload = Omit<
  CourseFoundationMockCourse,
  'courseId' | 'currentVersionId' | 'publishStatus' | 'publishedTime' | 'status' | 'version' | 'updateTime'
> & { status?: 0 | 1 };

/** 更新课程允许修改的字段。 */
export type CourseFoundationMockCourseUpdatePayload = Pick<
  CourseFoundationMockCourse,
  | 'courseName'
  | 'coverFileId'
  | 'introduction'
  | 'teachingObjective'
  | 'syllabus'
  | 'teacherUserId'
  | 'assessmentMethod'
  | 'credit'
  | 'pointMode'
  | 'pointValue'
  | 'sourceDownloadMode'
  | 'isSpeedPlayAllowed'
  | 'isStudyAnticheatEnabled'
  | 'isStudyDescriptionEnabled'
  | 'isWatermarkEnabled'
  | 'status'
  | 'version'
>;

/** 课程分页查询字段。 */
export interface CourseFoundationMockCoursePageQuery {
  pageNum: number;
  pageSize: number;
  'query.courseName'?: string;
  'query.teacherUserId'?: number;
  'query.publishStatus'?: CourseFoundationMockCourse['publishStatus'];
  'query.status'?: 0 | 1;
  'query.categoryId'?: number;
}

/** 课程分页响应。 */
export interface CourseFoundationMockCoursePage {
  list: CourseFoundationMockCourse[];
  total: number;
}

/** 可被其他课程 Mock 复用的基础状态容器。 */
export const courseFoundationMockStore = {
  categories: [] as CourseFoundationMockCategory[],
  nextCategoryId: 1,
  courses: [] as CourseFoundationMockCourse[],
  nextCourseId: 1
};

/** 构造成功响应。 */
function success<TData>(data: TData): CourseFoundationMockResponse<TData> {
  return { code: 0, message: 'success', data };
}

/** 构造业务失败响应。 */
function failure<TData>(code: number, message: string, data: TData): CourseFoundationMockResponse<TData> {
  return { code, message, data };
}

/** 重置 Mock 数据，保证开发刷新和自动化测试具有确定性。 */
export function resetCourseFoundationMock(): void {
  courseFoundationMockStore.categories.splice(
    0,
    courseFoundationMockStore.categories.length,
    {
      courseCategoryId: 1,
      parentId: 0,
      categoryName: '安全基础课程',
      description: '企业安全教育基础分类',
      sortOrder: 1,
      status: 1,
      version: 0
    },
    {
      courseCategoryId: 2,
      parentId: 1,
      categoryName: '入职安全培训',
      description: '新员工入职必修课程',
      sortOrder: 1,
      status: 1,
      version: 0
    }
  );
  courseFoundationMockStore.nextCategoryId = 3;
  courseFoundationMockStore.courses.splice(0, courseFoundationMockStore.courses.length, {
    courseId: 1,
    courseCode: 'CRS-MOCK-001',
    courseName: '新员工安全入门',
    introduction: '覆盖通用安全规范与入职须知。',
    teachingObjective: '掌握岗位安全基础要求。',
    syllabus: '安全责任、风险识别、应急处置。',
    assessmentMethod: '完成全部必修内容。',
    credit: 1,
    pointMode: 'NONE',
    pointValue: 0,
    sourceDownloadMode: 'NONE',
    isSpeedPlayAllowed: 0,
    isStudyAnticheatEnabled: 1,
    isStudyDescriptionEnabled: 1,
    isWatermarkEnabled: 1,
    currentVersionId: null,
    coverFileId: null,
    contentMode: 'MULTI_CHAPTER',
    publishStatus: 'DRAFT',
    publishedTime: null,
    status: 1,
    version: 0,
    categoryIds: [2],
    updateTime: '2026-07-23T09:00:00'
  });
  courseFoundationMockStore.nextCourseId = 2;
}

/** 将扁平分类记录转换为接口约定的 children 树。 */
function buildCategoryTree(categories: CourseFoundationMockCategory[]): CourseFoundationMockCategory[] {
  const nodes = new Map<number, CourseFoundationMockCategory>();
  categories.forEach((category) => nodes.set(category.courseCategoryId, { ...category, children: [] }));
  const roots: CourseFoundationMockCategory[] = [];
  nodes.forEach((category) => {
    const parent = nodes.get(category.parentId);
    if (parent) parent.children?.push(category);
    else roots.push(category);
  });
  const sortNodes = (items: CourseFoundationMockCategory[]): void => {
    items.sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0));
    items.forEach((item) => sortNodes(item.children ?? []));
  };
  sortNodes(roots);
  return roots;
}

/** 模拟课程分类树查询。 */
export async function mockGetCourseCategoryTree(
  params: CourseFoundationMockCategoryQuery = {}
): Promise<CourseFoundationMockResponse<CourseFoundationMockCategory[]>> {
  const keyword = params.categoryName?.trim().toLocaleLowerCase();
  const filtered = courseFoundationMockStore.categories.filter(
    (category) =>
      (!keyword || category.categoryName.toLocaleLowerCase().includes(keyword)) && (params.status === undefined || category.status === params.status)
  );
  return success(buildCategoryTree(filtered));
}

/** 模拟新增课程分类。 */
export async function mockCreateCourseCategory(data: CourseFoundationMockCategoryCreatePayload): Promise<CourseFoundationMockResponse<number>> {
  const courseCategoryId = courseFoundationMockStore.nextCategoryId++;
  courseFoundationMockStore.categories.push({
    courseCategoryId,
    parentId: data.parentId,
    categoryName: data.categoryName,
    description: data.description,
    sortOrder: data.sortOrder ?? 0,
    status: data.status ?? 1,
    version: 0
  });
  return success(courseCategoryId);
}

/** 模拟携带乐观锁版本更新课程分类。 */
export async function mockUpdateCourseCategory(
  categoryId: number,
  data: CourseFoundationMockCategoryUpdatePayload
): Promise<CourseFoundationMockResponse<null>> {
  const category = courseFoundationMockStore.categories.find((item) => item.courseCategoryId === categoryId);
  if (!category) return failure(5011, '课程分类不存在或已删除。', null);
  if (category.version !== data.version) return failure(2040, '数据已被更新，请刷新后重试。', null);
  Object.assign(category, data, { version: category.version + 1 });
  return success(null);
}

/** 模拟逻辑删除课程分类。 */
export async function mockDeleteCourseCategory(categoryId: number): Promise<CourseFoundationMockResponse<null>> {
  const index = courseFoundationMockStore.categories.findIndex((item) => item.courseCategoryId === categoryId);
  if (index < 0) return failure(5011, '课程分类不存在或已删除。', null);
  if (courseFoundationMockStore.categories.some((item) => item.parentId === categoryId)) {
    return failure(5006, '存在子分类，无法删除。', null);
  }
  courseFoundationMockStore.categories.splice(index, 1);
  return success(null);
}

/** 复制课程详情，避免调用方直接修改共享 Mock 状态。 */
function cloneCourse(course: CourseFoundationMockCourse): CourseFoundationMockCourse {
  return { ...course, categoryIds: [...(course.categoryIds ?? [])] };
}

/** 模拟课程分页及 Contract 定义的 query.* 筛选。 */
export async function mockGetCoursePage(
  params: CourseFoundationMockCoursePageQuery
): Promise<CourseFoundationMockResponse<CourseFoundationMockCoursePage>> {
  const keyword = params['query.courseName']?.trim().toLocaleLowerCase();
  const filtered = courseFoundationMockStore.courses.filter(
    (course) =>
      (!keyword || `${course.courseName}${course.syllabus ?? ''}`.toLocaleLowerCase().includes(keyword)) &&
      (!params['query.teacherUserId'] || course.teacherUserId === params['query.teacherUserId']) &&
      (!params['query.publishStatus'] || course.publishStatus === params['query.publishStatus']) &&
      (params['query.status'] === undefined || course.status === params['query.status']) &&
      (params['query.categoryId'] === undefined || course.categoryIds?.includes(params['query.categoryId']))
  );
  const start = Math.max(0, (params.pageNum - 1) * params.pageSize);
  return success({
    list: filtered.slice(start, start + params.pageSize).map(cloneCourse),
    total: filtered.length
  });
}

/** 模拟创建草稿课程。 */
export async function mockCreateCourse(data: CourseFoundationMockCourseCreatePayload): Promise<CourseFoundationMockResponse<number>> {
  if (courseFoundationMockStore.courses.some((course) => course.courseCode === data.courseCode)) {
    return failure(5012, '课程编码已存在。', 0);
  }
  const courseId = courseFoundationMockStore.nextCourseId++;
  courseFoundationMockStore.courses.push({
    ...data,
    courseId,
    status: data.status ?? 1,
    categoryIds: [...(data.categoryIds ?? [])],
    currentVersionId: null,
    coverFileId: data.coverFileId ?? null,
    publishStatus: 'DRAFT',
    publishedTime: null,
    version: 0,
    updateTime: '2026-07-23T09:00:00'
  });
  return success(courseId);
}

/** 模拟查询课程详情。 */
export async function mockGetCourseDetail(courseId: number): Promise<CourseFoundationMockResponse<CourseFoundationMockCourse | null>> {
  const course = courseFoundationMockStore.courses.find((item) => item.courseId === courseId);
  return course ? success(cloneCourse(course)) : failure(5011, '课程不存在或已删除。', null);
}

/** 模拟携带乐观锁版本更新课程基本信息。 */
export async function mockUpdateCourse(courseId: number, data: CourseFoundationMockCourseUpdatePayload): Promise<CourseFoundationMockResponse<null>> {
  const course = courseFoundationMockStore.courses.find((item) => item.courseId === courseId);
  if (!course) return failure(5011, '课程不存在或已删除。', null);
  if (course.version !== data.version) return failure(2040, '数据已被更新，请刷新后重试。', null);
  const mutableFields: Partial<CourseFoundationMockCourse> = { ...data };
  delete mutableFields.version;
  Object.assign(course, mutableFields, { version: course.version + 1, updateTime: '2026-07-23T09:30:00' });
  return success(null);
}

/** 模拟逻辑删除课程。 */
export async function mockDeleteCourse(courseId: number): Promise<CourseFoundationMockResponse<null>> {
  const index = courseFoundationMockStore.courses.findIndex((item) => item.courseId === courseId);
  if (index < 0) return failure(5011, '课程不存在或已删除。', null);
  courseFoundationMockStore.courses.splice(index, 1);
  return success(null);
}

/** 模拟覆盖课程分类关联。 */
export async function mockAssignCourseCategories(courseId: number, categoryIds: number[]): Promise<CourseFoundationMockResponse<null>> {
  const course = courseFoundationMockStore.courses.find((item) => item.courseId === courseId);
  if (!course) return failure(5011, '课程不存在或已删除。', null);
  course.categoryIds = [...categoryIds];
  return success(null);
}

/** 模拟携带乐观锁版本切换课程目录模式。 */
export async function mockSwitchCourseContentMode(
  courseId: number,
  contentMode: CourseFoundationMockCourse['contentMode'],
  version: number
): Promise<CourseFoundationMockResponse<null>> {
  const course = courseFoundationMockStore.courses.find((item) => item.courseId === courseId);
  if (!course) return failure(5011, '课程不存在或已删除。', null);
  if (course.version !== version) return failure(2040, '数据已被更新，请刷新后重试。', null);
  course.contentMode = contentMode;
  course.version += 1;
  course.updateTime = '2026-07-23T09:30:00';
  return success(null);
}

resetCourseFoundationMock();
