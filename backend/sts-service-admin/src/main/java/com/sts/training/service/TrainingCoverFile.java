package com.sts.training.service;

import org.springframework.core.io.Resource;

/**
 * 培训类型或学员计划封面的原始文件。
 */
public record TrainingCoverFile(Resource resource, String fileName, String contentType, long fileSize, String etag) {
}
