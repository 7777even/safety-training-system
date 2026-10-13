package com.sts.course.service;

import com.sts.course.dto.ChapterCreateRequest;
import com.sts.course.dto.ChapterUpdateRequest;
import com.sts.course.vo.CourseChapterVO;

import java.util.List;

public interface CourseChapterService {

  List<CourseChapterVO> listChapters(Long courseId);

  Long createChapter(ChapterCreateRequest request);

  void updateChapter(Long chapterId, ChapterUpdateRequest request);

  void deleteChapter(Long chapterId);

  /**
   * 级联删除课程下全部章节（课程被删除时调用）。
   */
  void deleteAllChaptersOfCourse(Long courseId);

  /**
   * 将课程的多个章节合并为一个：保留排序最靠前（并列时保留 ID 最小）的章节，其余逻辑删除。
   * 只处理章节层面；课程内容项的搬迁属于 Task 3（{@code sts_course_item} 建表后）职责，
   * 需在该表就绪后扩展本方法或由调用方在同一事务中一并处理。
   *
   * @return 合并后唯一存活章节的 ID
   */
  Long mergeChaptersToSingle(Long courseId);
}
