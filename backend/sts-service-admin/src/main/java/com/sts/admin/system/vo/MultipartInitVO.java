package com.sts.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultipartInitVO {
  private String uploadId;
  private Long partSize;
  private List<Integer> uploadedParts;
}
