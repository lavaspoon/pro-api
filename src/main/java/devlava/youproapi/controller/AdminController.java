package devlava.youproapi.controller;

import devlava.youproapi.dto.AdminDashboardResponse;
import devlava.youproapi.dto.AdminLeafTeamsResponse;
import devlava.youproapi.dto.AdminRankingResponse;
import devlava.youproapi.dto.AdminReviewQueueResponse;
import devlava.youproapi.dto.CaseJudgeRequest;
import devlava.youproapi.dto.CaseResponse;
import devlava.youproapi.dto.CsSatisfactionCenterMonthDetailResponse;
import devlava.youproapi.dto.CsSatisfactionExcludeLogResponse;
import devlava.youproapi.dto.CsSatisfactionExcludeTimeRequest;
import devlava.youproapi.dto.CsSatisfactionExcludeTimeResponse;
import devlava.youproapi.dto.CsSatisfactionMemberMonthlyRowsResponse;
import devlava.youproapi.dto.CsSatisfactionMonthlyTargetsRequest;
import devlava.youproapi.dto.CsSatisfactionMonthlyTargetsResponse;
import devlava.youproapi.dto.CsSatisfactionAdminDashboardKpiResponse;
import devlava.youproapi.dto.CsSatisfactionMonthlyOverviewResponse;
import devlava.youproapi.dto.CsSatisfactionMonthlyTrendResponse;
import devlava.youproapi.dto.CsSatisfactionRankingResponse;
import devlava.youproapi.dto.CsSatisfactionSummaryResponse;
import devlava.youproapi.dto.CsSatisfactionTodayHourlyResponse;
import devlava.youproapi.dto.CsSatisfactionTargetsUnifiedRequest;
import devlava.youproapi.dto.CsSatisfactionTargetsUnifiedResponse;
import devlava.youproapi.dto.TargetMemberUploadResponse;
import devlava.youproapi.dto.TeamDetailResponse;
import devlava.youproapi.service.AdminService;
import devlava.youproapi.service.CsSatisfactionService;
import devlava.youproapi.service.TargetMemberUploadService;
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
    private final TargetMemberUploadService targetMemberUploadService;

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
     * GET /api/admin/ranking?year=2026&topN=15
     */
    @GetMapping("/ranking")
    public ResponseEntity<AdminRankingResponse> getRanking(
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "15") int topN) {
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
     * 사례 상세 조회
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
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer secondDepthDeptId,
            @RequestParam(required = false, defaultValue = "false") boolean rollingThroughYesterday) {
        return ResponseEntity.ok(
                csSatisfactionService.getSummary(year, month, secondDepthDeptId, rollingThroughYesterday));
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
     * CS 만족도 — 통합 월별(평가·만족·불만족) + 중점추진과제 월별 건수 (한 번에)
     * GET /api/admin/cs-satisfaction/monthly-overview?year=
     */
    @GetMapping("/cs-satisfaction/monthly-overview")
    public ResponseEntity<CsSatisfactionMonthlyOverviewResponse> csSatisfactionMonthlyOverview(
            @RequestParam(required = false) Integer year) {
        int y = year != null ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(csSatisfactionService.getMonthlyOverview(y));
    }

    /**
     * CS 만족도 — 관리자 상단 KPI(센터 연간 달성·스킬 평균 달성·중점 3종 당월)
     * GET /api/admin/cs-satisfaction/dashboard-kpis?year=
     */
    @GetMapping("/cs-satisfaction/dashboard-kpis")
    public ResponseEntity<CsSatisfactionAdminDashboardKpiResponse> csSatisfactionDashboardKpis(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(csSatisfactionService.getAdminDashboardKpis(year, month));
    }

    /**
     * CS 만족도 — 금일(09:00~18:59, KST) 시간대별 만족도 스냅샷
     * GET /api/admin/cs-satisfaction/today-hourly?secondDepthDeptId=&skill=&adminSkid=
     * <p>{@code secondDepthDeptId} 생략 시 로그인 관리자 소속 센터 기본(미소속이면 전체),
     * {@code 0}이면 센터 전체. {@code skill} 생략 시 프로필 스킬 기본, 빈 문자열이면 스킬 전체.
     */
    @GetMapping("/cs-satisfaction/today-hourly")
    public ResponseEntity<CsSatisfactionTodayHourlyResponse> csSatisfactionTodayHourly(
            @RequestParam(required = false) Integer secondDepthDeptId,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String adminSkid) {
        return ResponseEntity.ok(csSatisfactionService.getTodayHourly(secondDepthDeptId, skill, adminSkid));
    }

    /**
     * CS 만족도 — 연간 구성원 랭킹(만족·5대도시·5060·문제해결 각 상위 N명)
     * GET /api/admin/cs-satisfaction/ranking?year=&topN=3
     */
    @GetMapping("/cs-satisfaction/ranking")
    public ResponseEntity<CsSatisfactionRankingResponse> csSatisfactionRanking(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false, defaultValue = "3") int topN) {
        return ResponseEntity.ok(csSatisfactionService.getRanking(year, month, topN));
    }

    /**
     * 선택 팀(리프) 또는 센터의 만족도 요약 + 구성원별 건수.
     * {@code month} 가 있으면 해당 월만, 없으면 해당 연도 1/1~12/31.
     * GET /api/admin/cs-satisfaction/center-month-detail?secondDepthDeptId=&year=&month=
     */
    @GetMapping("/cs-satisfaction/center-month-detail")
    public ResponseEntity<CsSatisfactionCenterMonthDetailResponse> csSatisfactionCenterMonthDetail(
            @RequestParam int secondDepthDeptId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false, defaultValue = "false") boolean rollingThroughYesterday) {
        return ResponseEntity.ok(csSatisfactionService.getCenterMonthDetail(
                secondDepthDeptId, year, month, rollingThroughYesterday));
    }

    /**
     * 평가 제외 적용 이력 (최근 N건)
     * GET /api/admin/cs-satisfaction/exclude-log?limit=50
     */
    @GetMapping("/cs-satisfaction/exclude-log")
    public ResponseEntity<CsSatisfactionExcludeLogResponse> csSatisfactionExcludeLog(
            @RequestParam(required = false, defaultValue = "50") int limit) {
        return ResponseEntity.ok(csSatisfactionService.getExcludeLogRecent(limit));
    }

    /**
     * CS 만족도 — 구성원별 연간 접수 row를 월별 그룹으로 조회
     * GET /api/admin/cs-satisfaction/member-monthly-rows?skid=&year=
     */
    @GetMapping("/cs-satisfaction/member-monthly-rows")
    public ResponseEntity<CsSatisfactionMemberMonthlyRowsResponse> csSatisfactionMemberMonthlyRows(
            @RequestParam String skid,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(csSatisfactionService.getMemberMonthlyRows(skid, year));
    }

    /**
     * CS 만족도 — 스킬 + 상담일시 구간(시작·종료 포함) 평가 제외(useYn='N')
     * POST /api/admin/cs-satisfaction/exclude-time
     */
    @PostMapping("/cs-satisfaction/exclude-time")
    public ResponseEntity<CsSatisfactionExcludeTimeResponse> csSatisfactionExcludeTime(
            @Valid @RequestBody CsSatisfactionExcludeTimeRequest req) {
        return ResponseEntity.ok(csSatisfactionService.excludeTime(req));
    }


    /**
     * 월간 목표(%) 조회 — 상위 센터별 저장 여부 포함. DB에는 해당 월 1일로 저장.
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

    /**
     * 통합 목표 조회 (부서/스킬/연간)
     * GET /api/admin/cs-satisfaction/targets-unified
     */
    @GetMapping("/cs-satisfaction/targets-unified")
    public ResponseEntity<CsSatisfactionTargetsUnifiedResponse> csSatisfactionTargetsUnifiedGet(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(csSatisfactionService.getTargetsUnified(year, month));
    }

    /**
     * 통합 목표 저장 (부서/스킬/연간)
     * POST /api/admin/cs-satisfaction/targets-unified
     */
    @PostMapping("/cs-satisfaction/targets-unified")
    public ResponseEntity<Void> csSatisfactionTargetsUnified(
            @Valid @RequestBody CsSatisfactionTargetsUnifiedRequest req) {
        csSatisfactionService.upsertTargetsUnified(req);
        return ResponseEntity.ok().build();
    }

    /**
     * 평가 대상자 업로드
     * POST /api/admin/target-members/upload (multipart file 필드명: file)
     */
    @PostMapping(value = "/target-members/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TargetMemberUploadResponse> targetMembersUpload(
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(targetMemberUploadService.uploadExcel(file));
    }
}
