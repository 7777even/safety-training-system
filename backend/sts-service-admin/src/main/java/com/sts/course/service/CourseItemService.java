package com.sts.course.service;

import com.sts.course.dto.*;
import com.sts.course.vo.CourseItemVO;
import com.sts.course.vo.LiveSessionVO;

import java.util.List;

public interface CourseItemService {

  List<CourseItemVO> listByChapter(Long chapterId);

  Long create(ItemCreateRequest request);

  void update(Long itemId, ItemUpdateRequest request);

  void move(Long itemId, ItemMoveRequest request);

  void delete(Long itemId);

  void moveAllCourseItemsToChapter(Long courseId, Long targetChapterId);

  void deleteAllOfCourse(Long courseId);

  Long countByChapter(Long chapterId);

  void assertChapterHasNoItems(Long chapterId);

  LiveSessionVO getLiveSession(Long itemId);

  void updateLiveSession(Long itemId, LiveSessionUpdateRequest request);
}
