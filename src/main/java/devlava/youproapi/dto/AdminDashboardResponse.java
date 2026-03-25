package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AdminDashboardResponse {

    private int year;
    private double centerAvg;
    /** 해당 연도 전체 신청(접수) 건수 */
    private long totalSubmitted;
    private long totalSelected;
    private int memberCount;
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
        private String name;           // deptName
        private int memberCount;
        private long totalSelected;
        private double avgSelected;
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
