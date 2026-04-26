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

    /** 내 연간 누적 선정 건수 (CS 만족도 달성 월 선정건만 합산) */
    private long myTotalSelected;

    /** 이번 달 선정 건수 (아직 스케줄러 반영 전 실시간) */
    private long monthlySelected;

    /** 월 선정 한도 (고정: 3) */
    private int monthlyLimit;

    /** 현재 검토 대기 건수 */
    private long pendingCount;

    /** 연간 선정 한도 (고정: 36) */
    private int annualLimit;

    /**
     * 이달 CS 만족도 목표 달성 여부.
     * <ul>
     *   <li>{@code true}  — 달성</li>
     *   <li>{@code false} — 미달성</li>
     *   <li>{@code null}  — 데이터 없음 / 목표 미설정</li>
     * </ul>
     */
    private Boolean currentMonthCsTargetMet;

    /**
     * 올해 지급 예정 금액 합계 (월별 등급 × 단가 누계).
     * 스케줄러가 처리한 월만 포함되며, 아직 미처리된 이번 달은 제외.
     */
    private long totalReflectedWon;

    /**
     * 올해 가장 많이 반영된 구성원의 누적 건수 — 프로그레스 바 '1위' 마커용.
     * 아직 반영 데이터가 없으면 0.
     */
    private long topSelected;

    /**
     * {@code youpro.admin}에 설정된 부서 범위 내 리프 팀 기준, 해당 연도 팀 선정 건수 합 순위.
     * 소속 팀이 범위에 없으면 inScope=false.
     */
    private boolean evalCenterInScope;

    /** 순위 (1부터). 범위 밖이면 null */
    private Integer evalCenterTeamRank;

    /** 순위 산정에 포함된 팀 수 */
    private Integer evalCenterTeamTotal;

    /** 우리 팀 연간 누적 선정 합 (팀 단위) */
    private Long evalCenterTeamSelectedYear;

    /** youYn='Y' 평가대상자 전체 중 개인 선정 건수 기준 순위 (1부터). */
    private Integer myIndividualRank;

    /** 순위 산정 대상 평가대상자 총 인원 */
    private Integer individualRankTotal;

    @Getter
    @Builder
    public static class TeamInfo {
        private Integer id;
        private String name;
    }
}
