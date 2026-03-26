package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 검토 대기 화면용 — 대시보드 + 대기 사례를 한 번에 반환 (동일 스코프·중복 조회 최소화)
 */
@Getter
@Builder
public class AdminReviewQueueResponse {

    private AdminDashboardResponse dashboard;
    /** 검토 대기 건만 (기존 호환·통계용) */
    private List<CaseResponse> pendingCases;
    /** 동일 스코프 구성원의 전체 사례 (선정·비선정 포함) */
    private List<CaseResponse> allCases;
}
