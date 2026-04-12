package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class CsSatisfactionMonthlyTargetsResponse {

    private int year;
    private int month;
    /**
     * 해당 연·월에 설정된 모든 상위 센터(second-depth)에 목표가 DB에 있으면 true — 엑셀 업로드 허용 기준.
     * 저장 키는 항상 그 달 1일({@code target_date}).
     */
    private boolean allCentersSet;
    private List<CenterTargetRow> centers;

    @Getter
    @Builder
    public static class CenterTargetRow {
        private int secondDepthDeptId;
        private String secondDepthName;
        /** 없으면 null */
        private BigDecimal targetPercent;
    }
}
