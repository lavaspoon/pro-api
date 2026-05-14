package devlava.youproapi.service;

import devlava.youproapi.config.YouproAdminProperties;
import devlava.youproapi.domain.TbLmsDept;
import devlava.youproapi.domain.TbLmsMember;
import devlava.youproapi.domain.TbYouIncentiveReflect;
import devlava.youproapi.domain.TbYouIncentiveMonthStat;
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
import devlava.youproapi.repository.TbYouIncentiveMonthStatRepository;
import devlava.youproapi.repository.TbYouIncentiveReflectRepository;
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
    private final TbYouIncentiveMonthStatRepository incentiveMonthStatRepository;
    private final TbYouIncentiveReflectRepository incentiveReflectRepository;

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
     * @param secondDepthDeptId null 이면 설정된 모든 2depth 하위 leaf 팀을 합집합
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
     * {@code tb_you_incentive_reflect} 해당 연도 최신 반영 월 {@code cumulative_count} 기준 랭킹
     * 및 2depth 센터별 누적 합계. 부서 트리 1회 + 스코프 구성원 1회 + 반영 행 배치 1회.
     */
    public AdminRankingResponse getRanking(int year, int topN) {
        List<TbLmsMember> scoped = loadScopedMembers();
        if (scoped.isEmpty()) {
            return AdminRankingResponse.builder()
                    .year(year)
                    .topN(topN)
                    .bySecondDepth(List.of())
                    .combined(AdminRankingResponse.CombinedRanking.builder()
                            .totalCumulative(0L)
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
        Map<String, Long> cumulativeBySkid = latestCumulativeCountBySkidForYear(year, allSkids);

        Map<Integer, String> rootNames = deptRepository.findAllById(rootSet).stream()
                .collect(Collectors.toMap(TbLmsDept::getDeptId, d -> d.getDeptName() != null ? d.getDeptName() : "", (a, b) -> a));

        List<AdminRankingResponse.SecondDepthRanking> blocks = new ArrayList<>();
        for (Integer rootId : adminProperties.getSecondDepthDeptIds()) {
            List<TbLmsMember> inRoot = byRoot.getOrDefault(rootId, List.of());
            long centerTotal = inRoot.stream()
                    .mapToLong(m -> cumulativeBySkid.getOrDefault(m.getSkid(), 0L))
                    .sum();
            blocks.add(AdminRankingResponse.SecondDepthRanking.builder()
                    .secondDepthDeptId(rootId)
                    .secondDepthName(rootNames.getOrDefault(rootId, String.valueOf(rootId)))
                    .centerTotalCumulative(centerTotal)
                    .topMembers(buildTopRankEntries(inRoot, cumulativeBySkid, topN))
                    .build());
        }

        List<TbLmsMember> combinedMembers = byRoot.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        long combinedTotal = combinedMembers.stream()
                .mapToLong(m -> cumulativeBySkid.getOrDefault(m.getSkid(), 0L))
                .sum();

        return AdminRankingResponse.builder()
                .year(year)
                .topN(topN)
                .bySecondDepth(blocks)
                .combined(AdminRankingResponse.CombinedRanking.builder()
                        .totalCumulative(combinedTotal)
                        .topMembers(buildTopRankEntries(combinedMembers, cumulativeBySkid, topN))
                        .build())
                .build();
    }

    /**
     * 해당 연도 각 skid 에 대해 {@code reflect_month} 가 가장 큰 행의 {@code cumulative_count}.
     */
    private Map<String, Long> latestCumulativeCountBySkidForYear(int year, List<String> skids) {
        if (skids.isEmpty()) {
            return Map.of();
        }
        List<TbYouIncentiveReflect> rows =
                incentiveReflectRepository.findByReflectYearAndSkidIn(year, skids);
        Map<String, TbYouIncentiveReflect> latestRow = new HashMap<>();
        for (TbYouIncentiveReflect r : rows) {
            latestRow.merge(r.getSkid(), r, (a, b) ->
                    b.getReflectMonth() >= a.getReflectMonth() ? b : a);
        }
        Map<String, Long> out = new HashMap<>();
        for (String skid : skids) {
            TbYouIncentiveReflect r = latestRow.get(skid);
            out.put(skid, r != null ? r.getCumulativeCount().longValue() : 0L);
        }
        return out;
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
            Map<String, Long> cumulativeBySkid,
            int topN) {
        return members.stream()
                .sorted((a, b) -> {
                    long va = cumulativeBySkid.getOrDefault(a.getSkid(), 0L);
                    long vb = cumulativeBySkid.getOrDefault(b.getSkid(), 0L);
                    int cmp = Long.compare(vb, va);
                    if (cmp != 0) {
                        return cmp;
                    }
                    String na = a.getMbName() != null ? a.getMbName() : "";
                    String nb = b.getMbName() != null ? b.getMbName() : "";
                    return na.compareTo(nb);
                })
                .limit(topN)
                .map(m -> AdminRankingResponse.RankEntry.builder()
                        .skid(m.getSkid())
                        .memberName(m.getMbName())
                        .teamName(m.getDeptName() != null ? m.getDeptName() : "")
                        .cumulativeCount(cumulativeBySkid.getOrDefault(m.getSkid(), 0L))
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
        Map<String, Long> reflectCumulativeBySkid = latestCumulativeCountBySkidForYear(year, allSkids);

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
                    reflectCumulativeBySkid,
                    fallbackName,
                    centerName,
                    groupName,
                    leaf.getSkill()));
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

        Map<String, Long> submittedYearBySkid = caseService.mapSubmittedBySkidForYear(allSkids, year);
        Map<String, Long> submittedMonthBySkid = caseService.mapSubmittedBySkidForYearMonth(allSkids, year, month);
        Map<String, Long> selectedYearMap = stats.getSelectedBySkidYear();
        Map<String, Long> selectedMonthMap = stats.getSelectedBySkidYearMonth();

        LocalDate priorMonthDate = LocalDate.of(year, month, 1).minusMonths(1);
        int priorReflectYear = priorMonthDate.getYear();
        int priorReflectMonth = priorMonthDate.getMonthValue();
        List<String> evalTargetSkids = scopedMembers.stream()
                .filter(AdminService::isEvalTargetMember)
                .map(TbLmsMember::getSkid)
                .distinct()
                .collect(Collectors.toList());
        Map<String, Long> priorMonthReflectBySkid = new HashMap<>();
        if (!evalTargetSkids.isEmpty()) {
            List<TbYouIncentiveReflect> priorReflectRows = incentiveReflectRepository
                    .findByReflectYearAndReflectMonthAndSkidIn(
                            priorReflectYear, priorReflectMonth, evalTargetSkids);
            for (TbYouIncentiveReflect r : priorReflectRows) {
                priorMonthReflectBySkid.merge(
                        r.getSkid(), r.getReflectedCount().longValue(), Long::sum);
            }
        }

        List<TbLmsDept> allDepts = deptRepository.findAllWithParentFetched();
        List<AdminDashboardResponse.CenterOverviewKpi> centerOverview =
                buildCenterOverviewFromMembers(
                        scopedMembers,
                        year,
                        month,
                        allDepts,
                        submittedMonthBySkid,
                        selectedMonthMap,
                        priorMonthReflectBySkid);

        Map<String, Long> pendingMap = stats.getPendingBySkid();
        Map<String, Long> judgedMap = stats.getJudgedBySkidYear();
        Map<String, Long> reflectCumulativeBySkid = latestCumulativeCountBySkidForYear(year, allSkids);

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
                        reflectCumulativeBySkid,
                        null,
                        "",
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

        List<String> allEvalTargetSkids = scopedMembers.stream()
                .filter(AdminService::isEvalTargetMember)
                .map(TbLmsMember::getSkid)
                .collect(Collectors.toList());
        Double overallAnnualRate = computeOverallAnnualCertificationRate(year, month, allEvalTargetSkids);

        return AdminDashboardResponse.builder()
                .year(year)
                .centerAvg(Math.round(centerAvg * 10.0) / 10.0)
                .totalSubmitted(totalSubmitted)
                .totalSelected(totalSelected)
                .memberCount(memberCount)
                .currentMonth(month)
                .priorReflectYear(priorReflectYear)
                .priorReflectMonth(priorReflectMonth)
                .monthlySubmitted(monthlySubmitted)
                .monthlySelected(monthlySelected)
                .overallAnnualCertificationRate(overallAnnualRate)
                .centerOverview(centerOverview)
                .monthlyTrend(monthlyTrend)
                .teams(teams)
                .build();
    }

    private AdminDashboardResponse emptyDashboard(int year, int month) {
        LocalDate priorMonthDate = LocalDate.of(year, month, 1).minusMonths(1);
        int priorReflectYear = priorMonthDate.getYear();
        int priorReflectMonth = priorMonthDate.getMonthValue();
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
                .priorReflectYear(priorReflectYear)
                .priorReflectMonth(priorReflectMonth)
                .monthlySubmitted(0L)
                .monthlySelected(0L)
                .overallAnnualCertificationRate(null)
                .centerOverview(buildCenterOverviewFromMembers(
                        List.of(),
                        year,
                        month,
                        deptRepository.findAllWithParentFetched(),
                        Map.of(),
                        Map.of(),
                        Map.of()))
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
            Map<String, Long> reflectCumulativeBySkid,
            String teamNameFallback,
            String centerName,
            String groupName,
            String skill) {

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

        long teamReflectCumulative = teamSkids.stream()
                .mapToLong(s -> reflectCumulativeBySkid.getOrDefault(s, 0L))
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
        int certifiedEvalTargets = (int) members.stream()
                .filter(AdminService::isEvalTargetMember)
                .filter(m -> reflectCumulativeBySkid.getOrDefault(m.getSkid(), 0L) >= 1)
                .count();
        Double certificationRate = evalTargetMemberCount == 0
                ? null
                : Math.round(1000.0 * certifiedEvalTargets / evalTargetMemberCount) / 10.0;

        return TeamSummary.builder()
                .id(deptIdx)
                .centerName(centerName != null ? centerName : "")
                .groupName(groupName != null ? groupName : "")
                .skill(skill != null ? skill.trim() : "")
                .name(teamName)
                .memberCount(evalTargetMemberCount)
                .totalSubmitted(teamSubmittedYear)
                .totalSelected(teamTotal)
                .avgSelected(Math.round(avg * 10.0) / 10.0)
                .monthlySubmitted(teamMonthlySubmitted)
                .monthlySelected(teamMonthlySelected)
                .reflectCumulativeTotal(teamReflectCumulative)
                .certifiedEvalTargetCount(certifiedEvalTargets)
                .certificationRate(certificationRate)
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

        List<TbLmsMember> evalTargets = members.stream()
                .filter(AdminService::isEvalTargetMember)
                .collect(Collectors.toList());
        if (evalTargets.isEmpty()) {
            return TeamDetailResponse.builder()
                    .team(TeamDetailResponse.TeamInfo.builder()
                            .id(deptIdx)
                            .name(teamName)
                            .build())
                    .members(List.of())
                    .build();
        }

        List<String> skids = evalTargets.stream()
                .map(TbLmsMember::getSkid)
                .collect(Collectors.toList());

        Map<String, Long> submittedYearMap = caseService.mapSubmittedBySkidForYear(skids, year);
        Map<String, Long> submittedMonthMap = caseService.mapSubmittedBySkidForYearMonth(skids, year, month);
        Map<String, Long> selectedYearMap = caseService.mapSelectedBySkidForYear(skids, year);
        Map<String, Long> selectedMonthMap = caseService.mapSelectedBySkidForYearMonth(skids, year, month);
        Map<String, Long> pendingMap = caseService.mapPendingBySkid(skids);
        Map<String, Long> reflectCumulativeBySkid = latestCumulativeCountBySkidForYear(year, skids);

        Map<String, TbLmsMember> memberMap = evalTargets.stream()
                .collect(Collectors.toMap(TbLmsMember::getSkid, m -> m));

        List<CaseResponse> allCases = caseService.getCasesBySkids(skids, memberMap::get);

        Map<String, List<CaseResponse>> casesBySkid = allCases.stream()
                .collect(Collectors.groupingBy(CaseResponse::getSkid));

        List<TeamDetailResponse.MemberDetail> memberDetails = evalTargets.stream()
                .map(m -> TeamDetailResponse.MemberDetail.builder()
                        .id(m.getSkid())
                        .name(m.getMbName())
                        .position(m.getMbPositionName())
                        .totalSubmitted(submittedYearMap.getOrDefault(m.getSkid(), 0L))
                        .totalSelected(selectedYearMap.getOrDefault(m.getSkid(), 0L))
                        .monthlySubmitted(submittedMonthMap.getOrDefault(m.getSkid(), 0L))
                        .monthlySelected(selectedMonthMap.getOrDefault(m.getSkid(), 0L))
                        .reflectCumulativeCount(reflectCumulativeBySkid.getOrDefault(m.getSkid(), 0L))
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

    /**
     * {@code youpro.admin.second-depth-dept-ids} 순서대로, 스코프 구성원을 센터 루트별로 나눠 상단 KPI를 만든다.
     */
    private List<AdminDashboardResponse.CenterOverviewKpi> buildCenterOverviewFromMembers(
            List<TbLmsMember> scopedMembers,
            int year,
            int month,
            List<TbLmsDept> allDepts,
            Map<String, Long> submittedMonthBySkid,
            Map<String, Long> selectedMonthBySkid,
            Map<String, Long> priorMonthReflectBySkid) {
        Map<Integer, Integer> parentOf = new HashMap<>();
        for (TbLmsDept d : allDepts) {
            parentOf.put(d.getDeptId(), d.getParent() != null ? d.getParent().getDeptId() : null);
        }
        Set<Integer> rootSet = new HashSet<>(adminProperties.getSecondDepthDeptIds());
        Map<Integer, String> rootNames = new HashMap<>();
        for (TbLmsDept d : deptRepository.findAllById(adminProperties.getSecondDepthDeptIds())) {
            rootNames.put(
                    d.getDeptId(),
                    d.getDeptName() != null ? d.getDeptName().trim() : String.valueOf(d.getDeptId()));
        }

        List<AdminDashboardResponse.CenterOverviewKpi> out = new ArrayList<>();
        for (Integer rootId : adminProperties.getSecondDepthDeptIds()) {
            if (rootId == null) {
                continue;
            }
            List<String> skids = scopedMembers.stream()
                    .filter(AdminService::isEvalTargetMember)
                    .filter(m -> rootId.equals(resolveSecondDepthRoot(m.getDeptIdx(), parentOf, rootSet)))
                    .map(TbLmsMember::getSkid)
                    .collect(Collectors.toList());
            long monthlySub = skids.stream()
                    .mapToLong(s -> submittedMonthBySkid.getOrDefault(s, 0L))
                    .sum();
            long monthlySel = skids.stream()
                    .mapToLong(s -> selectedMonthBySkid.getOrDefault(s, 0L))
                    .sum();
            long priorMonthReflected = skids.stream()
                    .mapToLong(s -> priorMonthReflectBySkid.getOrDefault(s, 0L))
                    .sum();
            Double rate = computeAnnualCertificationRate(year, month, rootId, skids);
            out.add(AdminDashboardResponse.CenterOverviewKpi.builder()
                    .secondDepthDeptId(rootId)
                    .centerName(rootNames.getOrDefault(rootId, String.valueOf(rootId)))
                    .memberCount(skids.size())
                    .monthlySubmitted(monthlySub)
                    .monthlySelected(monthlySel)
                    .priorMonthReflectedCount(priorMonthReflected)
                    .annualCertificationRate(rate)
                    .build());
        }
        return out;
    }

    /**
     * 센터(2depth)별 「연간 인증률」: 해당 연도 {@code tb_you_incentive_reflect} 에서
     * {@code cumulative_count >= 1} 인 해당 센터 평가대상자 수 ÷
     * {@code tb_you_incentive_month_stat} 해당 센터·연 1월~현재월 {@code eval_target_count} 산술평균 × 100.
     */
    private Double computeAnnualCertificationRate(
            int year, int currentMonth, int secondDepthDeptId, List<String> evalTargetSkids) {
        List<TbYouIncentiveMonthStat> snapshots =
                incentiveMonthStatRepository.findByReflectYearAndSecondDepthDeptIdAndReflectMonthBetweenOrderByReflectMonth(
                        year, secondDepthDeptId, 1, currentMonth);
        if (snapshots.isEmpty()) {
            return null;
        }
        double avgHead = snapshots.stream()
                .mapToInt(TbYouIncentiveMonthStat::getEvalTargetCount)
                .average()
                .orElse(0.0);
        if (avgHead <= 0.0) {
            return null;
        }
        long certifiedPeople = evalTargetSkids.isEmpty()
                ? 0L
                : incentiveReflectRepository.countDistinctSkidsCertifiedForYear(year, evalTargetSkids);
        return Math.round(1000.0 * certifiedPeople / avgHead) / 10.0;
    }

    /**
     * 종합 연간 인증률: 스코프 전체 평가대상자 중 인증 인원 ÷
     * 각 월(1~{@code currentMonth})에 대해 설정된 모든 센터의 해당 월 {@code eval_target_count} 합의 산술평균.
     * 어느 월이든 한 센터라도 스냅샷이 없으면 null.
     */
    private Double computeOverallAnnualCertificationRate(int year, int currentMonth, List<String> allEvalSkids) {
        if (allEvalSkids == null || allEvalSkids.isEmpty()) {
            return null;
        }
        List<Integer> centerIds = adminProperties.getSecondDepthDeptIds().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (centerIds.isEmpty()) {
            return null;
        }
        List<Double> monthDenominators = new ArrayList<>();
        for (int m = 1; m <= currentMonth; m++) {
            int sumMonth = 0;
            boolean complete = true;
            for (Integer cid : centerIds) {
                Optional<TbYouIncentiveMonthStat> row =
                        incentiveMonthStatRepository.findByReflectYearAndReflectMonthAndSecondDepthDeptId(
                                year, m, cid);
                if (row.isEmpty()) {
                    complete = false;
                    break;
                }
                sumMonth += row.get().getEvalTargetCount();
            }
            if (complete) {
                monthDenominators.add((double) sumMonth);
            }
        }
        if (monthDenominators.isEmpty()) {
            return null;
        }
        double avgHead = monthDenominators.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (avgHead <= 0.0) {
            return null;
        }
        long certifiedPeople = incentiveReflectRepository.countDistinctSkidsCertifiedForYear(year, allEvalSkids);
        return Math.round(1000.0 * certifiedPeople / avgHead) / 10.0;
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
