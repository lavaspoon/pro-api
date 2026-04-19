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
     * 해당 연·월 인증율(%) — {@code call_date}가 해당 월인 접수 대비 {@code status = selected}(선정·인증) 비율.
     * 해당 월 접수가 0이면 null.
     */
    private Double monthlyCertificationRate;
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
