package com.sts.learner;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.api.PageRequest;
import com.sts.learner.mapper.LearnerHistoryMapper;
import com.sts.learner.service.LearnerHistoryService;
import com.sts.learner.vo.HistorySource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.sts.archive.dto.HistoryCorrectionRequest;
import com.sts.archive.entity.ArchiveHistoryCorrection;
import java.util.ArrayList;
import java.util.Map;

/** 统一档案公开行为：已结束记录保留，隐藏成绩不能经更正暴露。 */
class LearnerHistoryServiceTest {
  @Test void historyIncludesEndedRecordsAndMasksUnreleasedResults() {
    var mapper = mock(LearnerHistoryMapper.class);
    var row = new HistorySource(); row.setRecordType("FORMAL_EXAM"); row.setRecordId(9L);
    row.setTitle("已结束考试"); row.setStatus("FAILED"); row.setOccurredTime(LocalDateTime.now());
    row.setScore(BigDecimal.valueOf(55)); row.setScoreVisible(false);
    when(mapper.userExists(2L,7L)).thenReturn(7L);
    when(mapper.exams(2L,7L)).thenReturn(List.of(row));
    var service = new LearnerHistoryService(mapper, new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
    try(var auth = mockStatic(StpUtil.class)) {
      auth.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      TenantContext.runWithTenant(2L, () -> {
        var page = service.mine(new PageRequest<>(1,20,null));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.list().getFirst().current().score()).isNull();
        assertThat(page.list().getFirst().original().status()).isEqualTo("FAILED");
        assertThatThrownBy(() -> service.myDetail("COURSE",99L)).hasMessageContaining("不存在");
      });
    }
  }
  @Test void correctionsAppendFromCurrentVersionAndNeverExposeUnreleasedScores() {
    var mapper=mock(LearnerHistoryMapper.class); var json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    var source=new HistorySource(); source.setRecordType("FORMAL_EXAM"); source.setRecordId(9L); source.setTitle("正式考试");
    source.setOccurredTime(LocalDateTime.now()); source.setStatus("FAILED"); source.setUserName("原姓名"); source.setScore(BigDecimal.valueOf(55)); source.setMaxScore(BigDecimal.valueOf(100));
    when(mapper.userExists(2L,7L)).thenReturn(7L); when(mapper.lockUser(2L,7L)).thenReturn(7L); when(mapper.exams(2L,7L)).thenReturn(List.of(source));
    var ledger=new ArrayList<ArchiveHistoryCorrection>(); when(mapper.corrections(2L,7L)).thenReturn(ledger);
    doAnswer(call -> { var row=(ArchiveHistoryCorrection)call.getArgument(0); row.setCorrectionId((long)ledger.size()+1); ledger.add(row); return null; }).when(mapper).append(any());
    var service=new LearnerHistoryService(mapper,json);
    try(var auth=mockStatic(StpUtil.class)) {
      auth.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      auth.when(() -> StpUtil.hasPermission("archive:history:correction:list")).thenReturn(true);
      TenantContext.runWithTenant(2L, () -> {
        var request=new HistoryCorrectionRequest(); request.setVersion(0); request.setReason("核实登记"); request.setChanges(Map.of("userName",json.valueToTree("更正姓名"),"score",json.valueToTree(60)));
        assertThat(service.correct(7L,"FORMAL_EXAM",9L,request).version()).isEqualTo(1);
        assertThatThrownBy(() -> service.correct(7L,"FORMAL_EXAM",9L,request)).hasMessageContaining("最新版本");
        request.setVersion(1); request.setChanges(Map.of("userName",json.valueToTree("再次更正")));
        service.correct(7L,"FORMAL_EXAM",9L,request);
        assertThat(ledger.getLast().getBeforeJson()).contains("更正姓名").doesNotContain("原姓名");
        var mine=service.myDetail("FORMAL_EXAM",9L);
        assertThat(mine.original().userName()).isEqualTo("原姓名"); assertThat(mine.current().userName()).isEqualTo("再次更正");
        assertThat(mine.current().score()).isNull(); assertThat(mine.original().score()).isNull();
        assertThat(mine.corrections()).allSatisfy(c -> { assertThat(c.before()).doesNotContainKey("score"); assertThat(c.after()).doesNotContainKey("score"); });
        request.setVersion(2); request.setReason(" "); assertThatThrownBy(() -> service.correct(7L,"FORMAL_EXAM",9L,request)).hasMessageContaining("理由");
        request.setReason("核实登记"); request.setChanges(Map.of("answer",json.valueToTree("禁止"))); assertThatThrownBy(() -> service.correct(7L,"FORMAL_EXAM",9L,request)).hasMessageContaining("字段");
        request.setChanges(Map.of("status",json.valueToTree("WITHDRAWN"))); assertThatThrownBy(() -> service.correct(7L,"FORMAL_EXAM",9L,request)).hasMessageContaining("领域");
        request.setChanges(Map.of("score",json.valueToTree(101))); assertThatThrownBy(() -> service.correct(7L,"FORMAL_EXAM",9L,request)).hasMessageContaining("范围");
        assertThat(ledger).hasSize(2); assertThat(source.getScore()).isEqualByComparingTo("55");
      });
      TenantContext.runWithTenant(3L, () -> assertThatThrownBy(() -> service.myDetail("FORMAL_EXAM",9L)).hasMessageContaining("不存在"));
    }
  }
  @Test void writePermissionDoesNotGrantTraceReadAndMissingDomainPermissionRejects() {
    var mapper=mock(LearnerHistoryMapper.class); var service=new LearnerHistoryService(mapper,new com.fasterxml.jackson.databind.ObjectMapper());
    try(var auth=mockStatic(StpUtil.class)) {
      auth.when(() -> StpUtil.checkPermission("exam:attempt:list")).thenThrow(new IllegalStateException("无领域权限"));
      TenantContext.runWithTenant(2L, () -> assertThatThrownBy(() -> service.correct(7L,"FORMAL_EXAM",9L,new HistoryCorrectionRequest())).hasMessageContaining("领域权限"));
      verify(mapper,never()).append(any()); verify(mapper,never()).lockUser(any(),any());
    }
  }
}
