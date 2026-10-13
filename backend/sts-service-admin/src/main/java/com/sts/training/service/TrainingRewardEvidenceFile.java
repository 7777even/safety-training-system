package com.sts.training.service;

import org.springframework.core.io.Resource;

/** 培训奖惩记录关联的受控佐证文件。 */
public record TrainingRewardEvidenceFile(Resource resource, String fileName, long fileSize) {
}
