package com.sts.certificate.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 证书来源类型的中文口径。
 *
 * <p>库内 {@code sourceType} 以 COURSE / EXAM / TRAINING 编码存储，证书变量 SOURCE_TYPE 需要按中文渲染，
 * 与签发规则、已签发证书管理页的下拉口径一致，避免证书上直接印出英文编码。
 *
 * <p>取值时未知编码原样返回，不静默丢失：便于从证书内容发现来源脏数据。
 */
public final class CertificateSourceTypes {

  public static final String COURSE = "COURSE";
  public static final String EXAM = "EXAM";
  public static final String TRAINING = "TRAINING";

  private static final Map<String, String> LABELS;

  private static final Set<String> CODES;

  static {
    Map<String, String> labels = new LinkedHashMap<>();
    labels.put(COURSE, "课程");
    labels.put(EXAM, "理论考试");
    labels.put(TRAINING, "培训计划");
    LABELS = Map.copyOf(labels);
    CODES = LABELS.keySet();
  }

  private CertificateSourceTypes() {}

  /** 已定义中文口径的来源类型编码。 */
  public static Set<String> codes() {
    return CODES;
  }

  /** 是否为已定义中文口径的来源类型编码。 */
  public static boolean isSupported(String code) {
    return code != null && LABELS.containsKey(code);
  }

  /** 证书变量 SOURCE_TYPE 的展示值：已知编码译为中文，未知编码原样返回。 */
  public static String label(String code) {
    if (code == null) {
      return "";
    }
    return LABELS.getOrDefault(code, code);
  }
}
