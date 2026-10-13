package com.sts.person.service;

import com.sts.person.dto.PersonSelectionRequest;

import java.util.List;

public interface PersonSelectionService {
  List<Long> resolve(PersonSelectionRequest request);
}
