package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 관리자 조직 스코프(구성원 IN) 기준 사례 집계 — 검토 대기 API 등에서 한 번에 로드한다.
 */
@Getter
@Builder
public class AdminScopedCaseStats {

    private final long totalSubmittedYear;
    private final long totalSelectedYear;

    /** 월(1~12) → 해당 연도 그 달 접수 건수(상태 무관) */
    private final Map<Integer, Long> submittedByMonth;

    /** 월(1~12) → 해당 연도 그 달 선정 건수 */
    private final Map<Integer, Long> selectedByMonth;

    private final Map<String, Long> selectedBySkidYear;
    private final Map<String, Long> selectedBySkidYearMonth;
    private final Map<String, Long> pendingBySkid;
    private final Map<String, Long> judgedBySkidYear;
}
