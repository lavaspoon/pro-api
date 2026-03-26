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

    /**
     * 평가 대상 센터(TB_LMS_DEPT 부서 5·7) 하위 팀 기준 연간 선정 합 순위.
     * 소속 팀이 해당 범위에 없으면 inScope=false.
     */
    private boolean evalCenterInScope;

    /** 순위 (1부터). 범위 밖이면 null */
    private Integer evalCenterTeamRank;

    /** 순위 산정에 포함된 팀 수 */
    private Integer evalCenterTeamTotal;

    /** 우리 팀 연간 누적 선정 합 (팀 단위) */
    private Long evalCenterTeamSelectedYear;

    @Getter
    @Builder
    public static class TeamInfo {
        private Integer id;
        private String name;
    }
}
