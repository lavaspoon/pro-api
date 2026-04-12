package devlava.youproapi.controller;

import devlava.youproapi.dto.AdminDashboardResponse;
import devlava.youproapi.dto.AdminLeafTeamsResponse;
import devlava.youproapi.dto.AdminRankingResponse;
import devlava.youproapi.dto.AdminReviewQueueResponse;
import devlava.youproapi.dto.CaseJudgeRequest;
import devlava.youproapi.dto.CaseResponse;
import devlava.youproapi.dto.CsSatisfactionCenterMonthDetailResponse;
import devlava.youproapi.dto.CsSatisfactionMonthlyTargetsRequest;
import devlava.youproapi.dto.CsSatisfactionMonthlyTargetsResponse;
import devlava.youproapi.dto.CsSatisfactionMonthlyTrendResponse;
import devlava.youproapi.dto.CsSatisfactionSummaryResponse;
import devlava.youproapi.dto.CsSatisfactionUploadResponse;
import devlava.youproapi.dto.TeamDetailResponse;
import devlava.youproapi.service.AdminService;
import devlava.youproapi.service.CsSatisfactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final CsSatisfactionService csSatisfactionService;

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

    /**
     * CS 만족도 — 실(2depth)별 연간 요약
     * GET /api/admin/cs-satisfaction/summary?year=&secondDepthDeptId=
     */
    @GetMapping("/cs-satisfaction/summary")
    public ResponseEntity<CsSatisfactionSummaryResponse> csSatisfactionSummary(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer secondDepthDeptId) {
        return ResponseEntity.ok(csSatisfactionService.getSummary(year, secondDepthDeptId));
    }

    /**
     * CS 만족도 — 선택 실의 올해 월별 평가·만족 건수
     * GET /api/admin/cs-satisfaction/monthly-trend?year=&secondDepthDeptId=
     */
    @GetMapping("/cs-satisfaction/monthly-trend")
    public ResponseEntity<CsSatisfactionMonthlyTrendResponse> csSatisfactionMonthlyTrend(
            @RequestParam(required = false) Integer year,
            @RequestParam int secondDepthDeptId) {
        int y = year != null ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(csSatisfactionService.getMonthlyTrend(y, secondDepthDeptId));
    }

    /**
     * 선택 센터의 해당 연·월 만족도 요약 + 구성원별 건수 (기본: 올해 이번 달)
     * GET /api/admin/cs-satisfaction/center-month-detail?secondDepthDeptId=&year=&month=
     */
    @GetMapping("/cs-satisfaction/center-month-detail")
    public ResponseEntity<CsSatisfactionCenterMonthDetailResponse> csSatisfactionCenterMonthDetail(
            @RequestParam int secondDepthDeptId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(csSatisfactionService.getCenterMonthDetail(secondDepthDeptId, year, month));
    }

    /**
     * CS 만족도 엑셀 업로드 (.xlsx) — 파일에 포함된 날짜는 기존 행 삭제 후 재적재
     * POST /api/admin/cs-satisfaction/upload  (multipart file 필드명: file)
     */
    @PostMapping(value = "/cs-satisfaction/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsSatisfactionUploadResponse> csSatisfactionUpload(
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(csSatisfactionService.uploadExcel(file));
    }

    /**
     * 월간 목표(%) 조회 — 상위 센터별 저장 여부 포함 (엑셀 업로드 전 완료 확인용). DB에는 해당 월 1일로 저장.
     * GET /api/admin/cs-satisfaction/monthly-targets?year=&month=
     */
    @GetMapping("/cs-satisfaction/monthly-targets")
    public ResponseEntity<CsSatisfactionMonthlyTargetsResponse> csSatisfactionMonthlyTargetsGet(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(csSatisfactionService.getMonthlyTargets(year, month));
    }

    /**
     * 월간 목표(%) 등록·수정 (센터별, 월 1회)
     * POST /api/admin/cs-satisfaction/monthly-targets
     */
    @PostMapping("/cs-satisfaction/monthly-targets")
    public ResponseEntity<Void> csSatisfactionMonthlyTargets(
            @Valid @RequestBody CsSatisfactionMonthlyTargetsRequest req) {
        csSatisfactionService.upsertMonthlyTargets(req);
        return ResponseEntity.ok().build();
    }
}
