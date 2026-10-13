package com.sts.ops.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpsWorkbenchShortcutVO {
  private String code;
  private String title;
  private String path;
  private String permission;
}
