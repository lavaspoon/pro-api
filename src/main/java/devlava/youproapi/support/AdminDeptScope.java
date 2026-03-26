package devlava.youproapi.support;

import devlava.youproapi.domain.TbLmsDept;

import java.util.*;

/**
 * 관리자 화면에서 특정 루트 부서(예: 5, 7) 하위로 조직 범위를 제한할 때 사용.
 */
public final class AdminDeptScope {

    /** 관리자 통계·대기 건 조회에 포함할 최상위 부서 ID (TB_LMS_DEPT.dept_id) */
    public static final List<Integer> ROOT_DEPT_IDS = List.of(5, 7);

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

    private static void collectRecursive(int id, Map<Integer, List<Integer>> children, Set<Integer> out) {
        out.add(id);
        for (Integer c : children.getOrDefault(id, List.of())) {
            collectRecursive(c, children, out);
        }
    }
}
