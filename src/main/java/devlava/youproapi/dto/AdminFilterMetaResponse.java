package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 관리자 대시보드 부서 필터 메타 — 백엔드 설정 기준 2depth 부서 목록·리프 depth.
 */
@Getter
@Builder
public class AdminFilterMetaResponse {

    /** 하단 팀 목록에 사용하는 depth (예: 5) */
    private int leafTeamDepth;

    /**
     * 비어 있으면 전체 서브트리 기준. 있으면 2depth 센터별 leaf dept_id (설정 leaf-dept-ids-by-second-depth 와 동일).
     */
    private Map<Integer, List<Integer>> leafDeptIdsBySecondDepth;

    /** 2depth 필터 후보 (dept_id, 부서명) */
    private List<SecondDepthDeptOption> secondDepthDepts;

    @Getter
    @Builder
    public static class SecondDepthDeptOption {
        private Integer id;
        private String name;
    }
}
