package com.sts.person.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.person.dto.*;
import com.sts.person.entity.PersonGroup;
import com.sts.person.mapper.*;
import com.sts.person.vo.PersonGroupVO;
import com.sts.person.vo.PersonPickerItemVO;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.service.TrainingResultSyncService;
import org.springframework.beans.factory.ObjectProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

/** 条件分类、用户组维护和当次成员展开。 */
@Service @RequiredArgsConstructor
public class PersonClassificationService {
  /** 用户组持久化。 */
  private final PersonGroupMapper groups;
  /** 当前人员及培训事实查询。 */
  private final PersonClassificationMapper people;
  /** 固定条件编解码。 */
  private final ObjectMapper json;
  /** 名单预览只返回既有候选人员展示字段。 */
  private final SysUserMapper users;
  /** 已匹配人员在指定计划中的参与事实。 */
  private final TrainingParticipantMapper participants;
  /** 延迟取只读结果服务，避免考试安排→人员选择→结果适配器的构造循环。 */
  private final ObjectProvider<TrainingResultSyncService> results;

  /** 返回可核对姓名的预览，避免前端展示主键代替人员。 */
  public List<PersonPickerItemVO> preview(PersonClassificationCondition condition) {
    List<Long> ids = resolve(condition);
    if (ids.isEmpty()) return List.of();
    return users.selectList(new LambdaQueryWrapper<SysUser>()
      .eq(SysUser::getOrganizationId, TenantContext.requireTenantId()).eq(SysUser::getStatus, 1)
      .in(SysUser::getUserId, ids).orderByAsc(SysUser::getUserId)).stream().map(user -> {
        PersonPickerItemVO vo = new PersonPickerItemVO(); BeanUtils.copyProperties(user, vo); return vo;
      }).toList();
  }

  /** 返回当前条件匹配人员，历史下发关系不参与计算。 */
  public List<Long> resolve(PersonClassificationCondition condition) {
    validate(condition);
    List<Long> ids = people.resolve(TenantContext.requireTenantId(), condition, LocalDate.now());
    if (ids.isEmpty() || condition.getResultStatuses() == null || condition.getResultStatuses().isEmpty()) return ids;
    // 复用正式来源实时结果，不能把未刷新的汇总列或过期投影当当前事实。
    return participants.selectList(new LambdaQueryWrapper<TrainingParticipant>()
      .eq(TrainingParticipant::getOrganizationId, TenantContext.requireTenantId())
      .eq(TrainingParticipant::getTrainingPlanId, condition.getTrainingPlanId())
      .ne(TrainingParticipant::getParticipantStatus, "WITHDRAWN").in(TrainingParticipant::getUserId, ids))
      .stream().filter(person -> condition.getResultStatuses().contains(results.getObject()
        .getCurrentResults(person.getTrainingPlanId(), person.getTrainingParticipantId()).getResultStatus()))
      .map(TrainingParticipant::getUserId).distinct().sorted().toList();
  }

  /** 多组取并集，仅允许本租户启用组。 */
  public List<Long> resolveGroups(List<Long> ids) {
    if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id <= 0))
      throw invalid("请选择有效用户组");
    Set<Long> users = new TreeSet<>();
    for (Long id : new LinkedHashSet<>(ids)) {
      PersonGroup group = requireGroup(id);
      if (!Integer.valueOf(1).equals(group.getStatus())) throw invalid("用户组已停用");
      users.addAll(resolve(readCondition(group.getConditionsJson())));
    }
    if (users.isEmpty()) throw invalid("用户组当前没有符合条件的启用人员");
    return new ArrayList<>(users);
  }

  /** 当前租户的组配置；停用组仍可编辑。 */
  public List<PersonGroupVO> list() {
    return groups.selectList(new LambdaQueryWrapper<PersonGroup>()
      .eq(PersonGroup::getOrganizationId, TenantContext.requireTenantId())
      .orderByAsc(PersonGroup::getGroupName)).stream().map(group -> {
        PersonGroupVO vo = new PersonGroupVO(); BeanUtils.copyProperties(group, vo);
        vo.setConditions(readCondition(group.getConditionsJson())); return vo;
      }).toList();
  }

  /** 创建或版本化编辑条件组，不改变已下发名单。 */
  @Transactional
  public Long save(Long id, PersonGroupRequest request) {
    validate(request.getConditions());
    PersonGroup group = id == null ? new PersonGroup() : requireGroup(id);
    if (id != null && (request.getVersion() == null || !request.getVersion().equals(group.getVersion())))
      throw conflict("用户组版本冲突，请刷新");
    group.setOrganizationId(TenantContext.requireTenantId());
    group.setGroupName(request.getGroupName().trim()); group.setStatus(request.getStatus());
    try { group.setConditionsJson(json.writeValueAsString(request.getConditions())); }
    catch (JsonProcessingException e) { throw invalid("分类条件无效"); }
    try {
      if (id == null) groups.insert(group);
      else { group.setVersion(request.getVersion()); if (groups.updateById(group) == 0) throw conflict("用户组版本冲突"); }
    } catch (DuplicateKeyException e) {
      throw new BusinessException(HttpStatus.CONFLICT, 4121, "用户组名称已存在");
    }
    return group.getGroupId();
  }

  /** 按租户检查对象，禁止读取其它租户条件。 */
  private PersonGroup requireGroup(Long id) {
    PersonGroup group = groups.selectOne(new LambdaQueryWrapper<PersonGroup>()
      .eq(PersonGroup::getGroupId, id).eq(PersonGroup::getOrganizationId, TenantContext.requireTenantId()));
    if (group == null) throw invalid("用户组不存在或不属于当前机构");
    return group;
  }

  /** 跨字段规则在服务端执行，内部下发路径也不能绕过。 */
  private void validate(PersonClassificationCondition condition) {
    if (condition == null) throw invalid("分类条件不能为空");
    if (condition.getMinAge() != null && condition.getMaxAge() != null
      && condition.getMinAge() > condition.getMaxAge()) throw invalid("最小年龄不能大于最大年龄");
    if (condition.getResultStatuses() != null && !condition.getResultStatuses().isEmpty()) {
      if (condition.getTrainingPlanId() == null) throw invalid("请选择培训计划后再筛选结果");
      if (!Set.of("INCOMPLETE", "PASSED", "FAILED").containsAll(condition.getResultStatuses()))
        throw invalid("培训结果状态无效");
    }
    if (condition.getGenders() != null && !Set.of("MALE", "FEMALE", "OTHER").containsAll(condition.getGenders()))
      throw invalid("性别筛选无效");
  }

  /** 持久化条件损坏必须明确报错，不能退化为全员下发。 */
  private PersonClassificationCondition readCondition(String value) {
    try { return json.readValue(value, PersonClassificationCondition.class); }
    catch (JsonProcessingException | IllegalArgumentException e) { throw invalid("用户组条件无效，请重新保存"); }
  }
  private BusinessException invalid(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, 4120, message); }
  private BusinessException conflict(String message) { return new BusinessException(HttpStatus.CONFLICT, 4122, message); }
}
