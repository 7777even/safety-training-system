package com.sts.course.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.course.dto.*;
import com.sts.course.vo.CourseOfferingListItemVO;
import com.sts.course.vo.CourseOfferingVO;

public interface CourseOfferingService {

  PageResult<CourseOfferingListItemVO> pageOfferings(PageRequest<CourseOfferingListQuery> request);

  CourseOfferingVO getOffering(Long offeringId);

  Long createOffering(CourseOfferingCreateRequest request);

  void updateOffering(Long offeringId, CourseOfferingUpdateRequest request);

  void deleteOffering(Long offeringId);

  void publishOffering(Long offeringId, CourseOfferingPublishRequest request);

  void endOffering(Long offeringId, CourseOfferingStatusRequest request);

  void cancelOffering(Long offeringId, CourseOfferingStatusRequest request);
}
