package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.training.dto.*;
import com.sts.training.entity.TrainingStandard;
import com.sts.training.entity.TrainingStandardRule;
import com.sts.training.entity.TrainingType;
import com.sts.training.mapper.TrainingStandardMapper;
import com.sts.training.mapper.TrainingStandardRuleMapper;
import com.sts.training.vo.TrainingStandardRuleVO;
import com.sts.training.vo.TrainingStandardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TrainingStandardServiceImpl
  extends BaseServiceImpl<TrainingStandardMapper, TrainingStandard>
  implements TrainingStandardService {

  private final TrainingStandardMapper trainingStandardMapper;
  private final TrainingStandardRuleMapper trainingStandardRuleMapper;
  private final TrainingResourceAccessHelper resourceAccessHelper;

  @Override
  public PageResult<TrainingStandardVO> page(PageRequest<TrainingStandardQuery> request) {
    TrainingStandardQuery query = request.getQuery();
    LambdaQueryWrapper<TrainingStandard> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.eq(StringUtils.hasText(query.getStandardCode()),
                 TrainingStandard::getStandardCode,
                 query.getStandardCode())
        .like(StringUtils.hasText(query.getStandardName()), TrainingStandard::getStandardName, query.getStandardName())
        .eq(query.getTrainingTypeId() != null, TrainingStandard::getTrainingTypeId, query.getTrainingTypeId())
        .eq(StringUtils.hasText(query.getPublishStatus()), TrainingStandard::getPublishStatus, query.getPublishStatus())
        .eq(query.getStatus() != null, TrainingStandard::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(TrainingStandard::getCreateTime);
    Page<TrainingStandard> page = trainingStandardMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  public TrainingStandardVO get(Long trainingStandardId) {
    TrainingStandardVO vo = toVO(requireStandard(trainingStandardId));
    vo.setRules(listRules(trainingStandardId));
    return vo;
  }

  @Override
  @Transactional
  public Long create(TrainingStandardCreateRequest request) {
    resourceAccessHelper.requireTypeForUse(request.getTrainingTypeId());
    TrainingStandard standard = new TrainingStandard();
    standard.setStandardCode(request.getStandardCode().trim());
    standard.setStandardName(request.getStandardName().trim());
    standard.setTrainingTypeId(request.getTrainingTypeId());
    standard.setStandardVersion(request.getStandardVersion().trim());
    standard.setValidFrom(request.getValidFrom());
    standard.setValidTo(request.getValidTo());
    standard.setPublishStatus("DRAFT");
    standard.setStatus(request.getStatus());
    try {
      trainingStandardMapper.insert(standard);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 8012, "同机构下标准编码版本已存在");
    }
    return standard.getTrainingStandardId();
  }

  @Override
  @Transactional
  public void update(Long trainingStandardId, TrainingStandardUpdateRequest request) {
    TrainingStandard standard = requireDraft(trainingStandardId);
    resourceAccessHelper.requireTypeForUse(request.getTrainingTypeId());
    standard.setVersion(request.getVersion());
    standard.setStandardName(request.getStandardName().trim());
    standard.setTrainingTypeId(request.getTrainingTypeId());
    standard.setStandardVersion(request.getStandardVersion().trim());
    standard.setValidFrom(request.getValidFrom());
    standard.setValidTo(request.getValidTo());
    standard.setStatus(request.getStatus());
    int updated;
    try {
      updated = trainingStandardMapper.updateById(standard);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 8012, "同机构下标准编码版本已存在");
    }
    if (updated == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8014, "培训标准版本冲突");
    }
  }

  @Override
  @Transactional
  public void delete(Long trainingStandardId) {
    requireDraft(trainingStandardId);
    softDeleteRules(trainingStandardId);
    this.removeById(trainingStandardId);
  }

  @Override
  @Transactional
  public void replaceRules(Long trainingStandardId, TrainingStandardRulesReplaceRequest request) {
    TrainingStandard standard = requireDraft(trainingStandardId);
    List<TrainingStandardRuleRequest> rules = request.getRules() == null ? List.of() : request.getRules();
    assertNoDuplicateSingletonRules(rules.stream().map(TrainingStandardRuleRequest::getRuleType).toList());
    assertRulesRespectTypeRuler(standard.getTrainingTypeId(),
                                rules.stream()
                                  .map(rule -> new RulerRule(rule.getRuleType(), rule.getRequiredValue(),
                                                             rule.getStatus()))
                                  .toList());
    standard.setVersion(request.getVersion());
    if (trainingStandardMapper.updateById(standard) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8014, "培训标准版本冲突");
    }
    softDeleteRules(trainingStandardId);
    for (TrainingStandardRuleRequest ruleReq : rules) {
      TrainingStandardRule rule = new TrainingStandardRule();
      rule.setTrainingStandardId(trainingStandardId);
      rule.setRuleType(ruleReq.getRuleType());
      rule.setRequiredValue(ruleReq.getRequiredValue());
      rule.setUnit(ruleReq.getUnit());
      rule.setResourceType(ruleReq.getResourceType());
      rule.setResourceId(ruleReq.getResourceId());
      rule.setSortOrder(ruleReq.getSortOrder());
      rule.setStatus(ruleReq.getStatus());
      rule.setDescription(ruleReq.getDescription());
      trainingStandardRuleMapper.insert(rule);
    }
  }

  @Override
  @Transactional
  public void publish(Long trainingStandardId, Integer version) {
    TrainingStandard standard = requireStandard(trainingStandardId);
    if (!"DRAFT".equals(standard.getPublishStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8015, "当前状态不可发布");
    }
    List<TrainingStandardRule> persistedRules = trainingStandardRuleMapper.selectList(
      new LambdaQueryWrapper<TrainingStandardRule>()
        .eq(TrainingStandardRule::getTrainingStandardId, trainingStandardId)
        .orderByAsc(TrainingStandardRule::getSortOrder));
    assertNoDuplicateSingletonRules(persistedRules.stream().map(TrainingStandardRule::getRuleType).toList());
    assertRulesRespectTypeRuler(standard.getTrainingTypeId(),
                                persistedRules.stream()
                                  .map(rule -> new RulerRule(rule.getRuleType(), rule.getRequiredValue(),
                                                             rule.getStatus()))
                                  .toList());
    standard.setVersion(version);
    standard.setPublishStatus("PUBLISHED");
    if (trainingStandardMapper.updateById(standard) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8014, "培训标准版本冲突");
    }
  }

  TrainingStandard requireStandard(Long id) {
    TrainingStandard standard = trainingStandardMapper.selectById(id);
    if (standard == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8011, "培训标准不存在");
    }
    return standard;
  }

  /**
   * 同一标准有效规则中，总学时和线上占比各只能一条；禁用行也计入。
   */
  private void assertNoDuplicateSingletonRules(List<String> ruleTypes) {
    int totalHours = 0;
    int onlineRatio = 0;
    for (String ruleType : ruleTypes) {
      if ("TOTAL_HOURS".equals(ruleType)) {
        totalHours++;
      } else if ("ONLINE_RATIO".equals(ruleType)) {
        onlineRatio++;
      }
    }
    if (totalHours > 1) {
      throw new BusinessException(HttpStatus.CONFLICT, 8016, "总学时规则只能配置一条");
    }
    if (onlineRatio > 1) {
      throw new BusinessException(HttpStatus.CONFLICT, 8016, "线上学时占比规则只能配置一条");
    }
  }

  /**
   * 启用的总学时不得低于类型已配置正数学时的最小值；线上占比不得宽于类型上限。
   */
  private void assertRulesRespectTypeRuler(Long trainingTypeId, List<RulerRule> rules) {
    boolean needsType = rules.stream().anyMatch(rule -> Objects.equals(1, rule.status())
      && ("TOTAL_HOURS".equals(rule.ruleType()) || "ONLINE_RATIO".equals(rule.ruleType())));
    if (!needsType) {
      return;
    }
    TrainingType type = resourceAccessHelper.requireTypeForUse(trainingTypeId);
    BigDecimal minHours = minPositive(type.getInitialHours(), type.getReviewHours());
    for (RulerRule rule : rules) {
      if (!Objects.equals(1, rule.status())) {
        continue;
      }
      if ("TOTAL_HOURS".equals(rule.ruleType())
        && (rule.value() == null || rule.value().compareTo(BigDecimal.ZERO) <= 0
        || (minHours != null && rule.value().compareTo(minHours) < 0))) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 8017, "标准总学时不得低于培训类型已配置学时的最小值");
      }
      if ("ONLINE_RATIO".equals(rule.ruleType()) && type.getOnlineMaxRatio() != null
        && (rule.value() == null || rule.value().compareTo(type.getOnlineMaxRatio()) > 0)) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 8018, "标准线上占比不得宽于培训类型上限");
      }
    }
  }

  private BigDecimal minPositive(BigDecimal left, BigDecimal right) {
    BigDecimal min = null;
    for (BigDecimal value : new BigDecimal[]{left, right}) {
      if (value != null && value.compareTo(BigDecimal.ZERO) > 0
        && (min == null || value.compareTo(min) < 0)) {
        min = value;
      }
    }
    return min;
  }

  private record RulerRule(String ruleType, BigDecimal value, Integer status) {
  }

  private TrainingStandard requireDraft(Long id) {
    TrainingStandard standard = requireStandard(id);
    if (!"DRAFT".equals(standard.getPublishStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8013, "仅草稿标准可编辑或删除");
    }
    return standard;
  }

  private List<TrainingStandardRuleVO> listRules(Long trainingStandardId) {
    return trainingStandardRuleMapper.selectList(new LambdaQueryWrapper<TrainingStandardRule>()
        .eq(TrainingStandardRule::getTrainingStandardId, trainingStandardId)
        .orderByAsc(TrainingStandardRule::getSortOrder))
      .stream().map(this::toRuleVO).toList();
  }

  private void softDeleteRules(Long trainingStandardId) {
    trainingStandardRuleMapper.update(null, Wrappers.<TrainingStandardRule>lambdaUpdate()
      .setSql("deleted = standard_rule_id")
      .eq(TrainingStandardRule::getTrainingStandardId, trainingStandardId)
      .eq(TrainingStandardRule::getDeleted, 0L));
  }

  private TrainingStandardVO toVO(TrainingStandard standard) {
    TrainingStandardVO vo = new TrainingStandardVO();
    BeanUtils.copyProperties(standard, vo);
    return vo;
  }

  private TrainingStandardRuleVO toRuleVO(TrainingStandardRule rule) {
    TrainingStandardRuleVO vo = new TrainingStandardRuleVO();
    BeanUtils.copyProperties(rule, vo);
    return vo;
  }
}
