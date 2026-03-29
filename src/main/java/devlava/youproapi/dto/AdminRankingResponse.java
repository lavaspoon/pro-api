package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 관리자 랭킹 — {@code TB_YOU_PRO_CASE} 접수 건수 기준, 2depth 센터별 통계 포함.
 */
@Getter
@Builder
public class AdminRankingResponse {

    private int year;
    private int topN;

    /** 설정된 각 2depth 센터별: 센터 접수 합계 + 상위 N명 */
    private List<SecondDepthRanking> bySecondDepth;

    /** 설정된 2depth 전체 합집합 기준 종합 */
    private CombinedRanking combined;

    @Getter
    @Builder
    public static class SecondDepthRanking {
        private Integer secondDepthDeptId;
        private String secondDepthName;
        /** 해당 2depth 하위 구성원의 해당 연도 접수 건수 합계 (TB_YOU_PRO_CASE) */
        private long centerTotalSubmitted;
        private List<RankEntry> topMembers;
    }

    @Getter
    @Builder
    public static class CombinedRanking {
        /** 2depth 전체 하위 해당 연도 접수 건수 합계 */
        private long totalSubmitted;
        private List<RankEntry> topMembers;
    }

    @Getter
    @Builder
    public static class RankEntry {
        private String skid;
        private String memberName;
        private String teamName;
        /** 해당 연도 TB_YOU_PRO_CASE 접수 건수 (상태 무관) */
        private long submittedCount;
    }
}
