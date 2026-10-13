package com.sts.learner.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.*;
import com.sts.admin.tenant.TenantContext;
import com.sts.archive.dto.HistoryCorrectionRequest;
import com.sts.archive.entity.ArchiveHistoryCorrection;
import com.sts.common.api.*;
import com.sts.common.exception.BusinessException;
import com.sts.learner.mapper.LearnerHistoryMapper;
import com.sts.learner.vo.*;
import com.sts.learner.vo.LearnerHistoryVO.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** 档案展示层聚合与追加更正，来源表始终只读。 */
@Service @RequiredArgsConstructor
public class LearnerHistoryService {
  /** 原始摘要查询和仅追加的账本。 */
  private final LearnerHistoryMapper mapper;
  /** 严格解析持久化JSON，不接收任意业务字段。 */
  private final ObjectMapper json;
  /** 查询分类、名称与发生日期。 */
  @Data public static class Query {
    /** 空值或ALL查全部来源。 */
    private String recordType;
    /** 来源名称。 */
    private String keyword;
    /** 发生日期起点，含当天。 */
    private LocalDate fromDate;
    /** 发生日期终点，含当天。 */
    private LocalDate toDate;
  }
  /** 类型与现有原记录读取权限对应；个人练习使用既有个人档案读取权限。 */
  private static final Map<String,String> READ_PERMISSIONS = Map.of(
    "COURSE","course:learning:view", "FORMAL_EXAM","exam:attempt:list", "COURSE_QUIZ","course:learning:view",
    "PRACTICE","archive:training:list", "SIMULATION","archive:training:list", "TRAINING","training:result:list");
  /** 只允许已有摘要字段；来源名称、业务引用不能更正。 */
  private static final Set<String> FIELDS = Set.of("userName","userCode","occurredTime","status","score","hours");
  /** 按来源的原领域状态，不接收跨领域或自定义状态。 */
  private static final Map<String,Set<String>> STATUSES = Map.of(
    "COURSE",Set.of("NOT_STARTED","LEARNING","COMPLETED","EXPIRED"),
    "FORMAL_EXAM",Set.of("NOT_STARTED","IN_PROGRESS","SUBMITTED","PENDING_REVIEW","PENDING_PUBLISH","PASSED","FAILED","COMPLETED","ABSENT"),
    "COURSE_QUIZ",Set.of("NOT_STARTED","IN_PROGRESS","SUBMITTED","PENDING_REVIEW","PENDING_PUBLISH","PASSED","FAILED","COMPLETED","ABSENT"),
    "PRACTICE",Set.of("ANSWERING","SUBMITTED","CLEARED"), "SIMULATION",Set.of("ANSWERING","SUBMITTED","CLEARED"),
    "TRAINING",Set.of("PENDING","LEARNING","COMPLETED","FAILED","EXPIRED","WITHDRAWN"));

