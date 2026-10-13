/** 按冻结父关系排列课程章节，供PC管理、PC学习和H5目录共用。 */
export function orderCourseChapters<T extends { chapterId: number; parentId?: number | null }>(chapters: T[]): Array<T & { depth: number }> {
  const children = new Map<number, T[]>();
  for (const chapter of chapters) {
    const parentId = chapter.parentId ?? 0;
    const siblings = children.get(parentId) ?? [];
    siblings.push(chapter);
    children.set(parentId, siblings);
  }
  const pending = (children.get(0) ?? []).map(chapter => ({ ...chapter, depth: 0 })).reverse();
  const result: Array<T & { depth: number }> = [];
  while (pending.length) {
    const chapter = pending.pop()!;
    result.push(chapter);
    const descendants = children.get(chapter.chapterId) ?? [];
    for (let i = descendants.length - 1; i >= 0; i--) pending.push({ ...descendants[i], depth: chapter.depth + 1 });
  }
  return result;
}
