package devlava.youproapi.service;

import devlava.youproapi.config.YouproAdminProperties;
import devlava.youproapi.domain.TbLmsDept;
import devlava.youproapi.domain.TbLmsMember;
import devlava.youproapi.domain.TbYouProCase;
import devlava.youproapi.dto.AdminDashboardResponse;
import devlava.youproapi.dto.AdminDashboardResponse.MemberSummary;
import devlava.youproapi.dto.AdminDashboardResponse.TeamSummary;
import devlava.youproapi.dto.AdminFilterMetaResponse;
import devlava.youproapi.dto.AdminLeafTeamsResponse;
import devlava.youproapi.dto.AdminRankingResponse;
import devlava.youproapi.dto.AdminReviewQueueResponse;
import devlava.youproapi.dto.AdminScopedCaseStats;
import devlava.youproapi.dto.CaseJudgeRequest;
import devlava.youproapi.dto.CaseResponse;
import devlava.youproapi.dto.TeamDetailResponse;
import devlava.youproapi.repository.TbLmsDeptRepository;
import devlava.youproapi.repository.TbLmsMemberRepository;
import devlava.youproapi.support.AdminDeptScope;
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

    /** {@link TbLmsMember#getYouYn()} 평가대상자 — 대시보드 KPI「평가 대상자」명수 */
    private static boolean isEvalTargetMember(TbLmsMember m) {
        return m != null && "Y".equalsIgnoreCase(m.getYouYn());
    }

    private final TbLmsMemberRepository memberRepository;
    private final TbLmsDeptRepository deptRepository;
    private final YouproAdminProperties adminProperties;
    private final AdminDeptScopeResolver deptScopeResolver;
    private final CaseService caseService;

    // ─── 관리자 대시보드 ─────────────────────────────────────────────────────

    public AdminDashboardResponse getDashboard() {
        List<TbLmsMember> scoped = loadScopedMembers();
        return buildDashboardFromMembers(scoped).toBuilder()
                .filterMeta(buildFilterMeta())
                .build();
    }

    /**
     * 2depth 부서(설정값) 기준 하위 leaf depth 팀 목록 + 팀별 집계.
     * 부서 트리 1회 + 구성원 IN 1회 + 통계 배치 2회 — N+1 없음.
     *
     * @param secondDepthDeptId null 이면 설정된 모든 2depth(5·6·7) 하위 leaf 팀을 합집합
     */
    public AdminLeafTeamsResponse getLeafTeamsForSecondDepth(Integer secondDepthDeptId) {
        List<Integer> cfg = adminProperties.getSecondDepthDeptIds();
        if (secondDepthDeptId != null && !cfg.contains(secondDepthDeptId)) {
            throw new IllegalArgumentException("허용되지 않은 2depth 부서입니다: " + secondDepthDeptId);
        }

        List<TbLmsDept> allDepts = deptRepository.findAllWithParentFetched();
        int leafDepth = adminProperties.getLeafTeamDepth();

        List<TbLmsDept> leafDepts = secondDepthDeptId == null
                ? AdminDeptScope.listLeafDeptsUnderAnyRoot(allDepts, cfg, leafDepth)
                : AdminDeptScope.listDeptsOfDepthInSubtree(allDepts, secondDepthDeptId, leafDepth);

        Set<Integer> scopeIds = deptScopeResolver.resolveAllowedDeptIds();
        leafDepts = leafDepts.stream()
                .filter(d -> scopeIds.contains(d.getDeptId()))
                .collect(Collectors.toList());

        List<TeamSummary> teams = buildLeafTeamSummaries(leafDepts, allDepts, cfg);
        return AdminLeafTeamsResponse.builder()
                .secondDepthDeptId(secondDepthDeptId)
                .teams(teams)
                .build();
    }

    /**
     * {@code TB_YOU_PRO_CASE} 해당 연도 접수 건수 기준 랭킹 및 2depth 센터별 접수 합계.
     * 부서 트리 1회 + 스코프 구성원 1회 + skid별 접수 집계 1회 — N+1 없음.
     */
    public AdminRankingResponse getRanking(int year, int topN) {
        List<TbLmsMember> scoped = loadScopedMembers();
        if (scoped.isEmpty()) {
            return AdminRankingResponse.builder()
                    .year(year)
                    .topN(topN)
                    .bySecondDepth(List.of())
                    .combined(AdminRankingResponse.CombinedRanking.builder()
                            .totalSubmitted(0L)
                            .topMembers(List.of())
                            .build())
                    .build();
        }

        List<TbLmsDept> allDepts = deptRepository.findAllWithParentFetched();
        Map<Integer, Integer> parentOf = new HashMap<>();
        for (TbLmsDept d : allDepts) {
            parentOf.put(d.getDeptId(), d.getParent() != null ? d.getParent().getDeptId() : null);
        }
        Set<Integer> rootSet = new HashSet<>(adminProperties.getSecondDepthDeptIds());

        Map<Integer, List<TbLmsMember>> byRoot = new HashMap<>();
        for (TbLmsMember m : scoped) {
            Integer deptIdx = m.getDeptIdx();
            if (deptIdx == null) {
                continue;
            }
            Integer root = resolveSecondDepthRoot(deptIdx, parentOf, rootSet);
            if (root == null) {
                continue;
            }
            byRoot.computeIfAbsent(root, k -> new ArrayList<>()).add(m);
        }

        List<String> allSkids = scoped.stream().map(TbLmsMember::getSkid).collect(Collectors.toList());
        Map<String, Long> submittedBySkid = caseService.mapSubmittedBySkidForYear(allSkids, year);

        Map<Integer, String> rootNames = deptRepository.findAllById(rootSet).stream()
                .collect(Collectors.toMap(TbLmsDept::getDeptId, d -> d.getDeptName() != null ? d.getDeptName() : "", (a, b) -> a));

        List<AdminRankingResponse.SecondDepthRanking> blocks = new ArrayList<>();
        for (Integer rootId : adminProperties.getSecondDepthDeptIds()) {
            List<TbLmsMember> inRoot = byRoot.getOrDefault(rootId, List.of());
            long centerTotal = inRoot.stream()
                    .mapToLong(m -> submittedBySkid.getOrDefault(m.getSkid(), 0L))
                    .sum();
            blocks.add(AdminRankingResponse.SecondDepthRanking.builder()
                    .secondDepthDeptId(rootId)
                    .secondDepthName(rootNames.getOrDefault(rootId, String.valueOf(rootId)))
                    .centerTotalSubmitted(centerTotal)
                    .topMembers(buildTopRankEntries(inRoot, submittedBySkid, topN))
                    .build());
        }

        List<TbLmsMember> combinedMembers = byRoot.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        long combinedTotal = combinedMembers.stream()
                .mapToLong(m -> submittedBySkid.getOrDefault(m.getSkid(), 0L))
                .sum();

        return AdminRankingResponse.builder()
                .year(year)
                .topN(topN)
                .bySecondDepth(blocks)
                .combined(AdminRankingResponse.CombinedRanking.builder()
                        .totalSubmitted(combinedTotal)
                        .topMembers(buildTopRankEntries(combinedMembers, submittedBySkid, topN))
                        .build())
                .build();
    }

    private static Integer resolveSecondDepthRoot(Integer deptIdx, Map<Integer, Integer> parentOf, Set<Integer> roots) {
        Integer cur = deptIdx;
        for (int i = 0; i < 64 && cur != null; i++) {
            if (roots.contains(cur)) {
                return cur;
            }
            cur = parentOf.get(cur);
        }
        return null;
    }

    private static List<AdminRankingResponse.RankEntry> buildTopRankEntries(
            List<TbLmsMember> members,
            Map<String, Long> submittedBySkid,
            int topN) {
        return members.stream()
                .sorted((a, b) -> Long.compare(
                        submittedBySkid.getOrDefault(b.getSkid(), 0L),
                        submittedBySkid.getOrDefault(a.getSkid(), 0L)))
                .limit(topN)
                .map(m -> AdminRankingResponse.RankEntry.builder()
                        .skid(m.getSkid())
                        .memberName(m.getMbName())
                        .teamName(m.getDeptName() != null ? m.getDeptName() : "")
                        .submittedCount(submittedBySkid.getOrDefault(m.getSkid(), 0L))
                        .build())
                .collect(Collectors.toList());
    }

    private AdminFilterMetaResponse buildFilterMeta() {
        List<Integer> ids = adminProperties.getSecondDepthDeptIds();
        List<TbLmsDept> rows = deptRepository.findAllById(ids);
        Map<Integer, TbLmsDept> byId = rows.stream()
                .collect(Collectors.toMap(TbLmsDept::getDeptId, d -> d, (a, b) -> a));

        List<AdminFilterMetaResponse.SecondDepthDeptOption> opts = new ArrayList<>();
        for (Integer id : ids) {
            TbLmsDept d = byId.get(id);
            opts.add(AdminFilterMetaResponse.SecondDepthDeptOption.builder()
                    .id(id)
                    .name(d != null && d.getDeptName() != null ? d.getDeptName() : String.valueOf(id))
                    .build());
        }

        Map<Integer, List<Integer>> leafByRoot = adminProperties.getLeafDeptIdsBySecondDepth();
        return AdminFilterMetaResponse.builder()
                .leafTeamDepth(adminProperties.getLeafTeamDepth())
                .leafDeptIdsBySecondDepth(leafByRoot != null ? new LinkedHashMap<>(leafByRoot) : new LinkedHashMap<>())
                .secondDepthDepts(opts)
                .build();
    }

    /**
     * leaf 부서 목록 순서대로 팀 요약 생성 — 구성원·통계는 배치만 사용.
     */
    private List<TeamSummary> buildLeafTeamSummaries(
            List<TbLmsDept> leafDepts,
            List<TbLmsDept> allDepts,
            List<Integer> secondDepthRootIds) {
        if (leafDepts.isEmpty()) {
            return List.of();
        }

        Set<Integer> rootSet = new HashSet<>(secondDepthRootIds);
        Map<Integer, Integer> parentOf = new HashMap<>();
        Map<Integer, TbLmsDept> deptById = new HashMap<>();
        for (TbLmsDept d : allDepts) {
            Integer id = d.getDeptId();
            deptById.put(id, d);
            parentOf.put(id, d.getParent() != null ? d.getParent().getDeptId() : null);
        }

        /*
         * 센터: 설정된 2depth 루트별 서브트리에 leaf가 포함되는지로 판별 (부모 체인 단독보다 안정적).
         * leaf 목록은 동일 규칙(listLeafDeptsUnderAnyRoot)으로 만들어졌으므로 항상 한 센터에 속해야 함.
         */
        Map<Integer, Integer> leafToCenterRootId = new HashMap<>();
        for (Integer rootId : secondDepthRootIds) {
            Set<Integer> subtree = AdminDeptScope.collectSubtreeDeptIds(allDepts, List.of(rootId));
            for (TbLmsDept leaf : leafDepts) {
                if (subtree.contains(leaf.getDeptId())) {
                    leafToCenterRootId.putIfAbsent(leaf.getDeptId(), rootId);
                }
            }
        }

        List<Integer> leafIds = leafDepts.stream()
                .map(TbLmsDept::getDeptId)
                .collect(Collectors.toList());

        List<TbLmsMember> members = memberRepository.findByUseYnAndDeptIdxIn("Y", leafIds);
        Map<Integer, List<TbLmsMember>> byDept = members.stream()
                .filter(m -> m.getDeptIdx() != null)
                .collect(Collectors.groupingBy(TbLmsMember::getDeptIdx));

        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        List<String> allSkids = members.stream().map(TbLmsMember::getSkid).collect(Collectors.toList());
        AdminScopedCaseStats stats = caseService.loadScopedDashboardStats(allSkids, year, month);

        Map<String, Long> submittedYearBySkid = caseService.mapSubmittedBySkidForYear(allSkids, year);
        Map<String, Long> submittedMonthBySkid = caseService.mapSubmittedBySkidForYearMonth(allSkids, year, month);
        Map<String, Long> selectedYearMap = stats.getSelectedBySkidYear();
        Map<String, Long> selectedMonthMap = stats.getSelectedBySkidYearMonth();
        Map<String, Long> pendingMap = stats.getPendingBySkid();
        Map<String, Long> judgedMap = stats.getJudgedBySkidYear();

        List<TeamSummary> out = new ArrayList<>();
        for (TbLmsDept leaf : leafDepts) {
            Integer did = leaf.getDeptId();
            List<TbLmsMember> ms = byDept.getOrDefault(did, List.of());
            String fallbackName = leaf.getDeptName() != null ? leaf.getDeptName() : "";
            Integer centerRootId = leafToCenterRootId.get(did);
            String centerName = centerNameFromRootId(centerRootId, deptById);
            if (centerName.isEmpty()) {
                centerRootId = findConfiguredRootAncestor(did, parentOf, rootSet);
                centerName = centerNameFromRootId(centerRootId, deptById);
            }
            String groupName = groupNameForLeaf(did, parentOf, rootSet, deptById, centerRootId);
            out.add(buildTeamSummary(
                    did,
                    ms,
                    submittedYearBySkid,
                    submittedMonthBySkid,
                    selectedYearMap,
                    selectedMonthMap,
                    pendingMap,
                    judgedMap,
                    fallbackName,
                    centerName,
                    groupName));
        }
        return out;
    }

    private static String centerNameFromRootId(Integer centerRootId, Map<Integer, TbLmsDept> deptById) {
        if (centerRootId == null) {
            return "";
        }
        TbLmsDept d = deptById.get(centerRootId);
        return d != null && d.getDeptName() != null ? d.getDeptName().trim() : "";
    }

    /** 부모 체인을 따라 설정된 센터(2depth) 루트 dept_id 를 찾음 — 서브트리 매칭 실패 시 보조 */
    private static Integer findConfiguredRootAncestor(
            int leafDeptId,
            Map<Integer, Integer> parentOf,
            Set<Integer> roots) {
        Integer cur = leafDeptId;
        for (int i = 0; i < 64 && cur != null; i++) {
            if (roots.contains(cur)) {
                return cur;
            }
            cur = parentOf.get(cur);
        }
        return null;
    }

    /**
     * 그룹: leaf 직속 상위 부서명. 직속 상위가 해당 센터 루트이거나(실이 센터 바로 하위) 다른 센터 루트이면 빈 값.
     */
    private static String groupNameForLeaf(
            int leafDeptId,
            Map<Integer, Integer> parentOf,
            Set<Integer> roots,
            Map<Integer, TbLmsDept> deptById,
            Integer resolvedCenterRootId) {
        Integer parentId = parentOf.get(leafDeptId);
        if (parentId == null) {
            return "";
        }
        if (resolvedCenterRootId != null && resolvedCenterRootId.equals(parentId)) {
            return "";
        }
        if (roots.contains(parentId)) {
            return "";
        }
        TbLmsDept p = deptById.get(parentId);
        return p != null && p.getDeptName() != null ? p.getDeptName().trim() : "";
    }

    /**
     * 검토 대기 화면 전용 — 대시보드와 대기 사례를 동일 스코프 구성원 기준으로 한 번에 구성.
     */
    public AdminReviewQueueResponse getReviewQueue() {
        List<TbLmsMember> scoped = loadScopedMembers();
        if (scoped.isEmpty()) {
            LocalDate now = LocalDate.now();
            return AdminReviewQueueResponse.builder()
                    .dashboard(emptyDashboard(now.getYear(), now.getMonthValue()).toBuilder()
                            .filterMeta(buildFilterMeta())
                            .build())
                    .pendingCases(List.of())
                    .build();
        }
        Set<String> skids = scoped.stream().map(TbLmsMember::getSkid).collect(Collectors.toSet());
        AdminDashboardResponse dashboard = buildDashboardFromMembers(scoped).toBuilder()
                .filterMeta(buildFilterMeta())
                .build();
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
        int year = now.getYear();
        int month = now.getMonthValue();

        if (scopedMembers.isEmpty()) {
            return emptyDashboard(year, month);
        }

        List<String> allSkids = scopedMembers.stream()
                .map(TbLmsMember::getSkid)
                .collect(Collectors.toList());

        AdminScopedCaseStats stats = caseService.loadScopedDashboardStats(allSkids, year, month);

        long totalSubmitted = stats.getTotalSubmittedYear();
        long totalSelected = stats.getTotalSelectedYear();
        int memberCount =
                (int) scopedMembers.stream().filter(AdminService::isEvalTargetMember).count();
        double centerAvg = memberCount == 0 ? 0.0 : (double) totalSelected / memberCount;

        Map<Integer, Long> submittedByMonth = stats.getSubmittedByMonth();
        Map<Integer, Long> selectedByMonth = stats.getSelectedByMonth();

        long monthlySubmitted = submittedByMonth.getOrDefault(month, 0L);
        long monthlySelected = selectedByMonth.getOrDefault(month, 0L);
        // 인증율 = 해당 월 접수(call_date) 대비 선정(selected) — 구성원 홈 인증률과 동일 정의
        Double monthlyCertificationRate =
                monthlySubmitted == 0
                        ? null
                        : Math.round(1000.0 * monthlySelected / monthlySubmitted) / 10.0;

        Map<String, Long> submittedYearBySkid = caseService.mapSubmittedBySkidForYear(allSkids, year);
        Map<String, Long> submittedMonthBySkid = caseService.mapSubmittedBySkidForYearMonth(allSkids, year, month);
        Map<String, Long> selectedYearMap = stats.getSelectedBySkidYear();
        Map<String, Long> selectedMonthMap = stats.getSelectedBySkidYearMonth();
        Map<String, Long> pendingMap = stats.getPendingBySkid();
        Map<String, Long> judgedMap = stats.getJudgedBySkidYear();

        Map<Integer, List<TbLmsMember>> byDept = scopedMembers.stream()
                .filter(m -> m.getDeptIdx() != null)
                .collect(Collectors.groupingBy(TbLmsMember::getDeptIdx));

        List<TeamSummary> teams = byDept.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> buildTeamSummary(
                        e.getKey(),
                        e.getValue(),
                        submittedYearBySkid,
                        submittedMonthBySkid,
                        selectedYearMap,
                        selectedMonthMap,
                        pendingMap,
                        judgedMap,
                        null,
                        "",
                        ""))
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
                .currentMonth(month)
                .monthlySubmitted(monthlySubmitted)
                .monthlySelected(monthlySelected)
                .monthlyCertificationRate(monthlyCertificationRate)
                .monthlyTrend(monthlyTrend)
                .teams(teams)
                .build();
    }

    private AdminDashboardResponse emptyDashboard(int year, int month) {
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
                .currentMonth(month)
                .monthlySubmitted(0L)
                .monthlySelected(0L)
                .monthlyCertificationRate(null)
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
            Map<String, Long> submittedYearBySkid,
            Map<String, Long> submittedMonthBySkid,
            Map<String, Long> selectedYearMap,
            Map<String, Long> selectedMonthMap,
            Map<String, Long> pendingMap,
            Map<String, Long> judgedMap,
            String teamNameFallback,
            String centerName,
            String groupName) {

        String teamName = !members.isEmpty()
                ? members.get(0).getDeptName()
                : (teamNameFallback != null ? teamNameFallback : "");

        List<String> teamSkids = members.stream()
                .map(TbLmsMember::getSkid)
                .collect(Collectors.toList());

        long teamSubmittedYear = teamSkids.stream()
                .mapToLong(s -> submittedYearBySkid.getOrDefault(s, 0L))
                .sum();
        long teamMonthlySubmitted = teamSkids.stream()
                .mapToLong(s -> submittedMonthBySkid.getOrDefault(s, 0L))
                .sum();

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

        int evalTargetMemberCount =
                (int) members.stream().filter(AdminService::isEvalTargetMember).count();

        return TeamSummary.builder()
                .id(deptIdx)
                .centerName(centerName != null ? centerName : "")
                .groupName(groupName != null ? groupName : "")
                .name(teamName)
                .memberCount(evalTargetMemberCount)
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

        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        List<TbLmsMember> members = memberRepository.findByDeptIdxAndUseYn(deptIdx, "Y");
        if (members.isEmpty()) {
            throw new IllegalArgumentException("팀을 찾을 수 없습니다: " + deptIdx);
        }

        String teamName = members.get(0).getDeptName();

        List<String> skids = members.stream()
                .map(TbLmsMember::getSkid)
                .collect(Collectors.toList());

        Map<String, Long> submittedYearMap = caseService.mapSubmittedBySkidForYear(skids, year);
        Map<String, Long> submittedMonthMap = caseService.mapSubmittedBySkidForYearMonth(skids, year, month);
        Map<String, Long> selectedYearMap = caseService.mapSelectedBySkidForYear(skids, year);
        Map<String, Long> selectedMonthMap = caseService.mapSelectedBySkidForYearMonth(skids, year, month);
        Map<String, Long> pendingMap = caseService.mapPendingBySkid(skids);

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
