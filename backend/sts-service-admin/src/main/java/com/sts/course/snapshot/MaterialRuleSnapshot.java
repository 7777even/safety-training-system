package com.sts.course.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({
  "requiredSeconds", "source"
})
public class MaterialRuleSnapshot {
  private Integer requiredSeconds;
  private String source;
}
