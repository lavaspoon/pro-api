package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 통합(설정된 상위 실 소속) 월별 건수 + 중점추진과제(Y/N 플래그) 월별 건수.
 */
@Getter
@Builder
public class CsSatisfactionMonthlyOverviewResponse {

    private int year;
    /** 평가·만족·불만족 (통합) */
    private List<UnifiedMonthPoint> unified;
    /** 5대도시 / 5060 / 문제해결 플래그 건수 */
    private List<FocusMonthPoint> focusTasks;

    @Getter
    @Builder
    public static class UnifiedMonthPoint {
        private int month;
        private long evalCount;
        private long satisfiedCount;
        private long dissatisfiedCount;
    }

    @Getter
    @Builder
    public static class FocusMonthPoint {
        private int month;
        private long fiveMajorCitiesCount;
        private long gen5060Count;
        private long problemResolvedCount;
    }
}
