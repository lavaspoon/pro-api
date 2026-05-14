package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder(toBuilder = true)
public class AdminDashboardResponse {

    /** 관리자 2depth 필터 메타(백엔드 설정) — 프론트 하드코딩 금지 */
    private AdminFilterMetaResponse filterMeta;

    private int year;
    private double centerAvg;
    /** 해당 연도 전체 신청(접수) 건수 — {@code call_date} 연도 기준 */
    private long totalSubmitted;
    /** 해당 연도 선정 건수 — {@code call_date} 연도, {@code status = selected} */
    private long totalSelected;
    private int memberCount;
    /** KPI용 현재 달(1~12) */
    private int currentMonth;
    /**
     * 상단 KPI 「전월 인증」에 사용하는 반영 연도 — {@code tb_you_incentive_reflect.reflect_year}
     * (현재 달 기준 전월; 1월이면 전년 12월).
     */
    private int priorReflectYear;
    /**
     * 상단 KPI 「전월 인증」에 사용하는 반영 월(1~12) — {@code tb_you_incentive_reflect.reflect_month}
     */
    private int priorReflectMonth;
    /** 해당 연·월 접수 건수(상태 무관) — {@code call_date} 연·월 */
    private long monthlySubmitted;
    /** 해당 연·월 선정 건수 — {@code call_date} 연·월, {@code status = selected} */
    private long monthlySelected;
    /**
     * 연간 인증률(%) 종합 — 스코프 전체 평가대상자 중 인증 인원 ÷
     * 각 월(1~{@code currentMonth})에 대해 설정된 모든 센터의 {@code eval_target_count} 합의 산술평균.
     * 어느 월이든 한 센터라도 스냅샷이 없으면 null.
     */
    private Double overallAnnualCertificationRate;
    /**
     * 설정된 각 2depth 센터별 상단 KPI(평가대상·해당 월 접수·전월 인증 반영 건수·연간 인증률).
     * 월 접수는 해당 센터 평가대상자(skid) 소속 사례 기준 합계.
     * 전월 인증은 {@link #priorReflectYear}/{@link #priorReflectMonth} 의 {@code tb_you_incentive_reflect.reflected_count} 합.
     */
    private List<CenterOverviewKpi> centerOverview;
    /** 해당 연도 1~12월 신청·선정 건수 (차트용) */
    private List<MonthlyTrendPoint> monthlyTrend;
    private List<TeamSummary> teams;

    @Getter
    @Builder
    public static class CenterOverviewKpi {
        /** {@code youpro.admin.second-depth-dept-ids} 항목과 동일 */
        private int secondDepthDeptId;
        private String centerName;
        /** 해당 센터 소속 스코프 내 평가대상자({@code you_yn = Y}) 수 */
        private int memberCount;
        /** 해당 센터 평가대상자 소속 사례의 {@code call_date} 기준 해당 연·월 접수 건수 합 */
        private long monthlySubmitted;
        /** 해당 센터 평가대상자 소속 사례의 해당 연·월 선정 건수 합 */
        private long monthlySelected;
        /**
         * 전월 인센티브 반영 인증 건수 합 — {@code tb_you_incentive_reflect.reflected_count}
         * ({@link AdminDashboardResponse#priorReflectYear}/{@link AdminDashboardResponse#priorReflectMonth}) 기준,
         * 해당 센터 평가대상자(skid)만 합산.
         */
        private long priorMonthReflectedCount;
        /**
         * 연간 인증률(%) — 해당 센터 평가대상자 중 해당 연 {@code cumulative_count >= 1} 인원 ÷
         * {@code tb_you_incentive_month_stat} 해당 센터·연도 1~{@code currentMonth}월 {@code eval_target_count} 평균 × 100.
         */
        private Double annualCertificationRate;
    }

    @Getter
    @Builder
    public static class MonthlyTrendPoint {
        private int month;       // 1–12
        private long submitted;
        private long selected;
    }

    @Getter
    @Builder
    public static class TeamSummary {
        private Integer id;            // deptIdx
        /** 2depth 센터(루트) 부서명 */
        private String centerName;
        /** leaf 직속 상위 부서명 — 상위가 센터이면 빈 문자열 */
        private String groupName;
        /** leaf 팀 부서 스킬 ({@code TB_LMS_DEPT.you_skill}) */
        private String skill;
        private String name;           // deptName
        /** 팀명 옆 배지용 — {@code you_yn = Y} 평가대상자 수 */
        private int memberCount;
        /** 해당 연도 팀 소속 접수(신청) 건수 합 */
        private long totalSubmitted;
        private long totalSelected;
        private double avgSelected;
        /** 이번 달 팀 소속 접수 건수 합 */
        private long monthlySubmitted;
        /** 이번 달 팀 소속 선정 건수 합 */
        private long monthlySelected;
        /**
         * 팀 소속 구성원별 해당 연 최신 반영 월 {@code tb_you_incentive_reflect.cumulative_count} 합계
         * (만족도 달성 시 반영되는 누적 인증 건수).
         */
        private long reflectCumulativeTotal;
        /**
         * 평가대상자({@code you_yn = Y}) 중 해당 연 {@code cumulative_count >= 1} 인 인원 수
         * ({@link #certificationRate} 분자).
         */
        private int certifiedEvalTargetCount;
        /**
         * 인증률(%) — {@link #certifiedEvalTargetCount} ÷ {@link #memberCount} × 100.
         * 평가대상자 0명이면 null.
         */
        private Double certificationRate;
        /** 팀 소속 구성원의 검토 대기 건수 합 */
        private long pendingCount;
        /** 해당 연도 판정 완료(선정+비선정) 건수 */
        private long judgedCount;
        private List<MemberSummary> members;
    }

    @Getter
    @Builder
    public static class MemberSummary {
        private String id;             // skid (frontend: member.id)
        private String name;           // mbName
        private String position;       // mbPositionName
        private long totalSelected;
        private long monthlySelected;
        private long pendingCount;
    }
}