  /** 本人档案不依赖当前任务是否有效，结果依原领域规则裁剪。 */
  public PageResult<LearnerHistoryVO> mine(PageRequest<Query> request) {
    validatePage(request);
    Query q = request.getQuery();
    if(q != null && q.getRecordType()!=null && !"ALL".equals(q.getRecordType())) validateType(q.getRecordType());
    if(q != null && q.getFromDate()!=null && q.getToDate()!=null && q.getFromDate().isAfter(q.getToDate())) throw invalid("日期范围不合法");
    Long user = StpUtil.getLoginIdAsLong(); requireUser(user);
    List<LearnerHistoryVO> rows = assemble(user, false, false).stream()
      .filter(r -> q==null || q.getRecordType()==null || "ALL".equals(q.getRecordType()) || q.getRecordType().equals(r.recordType()))
      .filter(r -> q==null || q.getKeyword()==null || q.getKeyword().isBlank() || r.title().contains(q.getKeyword().trim()))
      .filter(r -> q==null || q.getFromDate()==null || r.current().occurredTime()!=null && !r.current().occurredTime().toLocalDate().isBefore(q.getFromDate()))
      .filter(r -> q==null || q.getToDate()==null || r.current().occurredTime()!=null && !r.current().occurredTime().toLocalDate().isAfter(q.getToDate()))
      .toList();
    int start=(int)Math.min((long)(request.getPageNum()-1)*request.getPageSize(),rows.size());
    return new PageResult<>(rows.subList(start,Math.min(start+request.getPageSize(),rows.size())),rows.size());
  }
  /** 本人详情及更正轨迹只来自登录身份。 */
  public LearnerHistoryVO myDetail(String type,Long id) { return detail(StpUtil.getLoginIdAsLong(),type,id,false); }
  /** 管理个人档案只展示操作人已有领域读取权限覆盖的来源。 */
  public List<LearnerHistoryVO> forPerson(Long user) { requireUser(user); return assemble(user,true,false); }
  /** 更正留痕必须同时具备新权限和原记录读取权限。 */
  public LearnerHistoryVO corrections(Long user,String type,Long id) {
    StpUtil.checkPermission("archive:history:correction:list"); requireRead(type); return detail(user,type,id,true);
  }
  /** 行锁与唯一键共同约束追加版本；不回写来源，不触发计算或归档。 */
  @Transactional public LearnerHistoryVO correct(Long user,String type,Long id,HistoryCorrectionRequest request) {
    StpUtil.checkPermission("archive:history:correct"); requireRead(type);
    if(mapper.lockUser(TenantContext.requireTenantId(),user)==null) throw missing();
    var source = source(user,type,id); var current = detail(user,type,id,true);
    if(request.getVersion()==null || request.getVersion()!=current.version())
      throw new BusinessException(HttpStatus.CONFLICT,2040,"档案已被更正，请重新读取最新版本");
    if(request.getReason()==null || request.getReason().isBlank() || request.getReason().length()>500) throw invalid("更正理由不能为空且最多500字");
    if(request.getChanges()==null || request.getChanges().isEmpty() || !FIELDS.containsAll(request.getChanges().keySet())) throw invalid("更正字段不合法");
    Map<String,JsonNode> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
    var currentValues = values(current.current());
    request.getChanges().forEach((field,value) -> {
      validateValue(source,field,value);
      if(!Objects.equals(value,currentValues.get(field))) { before.put(field,currentValues.get(field)); after.put(field,value); }
    });
    if(after.isEmpty()) throw invalid("更正值与当前展示一致");
    var row = new ArchiveHistoryCorrection(); row.setOrganizationId(TenantContext.requireTenantId()); row.setUserId(user);
    row.setRecordType(type); row.setRecordId(id); row.setVersion(current.version()+1);
    row.setBeforeJson(write(before)); row.setAfterJson(write(after)); row.setReason(request.getReason().trim());
    row.setOperatorId(StpUtil.getLoginIdAsLong()); row.setOperatorName(mapper.operatorName(row.getOperatorId())); row.setOperatedTime(LocalDateTime.now());
    mapper.append(row);
    // 写权限不能借响应读取他人留痕，读权限独立判定。
    return view(source,mapper.corrections(TenantContext.requireTenantId(),user),true,
      StpUtil.hasPermission("archive:history:correction:list"));
  }
  /** 机构校验在查询前完成，跨机构主键不得查看或更正。 */
  private void requireUser(Long user) { if(mapper.userExists(TenantContext.requireTenantId(),user)==null) throw missing(); }
  private void validateType(String type) { if(!READ_PERMISSIONS.containsKey(type)) throw invalid("历史来源类型不合法"); }
  private void requireRead(String type) { validateType(type); StpUtil.checkPermission(READ_PERMISSIONS.get(type)); }
  private List<HistorySource> sources(Long user) {
    Long org = TenantContext.requireTenantId(); var rows = new ArrayList<HistorySource>();
    rows.addAll(mapper.courses(org,user)); rows.addAll(mapper.exams(org,user)); rows.addAll(mapper.practices(org,user)); rows.addAll(mapper.trainings(org,user));
    return rows;
  }
  private HistorySource source(Long user,String type,Long id) {
    validateType(type); requireUser(user);
    return sources(user).stream().filter(r -> type.equals(r.getRecordType()) && Objects.equals(id,r.getRecordId())).findFirst().orElseThrow(this::missing);
  }
  private LearnerHistoryVO detail(Long user,String type,Long id,boolean admin) {
    var row=source(user,type,id);
    return view(row, mapper.corrections(TenantContext.requireTenantId(),user),admin,true);
  }
  /** 列表省略逐次留痕正文，详情才返回；更正后的时间用于排序与日期查询。 */
  private List<LearnerHistoryVO> assemble(Long user,boolean admin,boolean traces) {
    var corrections=mapper.corrections(TenantContext.requireTenantId(),user);
    return sources(user).stream().filter(r -> !admin || StpUtil.hasPermission(READ_PERMISSIONS.get(r.getRecordType())))
      .map(r -> view(r,corrections,admin,traces))
      .sorted(Comparator.comparing((LearnerHistoryVO r)->r.current().occurredTime(),Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(LearnerHistoryVO::recordType).thenComparing(LearnerHistoryVO::recordId,Comparator.reverseOrder())).toList();
  }
  /** 更正成绩不能绕过原系统发布规则，包括轨迹中的前后值。 */
  private LearnerHistoryVO view(HistorySource source,List<ArchiveHistoryCorrection> all,boolean admin,boolean traces) {
    boolean visible=admin || source.isScoreVisible();
    var original=new Summary(source.getUserName(),source.getUserCode(),source.getOccurredTime(),source.getStatus(),source.getScore(),source.getHours(),visible);
    Map<String,JsonNode> current=values(original); int version=0; var history=new ArrayList<Correction>();
    for(var c:all) if(source.getRecordType().equals(c.getRecordType()) && source.getRecordId().equals(c.getRecordId())) {
      var before=parse(c.getBeforeJson()); var after=parse(c.getAfterJson()); current.putAll(after); version=c.getVersion();
      if(!visible) { before.remove("score"); after.remove("score"); }
      if(traces) history.add(new Correction(c.getCorrectionId(),c.getVersion(),before,after,c.getReason(),c.getOperatorId(),c.getOperatorName(),c.getOperatedTime()));
    }
    var displayed=new Summary(text(current.get("userName")),text(current.get("userCode")),time(current.get("occurredTime")),
      text(current.get("status")),visible?decimal(current.get("score")):null,decimal(current.get("hours")),visible);
    var safeOriginal=new Summary(original.userName(),original.userCode(),original.occurredTime(),original.status(),visible?original.score():null,original.hours(),visible);
    return new LearnerHistoryVO(source.getRecordType(),source.getRecordId(),source.getTitle(),safeOriginal,displayed,version,
      new Reference(source.getReferenceKind(),source.getResourceId(),source.getParentId(),source.getItemId(),source.getAttemptId(),source.getAction()),history);
  }
  private Map<String,JsonNode> values(Summary s) {
    var result=new LinkedHashMap<String,JsonNode>();
    result.put("userName",json.valueToTree(s.userName())); result.put("userCode",json.valueToTree(s.userCode()));
    result.put("occurredTime",json.valueToTree(s.occurredTime()==null?null:s.occurredTime().toString())); result.put("status",json.valueToTree(s.status()));
    result.put("score",json.valueToTree(s.score())); result.put("hours",json.valueToTree(s.hours())); return result;
  }
  /** 类型校验与来源字段适用性一起判断，练习不允许生成正式分数。 */
  private void validateValue(HistorySource s,String field,JsonNode v) {
    if(v==null) throw invalid("更正值不能缺失");
    switch(field) {
      case "userName","userCode" -> { if(!v.isTextual() || v.asText().isBlank() || v.asText().length()>100 || v.asText().codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT)) throw invalid("身份展示必须为1至100字有效文本"); }
      case "occurredTime" -> { if(!v.isTextual()) throw invalid("发生时间必须为日期时间文本"); try { LocalDateTime.parse(v.asText()); } catch(java.time.format.DateTimeParseException e) { throw invalid("发生时间格式不合法"); } }
      case "status" -> { if(!v.isTextual() || !STATUSES.get(s.getRecordType()).contains(v.asText())) throw invalid("状态不属于该来源领域"); }
      case "score" -> { if(s.getMaxScore()==null || Set.of("PRACTICE","SIMULATION").contains(s.getRecordType())) throw invalid("该来源没有可更正的成绩摘要"); validateNumber(v,s.getMaxScore()); }
      case "hours" -> { if(!Set.of("COURSE","TRAINING").contains(s.getRecordType())) throw invalid("该来源没有学时摘要"); validateNumber(v,null); }
      default -> throw invalid("更正字段不合法");
    }
  }
  private void validateNumber(JsonNode v,BigDecimal max) {
    if(v.isNull()) return;
    if(!v.isNumber() || v.decimalValue().signum()<0 || v.decimalValue().scale()>4 || v.decimalValue().precision()>12
      || max!=null && v.decimalValue().compareTo(max)>0) throw invalid("数值必须非负且在该领域范围内，最多4位小数");
  }
  private Map<String,JsonNode> parse(String value) { try { return json.readValue(value,new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String,JsonNode>>(){}); } catch(Exception e) { throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,1003,"档案更正记录无法解析"); } }
  private String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw invalid("更正值无法保存"); } }
  private String text(JsonNode v) { return v==null||v.isNull()?null:v.asText(); }
  private LocalDateTime time(JsonNode v) { return v==null||v.isNull()?null:LocalDateTime.parse(v.asText()); }
  private BigDecimal decimal(JsonNode v) { return v==null||v.isNull()?null:v.decimalValue(); }
  private void validatePage(PageRequest<?> r) { if(r.getPageNum()<1 || r.getPageSize()<1 || r.getPageSize()>200) throw invalid("分页范围不合法"); }
  private BusinessException invalid(String message) { return new BusinessException(HttpStatus.BAD_REQUEST,1001,message); }
  private BusinessException missing() { return new BusinessException(HttpStatus.NOT_FOUND,9205,"本人历史记录或人员不存在"); }
}
