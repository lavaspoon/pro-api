package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CsSatisfactionSummaryResponse {

    private int year;
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
    }
}
