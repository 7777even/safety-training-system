package com.sts.asset.mapper;

import com.sts.admin.mapper.AppBaseMapper;
import com.sts.asset.entity.AssetTranscodeJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AssetTranscodeJobMapper extends AppBaseMapper<AssetTranscodeJob> {
  @Select("""
    SELECT transcode_job_id FROM sts_asset_transcode_job
    WHERE job_status = 'PENDING' AND deleted = 0
    ORDER BY create_time, transcode_job_id LIMIT #{limit}
    """)
  List<Long> selectPendingIds(@Param("limit") int limit);

  @Update("""
    UPDATE sts_asset_transcode_job
    SET job_status='RUNNING', started_time=NOW(), finished_time=NULL,
        failure_code=NULL, failure_reason=NULL, update_time=NOW(), version=version+1
    WHERE transcode_job_id=#{jobId} AND job_status='PENDING' AND deleted=0
    """)
  int claimPending(@Param("jobId") Long jobId);

  @Select("""
    SELECT * FROM sts_asset_transcode_job
    WHERE job_status='RUNNING' AND started_time < #{cutoff} AND deleted=0
    ORDER BY started_time, transcode_job_id LIMIT #{limit}
    """)
  List<AssetTranscodeJob> selectTimedOutRunning(
    @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

  @Update("""
    UPDATE sts_asset_transcode_job
    SET job_status='SUCCEEDED', finished_time=NOW(), failure_code=NULL,
        failure_reason=NULL, update_time=NOW(), version=version+1
    WHERE transcode_job_id=#{jobId} AND job_status='RUNNING' AND deleted=0
    """)
  int finishSucceeded(@Param("jobId") Long jobId);

  @Update("""
    UPDATE sts_asset_transcode_job
    SET job_status='FAILED', finished_time=NOW(), failure_code=#{failureCode},
        failure_reason=#{failureReason}, update_time=NOW(), version=version+1
    WHERE transcode_job_id=#{jobId} AND job_status='RUNNING' AND deleted=0
    """)
  int finishFailed(@Param("jobId") Long jobId,
                   @Param("failureCode") String failureCode,
                   @Param("failureReason") String failureReason);
}
