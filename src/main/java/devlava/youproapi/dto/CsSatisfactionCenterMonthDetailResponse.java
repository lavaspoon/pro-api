package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CsSatisfactionCenterMonthDetailResponse {

    private int year;
    private int month;
    private int secondDepthDeptId;
    private String secondDepthName;

    private String statFrom;
    private String statTo;
    private boolean rollingThroughYesterday;

    private long evalCount;
    private long satisfiedCount;
    private long dissatisfiedCount;
    /** 만족건/평가건*100, 평가 0이면 null */
    private Double satisfactionRate;
    /** 중점추진과제 Y 건수(해당 연·월) */
    private long gen5060Count;
    private long fiveMajorCitiesCount;
    private long problemResolvedCount;
    private Double fiveMajorCitiesPct;
    private Double gen5060Pct;
    private Double problemResolvedPct;
    private Double problemResolvedInverseAchievementPct;
    private Double problemResolvedAnnualTargetPercent;

    private List<MemberMonthRow> members;

    @Getter
    @Builder
    public static class MemberMonthRow {
        private String skid;
        private String mbName;
        private String deptName;
        /** 구성원 소속 부서 skill의 해당 월 목표% (없으면 null) */
        private Double targetPercent;
        private long evalCount;
        private long satisfiedCount;
        private long dissatisfiedCount;
        private Double satisfactionRate;
        private long gen5060Count;
        private long fiveMajorCitiesCount;
        private long problemResolvedCount;
        private Double fiveMajorCitiesPct;
        private Double gen5060Pct;
        private Double problemResolvedPct;
        private Double problemResolvedInverseAchievementPct;
        /** 불만족 유형 1~5별 건수 */
        private List<UnsatisfiedTypeCount> unsatisfiedTypeCounts;
    }

    @Getter
    @Builder
    public static class UnsatisfiedTypeCount {
        private int dissatisfactionType;
        private String label;
        private long count;
    }
}
