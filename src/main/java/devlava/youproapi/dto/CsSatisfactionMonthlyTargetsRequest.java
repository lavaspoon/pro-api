package devlava.youproapi.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class CsSatisfactionMonthlyTargetsRequest {

    @NotNull
    @Min(2000)
    @Max(2100)
    private Integer year;

    @NotNull
    @Min(1)
    @Max(12)
    private Integer month;

    @NotNull
    @Valid
    private List<TargetRow> targets;

    @Getter
    @Setter
    public static class TargetRow {
        @NotNull
        private Integer secondDepthDeptId;
        @NotNull
        private BigDecimal targetPercent;
    }
}
