package com.sts.admin.display.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.sts.admin.entity.SysRole;
import com.sts.admin.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class DisplayRoleCacheRepositoryImpl implements DisplayRoleCacheRepository {

    private final SysRoleMapper roleMapper;
    private final Map<String, Cache<Object, Object>> caffeineCacheMap;

    @Override
    public Map<Long, String> getDisplayNames(Set<Long> roleIds, String l1CacheName) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Map.of();
        }
        Cache<Object, Object> cache = caffeineCacheMap.get(l1CacheName);
        Map<Long, String> result = new HashMap<>();
        Set<Long> missed = new HashSet<>();

        if (cache != null) {
            Map<Object, Object> present = cache.getAllPresent(roleIds);
            for (Long roleId : roleIds) {
                Object value = present.get(roleId);
                if (value instanceof String name) {
                    result.put(roleId, name);
                } else {
                    missed.add(roleId);
                }
            }
        } else {
            missed.addAll(roleIds);
        }

        if (!missed.isEmpty()) {
            roleMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleId, missed))
                .forEach(role -> {
                    result.put(role.getRoleId(), role.getRoleName());
                    if (cache != null) {
                        cache.put(role.getRoleId(), role.getRoleName());
                    }
                });
        }
        return result;
    }
}
