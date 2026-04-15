package devlava.youproapi.dto;

/** 구성원 당월 중점추진과제(5대도시·5060·문제해결) Y 건수 */
public record MemberCsFocusTasksResponse(
        long fiveMajorCitiesCount,
        long gen5060Count,
        long problemResolvedCount
) {}
