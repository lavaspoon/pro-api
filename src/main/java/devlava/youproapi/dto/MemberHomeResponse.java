package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberHomeResponse {

    /** 소속 팀 정보 */
    private TeamInfo team;

    /** 소속 팀 연간 인당 평균 선정 건수 */
    private double teamAvgSelected;

    /** 내 연간 누적 선정 건수 */
    private long myTotalSelected;

    /** 이번 달 선정 건수 */
    private long monthlySelected;

    /** 월 선정 한도 (고정: 3) */
    private int monthlyLimit;

    /** 현재 검토 대기 건수 */
    private long pendingCount;

    /** 연간 선정 한도 (고정: 36) */
    private int annualLimit;

    @Getter
    @Builder
    public static class TeamInfo {
        private Integer id;
        private String name;
    }
}
