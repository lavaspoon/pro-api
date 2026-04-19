package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * CS 만족도 연간 구성원 랭킹 — 만족(satisfied_yn=Y)이면서 해당 중점지표도 Y인 건수 기준 상위 N명씩.
 */
@Getter
@Builder
public class CsSatisfactionRankingResponse {

    private int year;
    private int topN;

    /** 만족 Y + 5대도시 Y 동시 만족 건수 상위 */
    private List<RankEntry> topByFiveMajorCities;
    /** 만족 Y + 5060 Y 동시 만족 건수 상위 */
    private List<RankEntry> topByGen5060;
    /** 만족 Y + 문제해결 Y 동시 만족 건수 상위 */
    private List<RankEntry> topByProblemResolved;

    @Getter
    @Builder
    public static class RankEntry {
        private String skid;
        private String memberName;
        private String teamName;
        private long count;
    }
}
