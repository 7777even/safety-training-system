package com.sts.person.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.person.dto.PersonPickerQuery;
import com.sts.person.service.PersonPickerService;
import com.sts.person.vo.PersonPickerItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PersonPickerController {
  private final PersonPickerService personPickerService;

  @GetMapping("/common/person-picker")
  @SaCheckPermission("common:person:list")
  public PageResult<PersonPickerItemVO> pagePersons(PageRequest<PersonPickerQuery> request) {
    return personPickerService.pagePersons(request);
  }
}
