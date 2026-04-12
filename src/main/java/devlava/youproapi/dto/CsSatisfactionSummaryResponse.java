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
        /** 리프 팀 부서명 (또는 기타 행 문구). */
        private String secondDepthName;
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
