package devlava.youproapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class CsSatisfactionTodayHourlyResponse {

    private String date;
    private String windowStart;
    private String windowEnd;
    private String zoneId;
    /** 적용된 센터(2depth) 필터. {@code null}이면 전체 센터. */
    private Integer appliedSecondDepthDeptId;
    /** 적용된 스킬 필터. {@code null}이면 전체 스킬. */
    private String appliedSkill;
    private Integer profileSuggestedCenterId;
    private String profileSuggestedSkill;
    /** 로그인 구성원이 관리 센터 트리에 속하지 않으면 {@code true} → 기본이 전체 보기 */
    private boolean profileUnscoped;
    private List<CenterOption> centers;
    private List<String> skillOptions;
    private List<HourSlot> hours;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CenterOption {
        private int id;
        private String name;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class HourSlot {
        private int hour;
        private String label;
        private int sampleCount;
        private Double satisfiedPct;
        private Double dissatisfiedPct;
        private Double fiveMajorCitiesPct;
        private Double gen5060Pct;
        private Double problemResolvedPct;
    }
}
