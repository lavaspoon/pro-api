package devlava.youproapi.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 통합 목표 저장 요청
 * 1) 부서별 월간 목표 (2개)
 * 2) 스킬별 월간 목표 (4개)
 * 3) 중점추진과제 연간 목표 (3개)
 */
@Getter
@Setter
public class CsSatisfactionTargetsUnifiedRequest {

    @NotNull
    @Min(2000)
    @Max(2100)
    private Integer year;

    @NotNull
    @Min(1)
    @Max(12)
    private Integer month;

    /** 부서별 월간 목표 (5번/6번) */
    @Valid
    private List<DeptMonthlyTarget> deptTargets;

    /** 스킬별 월간 목표 (리텐션/일반/이관/멀티기술) */
    @Valid
    private List<SkillMonthlyTarget> skillTargets;

    /** 중점추진과제 연간 목표 (5대도시/5060/문제해결) */
    @Valid
    private List<AnnualTaskTarget> annualTargets;

    @Getter
    @Setter
    public static class DeptMonthlyTarget {
        @NotNull
        private Integer deptId;  // 5 또는 6
        @NotNull
        private BigDecimal targetPercent;
    }

    @Getter
    @Setter
    public static class SkillMonthlyTarget {
        @NotNull
        private String skillName;  // '리텐션', '일반', '이관', '멀티/기술'
        @NotNull
        private BigDecimal targetPercent;
    }

    @Getter
    @Setter
    public static class AnnualTaskTarget {
        @NotNull
        private String taskCode;  // 'FIVE_MAJOR_CITIES', 'GEN_5060', 'PROBLEM_RESOLVED'
        @NotNull
        private BigDecimal targetPercent;
    }
}
