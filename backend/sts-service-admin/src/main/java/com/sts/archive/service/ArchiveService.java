package com.sts.archive.service;

import com.sts.admin.storage.StoredDownload;
import com.sts.archive.dto.ArchiveCreateFromPlanRequest;
import com.sts.archive.dto.ArchiveEnterpriseQuery;
import com.sts.archive.vo.ArchivePersonVO;
import com.sts.archive.vo.ArchiveTrainingVO;
import com.sts.archive.vo.ArchiveViewVO;

import java.util.List;

public interface ArchiveService {
  ArchiveTrainingVO createFromPlan(ArchiveCreateFromPlanRequest request);

  /**
   * 一期一档：按已结束培训查询同一事实，缺失项不为空时不得当作正式档案。
   *
   * @param includeCertificateFacts 调用方是否具备证书查询权限；为 false 时不得返回证书清单
   */
  ArchiveViewVO getByPlan(Long trainingPlanId, boolean includeCertificateFacts);

  /**
   * 企业档案：按企业名称和培训字段筛选已结束培训。
   *
   * @param includeCertificateFacts 调用方是否具备证书查询权限；为 false 时不得返回证书清单
   */
  List<ArchiveViewVO> listEnterprise(ArchiveEnterpriseQuery query, boolean includeCertificateFacts);

  /**
   * 个人档案：按人员查询其已结束培训记录。
   *
   * @param includeCertificateFacts 调用方是否具备证书查询权限；为 false 时不得返回证书清单
   */
  ArchivePersonVO getPerson(Long userId, boolean includeCertificateFacts);

  StoredDownload openPlanFile(Long trainingPlanId);

  StoredDownload openPersonFile(Long userId);
}
