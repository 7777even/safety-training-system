package com.sts.course.service;

import com.sts.course.vo.CourseAssetReferenceVO;

public interface CourseAssetReferenceService {

  CourseAssetReferenceVO getReferences(Long assetId);
}
