package devlava.youproapi.service;

import devlava.youproapi.config.YouproAdminProperties;
import devlava.youproapi.domain.TbLmsDept;
import devlava.youproapi.repository.TbLmsDeptRepository;
import devlava.youproapi.support.AdminDeptScope;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 관리자 조직 스코프 부서 ID 집합.
 * {@code leaf-dept-ids-by-second-depth} 미설정·비어 있으면: 설정된 2depth 루트 하위 전체 서브트리.
 * 설정 시: 센터별 leaf 목록 또는(키 생략) 해당 센터 leaf depth 전체.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDeptScopeResolver {

    private static final Logger log = LoggerFactory.getLogger(AdminDeptScopeResolver.class);

    private final TbLmsDeptRepository deptRepository;
    private final YouproAdminProperties adminProperties;

    @Cacheable(cacheNames = "adminDeptScope", key = "'allowedDeptIds'", sync = true)
    public Set<Integer> resolveAllowedDeptIds() {
        List<TbLmsDept> depts = deptRepository.findAllWithParentFetched();
        Map<Integer, List<Integer>> byRoot = adminProperties.getLeafDeptIdsBySecondDepth();
        if (byRoot == null || byRoot.isEmpty()) {
            return AdminDeptScope.collectSubtreeDeptIds(depts, adminProperties.getSecondDepthDeptIds());
        }
        return resolveLeafDeptAllowlistBySecondDepth(depts, byRoot);
    }

    private Set<Integer> resolveLeafDeptAllowlistBySecondDepth(
            List<TbLmsDept> depts, Map<Integer, List<Integer>> byRoot) {
        int leafDepth = adminProperties.getLeafTeamDepth();
        Map<Integer, TbLmsDept> byId = depts.stream()
                .collect(Collectors.toMap(TbLmsDept::getDeptId, d -> d, (a, b) -> a));

        Set<Integer> out = new LinkedHashSet<>();
        for (Integer rootId : adminProperties.getSecondDepthDeptIds()) {
            if (rootId == null) {
                continue;
            }
            if (!byRoot.containsKey(rootId)) {
                for (TbLmsDept d : AdminDeptScope.listDeptsOfDepthInSubtree(depts, rootId, leafDepth)) {
                    out.add(d.getDeptId());
                }
                continue;
            }
            List<Integer> ids = byRoot.get(rootId);
            if (ids == null || ids.isEmpty()) {
                continue;
            }
            Set<Integer> subtree = AdminDeptScope.collectSubtreeDeptIds(depts, List.of(rootId));
            for (Integer id : ids) {
                if (id == null) {
                    continue;
                }
                TbLmsDept d = byId.get(id);
                if (d == null) {
                    log.warn(
                            "youpro.admin.leaf-dept-ids-by-second-depth: 존재하지 않는 dept_id={} (2depth={})",
                            id,
                            rootId);
                    continue;
                }
                if (d.getDepth() == null || d.getDepth() != leafDepth) {
                    log.warn(
                            "youpro.admin.leaf-dept-ids-by-second-depth: depth 불일치 dept_id={} leaf-team-depth={} actual={} (2depth={})",
                            id,
                            leafDepth,
                            d.getDepth(),
                            rootId);
                    continue;
                }
                if (!subtree.contains(id)) {
                    log.warn(
                            "youpro.admin.leaf-dept-ids-by-second-depth: 해당 2depth 하위가 아님 dept_id={} (2depth={})",
                            id,
                            rootId);
                    continue;
                }
                out.add(id);
            }
        }
        return out;
    }
}
