package devlava.youproapi.support;

import devlava.youproapi.domain.TbLmsDept;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 관리자 화면에서 설정된 2depth 루트 부서들의 하위로 조직 범위를 제한할 때 사용.
 * 루트 ID 목록은 {@link devlava.youproapi.config.YouproAdminProperties#getSecondDepthDeptIds()} 로만 주입한다.
 */
public final class AdminDeptScope {

    private AdminDeptScope() {
    }

    /**
     * 주어진 루트들의 하위 부서 ID 전체(루트 포함)를 수집한다.
     *
     * @param allDepts TB_LMS_DEPT 전체 (parent 는 이미 로드된 상태 권장)
     * @param rootIds  루트 부서 ID 목록
     */
    public static Set<Integer> collectSubtreeDeptIds(List<TbLmsDept> allDepts, Collection<Integer> rootIds) {
        Map<Integer, List<Integer>> children = new HashMap<>();
        for (TbLmsDept d : allDepts) {
            Integer id = d.getDeptId();
            if (d.getParent() != null) {
                Integer pid = d.getParent().getDeptId();
                children.computeIfAbsent(pid, k -> new ArrayList<>()).add(id);
            }
        }
        Set<Integer> out = new HashSet<>();
        for (Integer root : rootIds) {
            if (root != null) {
                collectRecursive(root, children, out);
            }
        }
        return out;
    }

    /**
     * 한 루트 하위 서브트리에서, 지정 depth 인 부서만 dept_id 순으로 반환 (추가 쿼리 없음).
     */
    public static List<TbLmsDept> listDeptsOfDepthInSubtree(
            List<TbLmsDept> allDepts, int subtreeRootId, int depth) {
        Set<Integer> subtree = collectSubtreeDeptIds(allDepts, List.of(subtreeRootId));
        return allDepts.stream()
                .filter(d -> d.getDepth() != null
                        && d.getDepth() == depth
                        && subtree.contains(d.getDeptId()))
                .sorted(Comparator.comparing(TbLmsDept::getDeptId))
                .collect(Collectors.toList());
    }

    /**
     * 여러 루트 각각의 서브트리에서 leaf depth 부서를 모은 뒤, dept_id 중복 제거·정렬.
     */
    public static List<TbLmsDept> listLeafDeptsUnderAnyRoot(
            List<TbLmsDept> allDepts, Collection<Integer> rootIds, int leafDepth) {
        Set<Integer> seen = new HashSet<>();
        List<TbLmsDept> acc = new ArrayList<>();
        for (Integer root : rootIds) {
            if (root == null) {
                continue;
            }
            for (TbLmsDept d : listDeptsOfDepthInSubtree(allDepts, root, leafDepth)) {
                if (seen.add(d.getDeptId())) {
                    acc.add(d);
                }
            }
        }
        acc.sort(Comparator.comparing(TbLmsDept::getDeptId));
        return acc;
    }

    private static void collectRecursive(int id, Map<Integer, List<Integer>> children, Set<Integer> out) {
        out.add(id);
        for (Integer c : children.getOrDefault(id, List.of())) {
            collectRecursive(c, children, out);
        }
    }
}
