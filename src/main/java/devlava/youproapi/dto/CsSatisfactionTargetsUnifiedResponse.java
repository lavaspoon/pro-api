package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 통합 목표 조회 응답
 */
@Getter
@Builder
public class CsSatisfactionTargetsUnifiedResponse {

    private int year;
    private int month;

    /** 모든 필수 목표가 설정되었는지 여부 (엑셀 업로드 허용 기준) */
    private boolean allTargetsSet;

    /** 부서별 월간 목표 */
    private List<DeptMonthlyTargetRow> deptTargets;

    /** 스킬별 월간 목표 */
    private List<SkillMonthlyTargetRow> skillTargets;

    /** 중점추진과제 연간 목표 */
    private List<AnnualTaskTargetRow> annualTargets;

    @Getter
    @Builder
    public static class DeptMonthlyTargetRow {
        private int deptId;
        private String deptName;
        private BigDecimal targetPercent;
    }

    @Getter
    @Builder
    public static class SkillMonthlyTargetRow {
        private String skillName;
        private BigDecimal targetPercent;
    }

    @Getter
    @Builder
    public static class AnnualTaskTargetRow {
        private String taskCode;
        private String taskName;
        private BigDecimal targetPercent;
    }
}
