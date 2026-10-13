package com.sts.course.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.course.dto.CourseEnrollmentListQuery;
import com.sts.course.vo.CourseEnrollmentListItemVO;

import java.util.List;

import com.sts.person.dto.PersonSelectionRequest;

public interface CourseEnrollmentService {

  PageResult<CourseEnrollmentListItemVO> pageEnrollments(Long offeringId,
                                                         PageRequest<CourseEnrollmentListQuery> request);

  void assign(Long offeringId, PersonSelectionRequest selection);

  void removeEnrollment(Long enrollmentId);

  Long selfEnroll(Long offeringId);

  void selfWithdraw(Long enrollmentId);
}
