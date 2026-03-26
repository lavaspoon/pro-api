package devlava.youproapi.service;

import devlava.youproapi.domain.TbLmsMember;
import devlava.youproapi.domain.TbYouProCase;
import devlava.youproapi.dto.AdminDashboardResponse;
import devlava.youproapi.dto.AdminDashboardResponse.MemberSummary;
import devlava.youproapi.dto.AdminDashboardResponse.TeamSummary;
import devlava.youproapi.dto.AdminReviewQueueResponse;
import devlava.youproapi.dto.AdminScopedCaseStats;
import devlava.youproapi.dto.CaseJudgeRequest;
import devlava.youproapi.dto.CaseResponse;
import devlava.youproapi.dto.TeamDetailResponse;
import devlava.youproapi.repository.TbLmsMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private static final int MONTHLY_LIMIT = 3;

    private final TbLmsMemberRepository     memberRepository;
    private final AdminDeptScopeResolver    deptScopeResolver;
    private final CaseService               caseService;

    // ─── 관리자 대시보드 (부서 5·7 하위만, 통계 N+1 없음) ─────────────────────

    public AdminDashboardResponse getDashboard() {
        return buildDashboardFromMembers(loadScopedMembers());
    }

    /**
     * 검토 대기 화면 전용 — 대시보드와 대기 사례를 동일 스코프 구성원 기준으로 한 번에 구성.
     * (부서 트리·구성원 IN 조회는 1회, 통계·대기는 배치 쿼리)
     */
    public AdminReviewQueueResponse getReviewQueue() {
        List<TbLmsMember> scoped = loadScopedMembers();
        if (scoped.isEmpty()) {
            return AdminReviewQueueResponse.builder()
                    .dashboard(emptyDashboard(LocalDate.now().getYear()))
                    .pendingCases(List.of())
                    .build();
        }
        Set<String> skids = scoped.stream().map(TbLmsMember::getSkid).collect(Collectors.toSet());
        AdminDashboardResponse dashboard = buildDashboardFromMembers(scoped);
        List<CaseResponse> pending = caseService.getPendingCasesForSkids(skids);

        Map<String, TbLmsMember> memberBySkid = scoped.stream()
                .collect(Collectors.toMap(TbLmsMember::getSkid, m -> m, (a, b) -> a));
        List<String> skidList = new ArrayList<>(skids);
        List<CaseResponse> allCases = caseService.getCasesBySkids(skidList, memberBySkid::get);

        return AdminReviewQueueResponse.builder()
                .dashboard(dashboard)
                .pendingCases(pending)
                .allCases(allCases)
                .build();
    }

    private AdminDashboardResponse buildDashboardFromMembers(List<TbLmsMember> scopedMembers) {
        LocalDate now = LocalDate.now();
        int year  = now.getYear();
        int month = now.getMonthValue();

        if (scopedMembers.isEmpty()) {
            return emptyDashboard(year);
        }

        List<String> allSkids = scopedMembers.stream()
                .map(TbLmsMember::getSkid)
                .collect(Collectors.toList());

        AdminScopedCaseStats stats = caseService.loadScopedDashboardStats(allSkids, year, month);

        long totalSubmitted = stats.getTotalSubmittedYear();
        long totalSelected  = stats.getTotalSelectedYear();
        int  memberCount    = scopedMembers.size();
        double centerAvg    = memberCount == 0 ? 0.0 : (double) totalSelected / memberCount;

        Map<Integer, Long> submittedByMonth = stats.getSubmittedByMonth();
        Map<Integer, Long> selectedByMonth  = stats.getSelectedByMonth();

        Map<String, Long> selectedYearMap  = stats.getSelectedBySkidYear();
        Map<String, Long> selectedMonthMap = stats.getSelectedBySkidYearMonth();
        Map<String, Long> pendingMap       = stats.getPendingBySkid();
        Map<String, Long> judgedMap        = stats.getJudgedBySkidYear();

        Map<Integer, List<TbLmsMember>> byDept = scopedMembers.stream()
                .filter(m -> m.getDeptIdx() != null)
                .collect(Collectors.groupingBy(TbLmsMember::getDeptIdx));

        List<TeamSummary> teams = byDept.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> buildTeamSummary(
                        e.getKey(),
                        e.getValue(),
                        year,
                        month,
                        selectedYearMap,
                        selectedMonthMap,
                        pendingMap,
                        judgedMap))
                .collect(Collectors.toList());

        List<AdminDashboardResponse.MonthlyTrendPoint> monthlyTrend = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            monthlyTrend.add(AdminDashboardResponse.MonthlyTrendPoint.builder()
                    .month(m)
                    .submitted(submittedByMonth.getOrDefault(m, 0L))
                    .selected(selectedByMonth.getOrDefault(m, 0L))
                    .build());
        }

        return AdminDashboardResponse.builder()
                .year(year)
                .centerAvg(Math.round(centerAvg * 10.0) / 10.0)
                .totalSubmitted(totalSubmitted)
                .totalSelected(totalSelected)
                .memberCount(memberCount)
                .monthlyTrend(monthlyTrend)
                .teams(teams)
                .build();
    }

    private AdminDashboardResponse emptyDashboard(int year) {
        List<AdminDashboardResponse.MonthlyTrendPoint> monthlyTrend = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            monthlyTrend.add(AdminDashboardResponse.MonthlyTrendPoint.builder()
                    .month(m)
                    .submitted(0L)
                    .selected(0L)
                    .build());
        }
        return AdminDashboardResponse.builder()
                .year(year)
                .centerAvg(0.0)
                .totalSubmitted(0L)
                .totalSelected(0L)
                .memberCount(0)
                .monthlyTrend(monthlyTrend)
                .teams(List.of())
                .build();
    }

    private List<TbLmsMember> loadScopedMembers() {
        Set<Integer> allowedDeptIds = deptScopeResolver.resolveAllowedDeptIds();
        if (allowedDeptIds.isEmpty()) {
            return List.of();
        }
        return memberRepository.findByUseYnAndDeptIdxIn("Y", allowedDeptIds);
    }

    private TeamSummary buildTeamSummary(
            Integer deptIdx,
            List<TbLmsMember> members,
            int year,
            int month,
            Map<String, Long> selectedYearMap,
            Map<String, Long> selectedMonthMap,
            Map<String, Long> pendingMap,
            Map<String, Long> judgedMap) {

        String teamName = members.isEmpty() ? "" : members.get(0).getDeptName();

        List<String> teamSkids = members.stream()
                .map(TbLmsMember::getSkid)
                .collect(Collectors.toList());

        long teamSubmittedYear = caseService.countSubmittedBySkidsAndYear(teamSkids, year);
        long teamMonthlySubmitted = caseService.countSubmittedBySkidsAndYearMonth(teamSkids, year, month);

        long teamTotal = members.stream()
                .mapToLong(m -> selectedYearMap.getOrDefault(m.getSkid(), 0L))
                .sum();

        long teamMonthlySelected = members.stream()
                .mapToLong(m -> selectedMonthMap.getOrDefault(m.getSkid(), 0L))
                .sum();

        double avg = members.isEmpty() ? 0.0 : (double) teamTotal / members.size();

        List<MemberSummary> memberSummaries = members.stream()
                .map(m -> MemberSummary.builder()
                        .id(m.getSkid())
                        .name(m.getMbName())
                        .position(m.getMbPositionName())
                        .totalSelected(selectedYearMap.getOrDefault(m.getSkid(), 0L))
                        .monthlySelected(selectedMonthMap.getOrDefault(m.getSkid(), 0L))
                        .pendingCount(pendingMap.getOrDefault(m.getSkid(), 0L))
                        .build())
                .collect(Collectors.toList());

        long teamPending = memberSummaries.stream().mapToLong(MemberSummary::getPendingCount).sum();
        long teamJudged = members.stream()
                .mapToLong(m -> judgedMap.getOrDefault(m.getSkid(), 0L))
                .sum();

        return TeamSummary.builder()
                .id(deptIdx)
                .name(teamName)
                .memberCount(members.size())
                .totalSubmitted(teamSubmittedYear)
                .totalSelected(teamTotal)
                .avgSelected(Math.round(avg * 10.0) / 10.0)
                .monthlySubmitted(teamMonthlySubmitted)
                .monthlySelected(teamMonthlySelected)
                .pendingCount(teamPending)
                .judgedCount(teamJudged)
                .members(memberSummaries)
                .build();
    }

    // ─── 팀 상세 ────────────────────────────────────────────────────────────

    public TeamDetailResponse getTeamDetail(Integer deptIdx) {
        assertDeptInScope(deptIdx);

        LocalDate now   = LocalDate.now();
        int year        = now.getYear();
        int month       = now.getMonthValue();

        List<TbLmsMember> members = memberRepository.findByDeptIdxAndUseYn(deptIdx, "Y");
        if (members.isEmpty()) {
            throw new IllegalArgumentException("팀을 찾을 수 없습니다: " + deptIdx);
        }

        String teamName = members.get(0).getDeptName();

        List<String> skids = members.stream()
                .map(TbLmsMember::getSkid)
                .collect(Collectors.toList());

        Map<String, Long> submittedYearMap  = caseService.mapSubmittedBySkidForYear(skids, year);
        Map<String, Long> submittedMonthMap = caseService.mapSubmittedBySkidForYearMonth(skids, year, month);
        Map<String, Long> selectedYearMap  = caseService.mapSelectedBySkidForYear(skids, year);
        Map<String, Long> selectedMonthMap = caseService.mapSelectedBySkidForYearMonth(skids, year, month);
        Map<String, Long> pendingMap       = caseService.mapPendingBySkid(skids);

        Map<String, TbLmsMember> memberMap = members.stream()
                .collect(Collectors.toMap(TbLmsMember::getSkid, m -> m));

        List<CaseResponse> allCases = caseService.getCasesBySkids(skids, memberMap::get);

        Map<String, List<CaseResponse>> casesBySkid = allCases.stream()
                .collect(Collectors.groupingBy(CaseResponse::getSkid));

        List<TeamDetailResponse.MemberDetail> memberDetails = members.stream()
                .map(m -> TeamDetailResponse.MemberDetail.builder()
                        .id(m.getSkid())
                        .name(m.getMbName())
                        .position(m.getMbPositionName())
                        .totalSubmitted(submittedYearMap.getOrDefault(m.getSkid(), 0L))
                        .totalSelected(selectedYearMap.getOrDefault(m.getSkid(), 0L))
                        .monthlySubmitted(submittedMonthMap.getOrDefault(m.getSkid(), 0L))
                        .monthlySelected(selectedMonthMap.getOrDefault(m.getSkid(), 0L))
                        .monthlyLimit(MONTHLY_LIMIT)
                        .pendingCount(pendingMap.getOrDefault(m.getSkid(), 0L))
                        .cases(casesBySkid.getOrDefault(m.getSkid(), List.of()))
                        .build())
                .collect(Collectors.toList());

        return TeamDetailResponse.builder()
                .team(TeamDetailResponse.TeamInfo.builder()
                        .id(deptIdx)
                        .name(teamName)
                        .build())
                .members(memberDetails)
                .build();
    }

    // ─── 관리자 사례 (스코프 검증) ───────────────────────────────────────────

    public List<CaseResponse> getPendingCases() {
        List<TbLmsMember> scoped = loadScopedMembers();
        if (scoped.isEmpty()) {
            return List.of();
        }
        Set<String> skids = scoped.stream().map(TbLmsMember::getSkid).collect(Collectors.toSet());
        return caseService.getPendingCasesForSkids(skids);
    }

    public CaseResponse getCaseForReview(Long caseId) {
        assertCaseInScope(caseId);
        return caseService.getCaseForReview(caseId);
    }

    @Transactional(readOnly = false)
    public CaseResponse judgeCase(Long caseId, CaseJudgeRequest req) {
        assertCaseInScope(caseId);
        return caseService.judgeCase(caseId, req);
    }

    private void assertDeptInScope(Integer deptIdx) {
        if (deptIdx == null || !deptScopeResolver.resolveAllowedDeptIds().contains(deptIdx)) {
            throw new IllegalArgumentException("해당 팀은 관리자 조직 범위에 포함되지 않습니다: " + deptIdx);
        }
    }

    private void assertCaseInScope(Long caseId) {
        TbYouProCase c = caseService.getCaseEntityOrThrow(caseId);
        TbLmsMember m = memberRepository.findById(c.getSkid()).orElse(null);
        Integer d = m != null ? m.getDeptIdx() : null;
        if (d == null || !deptScopeResolver.resolveAllowedDeptIds().contains(d)) {
            throw new IllegalArgumentException("해당 사례는 관리자 조직 범위에 포함되지 않습니다.");
        }
    }
}
