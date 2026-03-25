package devlava.youproapi.controller;

import devlava.youproapi.dto.AdminDashboardResponse;
import devlava.youproapi.dto.CaseJudgeRequest;
import devlava.youproapi.dto.CaseResponse;
import devlava.youproapi.dto.TeamDetailResponse;
import devlava.youproapi.service.AdminService;
import devlava.youproapi.service.CaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final CaseService  caseService;

    /**
     * 관리자 대시보드
     * GET /api/admin/dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboard());
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
        return ResponseEntity.ok(caseService.getPendingCases());
    }

    /**
     * 사례 상세 조회 (STT 대화 포함)
     * GET /api/admin/cases/{caseId}
     */
    @GetMapping("/cases/{caseId}")
    public ResponseEntity<CaseResponse> getCaseForReview(@PathVariable Long caseId) {
        return ResponseEntity.ok(caseService.getCaseForReview(caseId));
    }

    /**
     * 사례 판정 (선정/비선정)
     * POST /api/admin/cases/{caseId}/judge
     */
    @PostMapping("/cases/{caseId}/judge")
    public ResponseEntity<CaseResponse> judgeCase(
            @PathVariable Long caseId,
            @Valid @RequestBody CaseJudgeRequest req) {
        return ResponseEntity.ok(caseService.judgeCase(caseId, req));
    }
}
