package com.sts.asset.mapper;

import com.sts.admin.mapper.AppBaseMapper;
import com.sts.asset.entity.Asset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AssetMapper extends AppBaseMapper<Asset> {
  @Update("""
    UPDATE sts_asset_asset
    SET process_status=#{processStatus}, update_time=NOW(), version=version+1
    WHERE asset_id=#{assetId} AND organization_id=#{organizationId} AND deleted=0
    """)
  int updateProcessStatus(@Param("assetId") Long assetId,
                          @Param("organizationId") Long organizationId,
                          @Param("processStatus") String processStatus);

  @Update("""
    UPDATE sts_asset_asset
    SET process_status='SUCCEEDED', duration_seconds=COALESCE(#{durationSeconds}, duration_seconds),
        page_count=COALESCE(#{pageCount}, page_count),
        update_time=NOW(), version=version+1
    WHERE asset_id=#{assetId} AND organization_id=#{organizationId} AND deleted=0
    """)
  int finishTranscode(@Param("assetId") Long assetId,
                      @Param("organizationId") Long organizationId,
                      @Param("durationSeconds") Integer durationSeconds,
                      @Param("pageCount") Integer pageCount);
}
