package com.sts.certificate.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.certificate.vo.CertificateTemplateElementVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 证书模板元素坐标的 JSON 名称契约。
 *
 * <p>Jackson 的默认（legacy）属性名策略会把 {@code getXPosition()} 推导成 {@code xposition}，
 * 与 {@code docs/api/certificate.openapi.json} 中声明的 {@code xPosition} 不一致：
 * 前端按契约传 {@code xPosition} 会被静默丢弃，模板设计器永远存不下元素位置。
 * 这里用与应用一致的对象映射器锁住契约字段名，避免再次退化。
 */
class CertificateElementJsonContractTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static CertificateElementRequest request() {
    CertificateElementRequest request = new CertificateElementRequest();
    request.setElementType("TEXT");
    request.setStaticContent("兹证明");
    request.setXPosition(638);
    request.setYPosition(452);
    request.setFontSize(30);
    request.setFontColor("#6B6B6B");
    request.setSortOrder(1);
    request.setStatus(1);
    return request;
  }

  @Test
  void shouldSerializeElementRequestWithCamelCasePositions() throws Exception {
    String json = mapper.writeValueAsString(request());

    assertThat(json).contains("\"xPosition\":638").contains("\"yPosition\":452");
    assertThat(json).doesNotContain("\"xposition\"").doesNotContain("\"yposition\"");
  }

  @Test
  void shouldDeserializeElementRequestFromCamelCasePositions() throws Exception {
    String json = "{\"elementType\":\"TEXT\",\"staticContent\":\"兹证明\",\"xPosition\":638,"
      + "\"yPosition\":452,\"fontSize\":30,\"fontColor\":\"#6B6B6B\",\"sortOrder\":1,\"status\":1}";

    CertificateElementRequest parsed = mapper.readValue(json, CertificateElementRequest.class);

    assertThat(parsed.getXPosition()).isEqualTo(638);
    assertThat(parsed.getYPosition()).isEqualTo(452);
  }

  @Test
  void shouldSerializeElementVoWithCamelCasePositions() throws Exception {
    CertificateTemplateElementVO vo = new CertificateTemplateElementVO();
    vo.setTemplateElementId(1L);
    vo.setElementType("VARIABLE");
    vo.setVariableCode("HOLDER_NAME");
    vo.setXPosition(570);
    vo.setYPosition(512);
    vo.setSortOrder(2);
    vo.setStatus(1);

    String json = mapper.writeValueAsString(vo);

    assertThat(json).contains("\"xPosition\":570").contains("\"yPosition\":512");
    assertThat(json).doesNotContain("\"xposition\"");
  }

  @Test
  void shouldDeserializeElementVoFromCamelCasePositions() throws Exception {
    String json = "{\"templateElementId\":1,\"elementType\":\"TEXT\",\"xPosition\":570,\"yPosition\":512}";

    CertificateTemplateElementVO parsed = mapper.readValue(json, CertificateTemplateElementVO.class);

    assertThat(parsed.getXPosition()).isEqualTo(570);
    assertThat(parsed.getYPosition()).isEqualTo(512);
  }
}
