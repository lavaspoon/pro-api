package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 관리자 CS 만족도 상단 KPI */
@Getter
@Builder
public class CsSatisfactionAdminDashboardKpiResponse {

    /** {@link #getSummary} 와 동일한 연간 집계 연도 */
    private int summaryYear;

    /** 당월 지표(스킬 평균·중점 3종) 기준 연도 — 서버 현재 연·월 */
    private int kpiYear;

    /** 당월 지표 월 (1~12) */
    private int kpiMonth;

    /** 월간 목표 기준 만족도 달성률 (종합/센터별) */
    private List<ScopeAchievementRow> centerAchievements;
    /** 연간 목표 기준 5대도시 달성률 (종합/센터별) */
    private List<ScopeAchievementRow> fiveMajorCities;
    /** 연간 목표 기준 5060 달성률 (종합/센터별) */
    private List<ScopeAchievementRow> gen5060;
    /** 연간 목표 기준 문제해결 달성률 (종합/센터별) */
    private List<ScopeAchievementRow> problemResolved;

    @Getter
    @Builder
    public static class ScopeAchievementRow {
        /** OVERALL 또는 센터 id 문자열 */
        private String scopeKey;
        /** 종합/부산/서부 등 화면 표시명 */
        private String scopeName;
        /** 목표값(%) */
        private Double targetPercent;
        /** 실적값(%) */
        private Double actualRate;
        /** 목표 대비 달성률% (100=목표 달성) */
        private Double achievementRate;
        private Boolean targetMet;
    }
}
