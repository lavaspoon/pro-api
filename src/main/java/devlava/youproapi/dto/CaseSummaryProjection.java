package devlava.youproapi.dto;

import java.time.LocalDateTime;

/**
 * 사례 목록 조회 시 {@code case_id} 등 전체 행 로딩 없이 제목·일자만 필요할 때 사용.
 */
public interface CaseSummaryProjection {

    String getTitle();

    String getStatus();

    String getCallDate();

    LocalDateTime getSubmittedAt();
}
