package com.sts.person.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.person.dto.PersonPickerQuery;
import com.sts.person.vo.PersonPickerItemVO;

public interface PersonPickerService {
  PageResult<PersonPickerItemVO> pagePersons(PageRequest<PersonPickerQuery> request);
}
