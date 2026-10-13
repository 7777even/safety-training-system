package com.sts.common.api.dict;

import java.util.List;
import java.util.Map;

public record DictCatalogVO(
    String version,
    Map<String, List<DictItemVO>> types
) {
}
