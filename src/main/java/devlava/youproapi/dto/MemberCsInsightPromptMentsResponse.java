package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 구성원 CS 만족도 — LM 인사이트 프롬프트에 넣을 Good/Bad 멘트 원문 목록.
 */
@Getter
@Builder
public class MemberCsInsightPromptMentsResponse {
    /** 평가시간(useYn)={@code Y} 이고, 최신 상담일 행에서 추출한 긍정코멘트(중복 제거·순서 유지) */
    private List<String> goodMents;
    /** 동일 조건의 부정코멘트 */
    private List<String> badMents;
    /**
     * 멘트 수집 구간의 끝: 풀 중 상담(평가)일시({@code evalDate})가 가장 늦은 날(yyyy-MM-dd).
     * 상담일시가 없는 행만 있으면 {@code null}(이 경우 슬라이스는 풀 전체).
     */
    private String latestConsultDate;
    /**
     * 멘트 수집 구간의 시작(yyyy-MM-dd). {@link #latestConsultDate}가 있을 때 그날 포함 최근 10일 구간의 첫날.
     * 상담일시 없음으로 풀 전체를 쓸 때는 {@code null}.
     */
    private String mentWindowStartDate;
}
