package com.sts.certificate.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * 证书模板中的文字或变量元素。
 */
@Data
public class CertificateTemplateElementVO {
  private Long templateElementId;
  private String elementType;
  private String variableCode;
  private String staticContent;
  /**
   * 元素左上角横坐标（像素）。显式声明 JSON 名：Jackson 默认策略会把 getter 推导成
   * {@code xposition}，与 OpenAPI 契约不一致，前端读取坐标会得到 undefined。
   * 字段与读写方法都要标注，否则 Jackson 会当成两个属性。
   */
  @JsonProperty("xPosition")
  @Getter(onMethod_ = @JsonProperty("xPosition"))
  @Setter(onMethod_ = @JsonProperty("xPosition"))
  private Integer xPosition;
  /**
   * 元素左上角纵坐标（像素），JSON 名同 {@link #xPosition} 的原因显式声明。
   */
  @JsonProperty("yPosition")
  @Getter(onMethod_ = @JsonProperty("yPosition"))
  @Setter(onMethod_ = @JsonProperty("yPosition"))
  private Integer yPosition;
  private Integer width;
  private Integer height;
  private String fontFamily;
  private Integer fontSize;
  private String fontColor;
  private Integer sortOrder;
  private Integer status;
}
