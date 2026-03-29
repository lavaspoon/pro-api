package devlava.youproapi.service;

import devlava.youproapi.config.YouproAdminProperties;
import devlava.youproapi.domain.TbLmsDept;
import devlava.youproapi.repository.TbLmsDeptRepository;
import devlava.youproapi.support.AdminDeptScope;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 관리자 조직 스코프(설정된 2depth 부서 하위) ID 집합. 부서 트리는 자주 바뀌지 않으므로 짧은 TTL 캐시로
 * 대시보드·검토대기 등 병렬 요청 시 동일 쿼리 반복을 줄인다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDeptScopeResolver {

    private final TbLmsDeptRepository deptRepository;
    private final YouproAdminProperties adminProperties;

    @Cacheable(cacheNames = "adminDeptScope", key = "'allowedDeptIds'", sync = true)
    public Set<Integer> resolveAllowedDeptIds() {
        List<TbLmsDept> depts = deptRepository.findAllWithParentFetched();
        return AdminDeptScope.collectSubtreeDeptIds(depts, adminProperties.getSecondDepthDeptIds());
    }
}
