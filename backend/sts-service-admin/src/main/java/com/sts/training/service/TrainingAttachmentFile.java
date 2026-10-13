package com.sts.training.service;

import org.springframework.core.io.Resource;

/** 培训资料记录关联的受控原始文件，按资料记录定位，不暴露任意文件 ID。 */
public record TrainingAttachmentFile(Resource resource, String fileName, long fileSize) {
}
