package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MemberSatisfactionResponse {
    private Double monthlyTargetPct;
    private Double target;
    private Long receivedCount;
    private Long totalSamples;
    private Long satisfiedCount;
    private Long unsatisfiedCount;
    private Double monthlyActualPct;
    private Double monthlyAchievementRate;
    private Boolean monthlyTargetMet;
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
