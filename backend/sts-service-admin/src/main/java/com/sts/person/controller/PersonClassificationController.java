package com.sts.person.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.person.dto.*;
import com.sts.person.service.*;
import com.sts.person.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 本机构人员特征、分类与用户组接口。 */
@RestController @RequiredArgsConstructor
public class PersonClassificationController {
  /** 手工特征与培训事实。 */
  private final PersonProfileService profiles;
  /** 条件分类与组维护。 */
  private final PersonClassificationService groups;
  /** 读取手工特征。 */
  @GetMapping("/person/profiles/{userId}") @SaCheckPermission("person:profile:list")
  public PersonProfileVO profile(@PathVariable Long userId) { return profiles.get(userId); }
  /** 版本化保存手工特征。 */
  @PutMapping("/person/profiles/{userId}") @SaCheckPermission("person:profile:edit")
  public void saveProfile(@PathVariable Long userId, @Valid @RequestBody PersonProfileRequest request) { profiles.save(userId, request); }
  /** 查询系统培训事实。 */
  @GetMapping("/person/profiles/{userId}/training-facts") @SaCheckPermission("person:profile:list")
  public List<PersonTrainingFactVO> trainingFacts(@PathVariable Long userId) { return profiles.trainingFacts(userId); }
  /** 预览当前条件，零匹配返回空名单。 */
  @PostMapping("/person/groups/preview") @SaCheckPermission("person:group:list")
  public List<PersonPickerItemVO> preview(@Valid @RequestBody PersonClassificationCondition condition) { return groups.preview(condition); }
  /** 读取本租户所有组供管理与选择。 */
  @GetMapping("/person/groups") @SaCheckPermission("person:group:list")
  public List<PersonGroupVO> groups() { return groups.list(); }
  /** 新建条件组。 */
  @PostMapping("/person/groups") @SaCheckPermission("person:group:edit")
  public Long create(@Valid @RequestBody PersonGroupRequest request) { return groups.save(null, request); }
  /** 修改条件组，已下发名单不变。 */
  @PutMapping("/person/groups/{groupId}") @SaCheckPermission("person:group:edit")
  public void update(@PathVariable Long groupId, @Valid @RequestBody PersonGroupRequest request) { groups.save(groupId, request); }
}
