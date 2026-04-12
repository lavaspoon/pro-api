package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CsSatisfactionCenterMonthDetailResponse {

    private int year;
    private int month;
    private int secondDepthDeptId;
    private String secondDepthName;

    private long evalCount;
    private long satisfiedCount;
    private long dissatisfiedCount;
    /** 만족건/평가건*100, 평가 0이면 null */
    private Double satisfactionRate;

    private List<MemberMonthRow> members;

    @Getter
    @Builder
    public static class MemberMonthRow {
        private String skid;
        private String mbName;
        private String deptName;
        private long evalCount;
        private long satisfiedCount;
        private long dissatisfiedCount;
        private Double satisfactionRate;
    }
}
