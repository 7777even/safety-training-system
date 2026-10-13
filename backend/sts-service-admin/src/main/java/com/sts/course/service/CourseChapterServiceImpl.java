package com.sts.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.course.converter.CourseChapterConverter;
import com.sts.course.dto.ChapterCreateRequest;
import com.sts.course.dto.ChapterUpdateRequest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseChapter;
import com.sts.course.entity.CourseItem;
import com.sts.course.mapper.CourseChapterMapper;
import com.sts.course.mapper.CourseItemMapper;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.vo.CourseChapterVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 同课程多层章节编排，父级修改必须防止循环，删除保留已有内容。
 */
@Service
@RequiredArgsConstructor
public class CourseChapterServiceImpl extends BaseServiceImpl<CourseChapterMapper, CourseChapter>
  implements CourseChapterService {

  private static final String ROOT_ANCESTORS = "0";
  private static final String DEFAULT_CHAPTER_NAME = "默认章节";

  private final CourseChapterMapper chapterMapper;
  private final CourseChapterConverter chapterConverter;
  private final CourseMapper courseMapper;
  private final CourseItemMapper itemMapper;

  @Override
  public List<CourseChapterVO> listChapters(Long courseId) {
    List<CourseChapter> chapters = chapterMapper.selectList(
      new LambdaQueryWrapper<CourseChapter>()
        .eq(CourseChapter::getCourseId, courseId)
        .orderByAsc(CourseChapter::getSortOrder)
        .orderByAsc(CourseChapter::getChapterId));
    return chapters.stream().map(chapterConverter::toVO).toList();
  }

  @Override
  @Transactional
  public Long createChapter(ChapterCreateRequest request) {
    lockCourse(request.getCourseId());
    requireCourseExists(request.getCourseId());
    assertNameNotDuplicated(request.getCourseId(), request.getChapterName(), null);

    CourseChapter chapter = chapterConverter.toEntity(request);
    Long parentId = request.getParentId() == null ? 0L : request.getParentId();
    chapter.setParentId(parentId);
    chapter.setAncestors(parentAncestors(request.getCourseId(), parentId, null));
    try {
      chapterMapper.insert(chapter);
    } catch (DuplicateKeyException e) {
      throw new BusinessException(HttpStatus.CONFLICT, 5022, "同课程下章节名称已存在");
    }
    return chapter.getChapterId();
  }

  @Override
  @Transactional
  public void updateChapter(Long chapterId, ChapterUpdateRequest request) {
    CourseChapter chapter = requireChapter(chapterId);
    lockCourse(chapter.getCourseId());
    chapter = requireCurrentChapter(chapterId);
    chapter.setVersion(request.getVersion());
    assertNameNotDuplicated(chapter.getCourseId(), request.getChapterName(), chapterId);
    Long parentId = request.getParentId() == null ? chapter.getParentId() : request.getParentId();
    String ancestors = parentAncestors(chapter.getCourseId(), parentId, chapterId);
    boolean moved = !Objects.equals(parentId, chapter.getParentId());
    chapterConverter.updateEntity(chapter, request);
    chapter.setParentId(parentId);
    chapter.setAncestors(ancestors);
    try {
      chapterMapper.updateByIdWithVersionCheck(chapter);
      if (moved) refreshDescendantAncestors(chapter);
    } catch (DuplicateKeyException e) {
      throw new BusinessException(HttpStatus.CONFLICT, 5022, "同课程下章节名称已存在");
    }
  }

  @Override
  @Transactional
  public void deleteChapter(Long chapterId) {
    CourseChapter chapter = requireChapter(chapterId);
    lockCourse(chapter.getCourseId());
    chapter = requireCurrentChapter(chapterId);
    Long remaining = chapterMapper.selectCount(
      new LambdaQueryWrapper<CourseChapter>().eq(CourseChapter::getCourseId, chapter.getCourseId()).last("FOR UPDATE"));
    if (remaining != null && remaining <= 1) {
      throw new BusinessException(HttpStatus.CONFLICT, 5023, "课程至少保留一个章节，不能删除最后一个");
    }
    if (chapterMapper.selectCount(new LambdaQueryWrapper<CourseChapter>()
      .eq(CourseChapter::getCourseId, chapter.getCourseId()).eq(CourseChapter::getParentId, chapterId).last("FOR UPDATE")) > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 5034, "章节下存在子章节，不能删除");
    }
    Long itemCount = itemMapper.selectCount(
      new LambdaQueryWrapper<CourseItem>().eq(CourseItem::getChapterId, chapterId).last("FOR UPDATE"));
    if (itemCount != null && itemCount > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 5034, "章节下存在内容项，不能删除");
    }
    this.removeById(chapterId);
  }

  @Override
  @Transactional
  public void deleteAllChaptersOfCourse(Long courseId) {
    lockCourse(courseId);
    List<CourseChapter> chapters = chapterMapper.selectList(
      new LambdaQueryWrapper<CourseChapter>().eq(CourseChapter::getCourseId, courseId).last("FOR UPDATE"));
    for (CourseChapter chapter : chapters) {
      this.removeById(chapter.getChapterId());
    }
  }

  @Override
  @Transactional
  public Long mergeChaptersToSingle(Long courseId) {
    lockCourse(courseId);
    List<CourseChapter> chapters = chapterMapper.selectList(
      new LambdaQueryWrapper<CourseChapter>()
        .eq(CourseChapter::getCourseId, courseId)
        .orderByAsc(CourseChapter::getSortOrder)
        .orderByAsc(CourseChapter::getChapterId).last("FOR UPDATE"));
    if (chapters.isEmpty()) {
      // 理论不会发生（每个课程创建时都会生成默认章节），防御性兜底创建一个。
      CourseChapter defaultChapter = new CourseChapter();
      defaultChapter.setCourseId(courseId);
      defaultChapter.setParentId(0L);
      defaultChapter.setAncestors(ROOT_ANCESTORS);
      defaultChapter.setChapterName(DEFAULT_CHAPTER_NAME);
      defaultChapter.setSortOrder(0);
      defaultChapter.setStatus(1);
      chapterMapper.insert(defaultChapter);
      return defaultChapter.getChapterId();
    }
    chapters = treeOrder(chapters);
    CourseChapter survivor = chapters.get(0);
    survivor.setParentId(0L);
    survivor.setAncestors(ROOT_ANCESTORS);
    chapterMapper.updateById(survivor);
    moveAllItemsToSurvivorChapter(courseId, survivor.getChapterId());
    for (int i = 1; i < chapters.size(); i++) {
      this.removeById(chapters.get(i).getChapterId());
    }
    return survivor.getChapterId();
  }

  private void moveAllItemsToSurvivorChapter(Long courseId, Long survivorChapterId) {
    List<CourseChapter> chapters = chapterMapper.selectList(
      new LambdaQueryWrapper<CourseChapter>()
        .eq(CourseChapter::getCourseId, courseId)
        .orderByAsc(CourseChapter::getSortOrder)
        .orderByAsc(CourseChapter::getChapterId).last("FOR UPDATE"));
    chapters = treeOrder(chapters);
    java.util.Map<Long, Integer> chapterOrder = new java.util.HashMap<>();
    for (int i = 0; i < chapters.size(); i++) {
      chapterOrder.put(chapters.get(i).getChapterId(), i);
    }
    List<CourseItem> items = new java.util.ArrayList<>(itemMapper.selectList(
      new LambdaQueryWrapper<CourseItem>().eq(CourseItem::getCourseId, courseId).last("FOR UPDATE")));
    items.sort(java.util.Comparator
                 .comparing((CourseItem i) -> chapterOrder.getOrDefault(i.getChapterId(), Integer.MAX_VALUE))
                 .thenComparing(CourseItem::getSortOrder, java.util.Comparator.nullsLast(Integer::compareTo))
                 .thenComparing(CourseItem::getCourseItemId, java.util.Comparator.nullsLast(Long::compareTo)));
    int order = 0;
    for (CourseItem item : items) {
      item.setChapterId(survivorChapterId);
      item.setSortOrder(order++);
      itemMapper.updateById(item);
    }
  }

  /** 从父关系逐级校验，不能依赖可能陈旧的祖先字符串判定循环。 */
  private String parentAncestors(Long courseId, Long parentId, Long childId) {
    if (parentId == null || parentId == 0L) return ROOT_ANCESTORS;
    Set<Long> visited = new HashSet<>();
    List<Long> path = new ArrayList<>();
    Long current = parentId;
    while (current != null && current != 0L) {
      if (Objects.equals(current, childId) || !visited.add(current)) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5024, "父章节不能是自身或后代");
      }
      CourseChapter parent = requireCurrentChapter(current);
      if (!Objects.equals(parent.getCourseId(), courseId)) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5024, "父章节必须属于当前课程");
      }
      path.add(current);
      current = parent.getParentId();
    }
    Collections.reverse(path);
    return ROOT_ANCESTORS + "," + path.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
  }

  /** 移动目录后同步全部后代路径，保持原有父关系不变。 */
  private void refreshDescendantAncestors(CourseChapter parent) {
    List<CourseChapter> all = chapterMapper.selectList(new LambdaQueryWrapper<CourseChapter>()
      .eq(CourseChapter::getCourseId, parent.getCourseId()).last("FOR UPDATE"));
    Deque<CourseChapter> pending = new ArrayDeque<>();
    pending.add(parent);
    while (!pending.isEmpty()) {
      CourseChapter node = pending.removeFirst();
      for (CourseChapter child : all) {
        if (!Objects.equals(child.getParentId(), node.getChapterId())) continue;
        child.setAncestors(node.getAncestors() + "," + node.getChapterId());
        chapterMapper.updateByIdWithVersionCheck(child);
        pending.add(child);
      }
    }
  }

  /** 前序遍历只改变呈现/合并顺序，不改变章节或内容数量。 */
  private List<CourseChapter> treeOrder(List<CourseChapter> chapters) {
    List<CourseChapter> result = new ArrayList<>();
    Deque<CourseChapter> pending = new ArrayDeque<>();
    chapters.stream().filter(c -> c.getParentId() == null || c.getParentId() == 0L).forEach(pending::addLast);
    while (!pending.isEmpty()) {
      CourseChapter node = pending.removeFirst();
      result.add(node);
      List<CourseChapter> children = chapters.stream().filter(c -> Objects.equals(c.getParentId(), node.getChapterId())).toList();
      for (int i = children.size() - 1; i >= 0; i--) pending.addFirst(children.get(i));
    }
    if (result.size() != chapters.size()) throw new BusinessException(HttpStatus.BAD_REQUEST, 5024, "课程目录父关系无效");
    return result;
  }

  private void assertNameNotDuplicated(Long courseId, String chapterName, Long excludeChapterId) {
    LambdaQueryWrapper<CourseChapter> wrapper = new LambdaQueryWrapper<CourseChapter>()
      .eq(CourseChapter::getCourseId, courseId)
      .eq(CourseChapter::getChapterName, chapterName);
    if (excludeChapterId != null) {
      wrapper.ne(CourseChapter::getChapterId, excludeChapterId);
    }
    Long count = chapterMapper.selectCount(wrapper);
    if (count != null && count > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 5022, "同课程下章节名称已存在");
    }
  }

  /** 目录和内容项共用课程锁，防止删除与新增、关联操作穿插。 */
  private void lockCourse(Long courseId) {
    courseMapper.selectOne(new LambdaQueryWrapper<Course>().eq(Course::getCourseId, courseId).last("FOR UPDATE"));
  }

  /** 锁后读取已提交的父关系，不能沿用本事务先前的快照。 */
  private CourseChapter requireCurrentChapter(Long chapterId) {
    CourseChapter chapter = chapterMapper.selectCurrent(chapterId);
    if (chapter == null) throw new BusinessException(HttpStatus.NOT_FOUND, 5021, "课程章节不存在");
    return chapter;
  }

  private void requireCourseExists(Long courseId) {
    if (courseMapper.selectById(courseId) == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5011, "课程不存在");
    }
  }

  private CourseChapter requireChapter(Long chapterId) {
    CourseChapter chapter = chapterMapper.selectById(chapterId);
    if (chapter == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5021, "课程章节不存在");
    }
    return chapter;
  }
}
