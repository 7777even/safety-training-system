package com.sts.archive.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.admin.storage.StoredDownload;
import com.sts.archive.dto.ArchiveCreateFromPlanRequest;
import com.sts.archive.dto.ArchiveEnterpriseQuery;
import com.sts.archive.service.ArchiveCertificateGuard;
import com.sts.archive.service.ArchiveService;
import com.sts.archive.vo.ArchivePersonVO;
import com.sts.archive.vo.ArchiveTrainingVO;
import com.sts.archive.vo.ArchiveViewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 三级档案查询与更新入口。版本历史和导出任务中心不再对外提供。
 */
@RestController
@RequiredArgsConstructor
public class ArchiveController {

  private final ArchiveService archiveService;
  private final ArchiveCertificateGuard certificateGuard;
  /** 统一历史只补在线个人档案，不改变既有PDF归档事实。 */
  private final com.sts.learner.service.LearnerHistoryService history;

  /**
   * 根据已结束且结果完整的培训更新档案快照；缺少结果等必需事实时拒绝。
   */
  @PostMapping("/archive/trainings/create-from-plan")
  @SaCheckPermission("archive:training:create")
  public ArchiveTrainingVO createFromPlan(@RequestBody @Valid ArchiveCreateFromPlanRequest request) {
    return archiveService.createFromPlan(request);
  }

  /**
   * 一期一档：查看指定培训的档案事实，缺失项不为空时不得当作正式档案完成。
   * <p>证书清单受 {@code certificate:cert:list} 门控；缺少该权限时响应不含证书清单字段。
   */
  @GetMapping("/archive/trainings/{trainingPlanId}")
  @SaCheckPermission("archive:training:list")
  public ArchiveViewVO getByPlan(@PathVariable Long trainingPlanId) {
    return archiveService.getByPlan(trainingPlanId, certificateGuard.canListCertificates());
  }

  /**
   * 下载一期一档文件；缺少必需事实时拒绝。
   */
  @GetMapping("/archive/trainings/{trainingPlanId}/file")
  @SaCheckPermission("archive:training:list")
  public ResponseEntity<Resource> downloadPlanFile(@PathVariable Long trainingPlanId) {
    return toFile(archiveService.openPlanFile(trainingPlanId));
  }

  /**
   * 企业档案：按企业名称和培训字段筛选已结束培训。证书清单门控同一期一档。
   */
  @GetMapping("/archive/enterprises")
  @SaCheckPermission("archive:training:list")
  public List<ArchiveViewVO> listEnterprise(ArchiveEnterpriseQuery query) {
    return archiveService.listEnterprise(query, certificateGuard.canListCertificates());
  }

  /**
   * 个人档案：按人员查询已结束培训记录、奖惩、综合评价和证书入口数据。证书清单门控同一期一档。
   */
  @GetMapping("/archive/persons/{userId}")
  @SaCheckPermission("archive:training:list")
  public ArchivePersonVO getPerson(@PathVariable Long userId) {
    var person = archiveService.getPerson(userId, certificateGuard.canListCertificates());
    person.setHistory(history.forPerson(userId));
    return person;
  }

  /**
   * 下载个人档案文件；缺少必需事实时拒绝。
   */
  @GetMapping("/archive/persons/{userId}/file")
  @SaCheckPermission("archive:training:list")
  public ResponseEntity<Resource> downloadPersonFile(@PathVariable Long userId) {
    return toFile(archiveService.openPersonFile(userId));
  }

  private static ResponseEntity<Resource> toFile(StoredDownload file) {
    ContentDisposition disposition = ContentDisposition.attachment()
      .filename(file.fileName(), StandardCharsets.UTF_8)
      .build();
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(file.contentType()))
      .contentLength(file.fileSize())
      .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
      .body(file.resource());
  }
}
