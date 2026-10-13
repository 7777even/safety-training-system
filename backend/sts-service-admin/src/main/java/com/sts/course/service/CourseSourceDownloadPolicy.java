package com.sts.course.service;

import java.util.Set;

final class CourseSourceDownloadPolicy {

  private static final Set<String> ENABLED_MODES = Set.of("ALL", "PARTIAL");

  private CourseSourceDownloadPolicy() {
  }

  static boolean isEnabled(String sourceDownloadMode) {
    return sourceDownloadMode != null && ENABLED_MODES.contains(sourceDownloadMode);
  }
}
