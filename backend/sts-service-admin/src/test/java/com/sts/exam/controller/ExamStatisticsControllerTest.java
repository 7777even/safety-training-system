package com.sts.exam.controller;

import com.sts.exam.service.ExamStatisticsService;
import com.sts.exam.vo.ExamStatisticsPersonVO;
import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

/** 导出不能接受当前筛选外的人员 ID。 */
class ExamStatisticsControllerTest {
  @Test void exportRejectsInvisibleSelectionInsteadOfSilentlyDroppingIt() {
    var service=mock(ExamStatisticsService.class);
    when(service.people(eq(1L),isNull())).thenReturn(List.of());
    assertThatThrownBy(()->new ExamStatisticsController(service).export(1L,null,List.of(99L))).isInstanceOf(BusinessException.class).hasMessageContaining("当前可见");
  }
  @Test void exportContainsOnlySelectedVisiblePeople() {
    var service=mock(ExamStatisticsService.class);
    var p=new ExamStatisticsPersonVO(1L,1L,"张三","u1","PASSED",null,null,true,true,false,null,null,null,null,null,null);
    when(service.people(eq(1L),isNull())).thenReturn(List.of(p));
    var response=new ExamStatisticsController(service).export(1L,null,List.of(1L));
    assertThat(response.getHeaders().getContentType().toString()).contains("spreadsheetml");
    assertThat(response.getBody()).startsWith((byte)'P',(byte)'K');
  }
}
