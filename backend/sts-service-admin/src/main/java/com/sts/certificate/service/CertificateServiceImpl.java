package com.sts.certificate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.storage.GeneratedFileStore;
import com.sts.admin.storage.StoredDownload;
import com.sts.certificate.dto.CertificateIssueRequest;
import com.sts.certificate.dto.CertificateQuery;
import com.sts.certificate.entity.CertificateCertificate;
import com.sts.certificate.entity.CertificateTemplate;
import com.sts.certificate.entity.CertificateTemplateElement;
import com.sts.certificate.entity.CertificateTemplateVersion;
import com.sts.certificate.mapper.CertificateCertificateMapper;
import com.sts.certificate.mapper.CertificateTemplateElementMapper;
import com.sts.certificate.mapper.CertificateTemplateMapper;
import com.sts.certificate.mapper.CertificateTemplateVersionMapper;
import com.sts.certificate.util.CertificateSourceTypes;
import com.sts.certificate.vo.CertificateVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.Course;
import com.sts.course.mapper.CourseMapper;
import com.sts.exam.entity.Exam;
import com.sts.exam.mapper.ExamMapper;
import com.sts.survey.util.SnapshotHashUtil;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.mapper.TrainingPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CertificateServiceImpl implements CertificateService {

  private static final DateTimeFormatter ISSUED_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final CertificateCertificateMapper certificateMapper;
  private final CertificateTemplateMapper templateMapper;
  private final CertificateTemplateVersionMapper templateVersionMapper;
  private final CertificateTemplateElementMapper templateElementMapper;
  private final SysUserMapper userMapper;
  private final ObjectMapper objectMapper;
  private final CertificateImageRenderer imageRenderer;
  private final GeneratedFileStore generatedFileStore;
  /** 证书来源主数据仅用于列表名称回填，不改变签发时的 PNG。 */
  private final CourseMapper courseMapper;
  private final ExamMapper examMapper;
  private final TrainingPlanMapper trainingPlanMapper;

  @Override
  @Transactional
  public CertificateVO issue(CertificateIssueRequest request) {
    CertificateCertificate existing = certificateMapper.selectOne(new LambdaQueryWrapper<CertificateCertificate>()
      .eq(CertificateCertificate::getHolderUserId, request.getHolderUserId())
      .eq(CertificateCertificate::getSourceType, request.getSourceType())
      .eq(CertificateCertificate::getSourceId, request.getSourceId())
      .eq(CertificateCertificate::getCertificateTemplateId, request.getCertificateTemplateId())
      .eq(CertificateCertificate::getCertificateStatus, "ISSUED"));
    if (existing != null) {
      return toVO(existing);
    }

    SysUser holder = userMapper.selectById(request.getHolderUserId());
    if (holder == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9412, "证书持有人不存在");
    }
    CertificateTemplate template = templateMapper.selectById(request.getCertificateTemplateId());
    if (template == null || !Integer.valueOf(1).equals(template.getStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9413, "证书模板不可用");
    }
    CertificateTemplateVersion version = templateVersionMapper.selectById(request.getTemplateVersionId());
    if (version == null || !request.getCertificateTemplateId().equals(version.getCertificateTemplateId())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9414, "证书模板版本不匹配");
    }

    String certificateNo = "CERT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    LocalDateTime issuedTime = LocalDateTime.now();
    Map<String, String> variables = variables(certificateNo, holder.getUserName(), request.getSourceType(), issuedTime);
    byte[] png = imageRenderer.render(version, elements(version.getTemplateVersionId()), variables);
    Long fileId = generatedFileStore.store(png, certificateNo + ".png", "certificate", "image/png", "png");

    String snapshotJson;
    try {
      snapshotJson = objectMapper.writeValueAsString(new LinkedHashMap<>(Map.of(
        "holderUserId", request.getHolderUserId(),
        "holderName", holder.getUserName() == null ? "" : holder.getUserName(),
        "sourceType", request.getSourceType(),
        "sourceId", request.getSourceId(),
        "templateVersionId", request.getTemplateVersionId(),
        "certificateNo", certificateNo
      )));
    } catch (Exception ex) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 9410, "证书快照序列化失败");
    }

    CertificateCertificate cert = new CertificateCertificate();
    cert.setCertificateNo(certificateNo);
    cert.setCertificateRuleId(request.getCertificateRuleId());
    cert.setCertificateTemplateId(request.getCertificateTemplateId());
    cert.setTemplateVersionId(request.getTemplateVersionId());
    cert.setHolderUserId(request.getHolderUserId());
    cert.setSourceType(request.getSourceType());
    cert.setSourceId(request.getSourceId());
    cert.setSourceResultId(request.getSourceResultId());
    cert.setIssuedTime(issuedTime);
    cert.setCertificateFileId(fileId);
    cert.setContentSnapshotJson(snapshotJson);
    cert.setSnapshotHash(SnapshotHashUtil.sha256Hex(snapshotJson));
    cert.setCertificateStatus("ISSUED");
    try {
      certificateMapper.insert(cert);
    } catch (DuplicateKeyException ex) {
      CertificateCertificate dup = certificateMapper.selectOne(new LambdaQueryWrapper<CertificateCertificate>()
        .eq(CertificateCertificate::getHolderUserId, request.getHolderUserId())
        .eq(CertificateCertificate::getSourceType, request.getSourceType())
        .eq(CertificateCertificate::getSourceId, request.getSourceId())
        .eq(CertificateCertificate::getCertificateTemplateId, request.getCertificateTemplateId()));
      return toVO(dup);
    }
    return toVO(cert);
  }

  @Override
  public PageResult<CertificateVO> page(PageRequest<CertificateQuery> request) {
    return pageInternal(request, null);
  }

  @Override
  public PageResult<CertificateVO> pageMine(Long holderUserId, PageRequest<CertificateQuery> request) {
    return pageInternal(request, holderUserId);
  }

  @Override
  public StoredDownload openCertificateFile(Long certificateId, Long currentUserId, boolean adminView) {
    CertificateCertificate cert = certificateMapper.selectById(certificateId);
    if (cert == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 9415, "证书不存在");
    }
    if (!adminView && !Objects.equals(cert.getHolderUserId(), currentUserId)) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 9416, "只能查看本人证书");
    }
    return generatedFileStore.open(cert.getCertificateFileId(), 9417, "证书文件不存在");
  }

  @Override
  public StoredDownload preview(Long certificateTemplateId) {
    CertificateTemplate template = templateMapper.selectById(certificateTemplateId);
    if (template == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 9401, "证书模板不存在");
    }
    CertificateTemplateVersion version = templateVersionMapper.selectOne(new LambdaQueryWrapper<CertificateTemplateVersion>()
                                                                           .eq(CertificateTemplateVersion::getCertificateTemplateId,
                                                                               certificateTemplateId)
                                                                           .eq(CertificateTemplateVersion::getVersionNo,
                                                                               template.getCurrentVersionNo())
                                                                           .eq(CertificateTemplateVersion::getPublishedStatus,
                                                                               "PUBLISHED"));
    if (version == null) {
      throw new BusinessException(HttpStatus.CONFLICT, 9414, "模板尚未发布，无法预览");
    }
    Map<String, String> variables = variables("CERT-SAMPLE", "张三", CertificateSourceTypes.TRAINING,
      LocalDateTime.now());
    byte[] png = imageRenderer.render(version, elements(version.getTemplateVersionId()), variables);
    return new StoredDownload(new ByteArrayResource(png), "certificate-preview.png", "image/png", png.length);
  }

  private PageResult<CertificateVO> pageInternal(PageRequest<CertificateQuery> request, Long forceHolderUserId) {
    CertificateQuery query = request.getQuery();
    LambdaQueryWrapper<CertificateCertificate> wrapper = new LambdaQueryWrapper<>();
    Long holderUserId = forceHolderUserId != null ? forceHolderUserId : (query == null ? null : query.getHolderUserId());
    String sourceType = query == null ? null : query.getSourceType();
    Long sourceId = query == null ? null : query.getSourceId();
    String status = query == null ? null : query.getCertificateStatus();
    wrapper.eq(holderUserId != null, CertificateCertificate::getHolderUserId, holderUserId)
      .eq(StringUtils.hasText(sourceType), CertificateCertificate::getSourceType, sourceType)
      .eq(sourceId != null, CertificateCertificate::getSourceId, sourceId)
      .eq(StringUtils.hasText(status), CertificateCertificate::getCertificateStatus, status)
      .orderByDesc(CertificateCertificate::getIssuedTime);
    Page<CertificateCertificate> page = certificateMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    List<CertificateVO> rows = page.getRecords().stream().map(this::toVO).toList();
    fillSourceNames(rows);
    return new PageResult<>(rows, page.getTotal());
  }

  /** 仅查询当前页涉及的三类来源，避免证书列表逐行访问主数据。 */
  private void fillSourceNames(List<CertificateVO> rows) {
    List<Long> courseIds = sourceIds(rows, "COURSE");
    List<Long> examIds = sourceIds(rows, "EXAM");
    List<Long> trainingIds = sourceIds(rows, "TRAINING");
    Map<Long, String> courses = courseIds.isEmpty() ? Map.of() : courseMapper.selectBatchIds(courseIds).stream()
      .collect(Collectors.toMap(Course::getCourseId, Course::getCourseName));
    Map<Long, String> exams = examIds.isEmpty() ? Map.of() : examMapper.selectBatchIds(examIds).stream()
      .collect(Collectors.toMap(Exam::getExamId, Exam::getExamName));
    Map<Long, String> trainings = trainingIds.isEmpty() ? Map.of() : trainingPlanMapper.selectBatchIds(trainingIds).stream()
      .collect(Collectors.toMap(TrainingPlan::getTrainingPlanId, TrainingPlan::getPlanName));
    rows.forEach(row -> row.setSourceName(switch (row.getSourceType()) {
      case "COURSE" -> courses.get(row.getSourceId());
      case "EXAM" -> exams.get(row.getSourceId());
      case "TRAINING" -> trainings.get(row.getSourceId());
      default -> null;
    }));
  }

  private static List<Long> sourceIds(List<CertificateVO> rows, String type) {
    return rows.stream().filter(row -> type.equals(row.getSourceType()))
      .map(CertificateVO::getSourceId).filter(Objects::nonNull).distinct().toList();
  }

  private List<CertificateTemplateElement> elements(Long templateVersionId) {
    return templateElementMapper.selectList(new LambdaQueryWrapper<CertificateTemplateElement>()
                                              .eq(CertificateTemplateElement::getTemplateVersionId, templateVersionId)
                                              .orderByAsc(CertificateTemplateElement::getSortOrder));
  }

  private static Map<String, String> variables(String certificateNo, String holderName, String sourceType,
                                               LocalDateTime issuedTime) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("CERT_NO", certificateNo);
    values.put("HOLDER_NAME", holderName == null ? "" : holderName);
    values.put("SOURCE_TYPE", CertificateSourceTypes.label(sourceType));
    values.put("ISSUED_TIME", issuedTime == null ? "" : issuedTime.format(ISSUED_TIME));
    return values;
  }

  private CertificateVO toVO(CertificateCertificate cert) {
    CertificateVO vo = new CertificateVO();
    BeanUtils.copyProperties(cert, vo);
    return vo;
  }
}
