package com.sts.person.mapper;
import com.sts.person.dto.PersonClassificationCondition;
import com.sts.person.vo.PersonTrainingFactVO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;
/** SQL先筛选再返回，避免把全机构人员拉到内存分类。 */
@Mapper public interface PersonClassificationMapper {
  /** 展开当前条件下启用人员。 */
  List<Long> resolve(@Param("organizationId") Long organizationId,
    @Param("condition") PersonClassificationCondition condition, @Param("today") LocalDate today);
  /** 查询有效培训及奖惩事实。 */
  List<PersonTrainingFactVO> trainingFacts(@Param("organizationId") Long organizationId, @Param("userId") Long userId);
}
