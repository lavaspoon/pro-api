package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 관리자 CS 만족도 상단 KPI — 센터(2depth) 연간 목표 대비 달성, 스킬별 평균 달성(당월),
 * 중점지표 3종 당월 실적(연간 목표 대비).
 */
@Getter
@Builder
public class CsSatisfactionAdminDashboardKpiResponse {

    /** {@link #getSummary} 와 동일한 연간 집계 연도 */
    private int summaryYear;

    /** 당월 지표(스킬 평균·중점 3종) 기준 연도 — 서버 현재 연·월 */
    private int kpiYear;

    /** 당월 지표 월 (1~12) */
    private int kpiMonth;

    /** 설정된 상위 센터(2depth)별 연간 목표 대비 달성 */
    private List<CenterAchievementRow> centerAchievements;

    /**
     * 평가대상자 중 {@code you_skill} 이 해당 월 스킬 목표({@code skill_name})와 일치하는 구성원만 대상으로,
     * 구성원별 (접수 대비 만족%) ÷ (해당 스킬 월 목표%) × 100 의 산술 평균.
     */
    private Double overallAvgSkillAchievementRate;

    /** 위 평균에 포함된 구성원 수 */
    private Integer overallAvgSkillMemberCount;

    /**
     * 전일 대비: 어제까지 월 누적 대비 오늘까지 월 누적의 {@link #overallAvgSkillAchievementRate} 차이(퍼센트포인트).
     * 당월 1일 전날이면 null.
     */
    private Double overallAvgSkillAchievementDayOverDayPp;

    private FocusTaskAchievementRow fiveMajorCities;
    private FocusTaskAchievementRow gen5060;
    private FocusTaskAchievementRow problemResolved;

    @Getter
    @Builder
    public static class CenterAchievementRow {
        private int secondDepthDeptId;
        private String centerName;
        /** 접수 대비 만족% (0~100) */
        private Double satisfactionRate;
        /** 해당 연도 월간 센터 목표% 평균 */
        private Double targetPercentAvg;
        /** 목표 대비 달성률% (100=목표 달성) */
        private Double achievementRate;
        /** {@code achievementRate} ≥ 100 */
        private Boolean targetMet;
    }

    @Getter
    @Builder
    public static class FocusTaskAchievementRow {
        private String taskCode;
        private String taskName;
        /** 연간 목표% */
        private Double targetPercent;
        /** 당월 접수 대비 (만족 Y + 해당 지표 Y) 비율% */
        private Double actualRate;
        /** 목표 대비 달성률% */
        private Double achievementRate;
        /**
         * 전일 대비 {@link #achievementRate} 변화(퍼센트포인트). 어제까지 월 누적과 비교, 당월 1일 전날이면 null.
         */
        private Double achievementRateDayOverDayPp;
        private Long evalCount;
        private Long focusCount;
    }
}
