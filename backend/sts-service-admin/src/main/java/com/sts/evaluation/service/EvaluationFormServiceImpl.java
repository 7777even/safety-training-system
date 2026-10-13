package com.sts.evaluation.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.evaluation.dto.*;
import com.sts.evaluation.entity.EvaluationForm;
import com.sts.evaluation.entity.EvaluationFormVersion;
import com.sts.evaluation.entity.EvaluationItem;
import com.sts.evaluation.entity.EvaluationScoreRule;
import com.sts.evaluation.mapper.EvaluationFormMapper;
import com.sts.evaluation.mapper.EvaluationFormVersionMapper;
import com.sts.evaluation.mapper.EvaluationItemMapper;
import com.sts.evaluation.mapper.EvaluationScoreRuleMapper;
import com.sts.evaluation.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EvaluationFormServiceImpl
  extends BaseServiceImpl<EvaluationFormMapper, EvaluationForm>
  implements EvaluationFormService {

  private static final Set<String> PRODUCT_FORM_TYPES = Set.of("COMPOSITE", "EXPERT_INSPECTION");

  private final EvaluationFormMapper evaluationFormMapper;
  private final EvaluationFormVersionMapper evaluationFormVersionMapper;
  private final EvaluationItemMapper evaluationItemMapper;
  private final EvaluationScoreRuleMapper evaluationScoreRuleMapper;

  @Override
  public PageResult<EvaluationFormVO> page(PageRequest<EvaluationFormQuery> request) {
    EvaluationFormQuery query = request.getQuery();
    if (query != null && StringUtils.hasText(query.getFormType())
      && !PRODUCT_FORM_TYPES.contains(query.getFormType())) {
      return new PageResult<>(List.of(), 0L);
    }
    LambdaQueryWrapper<EvaluationForm> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.eq(StringUtils.hasText(query.getFormCode()), EvaluationForm::getFormCode, query.getFormCode())
        .like(StringUtils.hasText(query.getFormName()), EvaluationForm::getFormName, query.getFormName())
        .eq(StringUtils.hasText(query.getFormType()), EvaluationForm::getFormType, query.getFormType())
        .eq(query.getStatus() != null, EvaluationForm::getStatus, query.getStatus());
    }
    if (query == null || !StringUtils.hasText(query.getFormType())) {
      wrapper.in(EvaluationForm::getFormType, PRODUCT_FORM_TYPES);
    }
    wrapper.orderByDesc(EvaluationForm::getCreateTime);
    Page<EvaluationForm> page = evaluationFormMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  public EvaluationFormVO get(Long evaluationFormId) {
    return toVO(requireForm(evaluationFormId));
  }

  @Override
  @Transactional
  public Long create(EvaluationFormCreateRequest request) {
    EvaluationForm form = new EvaluationForm();
    form.setFormCode(request.getFormCode().trim());
    form.setFormName(request.getFormName().trim());
    form.setFormType(requireProductFormType(request.getFormType()));
    form.setCurrentVersionNo(0);
    form.setStatus(request.getStatus());
    form.setDescription(request.getDescription());
    try {
      evaluationFormMapper.insert(form);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 8502, "同机构下表单编码已存在");
    }
    return form.getEvaluationFormId();
  }

  @Override
  @Transactional
  public void update(Long evaluationFormId, EvaluationFormUpdateRequest request) {
    EvaluationForm form = requireForm(evaluationFormId);
    form.setVersion(request.getVersion());
    form.setFormName(request.getFormName().trim());
    form.setFormType(requireProductFormType(request.getFormType()));
    form.setStatus(request.getStatus());
    form.setDescription(request.getDescription());
    if (evaluationFormMapper.updateById(form) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8504, "评价表单版本冲突");
    }
  }

  @Override
  @Transactional
  public void delete(Long evaluationFormId) {
    requireForm(evaluationFormId);
    if (evaluationFormVersionMapper.selectCount(new LambdaQueryWrapper<EvaluationFormVersion>()
                                                  .eq(EvaluationFormVersion::getEvaluationFormId,
                                                      evaluationFormId)) > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8507, "评价表单已有发布版本，不可删除");
    }
    this.removeById(evaluationFormId);
  }

  @Override
  public EvaluationFormVersionDetailVO getVersionDetail(Long formVersionId) {
    EvaluationFormVersion version = requireVersion(formVersionId);
    return toVersionDetail(version);
  }

  @Override
  public List<EvaluationFormVersionSummaryVO> listVersionSummaries(Long evaluationFormId) {
    requireForm(evaluationFormId);
    return evaluationFormVersionMapper.selectList(new LambdaQueryWrapper<EvaluationFormVersion>()
                                                    .eq(EvaluationFormVersion::getEvaluationFormId, evaluationFormId)
                                                    .orderByDesc(EvaluationFormVersion::getVersionNo))
      .stream()
      .sorted(Comparator.comparing(EvaluationFormVersion::getVersionNo,
                                   Comparator.nullsLast(Comparator.reverseOrder())))
      .map(this::toVersionSummary).toList();
  }

  @Override
  @Transactional
  public EvaluationFormVersionDetailVO publishNewVersion(Long evaluationFormId, EvaluationFormPublishRequest request) {
    EvaluationForm form = requireForm(evaluationFormId);
    if (request.getItems() == null || request.getItems().isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8505, "评价项目不能为空");
    }
    validateCompositeRules(form, request);
    validateExpertInspectionRules(form, request);
    int nextNo = (form.getCurrentVersionNo() == null ? 0 : form.getCurrentVersionNo()) + 1;
    form.setVersion(request.getVersion());
    form.setCurrentVersionNo(nextNo);
    if (evaluationFormMapper.updateById(form) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8504, "评价表单版本冲突");
    }
    EvaluationFormVersion version = new EvaluationFormVersion();
    version.setEvaluationFormId(evaluationFormId);
    version.setVersionNo(nextNo);
    version.setTotalScore(request.getTotalScore());
    version.setPublishedStatus("PUBLISHED");
    version.setPublishedBy(StpUtil.getLoginIdAsLong());
    version.setPublishedTime(LocalDateTime.now());
    version.setVersionNote(request.getVersionNote());
    evaluationFormVersionMapper.insert(version);

    Map<String, Long> itemCodeToId = new HashMap<>();
    for (EvaluationItemRequest itemReq : request.getItems()) {
      EvaluationItem item = new EvaluationItem();
      item.setFormVersionId(version.getFormVersionId());
      item.setItemCode(itemReq.getItemCode());
      item.setItemName(itemReq.getItemName());
      item.setItemType(itemReq.getItemType());
      item.setMaxScore(itemReq.getMaxScore());
      item.setWeight(itemReq.getWeight());
      item.setSortOrder(itemReq.getSortOrder());
      item.setIsRequired(itemReq.getIsRequired());
      evaluationItemMapper.insert(item);
      itemCodeToId.put(itemReq.getItemCode(), item.getEvaluationItemId());
    }

    if (request.getScoreRules() != null) {
      for (EvaluationScoreRuleRequest ruleReq : request.getScoreRules()) {
        EvaluationScoreRule rule = new EvaluationScoreRule();
        rule.setFormVersionId(version.getFormVersionId());
        Long evaluationItemId = itemCodeToId.get(ruleReq.getEvaluationItemCode());
        if (evaluationItemId == null) {
          throw new BusinessException(HttpStatus.BAD_REQUEST, 8506, "评分规则引用的评价项目编码不存在");
        }
        rule.setEvaluationItemId(evaluationItemId);
        rule.setCalculationType(ruleReq.getCalculationType());
        rule.setSourceType(ruleReq.getSourceType());
        rule.setScoreChange(ruleReq.getScoreChange());
        rule.setWeight(ruleReq.getWeight());
        rule.setSortOrder(ruleReq.getSortOrder());
        rule.setRuleDescription(ruleReq.getRuleDescription());
        evaluationScoreRuleMapper.insert(rule);
      }
    }

    return toVersionDetail(version);
  }

  /**
   * 综合成绩只允许引用可由培训计划正式事实产生的类别分，避免项目权重和规则权重形成两套公式。
   */
  private void validateCompositeRules(EvaluationForm form, EvaluationFormPublishRequest request) {
    if (!"COMPOSITE".equals(form.getFormType())) {
      return;
    }
    List<EvaluationScoreRuleRequest> rules = request.getScoreRules();
    if (rules == null || rules.isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8508, "综合评价表必须配置课程、考试或线下成绩来源规则");
    }
    Map<String, EvaluationItemRequest> items = new HashMap<>();
    for (EvaluationItemRequest item : request.getItems()) {
      if (item.getItemCode() == null || !items.containsKey(item.getItemCode())) {
        items.put(item.getItemCode(), item);
      } else {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 8508, "综合评价表项目编码不能重复");
      }
    }
    Set<String> sources = new HashSet<>();
    Set<String> linkedItems = new HashSet<>();
    BigDecimal total = BigDecimal.ZERO;
    for (EvaluationScoreRuleRequest rule : rules) {
      if (!"WEIGHT".equals(rule.getCalculationType())
        || !("COURSE".equals(rule.getSourceType()) || "EXAM".equals(rule.getSourceType()) || "OFFLINE".equals(rule.getSourceType()))
        || rule.getWeight() == null || rule.getWeight().compareTo(BigDecimal.ZERO) <= 0
        || !sources.add(rule.getSourceType()) || !linkedItems.add(rule.getEvaluationItemCode())) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 8508, "综合评价规则仅支持唯一的课程、考试、线下加权来源");
      }
      EvaluationItemRequest item = items.get(rule.getEvaluationItemCode());
      if (item == null || !"SCORE".equals(item.getItemType()) || !Integer.valueOf(1).equals(item.getIsRequired())
        || item.getMaxScore() == null || item.getMaxScore().compareTo(BigDecimal.valueOf(100)) != 0
        || (item.getWeight() != null && item.getWeight().compareTo(rule.getWeight()) != 0)) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 8508, "综合评价规则必须关联必填、满分100且权重一致的分数项目");
      }
      total = total.add(rule.getWeight());
    }
    if (total.compareTo(BigDecimal.ONE) != 0) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8508, "综合评价规则权重合计必须为1");
    }
  }

  /** 专家查课只保存现场分数和意见，不允许复用综合评价的自动规则。 */
  private void validateExpertInspectionRules(EvaluationForm form, EvaluationFormPublishRequest request) {
    if (!"EXPERT_INSPECTION".equals(form.getFormType())) {
      return;
    }
    if (request.getScoreRules() != null && !request.getScoreRules().isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8508, "专家检查表不允许配置自动评分规则");
    }
    BigDecimal total = BigDecimal.ZERO;
    for (EvaluationItemRequest item : request.getItems()) {
      if (!"SCORE".equals(item.getItemType()) && !"TEXT".equals(item.getItemType())) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 8508, "专家检查表仅支持分数项和文本意见项");
      }
      if ("SCORE".equals(item.getItemType())) {
        if (item.getMaxScore() == null || item.getMaxScore().compareTo(BigDecimal.ZERO) <= 0 || item.getWeight() != null) {
          throw new BusinessException(HttpStatus.BAD_REQUEST, 8508, "专家检查分数项必须设置正数满分且不配置权重");
        }
        total = total.add(item.getMaxScore());
      } else if (item.getMaxScore() != null || item.getWeight() != null) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 8508, "专家检查文本项不得设置满分或权重");
      }
    }
    if (total.compareTo(BigDecimal.valueOf(100)) != 0 || request.getTotalScore().compareTo(BigDecimal.valueOf(100)) != 0) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8508, "专家检查表分数项满分合计和版本总分必须为100");
    }
  }

  /**
   * 产品入口只允许综合评价表和专家查课表。
   */
  private String requireProductFormType(String formType) {
    if (!PRODUCT_FORM_TYPES.contains(formType)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8508, "仅支持综合评价表或专家查课表");
    }
    return formType;
  }

  EvaluationForm requireForm(Long id) {
    EvaluationForm form = evaluationFormMapper.selectById(id);
    if (form == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8501, "评价表单不存在");
    }
    return form;
  }

  EvaluationFormVersion requireVersion(Long formVersionId) {
    EvaluationFormVersion version = evaluationFormVersionMapper.selectById(formVersionId);
    if (version == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8503, "评价表单版本不存在");
    }
    if ("PUBLISHED".equals(version.getPublishedStatus())) {
      // read-only access is allowed for getVersionDetail
    }
    return version;
  }

  private EvaluationFormVersionDetailVO toVersionDetail(EvaluationFormVersion version) {
    EvaluationFormVersionDetailVO vo = new EvaluationFormVersionDetailVO();
    BeanUtils.copyProperties(version, vo);
    vo.setItems(evaluationItemMapper.selectList(new LambdaQueryWrapper<EvaluationItem>()
        .eq(EvaluationItem::getFormVersionId, version.getFormVersionId())
        .orderByAsc(EvaluationItem::getSortOrder))
      .stream().map(this::toItemVO).toList());
    vo.setScoreRules(evaluationScoreRuleMapper.selectList(new LambdaQueryWrapper<EvaluationScoreRule>()
        .eq(EvaluationScoreRule::getFormVersionId, version.getFormVersionId())
        .orderByAsc(EvaluationScoreRule::getSortOrder))
      .stream().map(this::toScoreRuleVO).toList());
    return vo;
  }

  private EvaluationFormVersionSummaryVO toVersionSummary(EvaluationFormVersion version) {
    EvaluationFormVersionSummaryVO vo = new EvaluationFormVersionSummaryVO();
    BeanUtils.copyProperties(version, vo);
    return vo;
  }

  private EvaluationFormVO toVO(EvaluationForm form) {
    EvaluationFormVO vo = new EvaluationFormVO();
    BeanUtils.copyProperties(form, vo);
    return vo;
  }

  private EvaluationItemVO toItemVO(EvaluationItem item) {
    EvaluationItemVO vo = new EvaluationItemVO();
    BeanUtils.copyProperties(item, vo);
    return vo;
  }

  private EvaluationScoreRuleVO toScoreRuleVO(EvaluationScoreRule rule) {
    EvaluationScoreRuleVO vo = new EvaluationScoreRuleVO();
    BeanUtils.copyProperties(rule, vo);
    return vo;
  }
}
