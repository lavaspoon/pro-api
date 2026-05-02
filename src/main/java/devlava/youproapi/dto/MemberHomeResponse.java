package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MemberHomeResponse {

    /** 소속 팀 정보 */
    private TeamInfo team;

    /** 소속 팀 연간 인당 평균 선정 건수 */
    private double teamAvgSelected;

    /** 내 연간 누적 인증 건수 — 만족도 달성 월의 선정 건만 스케줄러 반영 합산 */
    private long myTotalSelected;

    /**
     * 화면에 표시하는 반영 누적 건수.
     * {@code tb_you_incentive_reflect} 에서 해당 연도 중 {@code reflect_month} 가 가장 큰(가장 최근 반영) 행의
     * {@code cumulative_count} 값이다. 반영 이력이 없으면 0.
     */
    private long yearReflectCumulativeCount;

    /**
     * 1~9월 프로그램 기간 월별 반영 요약(행 없음이면 해당 월 값은 null).
     */
    private List<ReflectMonthRow> reflectMonthsJanSep;

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

    /** 구성원 부서 스킬명 — 만족도 목표 매칭 기준. 없으면 null */
    private String deptSkillName;

    /** 이번 달 만족도 접수 건수 (DB에서 useYn='Y' 만) */
    private Long csReceivedUseY;

    /** 이번 달 긍정 건수 (동일 조건의 접수 중 satisfiedYn='Y') */
    private Long csSatisfiedUseY;

    /** 부서 스킬 월간 만족도 목표 % */
    private Double csSkillTargetPercent;

    /** 이번 달 실제 만족도 % (표시용, 소수 1자리) */
    private Double csActualPercent;

    /**
     * 올해 스케줄러가 확정 반영한 월의 지급액 합계.
     * 반영 건수가 0인 월(미달성·달성+선정 0건)은 0원으로 집계된다.
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
    public static class ReflectMonthRow {
        /** 1~9 */
        private int month;
        /** 반영 행이 없으면 null. 있으면 {@code cs_target_met} — 달성 여부 */
        private Boolean csTargetMet;
        /** 반영 행이 없으면 null. 있으면 {@code selected_count_raw} */
        private Integer selectedCountRaw;
    }

    @Getter
    @Builder
    public static class TeamInfo {
        private Integer id;
        private String name;
    }
}
