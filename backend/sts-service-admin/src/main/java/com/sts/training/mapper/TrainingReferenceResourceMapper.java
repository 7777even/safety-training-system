package com.sts.training.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.training.vo.TrainingReferenceResourceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TrainingReferenceResourceMapper {
  IPage<TrainingReferenceResourceVO> selectReferenceResourcePage(
    Page<TrainingReferenceResourceVO> page,
    @Param("resourceType") String resourceType,
    @Param("keyword") String keyword);
}
