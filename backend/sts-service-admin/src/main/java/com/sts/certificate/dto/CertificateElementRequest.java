package com.sts.certificate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class CertificateElementRequest {
  @NotBlank
  private String elementType;
  private String variableCode;
  private String staticContent;
  /**
   * 元素左上角横坐标（像素）。显式声明 JSON 名：Jackson 默认策略会把 getter 推导成
   * {@code xposition}，与 OpenAPI 契约 {@code xPosition} 不一致，导致前端传入的坐标被静默丢弃。
   * 字段与读写方法都要标注，否则 Jackson 会当成两个属性。
   */
  @JsonProperty("xPosition")
  @Getter(onMethod_ = @JsonProperty("xPosition"))
  @Setter(onMethod_ = @JsonProperty("xPosition"))
  @NotNull
  private Integer xPosition;
  /**
   * 元素左上角纵坐标（像素），JSON 名同 {@link #xPosition} 的原因显式声明。
   */
  @JsonProperty("yPosition")
  @Getter(onMethod_ = @JsonProperty("yPosition"))
  @Setter(onMethod_ = @JsonProperty("yPosition"))
  @NotNull
  private Integer yPosition;
  private Integer width;
  private Integer height;
  /**
   * 字体名称；为空时使用系统逻辑无衬线字体。
   */
  private String fontFamily;
  /**
   * 字号；为空时使用渲染器默认字号。
   */
  private Integer fontSize;
  /**
   * 十六进制字体颜色，例如 #112233。
   */
  private String fontColor;
  @NotNull
  private Integer sortOrder;
  @NotNull
  private Integer status;
}
