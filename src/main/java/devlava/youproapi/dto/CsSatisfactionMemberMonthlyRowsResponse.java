package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CsSatisfactionMemberMonthlyRowsResponse {

    private int year;
    private String skid;
    private String memberName;
    private long totalCount;
    private List<MonthBucket> months;

    @Getter
    @Builder
    public static class MonthBucket {
        private int month;
        private long count;
        private List<RowItem> rows;
    }

    @Getter
    @Builder
    public static class RowItem {
        private Long id;
        private LocalDateTime consultDateTime;
        private String consultType1;
        private String consultType2;
        private String consultType3;
        private String satisfiedYn;
        private String fiveMajorCitiesYn;
        private String gen5060Yn;
        private String problemResolvedYn;
        private String goodMent;
        private String badMent;
        /** 불만족 유형: null·빈 문자열 또는 "1"~"5" */
        private String dissatisfactionType;
    }
}
