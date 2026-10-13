package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.training.dto.*;
import com.sts.training.entity.TrainingTemplate;
import com.sts.training.entity.TrainingTemplateContent;
import com.sts.training.entity.TrainingTemplateStage;
import com.sts.training.mapper.TrainingTemplateContentMapper;
import com.sts.training.mapper.TrainingTemplateMapper;
import com.sts.training.mapper.TrainingTemplateStageMapper;
import com.sts.training.vo.TrainingTemplateContentVO;
import com.sts.training.vo.TrainingTemplateStageVO;
import com.sts.training.vo.TrainingTemplateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TrainingTemplateServiceImpl
  extends BaseServiceImpl<TrainingTemplateMapper, TrainingTemplate>
  implements TrainingTemplateService {

  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("COURSE", "EXAM", "QUESTION_BANK", "OFFLINE");

  private final TrainingTemplateMapper trainingTemplateMapper;
  private final TrainingTemplateStageMapper trainingTemplateStageMapper;
  private final TrainingTemplateContentMapper trainingTemplateContentMapper;
  private final TrainingResourceAccessHelper resourceAccessHelper;
  private final TrainingContentConfigCodec configCodec;

  @Override
  public PageResult<TrainingTemplateVO> page(PageRequest<TrainingTemplateQuery> request) {
    TrainingTemplateQuery query = request.getQuery();
    LambdaQueryWrapper<TrainingTemplate> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.eq(StringUtils.hasText(query.getTemplateCode()),
                 TrainingTemplate::getTemplateCode,
                 query.getTemplateCode())
        .like(StringUtils.hasText(query.getTemplateName()), TrainingTemplate::getTemplateName, query.getTemplateName())
        .eq(query.getTemplateCategoryId() != null, TrainingTemplate::getTemplateCategoryId, query.getTemplateCategoryId())
        .eq(query.getTrainingTypeId() != null, TrainingTemplate::getTrainingTypeId, query.getTrainingTypeId())
        .eq(StringUtils.hasText(query.getPublishStatus()), TrainingTemplate::getPublishStatus, query.getPublishStatus())
        .eq(query.getStatus() != null, TrainingTemplate::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(TrainingTemplate::getCreateTime);
    Page<TrainingTemplate> page = trainingTemplateMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  public TrainingTemplateVO get(Long trainingTemplateId) {
    TrainingTemplateVO vo = toVO(requireTemplate(trainingTemplateId));
    vo.setStages(listStages(trainingTemplateId));
    return vo;
  }

  @Override
  @Transactional
  public Long create(TrainingTemplateCreateRequest request) {
    resourceAccessHelper.requireTypeForUse(request.getTrainingTypeId());
    resourceAccessHelper.requireStandardForUse(request.getTrainingStandardId());
    TrainingTemplate template = new TrainingTemplate();
    template.setTemplateCode(request.getTemplateCode().trim());
    template.setTemplateName(request.getTemplateName().trim());
    template.setTemplateCategoryId(request.getTemplateCategoryId());
    template.setTrainingTypeId(request.getTrainingTypeId());
    template.setTrainingStandardId(request.getTrainingStandardId());
    template.setDescription(request.getDescription());
    template.setPublishStatus("DRAFT");
    template.setStatus(request.getStatus());
    try {
      trainingTemplateMapper.insert(template);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 8032, "同机构下模板编码已存在");
    }
    return template.getTrainingTemplateId();
  }

  @Override
  @Transactional
  public void update(Long trainingTemplateId, TrainingTemplateUpdateRequest request) {
    TrainingTemplate template = requireDraft(trainingTemplateId);
    resourceAccessHelper.requireTypeForUse(request.getTrainingTypeId());
    resourceAccessHelper.requireStandardForUse(request.getTrainingStandardId());
    template.setVersion(request.getVersion());
    template.setTemplateName(request.getTemplateName().trim());
    template.setTemplateCategoryId(request.getTemplateCategoryId());
    template.setTrainingTypeId(request.getTrainingTypeId());
    template.setTrainingStandardId(request.getTrainingStandardId());
    template.setDescription(request.getDescription());
    template.setStatus(request.getStatus());
    if (trainingTemplateMapper.updateById(template) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8034, "培训模板版本冲突");
    }
  }

  @Override
  @Transactional
  public void delete(Long trainingTemplateId) {
    requireDraft(trainingTemplateId);
    deleteStagesAndContents(trainingTemplateId);
    this.removeById(trainingTemplateId);
  }

  @Override
  @Transactional
  public void replaceStages(Long trainingTemplateId, TrainingTemplateStagesReplaceRequest request) {
    TrainingTemplate template = requireTemplate(trainingTemplateId);
    if (!"DRAFT".equals(template.getPublishStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8035, "已发布模板不可编辑阶段");
    }
    if (request.getStages() != null) {
      for (TrainingTemplateStageRequest stageReq : request.getStages()) {
        if (stageReq.getContents() == null) {
          continue;
        }
        for (TrainingTemplateContentRequest contentReq : stageReq.getContents()) {
          validateContentType(contentReq.getContentType());
          TrainingArrangedHours.requireForSave(contentReq.getContentType(), contentReq.getRequiredHours());
        }
      }
    }
    template.setVersion(request.getVersion());
    if (trainingTemplateMapper.updateById(template) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8034, "培训模板版本冲突");
    }
    deleteStagesAndContents(trainingTemplateId);
    if (request.getStages() != null) {
      for (TrainingTemplateStageRequest stageReq : request.getStages()) {
        TrainingTemplateStage stage = new TrainingTemplateStage();
        stage.setTrainingTemplateId(trainingTemplateId);
        stage.setStageName(stageReq.getStageName());
        stage.setStageType(stageReq.getStageType());
        stage.setRequiredHours(stageReq.getRequiredHours());
        stage.setSortOrder(stageReq.getSortOrder());
        stage.setStatus(stageReq.getStatus());
        trainingTemplateStageMapper.insert(stage);
        if (stageReq.getContents() != null) {
          for (TrainingTemplateContentRequest contentReq : stageReq.getContents()) {
            TrainingTemplateContent content = new TrainingTemplateContent();
            content.setTemplateStageId(stage.getTemplateStageId());
            content.setContentType(contentReq.getContentType());
            content.setResourceId(contentReq.getResourceId());
            content.setResourceVersionId(contentReq.getResourceVersionId());
            content.setAssessmentMode(contentReq.getAssessmentMode());
            content.setExecutionConfigJson(configCodec.toJson(contentReq.getExecutionConfig()));
            content.setContentName(contentReq.getContentName());
            content.setIsRequired(contentReq.getIsRequired());
            content.setRequiredHours(TrainingArrangedHours.requireForSave(
              contentReq.getContentType(), contentReq.getRequiredHours()));
            content.setSortOrder(contentReq.getSortOrder());
            content.setStatus(contentReq.getStatus());
            trainingTemplateContentMapper.insert(content);
          }
        }
      }
    }
  }

  @Override
  @Transactional
  public void publish(Long trainingTemplateId, Integer version) {
    TrainingTemplate template = requireTemplate(trainingTemplateId);
    if (!"DRAFT".equals(template.getPublishStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8036, "当前状态不可发布");
    }
    assertTemplateHasSyllabus(trainingTemplateId);
    template.setVersion(version);
    template.setPublishStatus("PUBLISHED");
    if (trainingTemplateMapper.updateById(template) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8034, "培训模板版本冲突");
    }
  }

  /**
   * 发布前须至少有一个阶段和一个内容；不校验执行配置。
   */
  private void assertTemplateHasSyllabus(Long trainingTemplateId) {
    List<TrainingTemplateStage> stages = trainingTemplateStageMapper.selectList(
      new LambdaQueryWrapper<TrainingTemplateStage>()
        .eq(TrainingTemplateStage::getTrainingTemplateId, trainingTemplateId));
    boolean hasContent = false;
    for (TrainingTemplateStage stage : stages) {
      List<TrainingTemplateContent> contents = trainingTemplateContentMapper.selectList(
        new LambdaQueryWrapper<TrainingTemplateContent>()
          .eq(TrainingTemplateContent::getTemplateStageId, stage.getTemplateStageId()));
      if (!contents.isEmpty()) {
        hasContent = true;
        break;
      }
    }
    if (stages.isEmpty() || !hasContent) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8042, "模板发布前须至少有一个阶段和一个内容");
    }
  }

  TrainingTemplate requireTemplate(Long id) {
    TrainingTemplate template = trainingTemplateMapper.selectById(id);
    if (template == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8031, "培训模板不存在");
    }
    return template;
  }

  private TrainingTemplate requireDraft(Long id) {
    TrainingTemplate template = requireTemplate(id);
    if (!"DRAFT".equals(template.getPublishStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8033, "仅草稿模板可编辑或删除");
    }
    return template;
  }

  private void validateContentType(String contentType) {
    if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8038, "不支持的内容类型");
    }
  }

  private void deleteStagesAndContents(Long trainingTemplateId) {
    List<TrainingTemplateStage> stages = trainingTemplateStageMapper.selectList(
      new LambdaQueryWrapper<TrainingTemplateStage>().eq(TrainingTemplateStage::getTrainingTemplateId, trainingTemplateId));
    for (TrainingTemplateStage stage : stages) {
      trainingTemplateContentMapper.update(null, Wrappers.<TrainingTemplateContent>lambdaUpdate()
        .setSql("deleted = template_content_id")
        .eq(TrainingTemplateContent::getTemplateStageId, stage.getTemplateStageId())
        .eq(TrainingTemplateContent::getDeleted, 0L));
    }
    trainingTemplateStageMapper.update(null, Wrappers.<TrainingTemplateStage>lambdaUpdate()
      .setSql("deleted = template_stage_id")
      .eq(TrainingTemplateStage::getTrainingTemplateId, trainingTemplateId)
      .eq(TrainingTemplateStage::getDeleted, 0L));
  }

  private List<TrainingTemplateStageVO> listStages(Long trainingTemplateId) {
    return trainingTemplateStageMapper.selectList(new LambdaQueryWrapper<TrainingTemplateStage>()
        .eq(TrainingTemplateStage::getTrainingTemplateId, trainingTemplateId)
        .orderByAsc(TrainingTemplateStage::getSortOrder))
      .stream().map(stage -> {
        TrainingTemplateStageVO vo = new TrainingTemplateStageVO();
        BeanUtils.copyProperties(stage, vo);
        vo.setContents(trainingTemplateContentMapper.selectList(new LambdaQueryWrapper<TrainingTemplateContent>()
            .eq(TrainingTemplateContent::getTemplateStageId, stage.getTemplateStageId())
            .orderByAsc(TrainingTemplateContent::getSortOrder))
          .stream().map(this::toContentVO).toList());
        return vo;
      }).toList();
  }

  private TrainingTemplateVO toVO(TrainingTemplate template) {
    TrainingTemplateVO vo = new TrainingTemplateVO();
    BeanUtils.copyProperties(template, vo);
    return vo;
  }

  private TrainingTemplateContentVO toContentVO(TrainingTemplateContent content) {
    TrainingTemplateContentVO vo = new TrainingTemplateContentVO();
    BeanUtils.copyProperties(content, vo);
    vo.setExecutionConfig(configCodec.fromJson(content.getExecutionConfigJson()));
    return vo;
  }
}
