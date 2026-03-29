package devlava.youproapi.controller;

import devlava.youproapi.dto.AdminDashboardResponse;
import devlava.youproapi.dto.AdminLeafTeamsResponse;
import devlava.youproapi.dto.AdminRankingResponse;
import devlava.youproapi.dto.AdminReviewQueueResponse;
import devlava.youproapi.dto.CaseJudgeRequest;
import devlava.youproapi.dto.CaseResponse;
import devlava.youproapi.dto.TeamDetailResponse;
import devlava.youproapi.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * 관리자 대시보드
     * GET /api/admin/dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboard());
    }

    /**
     * 2depth 부서 선택 시 하위 leaf(4depth) 팀 목록·집계 — 쿼리 배치, N+1 없음
     * GET /api/admin/filter/leaf-teams?secondDepthDeptId=5 (생략 시 설정된 2depth 전체 하위 leaf 합집합)
     */
    @GetMapping("/filter/leaf-teams")
    public ResponseEntity<AdminLeafTeamsResponse> getLeafTeams(
            @RequestParam(required = false) Integer secondDepthDeptId) {
        return ResponseEntity.ok(adminService.getLeafTeamsForSecondDepth(secondDepthDeptId));
    }

    /**
     * 랭킹 — TB_YOU_PRO_CASE 연도별 접수 건수 기준, 2depth 센터별 접수 합계 + 상위 N명
     * GET /api/admin/ranking?year=2026&topN=3
     */
    @GetMapping("/ranking")
    public ResponseEntity<AdminRankingResponse> getRanking(
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "3") int topN) {
        int y = year != null ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(adminService.getRanking(y, topN));
    }

    /**
     * 검토 대기 화면 — 대시보드 + 대기 사례 한 번에 (중복 스코프 조회 방지)
     * GET /api/admin/review-queue
     */
    @GetMapping("/review-queue")
    public ResponseEntity<AdminReviewQueueResponse> getReviewQueue() {
        return ResponseEntity.ok(adminService.getReviewQueue());
    }

    /**
     * 팀(실) 상세 구성원 현황
     * GET /api/admin/teams/{deptIdx}
     */
    @GetMapping("/teams/{deptIdx}")
    public ResponseEntity<TeamDetailResponse> getTeamDetail(@PathVariable Integer deptIdx) {
        return ResponseEntity.ok(adminService.getTeamDetail(deptIdx));
    }

    /**
     * 검토 대기 전체 사례
     * GET /api/admin/cases/pending
     */
    @GetMapping("/cases/pending")
    public ResponseEntity<List<CaseResponse>> getPendingCases() {
        return ResponseEntity.ok(adminService.getPendingCases());
    }

    /**
     * 사례 상세 조회 (STT 대화 포함)
     * GET /api/admin/cases/{caseId}
     */
    @GetMapping("/cases/{caseId}")
    public ResponseEntity<CaseResponse> getCaseForReview(@PathVariable Long caseId) {
        return ResponseEntity.ok(adminService.getCaseForReview(caseId));
    }

    /**
     * 사례 판정 (선정/비선정)
     * POST /api/admin/cases/{caseId}/judge
     */
    @PostMapping("/cases/{caseId}/judge")
    public ResponseEntity<CaseResponse> judgeCase(
            @PathVariable Long caseId,
            @Valid @RequestBody CaseJudgeRequest req) {
        return ResponseEntity.ok(adminService.judgeCase(caseId, req));
    }
}
