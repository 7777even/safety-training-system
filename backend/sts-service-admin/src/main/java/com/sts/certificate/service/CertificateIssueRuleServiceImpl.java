package com.sts.certificate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.certificate.dto.CertificateIssueRuleCreateRequest;
import com.sts.certificate.dto.CertificateIssueRuleUpdateRequest;
import com.sts.certificate.dto.CertificateRuleSourceQuery;
import com.sts.certificate.entity.CertificateIssueRule;
import com.sts.certificate.entity.CertificateTemplate;
import com.sts.certificate.entity.CertificateTemplateVersion;
import com.sts.certificate.mapper.CertificateIssueRuleMapper;
import com.sts.certificate.mapper.CertificateTemplateMapper;
import com.sts.certificate.mapper.CertificateTemplateVersionMapper;
import com.sts.certificate.vo.CertificateIssueRuleVO;
import com.sts.certificate.vo.CertificateRuleSourceCandidateVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.course.entity.Course;
import com.sts.course.mapper.CourseMapper;
import com.sts.exam.entity.Exam;
import com.sts.exam.mapper.ExamMapper;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.mapper.TrainingPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CertificateIssueRuleServiceImpl extends BaseServiceImpl<CertificateIssueRuleMapper, CertificateIssueRule>
  implements CertificateIssueRuleService {

  private final CertificateIssueRuleMapper ruleMapper;
  private final CertificateTemplateMapper templateMapper;
  private final CertificateTemplateVersionMapper templateVersionMapper;
  private final CourseMapper courseMapper;
  private final ExamMapper examMapper;
  private final TrainingPlanMapper trainingPlanMapper;

  private static final Set<String> SOURCE_TYPES = Set.of("COURSE", "EXAM", "TRAINING");

  @Override
  public List<CertificateIssueRuleVO> listRules() {
    // 租户条件由 TenantLineInnerInterceptor 注入，不再接受客户端指定机构
    return ruleMapper.selectList(new LambdaQueryWrapper<CertificateIssueRule>()
        .orderByDesc(CertificateIssueRule::getCreateTime))
      .stream().map(this::toVO).toList();
  }

  @Override
  public CertificateIssueRuleVO get(Long certificateRuleId) {
    return toVO(requireRule(certificateRuleId));
  }

  @Override
  @Transactional
  public Long create(CertificateIssueRuleCreateRequest request) {
    validateBinding(request);
    ensureSingleActiveRule(request.getSourceType(), request.getSourceId(), request.getStatus(), null);
    CertificateIssueRule rule = new CertificateIssueRule();
    BeanUtils.copyProperties(request, rule);
    try {
      ruleMapper.insert(rule);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 9412, "同来源与模板的签发规则已存在");
    }
    return rule.getCertificateRuleId();
  }

  @Override
  @Transactional
  public void update(Long certificateRuleId, CertificateIssueRuleUpdateRequest request) {
    CertificateIssueRule rule = requireRule(certificateRuleId);
    if (!Objects.equals(rule.getVersion(), request.getVersion())) {
      throw new BusinessException(HttpStatus.CONFLICT, 9409, "签发规则已被其他操作修改");
    }
    validateBinding(request);
    ensureSingleActiveRule(request.getSourceType(), request.getSourceId(), request.getStatus(),
                           certificateRuleId);
    BeanUtils.copyProperties(request, rule, "version");
    try {
      if (ruleMapper.updateById(rule) == 0) {
        throw new BusinessException(HttpStatus.CONFLICT, 9409, "签发规则已被其他操作修改");
      }
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 9412, "同来源与模板的签发规则已存在");
    }
  }

  @Override
  @Transactional
  public void delete(Long certificateRuleId) {
    requireRule(certificateRuleId);
    this.removeById(certificateRuleId);
  }

  @Override
  public PageResult<CertificateRuleSourceCandidateVO> pageSourceCandidates(
    PageRequest<CertificateRuleSourceQuery> request) {
    CertificateRuleSourceQuery query = request.getQuery();
    String type = query == null ? null : query.getSourceType();
    String keyword = query == null ? null : query.getKeyword();
    if (!SOURCE_TYPES.contains(type)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9408, "不支持的证书规则来源类型");
    }
    return switch (type) {
      case "COURSE" -> courseCandidates(request, keyword);
      case "EXAM" -> examCandidates(request, keyword);
      case "TRAINING" -> trainingCandidates(request, keyword);
      default -> throw new IllegalStateException("unreachable source type");
    };
  }

  private CertificateIssueRuleVO toVO(CertificateIssueRule rule) {
    CertificateIssueRuleVO vo = new CertificateIssueRuleVO();
    BeanUtils.copyProperties(rule, vo);
    CertificateTemplate template = templateMapper.selectById(rule.getCertificateTemplateId());
    vo.setCertificateTemplateName(template == null ? null : template.getTemplateName());
    vo.setSourceName(sourceName(rule.getSourceType(), rule.getSourceId()));
    return vo;
  }

  private CertificateIssueRule requireRule(Long certificateRuleId) {
    CertificateIssueRule rule = ruleMapper.selectById(certificateRuleId);
    if (rule == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 9411, "签发规则不存在");
    }
    return rule;
  }

  private void validateBinding(CertificateIssueRuleCreateRequest request) {
    CertificateTemplate template = templateMapper.selectById(request.getCertificateTemplateId());
    if (template == null || !Integer.valueOf(1).equals(template.getStatus())
      || template.getCurrentVersionNo() == null || template.getCurrentVersionNo() <= 0
      || templateVersionMapper.selectOne(new LambdaQueryWrapper<CertificateTemplateVersion>()
                                           .eq(CertificateTemplateVersion::getCertificateTemplateId,
                                               template.getCertificateTemplateId())
                                           .eq(CertificateTemplateVersion::getVersionNo, template.getCurrentVersionNo())
                                           .eq(CertificateTemplateVersion::getPublishedStatus, "PUBLISHED")) == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9407, "证书模板未启用或尚未发布");
    }
    if (!"PASS".equals(request.getConditionType()) || !eligibleSource(request.getSourceType(), request.getSourceId())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9408, "证书规则来源不可绑定");
    }
  }

  /**
   * 收口「一个来源对象只能绑定一个启用模板」：同一来源在启用态下至多一条规则。
   * 禁用态规则不参与约束，管理员可通过「先禁用旧规则、再启用新规则」替换来源所绑定的模板。
   *
   * @param excludeRuleId 更新场景需排除的自身规则 ID；新建时传 {@code null}
   */
  private void ensureSingleActiveRule(String sourceType, Long sourceId, Integer status, Long excludeRuleId) {
    if (!Integer.valueOf(1).equals(status)) {
      return;
    }
    Long active = ruleMapper.selectCount(new LambdaQueryWrapper<CertificateIssueRule>()
      .eq(CertificateIssueRule::getSourceType, sourceType)
      .eq(CertificateIssueRule::getSourceId, sourceId)
      .eq(CertificateIssueRule::getStatus, 1)
      .ne(excludeRuleId != null, CertificateIssueRule::getCertificateRuleId, excludeRuleId));
    if (active != null && active > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 9418, "该来源已存在启用中的签发规则，请先禁用后再启用新规则");
    }
  }

  private boolean eligibleSource(String sourceType, Long sourceId) {
    if (!SOURCE_TYPES.contains(sourceType) || sourceId == null) return false;
    return switch (sourceType) {
      case "COURSE" -> {
        Course value = courseMapper.selectById(sourceId);
        yield value != null && Integer.valueOf(1).equals(value.getStatus())
          && "PUBLISHED".equals(value.getPublishStatus());
      }
      case "EXAM" -> {
        Exam value = examMapper.selectById(sourceId);
        yield value != null && Integer.valueOf(1).equals(value.getStatus())
          && "ONLINE".equals(value.getExamType()) && "PUBLISHED".equals(value.getPublishStatus())
          && value.getPlanContentId() == null;
      }
      case "TRAINING" -> {
        TrainingPlan value = trainingPlanMapper.selectById(sourceId);
        yield value != null && Integer.valueOf(1).equals(value.getStatus())
          && Set.of("PUBLISHED", "RUNNING").contains(value.getPublishStatus());
      }
      default -> false;
    };
  }

  private String sourceName(String sourceType, Long sourceId) {
    if (sourceId == null || !SOURCE_TYPES.contains(sourceType)) return null;
    return switch (sourceType) {
      case "COURSE" -> {
        Course value = courseMapper.selectById(sourceId);
        yield value == null ? null : value.getCourseName();
      }
      case "EXAM" -> {
        Exam value = examMapper.selectById(sourceId);
        yield value == null ? null : value.getExamName();
      }
      case "TRAINING" -> {
        TrainingPlan value = trainingPlanMapper.selectById(sourceId);
        yield value == null ? null : value.getPlanName();
      }
      default -> null;
    };
  }

  private PageResult<CertificateRuleSourceCandidateVO> courseCandidates(
    PageRequest<CertificateRuleSourceQuery> request, String keyword) {
    Page<Course> page = courseMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()),
                                                new LambdaQueryWrapper<Course>()
                                                  .eq(Course::getPublishStatus, "PUBLISHED").eq(Course::getStatus, 1)
                                                  .and(StringUtils.hasText(keyword),
                                                       wrapper -> wrapper.like(Course::getCourseCode, keyword)
                                                         .or().like(Course::getCourseName, keyword))
                                                  .orderByDesc(Course::getPublishedTime).orderByDesc(Course::getCourseId));
    return new PageResult<>(page.getRecords().stream().map(value -> new CertificateRuleSourceCandidateVO(
      "COURSE", value.getCourseId(), value.getCourseCode(), value.getCourseName(), value.getPublishStatus())).toList(),
                            page.getTotal());
  }

  private PageResult<CertificateRuleSourceCandidateVO> examCandidates(
    PageRequest<CertificateRuleSourceQuery> request, String keyword) {
    Page<Exam> page = examMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()),
                                            new LambdaQueryWrapper<Exam>()
                                              .eq(Exam::getExamType, "ONLINE").eq(Exam::getPublishStatus,
                                                                                  "PUBLISHED").eq(Exam::getStatus, 1)
                                              .isNull(Exam::getPlanContentId)
                                              .and(StringUtils.hasText(keyword),
                                                   wrapper -> wrapper.like(Exam::getExamCode, keyword)
                                                     .or().like(Exam::getExamName, keyword))
                                              .orderByDesc(Exam::getPublishedTime).orderByDesc(Exam::getExamId));
    return new PageResult<>(page.getRecords().stream().map(value -> new CertificateRuleSourceCandidateVO(
      "EXAM", value.getExamId(), value.getExamCode(), value.getExamName(), value.getPublishStatus())).toList(),
                            page.getTotal());
  }

  private PageResult<CertificateRuleSourceCandidateVO> trainingCandidates(
    PageRequest<CertificateRuleSourceQuery> request, String keyword) {
    Page<TrainingPlan> page = trainingPlanMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()),
                                                            new LambdaQueryWrapper<TrainingPlan>()
                                                              .in(TrainingPlan::getPublishStatus,
                                                                  "PUBLISHED",
                                                                  "RUNNING").eq(TrainingPlan::getStatus, 1)
                                                              .and(StringUtils.hasText(keyword),
                                                                   wrapper -> wrapper.like(TrainingPlan::getPlanCode,
                                                                                           keyword)
                                                                     .or().like(TrainingPlan::getPlanName, keyword))
                                                              .orderByDesc(TrainingPlan::getPublishedTime).orderByDesc(
                                                                TrainingPlan::getTrainingPlanId));
    return new PageResult<>(page.getRecords().stream().map(value -> new CertificateRuleSourceCandidateVO(
      "TRAINING",
      value.getTrainingPlanId(),
      value.getPlanCode(),
      value.getPlanName(),
      value.getPublishStatus())).toList(),
                            page.getTotal());
  }
}
