package com.sts.person.mapper;
import com.sts.admin.mapper.AppBaseMapper;
import com.sts.person.entity.PersonProfile;
import org.apache.ibatis.annotations.Mapper;
/** PersonProfile 持久化及乐观锁。 */
@Mapper public interface PersonProfileMapper extends AppBaseMapper<PersonProfile> { }
