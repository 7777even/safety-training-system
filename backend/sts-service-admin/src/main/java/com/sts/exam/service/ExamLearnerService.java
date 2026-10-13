package com.sts.exam.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.exam.dto.ExamLearnerTaskQuery;
import com.sts.exam.vo.ExamAttemptVO;
import com.sts.exam.vo.ExamLearnerTaskVO;
import com.sts.exam.vo.ExamLearnerNoticeVO;
import org.springframework.core.io.Resource;

/**
 * 学员本人考试任务、封面与作答读取服务。
 */
public interface ExamLearnerService {
  PageResult<ExamLearnerTaskVO> pageMyTasks(PageRequest<ExamLearnerTaskQuery> request);

  ExamAttemptVO getMyAttempt(Long examAttemptId);

  /** 读取本机构本人已分配独立考试的考前须知。 */
  ExamLearnerNoticeVO getMyNotice(Long examId);

  ExamCoverFile openCover(Long examId);

  record ExamCoverFile(Resource resource, String fileName, String contentType, long fileSize, String etag) {
  }
}
