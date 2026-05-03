package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CsSatisfactionSummaryResponse {

    private int year;
    /** 집계 구간 시작일(ISO yyyy-MM-dd). 롤링·월 조회 공통. */
    private String statFrom;
    /** 집계 구간 종료일(ISO yyyy-MM-dd). */
    private String statTo;
    /** true 이면 statFrom~statTo 가 KST 기준 「당월 1일~전일」(1일이면 전월 전체). */
    private boolean rollingThroughYesterday;
    /** 집계 종료일 연도의 연간 목표% (문제해결, 역산 달성률 분모). 없으면 null */
    private Double problemResolvedAnnualTargetPercent;
    private AdminFilterMetaResponse filterMeta;
    private List<SecondDepthSatisfactionRow> rows;

    @Getter
    @Builder
    public static class SecondDepthSatisfactionRow {
        /** 리프 팀 {@code TB_LMS_DEPT.dept_id} (필터는 상위 2depth 센터, 행 단위는 하위 팀). 기타 행만 {@code -1}. */
        private Integer secondDepthDeptId;
        /** 상위 실(2depth 센터)명. 기타 행은 {@code —} 에 해당하는 값. */
        private String centerName;
        /** 리프 직상 부모가 센터가 아니면 그 부모 부서명, 없으면 {@code —}. */
        private String groupName;
        /** 리프 팀 부서 스킬 ({@code TB_LMS_DEPT.you_skill}) */
        private String skill;
        /** 리프 팀 부서명 (또는 기타 행 문구). */
        private String secondDepthName;
        /** 해당 리프 팀 서브트리 소속 · {@code you_yn = Y} 인 구성원 수 */
        private int evalTargetMemberCount;
        private long evalCount;
        private long satisfiedCount;
        private long dissatisfiedCount;
        /** 만족비중 % (만족건/평가건*100), 평가 0이면 null */
        private Double satisfactionRate;
        /** 해당 연·실의 월 목표% 산술평균(저장된 월 1일 행 기준), 없으면 null */
        private Double targetPercent;
        /** 목표 대비 달성율: 만족비중/목표%*100, 목표 없으면 null */
        private Double achievementRate;
        /** 이달 스킬목표 달성 구성원 수 (소속 부서 {@code TB_LMS_DEPT.you_skill} 기준) */
        private Long monthlySkillTargetAchievedCount;
        /** 이달 스킬목표 판정 가능 구성원 수 (목표 존재 + 평가건수>0) */
        private Long monthlySkillTargetEligibleCount;
        /** 이달 스킬목표 달성률% (달성/대상*100), 대상 0이면 null */
        private Double monthlySkillTargetAchievementRate;
        /** 중점추진과제(만족 Y + 5대 도시 Y) 건수 */
        private Long fiveMajorCitiesCount;
        /** 중점추진과제(만족 Y + 5060 Y) 건수 */
        private Long gen5060Count;
        /** 중점추진과제(만족 Y + 문제해결 Y) 건수 */
        private Long problemResolvedCount;
        /** 5대도시 Y 건수 / 평가건 * 100 */
        private Double fiveMajorCitiesPct;
        /** 5060 Y 건수 / 평가건 * 100 */
        private Double gen5060Pct;
        /** 문제해결 Y 건수 / 평가건 * 100 */
        private Double problemResolvedPct;
        /**
         * 문제해결 연간 목표 대비 역산 달성률(%): 허용 미달성비(targetGap) 대비 실제 미달성비(actualGap) 비율을 100 기준으로 환산, 상한 100.
         */
        private Double problemResolvedInverseAchievementPct;
    }
}
