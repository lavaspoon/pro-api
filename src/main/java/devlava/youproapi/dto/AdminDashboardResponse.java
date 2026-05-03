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
    /** 해당 연·월 접수 건수(상태 무관) — {@code call_date} 연·월 */
    private long monthlySubmitted;
    /** 해당 연·월 선정 건수 — {@code call_date} 연·월, {@code status = selected} */
    private long monthlySelected;
    /**
     * 연간 인증률(%) — 해당 연도에 {@code tb_you_incentive_reflect.cumulative_count >= 1} 인
     * 스코프 내 평가대상자 수 ÷ {@code tb_you_incentive_month_stat} 해당 연도 1월부터 KPI
     * {@code currentMonth} 월까지 {@code eval_target_count} 산술평균 × 100.
     * 스냅샷이 없거나 평균이 0이면 null.
     */
    private Double annualCertificationRate;
    /** 해당 연도 1~12월 신청·선정 건수 (차트용) */
    private List<MonthlyTrendPoint> monthlyTrend;
    private List<TeamSummary> teams;

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
