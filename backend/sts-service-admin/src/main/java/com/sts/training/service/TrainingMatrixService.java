package com.sts.training.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.course.mapper.*;
import com.sts.org.mapper.OrgPositionMapper;
import com.sts.training.dto.*;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.vo.TrainingMatrixVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** 岗位矩阵复用现有课程编排和计划草稿事务。 */
@Service @RequiredArgsConstructor public class TrainingMatrixService {
  /** 矩阵要求。 */ private final TrainingMatrixMapper matrices;
  /** 计划来源快照。 */ private final TrainingMatrixSourceMapper sources;
  /** 岗位归属。 */ private final OrgPositionMapper positions;
  /** 课程归属。 */ private final CourseMapper courses;
  /** 不可变课程版本。 */ private final CourseVersionMapper versions;
  /** 既有计划能力。 */ private final TrainingPlanService plans;
  /** 快照序列化。 */ private final ObjectMapper json;
  /** 当前机构矩阵；停用项仍可维护。 */
  public List<TrainingMatrixVO> list() {
    return matrices.selectList(new LambdaQueryWrapper<TrainingMatrix>().eq(TrainingMatrix::getOrganizationId,
      TenantContext.requireTenantId()).orderByAsc(TrainingMatrix::getPositionId, TrainingMatrix::getMatrixId))
      .stream().map(this::toVO).toList();
  }
  /** 创建或按版本编辑要求，禁用代替删除。 */
  @Transactional public Long save(Long id, TrainingMatrixRequest request) {
    var position = positions.selectById(request.getPositionId());
    if (position == null || !Objects.equals(position.getOrganizationId(), TenantContext.requireTenantId())
      || !Integer.valueOf(1).equals(position.getStatus())) throw invalid("岗位不存在或已停用");
    var course = requireCourse(request.getCourseId(), request.getCourseVersionId());
    var row = id == null ? new TrainingMatrix() : requireMatrix(id);
    if (id != null && !Objects.equals(row.getVersion(), request.getVersion())) throw conflict();
    BeanUtils.copyProperties(request, row); row.setOrganizationId(TenantContext.requireTenantId());
    row.setPositionName(position.getPositionName()); row.setCourseName(course.getCourseName());
    if (id == null) matrices.insert(row); else if (matrices.updateById(row) == 0) throw conflict();
    return row.getMatrixId();
  }
  /** 选中要求在同一事务生成草稿课表和来源快照，失败全部回滚。 */
  @Transactional public Long createPlan(TrainingMatrixPlanRequest request) {
    if (request.getPlan().getSourceTemplateId() != null) throw invalid("矩阵创建不可同时复制模板，请先核对来源");
    var rows = request.getMatrixIds().stream().distinct().map(this::requireMatrix).toList();
    for (var row : rows) {
      if (!Integer.valueOf(1).equals(row.getStatus())) throw invalid("矩阵要求已停用，请重新选择");
      var position = positions.selectById(row.getPositionId());
      if (position == null || !Objects.equals(position.getOrganizationId(), TenantContext.requireTenantId())
        || !Integer.valueOf(1).equals(position.getStatus())) throw invalid("矩阵岗位已失效");
      requireCourse(row.getCourseId(), row.getCourseVersionId());
    }
    var contents = merge(rows);
    Long id = plans.create(request.getPlan());
    var stage = new TrainingPlanStageRequest(); stage.setStageName("岗位矩阵培训"); stage.setStageType("ONLINE");
    stage.setSortOrder(1); stage.setContents(contents);
    stage.setRequiredHours(contents.stream().map(TrainingPlanContentRequest::getRequiredHours).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
    var replace = new TrainingPlanStagesReplaceRequest(); replace.setVersion(plans.get(id).getVersion());
    replace.setStages(List.of(stage)); plans.replaceStages(id, replace);
    var source = new TrainingMatrixSource(); source.setOrganizationId(TenantContext.requireTenantId()); source.setTrainingPlanId(id);
    try { source.setSnapshotJson(json.writeValueAsString(rows.stream().map(this::toVO).toList())); }
    catch (JsonProcessingException e) { throw invalid("矩阵来源无法保存"); }
    sources.insert(source); return id;
  }
  /** 读取冻结来源而非重算当前矩阵；先校验计划归属。 */
  public List<TrainingMatrixVO> source(Long planId) {
    plans.get(planId);
    var source = sources.selectOne(new LambdaQueryWrapper<TrainingMatrixSource>()
      .eq(TrainingMatrixSource::getOrganizationId, TenantContext.requireTenantId()).eq(TrainingMatrixSource::getTrainingPlanId, planId));
    if (source == null) return List.of();
    try { return json.readValue(source.getSnapshotJson(), json.getTypeFactory().constructCollectionType(List.class, TrainingMatrixVO.class)); }
    catch (JsonProcessingException e) { throw invalid("矩阵来源无法读取"); }
  }
  /** 相同课程版本合并取最大学时，不把多岗位要求重复累加。 */
  static List<TrainingPlanContentRequest> merge(List<TrainingMatrix> rows) {
    if (rows.isEmpty()) throw invalid("请选择至少一项岗位培训要求");
    Map<String, TrainingPlanContentRequest> merged = new LinkedHashMap<>();
    for (var row : rows) {
      String key = row.getCourseId() + ":" + row.getCourseVersionId();
      var content = merged.get(key);
      if (content == null) {
        content = new TrainingPlanContentRequest(); content.setContentType("COURSE"); content.setResourceId(row.getCourseId());
        content.setResourceVersionId(row.getCourseVersionId()); content.setContentName(row.getContentName());
        content.setRequiredHours(row.getRequiredHours()); content.setIsRequired(1); content.setIsScored(0);
        content.setSortOrder(merged.size()+1); var config = new TrainingContentExecutionConfig();
        config.setCompletionRule("ALL_REQUIRED_ITEMS"); content.setExecutionConfig(config); merged.put(key, content);
      } else content.setRequiredHours(content.getRequiredHours().max(row.getRequiredHours()));
    }
    var total = merged.values().stream().map(TrainingPlanContentRequest::getRequiredHours)
      .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    if (total.compareTo(new java.math.BigDecimal("999999.99")) > 0) throw invalid("合并后的阶段学时超过上限，请分批创建计划");
    return new ArrayList<>(merged.values());
  }
  private TrainingMatrix requireMatrix(Long id) {
    var row = matrices.selectById(id);
    if (row == null || !Objects.equals(row.getOrganizationId(), TenantContext.requireTenantId())) throw invalid("矩阵要求不存在或不属于当前机构");
    return row;
  }
  private com.sts.course.entity.Course requireCourse(Long id, Long versionId) {
    var course = courses.selectById(id); var version = versions.selectById(versionId);
    if (course == null || !Objects.equals(course.getOrganizationId(), TenantContext.requireTenantId())
      || !Integer.valueOf(1).equals(course.getStatus()) || !"PUBLISHED".equals(course.getPublishStatus())
      || version == null || !Objects.equals(version.getCourseId(), id)) throw invalid("课程或发布版本不可用");
    return course;
  }
  private TrainingMatrixVO toVO(TrainingMatrix row) { var vo = new TrainingMatrixVO(); BeanUtils.copyProperties(row, vo); return vo; }
  private static BusinessException invalid(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, 8160, message); }
  private BusinessException conflict() { return new BusinessException(HttpStatus.CONFLICT, 8161, "矩阵版本冲突，请刷新"); }
}
