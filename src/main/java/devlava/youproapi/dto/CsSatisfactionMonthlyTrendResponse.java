package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CsSatisfactionMonthlyTrendResponse {

    private int year;
    private Integer secondDepthDeptId;
    private String secondDepthName;
    private List<MonthlyPoint> months;

    @Getter
    @Builder
    public static class MonthlyPoint {
        private int month;
        private long evalCount;
        private long satisfiedCount;
    }
}
