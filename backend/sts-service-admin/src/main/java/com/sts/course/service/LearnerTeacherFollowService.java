package com.sts.course.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.api.*;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.TeacherFollow;
import com.sts.course.mapper.TeacherFollowMapper;
import com.sts.course.dto.LearnerCourseOfferingQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.stream.Collectors;

/** 同机构有效教师的本人关注与简洁详情，不引入独立讲师模型。 */
@Service @RequiredArgsConstructor
public class LearnerTeacherFollowService {
  /** 教师目录及本人关注关系持久化。 */
  private final TeacherFollowMapper mapper;
  /** 姓名外只返回本人关系状态，教师失效后列表允许取消。 */
  public record View(Long teacherId, String teacherName, boolean available, boolean followed) { }

  /** 姓名搜索并分页展示当前机构的有效教师。 */
  public PageResult<View> teachers(PageRequest<LearnerCourseOfferingQuery> request) {
    Map<Long, TeacherFollow> states = states(); String keyword = keyword(request);
    return page(mapper.selectTeachers(keyword, null).stream().map(t -> new View(t.getTeacherId(), t.getTeacherName(), true,
      states.containsKey(t.getTeacherId()) && Objects.equals(states.get(t.getTeacherId()).getIsFollowed(), 1))).toList(), request);
  }
  /** 本人关注列表保留教师失效状态，避免无法取消。 */
  public PageResult<View> follows(PageRequest<LearnerCourseOfferingQuery> request) {
    var active = mapper.selectTeachers(null, null).stream().collect(Collectors.toMap(t -> t.getTeacherId(), t -> t));
    var rows = mapper.selectList(new LambdaQueryWrapper<TeacherFollow>().eq(TeacherFollow::getUserId, StpUtil.getLoginIdAsLong())
      .eq(TeacherFollow::getIsFollowed, 1).orderByDesc(TeacherFollow::getUpdateTime)).stream().map(r -> new View(r.getTeacherId(),
        active.containsKey(r.getTeacherId()) ? active.get(r.getTeacherId()).getTeacherName() : r.getTeacherNameSnapshot(),
        active.containsKey(r.getTeacherId()), true)).filter(t -> keyword(request) == null || keyword(request).isBlank()
          || t.teacherName().contains(keyword(request))).toList();
    return page(rows, request);
  }
  /** 详情再校验有效教师角色与同机构访问范围。 */
  public View detail(Long teacherId) {
    var teachers = mapper.selectTeachers(null, teacherId);
    if (teachers.isEmpty()) throw denied();
    var teacher = teachers.getFirst(); var state = states().get(teacherId);
    return new View(teacherId, teacher.getTeacherName(), true, state != null && Objects.equals(state.getIsFollowed(), 1));
  }
  /** 关注验证教师有效性；取消已失效教师只修改本人关系。 */
  @Transactional
  public void setFollow(Long teacherId, boolean followed) {
    var existing = mapper.selectOne(new LambdaQueryWrapper<TeacherFollow>().eq(TeacherFollow::getUserId, StpUtil.getLoginIdAsLong())
      .eq(TeacherFollow::getTeacherId, teacherId));
    if (!followed && existing == null) return;
    String name = followed ? detail(teacherId).teacherName() : existing.getTeacherNameSnapshot();
    var row = new TeacherFollow(); row.setOrganizationId(TenantContext.requireTenantId()); row.setUserId(StpUtil.getLoginIdAsLong());
    row.setTeacherId(teacherId); row.setTeacherNameSnapshot(name); row.setIsFollowed(followed ? 1 : 0); mapper.upsertFollow(row);
  }
  /** 读取本人的教师关注关系。 */
  private Map<Long, TeacherFollow> states() {
    return mapper.selectList(new LambdaQueryWrapper<TeacherFollow>().eq(TeacherFollow::getUserId, StpUtil.getLoginIdAsLong()))
      .stream().collect(Collectors.toMap(TeacherFollow::getTeacherId, r -> r));
  }
  /** 取得姓名搜索关键词。 */
  private String keyword(PageRequest<LearnerCourseOfferingQuery> request) { return request.getQuery() == null ? null : request.getQuery().getKeyword(); }
  /** 返回本人受控教师摘要的当前页。 */
  private PageResult<View> page(List<View> rows, PageRequest<?> request) {
    int start = (int)Math.min((long)(request.getPageNum() - 1) * request.getPageSize(), rows.size());
    return new PageResult<>(rows.subList(start, Math.min(start + request.getPageSize(), rows.size())), rows.size());
  }
  /** 返回教师资源访问拒绝。 */
  private BusinessException denied() { return new BusinessException(HttpStatus.FORBIDDEN, 5094, "无权访问该教师详情"); }
}
