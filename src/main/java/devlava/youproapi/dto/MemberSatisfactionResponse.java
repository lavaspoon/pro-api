package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MemberSatisfactionResponse {
    private String skill;
    private Double monthlyTargetPct;
    private Double target;
    private Long receivedCount;
    private Long totalSamples;
    private Long satisfiedCount;
    private Long unsatisfiedCount;
    private Double monthlyActualPct;
    private Double monthlyAchievementRate;
    private Boolean monthlyTargetMet;
    /**
     * 이달 평가 건수(useYn=Y) 대비 비율(%). 분모는 모두 {@link #receivedCount}와 동일.
     */
    private Double unsatisfiedPct;
    /** 5대도시 항목 Y 건수 ÷ 이달 평가 건수 × 100 */
    private Double fiveMajorCitiesPct;
    /** 5060 항목 Y 건수 ÷ 이달 평가 건수 × 100 */
    private Double gen5060Pct;
    /** 문제해결 항목 Y 건수 ÷ 이달 평가 건수 × 100 */
    private Double problemResolvedPct;
    private List<UnsatisfiedCategory> unsatisfiedCategories;
    private List<DailyTrendPoint> dailyTrend;

    @Getter
    @Builder
    public static class UnsatisfiedCategory {
        private String label;
        private Integer dissatisfactionType;
        private Long count;
    }

    @Getter
    @Builder
    public static class DailyTrendPoint {
        private String day;
        private Long satisfiedCount;
        private Long receivedCount;
    }
}
