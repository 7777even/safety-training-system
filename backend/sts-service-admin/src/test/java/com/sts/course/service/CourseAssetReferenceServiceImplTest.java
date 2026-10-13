package com.sts.course.service;

import com.sts.course.mapper.CourseDownloadAssetMapper;
import com.sts.course.mapper.CourseItemMapper;
import com.sts.course.vo.CourseAssetReferenceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseAssetReferenceServiceImplTest {

  private static final Long ASSET_ID = 100L;

  private CourseItemMapper itemMapper;
  private CourseDownloadAssetMapper downloadAssetMapper;
  private CourseAssetReferenceServiceImpl service;

  @BeforeEach
  void setUp() {
    itemMapper = mock(CourseItemMapper.class);
    downloadAssetMapper = mock(CourseDownloadAssetMapper.class);
    service = new CourseAssetReferenceServiceImpl(itemMapper, downloadAssetMapper);
  }

  @Test
  @DisplayName("getReferences：聚合内容项与课程附件引用计数")
  void getReferences_aggregatesItemAndDownloadCounts() {
    when(itemMapper.selectCount(any())).thenReturn(3L);
    when(downloadAssetMapper.selectCount(any())).thenReturn(2L);

    CourseAssetReferenceVO result = service.getReferences(ASSET_ID);

    assertThat(result.getAssetId()).isEqualTo(ASSET_ID);
    assertThat(result.getItemCount()).isEqualTo(3L);
    assertThat(result.getDownloadCount()).isEqualTo(2L);
    assertThat(result.getReferenceCount()).isEqualTo(5L);
  }

  @Test
  @DisplayName("getReferences：mapper 返回 null 时按 0 计")
  void getReferences_nullCountsTreatedAsZero() {
    when(itemMapper.selectCount(any())).thenReturn(null);
    when(downloadAssetMapper.selectCount(any())).thenReturn(null);

    CourseAssetReferenceVO result = service.getReferences(ASSET_ID);

    assertThat(result.getItemCount()).isZero();
    assertThat(result.getDownloadCount()).isZero();
    assertThat(result.getReferenceCount()).isZero();
  }
}
