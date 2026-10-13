package com.sts.person.mapper;
import com.sts.admin.mapper.AppBaseMapper;
import com.sts.person.entity.PersonGroup;
import org.apache.ibatis.annotations.Mapper;
/** PersonGroup 持久化及乐观锁。 */
@Mapper public interface PersonGroupMapper extends AppBaseMapper<PersonGroup> { }
