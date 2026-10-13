package com.sts.asset.converter;

import com.sts.asset.dto.AssetCreateRequest;
import com.sts.asset.dto.AssetUpdateRequest;
import com.sts.asset.entity.Asset;
import com.sts.asset.vo.AssetListItemVO;
import com.sts.asset.vo.AssetVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AssetConverter {

  AssetVO toVO(Asset asset);

  AssetListItemVO toListItem(Asset asset);

  Asset toEntity(AssetCreateRequest request);

  @Mapping(target = "version", ignore = true)
  void updateEntity(@MappingTarget Asset entity, AssetUpdateRequest request);
}
