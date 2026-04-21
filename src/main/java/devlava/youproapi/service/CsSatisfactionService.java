package devlava.youproapi.service;

import devlava.youproapi.config.YouproAdminProperties;
import devlava.youproapi.domain.TbCsSatisfactionAnnualTarget;
import devlava.youproapi.domain.TbCsSatisfactionDeptMonthlyTarget;
import devlava.youproapi.domain.TbCsSatisfactionRecord;
import devlava.youproapi.domain.TbCsSatisfactionSkillTarget;
import devlava.youproapi.domain.TbLmsDept;
import devlava.youproapi.domain.TbLmsMember;
import devlava.youproapi.dto.AdminFilterMetaResponse;
import devlava.youproapi.dto.CsSatisfactionCenterMonthDetailResponse;
import devlava.youproapi.dto.CsSatisfactionMonthlyTargetsRequest;
import devlava.youproapi.dto.CsSatisfactionMonthlyTargetsResponse;
import devlava.youproapi.dto.CsSatisfactionMonthlyOverviewResponse;
import devlava.youproapi.dto.CsSatisfactionMonthlyTrendResponse;
import devlava.youproapi.dto.CsSatisfactionRankingResponse;
import devlava.youproapi.dto.CsSatisfactionAdminDashboardKpiResponse;
import devlava.youproapi.dto.CsSatisfactionSummaryResponse;
import devlava.youproapi.dto.CsSatisfactionTargetsUnifiedRequest;
import devlava.youproapi.dto.CsSatisfactionTargetsUnifiedResponse;
import devlava.youproapi.dto.MemberCsFocusTasksResponse;
import devlava.youproapi.dto.MemberCsUnsatisfiedDetailsResponse;
import devlava.youproapi.dto.MemberCsUnsatisfiedRecordItem;
import devlava.youproapi.dto.MemberSatisfactionResponse;
import devlava.youproapi.repository.TbCsSatisfactionAnnualTargetRepository;
import devlava.youproapi.repository.TbCsSatisfactionDeptMonthlyTargetRepository;
import devlava.youproapi.repository.TbCsSatisfactionRecordRepository;
import devlava.youproapi.repository.TbCsSatisfactionSkillTargetRepository;
import devlava.youproapi.repository.TbLmsDeptRepository;
import devlava.youproapi.repository.TbLmsMemberRepository;
import devlava.youproapi.support.AdminDeptScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CsSatisfactionService {

    /** 요약·월별 API에서 「실 미매칭」만 보거나 집계할 때 사용하는 sentinel (DB 부서 ID와 겹치지 않게 음수). */
    public static final int SECOND_DEPTH_UNMATCHED = -1;

    /** {@link TbLmsMember#getYouYn()} 평가대상자 — 만족도 집계·표 인원수에 사용 */
    private static final String EVAL_TARGET_YES = "Y";

    private static boolean isEvalTarget(TbLmsMember m) {
        return m != null && EVAL_TARGET_YES.equalsIgnoreCase(m.getYouYn());
    }

    private final TbCsSatisfactionRecordRepository recordRepository;
    private final TbCsSatisfactionDeptMonthlyTargetRepository deptMonthlyTargetRepository;
    private final TbCsSatisfactionSkillTargetRepository skillTargetRepository;
    private final TbCsSatisfactionAnnualTargetRepository annualTargetRepository;
    private final TbLmsMemberRepository memberRepository;
    private final TbLmsDeptRepository deptRepository;
    private final YouproAdminProperties adminProperties;
    private final AdminDeptScopeResolver deptScopeResolver;

    /**
     * 구성원 당월 중점추진과제 건수 — 각각 {@code satisfied_yn=Y} 이면서 해당 항목도 Y 인 건만 집계.
     */
    public MemberCsFocusTasksResponse getMemberFocusTaskCounts(String skid, int year, int month) {
        if (skid == null || skid.isBlank()) {
            throw new IllegalArgumentException("skid가 필요합니다.");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month는 1~12입니다.");
        }
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        List<TbCsSatisfactionRecord> records = recordRepository.findByEvalDateBetweenAndSkid(from, to, skid);
        long fiveMajor = 0;
        long gen5060 = 0;
        for (TbCsSatisfactionRecord r : records) {
            if (!"Y".equalsIgnoreCase(r.getSatisfiedYn())) {
                continue;
            }
            if ("Y".equalsIgnoreCase(r.getFiveMajorCitiesYn())) {
                fiveMajor++;
            }
            if ("Y".equalsIgnoreCase(r.getGen5060Yn())) {
                gen5060++;
            }
        }
        return new MemberCsFocusTasksResponse(fiveMajor, gen5060, 0L);
    }

    public MemberSatisfactionResponse getMemberSatisfaction(String skid, int year, int month) {
        if (skid == null || skid.isBlank()) {
            throw new IllegalArgumentException("skid가 필요합니다.");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month는 1~12입니다.");
        }
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        LocalDate monthKey = firstDayOfMonth(year, month);

        List<TbCsSatisfactionRecord> records = recordRepository.findByEvalDateBetweenAndSkid(from, to, skid);
        long received = records.size();
        long sat = records.stream().filter(r -> "Y".equalsIgnoreCase(r.getSatisfiedYn())).count();
        long unsat = records.stream().filter(r -> "N".equalsIgnoreCase(r.getSatisfiedYn())).count();

        TbLmsMember member = memberRepository.findById(skid).orElse(null);
        Double targetPct = null;
        if (member != null && member.getSkill() != null && !member.getSkill().isBlank()) {
            targetPct = skillTargetRepository
                    .findByTargetDateAndSkillName(monthKey, member.getSkill().trim())
                    .map(t -> t.getTargetPercent().doubleValue())
                    .orElse(null);
        }

        Double actualPct = received > 0 ? round1(100.0 * sat / received) : null;
        Double achievement = null;
        Boolean met = null;
        if (actualPct != null && targetPct != null && targetPct > 0) {
            achievement = round1(100.0 * actualPct / targetPct);
            met = achievement >= 100.0;
        }

        Map<Integer, Long> dissCountByType = records.stream()
                .filter(r -> r.getDissatisfactionType() != null)
                .collect(Collectors.groupingBy(TbCsSatisfactionRecord::getDissatisfactionType, Collectors.counting()));
        List<MemberSatisfactionResponse.UnsatisfiedCategory> unsatisfiedCategories = List.of(
                buildUnsatisfiedCategory(1, "서비스 지식부족", dissCountByType),
                buildUnsatisfiedCategory(2, "성의 없는 태도", dissCountByType),
                buildUnsatisfiedCategory(3, "적절하지 않는 혜택 안내", dissCountByType),
                buildUnsatisfiedCategory(4, "알아듣기 어려운 설명", dissCountByType),
                buildUnsatisfiedCategory(5, "문의내용 이해 못함", dissCountByType)
        );

        Map<LocalDate, List<TbCsSatisfactionRecord>> byDay = records.stream()
                .filter(r -> r.getEvalDate() != null)
                .collect(Collectors.groupingBy(TbCsSatisfactionRecord::getEvalDate));
        List<MemberSatisfactionResponse.DailyTrendPoint> dailyTrend = byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    long rc = e.getValue().size();
                    long sc = e.getValue().stream().filter(r -> "Y".equalsIgnoreCase(r.getSatisfiedYn())).count();
                    return MemberSatisfactionResponse.DailyTrendPoint.builder()
                            .day(e.getKey().getMonthValue() + "/" + e.getKey().getDayOfMonth())
                            .satisfiedCount(sc)
                            .receivedCount(rc)
                            .build();
                })
                .collect(Collectors.toList());

        return MemberSatisfactionResponse.builder()
                .monthlyTargetPct(targetPct)
                .target(targetPct)
                .receivedCount(received)
                .totalSamples(received)
                .satisfiedCount(sat)
                .unsatisfiedCount(unsat)
                .monthlyActualPct(actualPct)
                .monthlyAchievementRate(achievement)
                .monthlyTargetMet(met)
                .unsatisfiedCategories(unsatisfiedCategories)
                .dailyTrend(dailyTrend)
                .build();
    }

    private static MemberSatisfactionResponse.UnsatisfiedCategory buildUnsatisfiedCategory(
            int type, String label, Map<Integer, Long> countMap) {
        return MemberSatisfactionResponse.UnsatisfiedCategory.builder()
                .dissatisfactionType(type)
                .label(label)
                .count(countMap.getOrDefault(type, 0L))
                .build();
    }

    /**
     * 구성원 당월·불만족 유형(1~5)별 상담 레코드 상세 목록.
     */
    public MemberCsUnsatisfiedDetailsResponse getMemberUnsatisfiedDetails(
            String skid, int year, int month, int dissatisfactionType) {
        if (skid == null || skid.isBlank()) {
            throw new IllegalArgumentException("skid가 필요합니다.");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month는 1~12입니다.");
        }
        if (dissatisfactionType < 1 || dissatisfactionType > 5) {
            throw new IllegalArgumentException("dissatisfactionType은 1~5입니다.");
        }
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        List<TbCsSatisfactionRecord> rows = recordRepository
                .findByEvalDateBetweenAndSkidAndDissatisfactionTypeOrderByEvalDateDescIdDesc(
                        from, to, skid, dissatisfactionType);
        List<MemberCsUnsatisfiedRecordItem> items = rows.stream()
                .map(this::toUnsatisfiedItem)
                .collect(Collectors.toList());
        return new MemberCsUnsatisfiedDetailsResponse(items);
    }

    private MemberCsUnsatisfiedRecordItem toUnsatisfiedItem(TbCsSatisfactionRecord r) {
        return new MemberCsUnsatisfiedRecordItem(
                r.getId(),
                r.getEvalDate(),
                r.getConsultTime(),
                r.getSubsidiaryType(),
                null,
                null,
                null,
                r.getConsultType1(),
                r.getConsultType2(),
                r.getConsultType3(),
                r.getSkill(),
                r.getSatisfiedYn(),
                r.getGoodMent(),
                r.getBadMent(),
                r.getFiveMajorCitiesYn(),
                r.getGen5060Yn(),
                null);
    }

    public CsSatisfactionSummaryResponse getSummary(Integer year, Integer secondDepthDeptIdFilter) {
        int y = year != null ? year : LocalDate.now().getYear();
        validateSecondDepthFilter(secondDepthDeptIdFilter);

        if (secondDepthDeptIdFilter != null && secondDepthDeptIdFilter == SECOND_DEPTH_UNMATCHED) {
            return CsSatisfactionSummaryResponse.builder()
                    .year(y)
                    .filterMeta(buildFilterMeta())
                    .rows(Collections.emptyList())
                    .build();
        }

        LocalDate from = LocalDate.of(y, 1, 1);
        LocalDate to = LocalDate.of(y, 12, 31);

        List<TbCsSatisfactionRecord> records = recordRepository.findByEvalDateBetween(from, to);
        Set<String> skids = records.stream()
                .map(TbCsSatisfactionRecord::getSkid)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
        Map<String, TbLmsMember> memberBySkid = skids.isEmpty()
                ? Map.of()
                : memberRepository.findAllById(skids).stream()
                        .filter(m -> m.getSkid() != null)
                        .collect(Collectors.toMap(TbLmsMember::getSkid, m -> m, (a, b) -> a));

        List<TbLmsDept> allDepts = deptRepository.findAllWithParentFetched();
        Map<Integer, Integer> parentOf = buildParentMap(allDepts);
        Map<Integer, TbLmsDept> deptById = allDepts.stream()
                .collect(Collectors.toMap(TbLmsDept::getDeptId, d -> d, (a, b) -> a));
        Set<Integer> roots = new HashSet<>(adminProperties.getSecondDepthDeptIds());
        Map<Integer, String> rootNames = loadRootNames(roots);
        Map<Integer, Integer> leafToConfiguredCenter = buildLeafToConfiguredCenterMap();
        Set<Integer> allowedLeaves = leafTeamIdsExcludingFilterCenters();
        LocalDate now = LocalDate.now();
        LocalDate thisMonthFrom = LocalDate.of(now.getYear(), now.getMonthValue(), 1);
        LocalDate thisMonthTo = thisMonthFrom.withDayOfMonth(thisMonthFrom.lengthOfMonth());
        LocalDate thisMonthKey = firstDayOfMonth(now.getYear(), now.getMonthValue());

        List<TbLmsDept> leafDepts = listLeafTeamsFromConfig(
                allDepts,
                secondDepthDeptIdFilter == null ? null : secondDepthDeptIdFilter);

        Map<Integer, Agg> aggByLeaf = new LinkedHashMap<>();
        for (TbLmsDept leaf : leafDepts) {
            aggByLeaf.put(leaf.getDeptId(), new Agg());
        }

        List<TbCsSatisfactionRecord> monthRecords = recordRepository.findByEvalDateBetween(thisMonthFrom, thisMonthTo);
        Set<String> monthSkids = monthRecords.stream()
                .map(TbCsSatisfactionRecord::getSkid)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
        Map<String, TbLmsMember> monthMemberBySkid = monthSkids.isEmpty()
                ? Map.of()
                : memberRepository.findAllById(monthSkids).stream()
                .filter(m -> m.getSkid() != null)
                .collect(Collectors.toMap(TbLmsMember::getSkid, m -> m, (a, b) -> a));
        Map<Integer, Map<String, Agg>> monthAggByLeafAndSkid = new HashMap<>();
        for (TbCsSatisfactionRecord rec : monthRecords) {
            TbLmsMember m = monthMemberBySkid.get(rec.getSkid());
            if (m == null || m.getDeptIdx() == null || !isEvalTarget(m)) continue;
            Integer leafId = resolveLeafBucket(m.getDeptIdx(), allowedLeaves, parentOf);
            if (leafId == null || !aggByLeaf.containsKey(leafId)) continue;
            Map<String, Agg> bySkid = monthAggByLeafAndSkid.computeIfAbsent(leafId, k -> new HashMap<>());
            Agg ma = bySkid.computeIfAbsent(rec.getSkid(), k -> new Agg());
            ma.eval++;
            if ("Y".equalsIgnoreCase(rec.getSatisfiedYn())) ma.sat++;
        }

        Map<String, Double> monthSkillTargetCache = new HashMap<>();
        for (TbCsSatisfactionRecord rec : records) {
            TbLmsMember m = memberBySkid.get(rec.getSkid());
            if (m == null || m.getDeptIdx() == null || !isEvalTarget(m)) {
                continue;
            }
            Integer leafId = resolveLeafBucket(m.getDeptIdx(), allowedLeaves, parentOf);
            if (leafId == null || !aggByLeaf.containsKey(leafId)) {
                continue;
            }
            Agg bucket = aggByLeaf.get(leafId);
            bucket.eval++;
            if ("Y".equalsIgnoreCase(rec.getSatisfiedYn())) {
                bucket.sat++;
            } else if ("N".equalsIgnoreCase(rec.getSatisfiedYn())) {
                bucket.diss++;
            }
        }

        List<CsSatisfactionSummaryResponse.SecondDepthSatisfactionRow> rows = new ArrayList<>();

        for (TbLmsDept leaf : leafDepts) {
            Agg a = aggByLeaf.get(leaf.getDeptId());
            Double satRate = a.eval == 0 ? null : round1(100.0 * a.sat / a.eval);
            Integer rootForTarget =
                    resolveSecondDepthRootFallback(leaf.getDeptId(), parentOf, roots, allDepts);
            if (rootForTarget == null) {
                rootForTarget = leafToConfiguredCenter.get(leaf.getDeptId());
            }
            Double targetAvg = rootForTarget != null
                    ? averageTargetPercentForYear(rootForTarget, from, to)
                    : null;
            Double achievement = null;
            if (targetAvg != null && targetAvg > 0 && satRate != null) {
                achievement = round1(100.0 * satRate / targetAvg);
            }
            String leafName = leaf.getDeptName() != null ? leaf.getDeptName() : String.valueOf(leaf.getDeptId());
            String centerName = null;
            if (rootForTarget != null) {
                centerName = rootNames.getOrDefault(rootForTarget, String.valueOf(rootForTarget));
            }
            if (centerName == null || centerName.isBlank()) {
                centerName = "—";
            }
            Integer parentId = parentOf.get(leaf.getDeptId());
            String groupName = "—";
            if (parentId != null) {
                TbLmsDept p = deptById.get(parentId);
                if (p != null && p.getDeptName() != null && !p.getDeptName().isBlank()) {
                    groupName = p.getDeptName().trim();
                } else {
                    groupName = String.valueOf(parentId);
                }
            } else {
                groupName = "—";
            }
            Set<Integer> subtree = AdminDeptScope.collectSubtreeDeptIds(allDepts, List.of(leaf.getDeptId()));
            long evalTargetCnt = subtree.isEmpty()
                    ? 0L
                    : memberRepository.countByDeptIdxInAndYouYn(subtree, EVAL_TARGET_YES);
            int evalTargetMemberCount = (int) Math.min(evalTargetCnt, Integer.MAX_VALUE);

            long monthEligible = 0L;
            long monthAchieved = 0L;
            Map<String, Agg> bySkidMonth = monthAggByLeafAndSkid.getOrDefault(leaf.getDeptId(), Map.of());
            for (Map.Entry<String, Agg> e : bySkidMonth.entrySet()) {
                Agg ma = e.getValue();
                if (ma.eval <= 0) continue;
                TbLmsMember mm = monthMemberBySkid.get(e.getKey());
                String skill = (mm != null && mm.getSkill() != null) ? mm.getSkill().trim() : null;
                if (skill == null || skill.isEmpty()) continue;
                Double targetPct = monthSkillTargetCache.computeIfAbsent(skill, sk ->
                        skillTargetRepository.findByTargetDateAndSkillName(thisMonthKey, sk)
                                .map(t -> t.getTargetPercent().doubleValue())
                                .orElse(null));
                if (targetPct == null || targetPct <= 0) continue;
                monthEligible++;
                double satPct = 100.0 * ma.sat / ma.eval;
                if (satPct >= targetPct) monthAchieved++;
            }
            Double monthAchievementRate = monthEligible > 0
                    ? round1(100.0 * monthAchieved / monthEligible)
                    : null;

            rows.add(CsSatisfactionSummaryResponse.SecondDepthSatisfactionRow.builder()
                    .secondDepthDeptId(leaf.getDeptId())
                    .centerName(centerName)
                    .groupName(groupName)
                    .secondDepthName(leafName)
                    .evalTargetMemberCount(evalTargetMemberCount)
                    .evalCount(a.eval)
                    .satisfiedCount(a.sat)
                    .dissatisfiedCount(a.diss)
                    .satisfactionRate(satRate)
                    .targetPercent(targetAvg)
                    .achievementRate(achievement)
                    .monthlySkillTargetAchievedCount(monthAchieved)
                    .monthlySkillTargetEligibleCount(monthEligible)
                    .monthlySkillTargetAchievementRate(monthAchievementRate)
                    .build());
        }

        return CsSatisfactionSummaryResponse.builder()
                .year(y)
                .filterMeta(buildFilterMeta())
                .rows(rows)
                .build();
    }

    public CsSatisfactionMonthlyTrendResponse getMonthlyTrend(int year, int secondDepthDeptId) {
        validateSecondDepthFilter(secondDepthDeptId);
        if (secondDepthDeptId != SECOND_DEPTH_UNMATCHED
                && !adminProperties.getSecondDepthDeptIds().contains(secondDepthDeptId)) {
            throw new IllegalArgumentException("허용되지 않은 2depth 부서입니다: " + secondDepthDeptId);
        }

        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        List<TbCsSatisfactionRecord> records = recordRepository.findByEvalDateBetween(from, to);
        Set<String> skids = records.stream()
                .map(TbCsSatisfactionRecord::getSkid)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
        Map<String, TbLmsMember> memberBySkid = skids.isEmpty()
                ? Map.of()
                : memberRepository.findAllById(skids).stream()
                        .filter(m -> m.getSkid() != null)
                        .collect(Collectors.toMap(TbLmsMember::getSkid, m -> m, (a, b) -> a));

        List<TbLmsDept> allDepts = deptRepository.findAllWithParentFetched();
        Map<Integer, Integer> parentOf = buildParentMap(allDepts);
        Set<Integer> roots = new HashSet<>(adminProperties.getSecondDepthDeptIds());
        Set<Integer> allowedLeaves = leafTeamIdsExcludingFilterCenters();

        int[] evalByMonth = new int[13];
        int[] satByMonth = new int[13];
        int[] dissByMonth = new int[13];

        for (TbCsSatisfactionRecord rec : records) {
            TbLmsMember m = memberBySkid.get(rec.getSkid());
            if (m == null || !isEvalTarget(m) || m.getDeptIdx() == null) {
                continue;
            }
            if (secondDepthDeptId == SECOND_DEPTH_UNMATCHED) {
                if (resolveLeafBucket(m.getDeptIdx(), allowedLeaves, parentOf) != null) {
                    continue;
                }
            } else {
                Integer root = resolveSecondDepthRoot(m.getDeptIdx(), parentOf, roots);
                if (root == null || !root.equals(secondDepthDeptId)) {
                    continue;
                }
            }
            int mo = rec.getEvalDate().getMonthValue();
            evalByMonth[mo]++;
            if ("Y".equalsIgnoreCase(rec.getSatisfiedYn())) {
                satByMonth[mo]++;
            } else if ("N".equalsIgnoreCase(rec.getSatisfiedYn())) {
                dissByMonth[mo]++;
            }
        }

        String trendName;
        if (secondDepthDeptId == SECOND_DEPTH_UNMATCHED) {
            trendName = "기타 (실 미매칭)";
        } else {
            Map<Integer, String> rootNames = loadRootNames(Set.of(secondDepthDeptId));
            trendName = rootNames.getOrDefault(secondDepthDeptId, String.valueOf(secondDepthDeptId));
        }
        List<CsSatisfactionMonthlyTrendResponse.MonthlyPoint> points = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            points.add(CsSatisfactionMonthlyTrendResponse.MonthlyPoint.builder()
                    .month(m)
                    .evalCount(evalByMonth[m])
                    .satisfiedCount(satByMonth[m])
                    .dissatisfiedCount(dissByMonth[m])
                    .build());
        }

        return CsSatisfactionMonthlyTrendResponse.builder()
                .year(year)
                .secondDepthDeptId(secondDepthDeptId)
                .secondDepthName(trendName)
                .months(points)
                .build();
    }

    /**
     * 서부·부산 등 설정된 상위 실(2depth) 소속을 <strong>통합</strong>한 올해 월별 건수와
     * 중점추진(5대도시·5060·문제해결) 건수를 한 번에 반환합니다.
     * 해당 세 지표는 {@code satisfied_yn=Y} 이면서 각 항목도 Y 인 건만 집계합니다.
     */
    public CsSatisfactionMonthlyOverviewResponse getMonthlyOverview(int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        List<TbCsSatisfactionRecord> records = recordRepository.findByEvalDateBetween(from, to);
        Set<String> skids = records.stream()
                .map(TbCsSatisfactionRecord::getSkid)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
        Map<String, TbLmsMember> memberBySkid = skids.isEmpty()
                ? Map.of()
                : memberRepository.findAllById(skids).stream()
                        .filter(m -> m.getSkid() != null)
                        .collect(Collectors.toMap(TbLmsMember::getSkid, m -> m, (a, b) -> a));

        List<TbLmsDept> allDepts = deptRepository.findAllWithParentFetched();
        Map<Integer, Integer> parentOf = buildParentMap(allDepts);
        Set<Integer> roots = new HashSet<>(adminProperties.getSecondDepthDeptIds());

        int[] evalByMonth = new int[13];
        int[] satByMonth = new int[13];
        int[] dissByMonth = new int[13];
        int[] fiveByMonth = new int[13];
        int[] gen5060ByMonth = new int[13];
        int[] probByMonth = new int[13];

        for (TbCsSatisfactionRecord rec : records) {
            TbLmsMember m = memberBySkid.get(rec.getSkid());
            if (m == null || m.getDeptIdx() == null || !isEvalTarget(m)) {
                continue;
            }
            Integer root = resolveSecondDepthRoot(m.getDeptIdx(), parentOf, roots);
            if (root == null || !roots.contains(root)) {
                continue;
            }
            int mo = rec.getEvalDate().getMonthValue();
            evalByMonth[mo]++;
            if (isYes(rec.getSatisfiedYn())) {
                satByMonth[mo]++;
            } else if (isNo(rec.getSatisfiedYn())) {
                dissByMonth[mo]++;
            }
            if (isYes(rec.getSatisfiedYn())) {
                if (isYes(rec.getFiveMajorCitiesYn())) {
                    fiveByMonth[mo]++;
                }
                if (isYes(rec.getGen5060Yn())) {
                    gen5060ByMonth[mo]++;
                }
                if (isProblemResolvedYes(rec)) {
                    probByMonth[mo]++;
                }
            }
        }

        List<CsSatisfactionMonthlyOverviewResponse.UnifiedMonthPoint> unified = new ArrayList<>();
        List<CsSatisfactionMonthlyOverviewResponse.FocusMonthPoint> focus = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            unified.add(CsSatisfactionMonthlyOverviewResponse.UnifiedMonthPoint.builder()
                    .month(m)
                    .evalCount(evalByMonth[m])
                    .satisfiedCount(satByMonth[m])
                    .dissatisfiedCount(dissByMonth[m])
                    .build());
            focus.add(CsSatisfactionMonthlyOverviewResponse.FocusMonthPoint.builder()
                    .month(m)
                    .fiveMajorCitiesCount(fiveByMonth[m])
                    .gen5060Count(gen5060ByMonth[m])
                    .problemResolvedCount(probByMonth[m])
                    .build());
        }

        return CsSatisfactionMonthlyOverviewResponse.builder()
                .year(year)
                .unified(unified)
                .focusTasks(focus)
                .build();
    }

    /**
     * 관리자 만족도 상단 KPI — 센터 연간 목표 대비 달성, 스킬별 평균 달성(당월), 중점 3종 당월 실적(연간 목표 대비).
     * 당월 지표는 서버 현재 연·월 기준입니다.
     */
    public CsSatisfactionAdminDashboardKpiResponse getAdminDashboardKpis(Integer year) {
        LocalDate now = LocalDate.now();
        int monthY = now.getYear();
        int monthM = now.getMonthValue();
        LocalDate fromMonth = LocalDate.of(monthY, monthM, 1);
        LocalDate toMonth = fromMonth.withDayOfMonth(fromMonth.lengthOfMonth());
        LocalDate monthKey = firstDayOfMonth(monthY, monthM);

        List<TbCsSatisfactionRecord> recordsMonth = recordRepository.findByEvalDateBetween(fromMonth, toMonth);

        Set<String> skidsMonth = recordsMonth.stream()
                .map(TbCsSatisfactionRecord::getSkid)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
        Map<String, TbLmsMember> memberBySkid = skidsMonth.isEmpty()
                ? Map.of()
                : memberRepository.findAllById(skidsMonth).stream()
                        .filter(m -> m.getSkid() != null)
                        .collect(Collectors.toMap(TbLmsMember::getSkid, m -> m, (a, b) -> a));

        List<TbLmsDept> allDepts = deptRepository.findAllWithParentFetched();
        Map<Integer, Integer> parentOf = buildParentMap(allDepts);
        Set<Integer> roots = new HashSet<>(adminProperties.getSecondDepthDeptIds());
        Map<Integer, ScopeAgg> aggByRoot = new LinkedHashMap<>();
        for (Integer rid : adminProperties.getSecondDepthDeptIds()) {
            if (rid != null) aggByRoot.put(rid, new ScopeAgg());
        }
        ScopeAgg overall = new ScopeAgg();

        for (TbCsSatisfactionRecord rec : recordsMonth) {
            TbLmsMember m = memberBySkid.get(rec.getSkid());
            if (m == null || m.getDeptIdx() == null || !isEvalTarget(m)) continue;
            Integer root = resolveSecondDepthRoot(m.getDeptIdx(), parentOf, roots);
            if (root == null || !aggByRoot.containsKey(root)) continue;
            accumulateScopeAgg(aggByRoot.get(root), rec);
            accumulateScopeAgg(overall, rec);
        }

        Map<Integer, String> rootNames = loadRootNames(aggByRoot.keySet());
        Map<Integer, Double> monthlyCenterTarget = new HashMap<>();
        for (Integer rootId : aggByRoot.keySet()) {
            Double tp = deptMonthlyTargetRepository
                    .findByTargetDateAndSecondDepthDeptId(monthKey, rootId)
                    .map(t -> t.getTargetPercent().doubleValue())
                    .orElse(null);
            monthlyCenterTarget.put(rootId, tp);
        }

        Double overallMonthlyTarget = weightedOverallTarget(aggByRoot, monthlyCenterTarget);
        List<CsSatisfactionAdminDashboardKpiResponse.ScopeAchievementRow> centerRows = new ArrayList<>();
        centerRows.add(buildScopeAchievementRow("OVERALL", "종합", overallMonthlyTarget, overall.eval, overall.sat));
        for (Map.Entry<Integer, ScopeAgg> e : aggByRoot.entrySet()) {
            Integer rootId = e.getKey();
            centerRows.add(buildScopeAchievementRow(
                    String.valueOf(rootId),
                    rootNames.getOrDefault(rootId, String.valueOf(rootId)),
                    monthlyCenterTarget.get(rootId),
                    e.getValue().eval,
                    e.getValue().sat
            ));
        }
        centerRows = sortScopeRows(centerRows);

        Double tgtFive = annualTargetRepository
                .findByTargetYearAndTaskCode(monthY, "FIVE_MAJOR_CITIES")
                .map(t -> t.getTargetPercent().doubleValue())
                .orElse(null);
        Double tgt5060 = annualTargetRepository
                .findByTargetYearAndTaskCode(monthY, "GEN_5060")
                .map(t -> t.getTargetPercent().doubleValue())
                .orElse(null);
        Double tgtProb = annualTargetRepository
                .findByTargetYearAndTaskCode(monthY, "PROBLEM_RESOLVED")
                .map(t -> t.getTargetPercent().doubleValue())
                .orElse(null);
        List<CsSatisfactionAdminDashboardKpiResponse.ScopeAchievementRow> fiveRows = new ArrayList<>();
        fiveRows.add(buildScopeAchievementRow("OVERALL", "종합", tgtFive, overall.eval, overall.fiveMajor));
        List<CsSatisfactionAdminDashboardKpiResponse.ScopeAchievementRow> genRows = new ArrayList<>();
        genRows.add(buildScopeAchievementRow("OVERALL", "종합", tgt5060, overall.eval, overall.gen5060));
        List<CsSatisfactionAdminDashboardKpiResponse.ScopeAchievementRow> probRows = new ArrayList<>();
        probRows.add(buildScopeAchievementRow("OVERALL", "종합", tgtProb, overall.eval, overall.problemResolved));
        for (Map.Entry<Integer, ScopeAgg> e : aggByRoot.entrySet()) {
            Integer rootId = e.getKey();
            String scopeKey = String.valueOf(rootId);
            String scopeName = rootNames.getOrDefault(rootId, scopeKey);
            ScopeAgg a = e.getValue();
            fiveRows.add(buildScopeAchievementRow(scopeKey, scopeName, tgtFive, a.eval, a.fiveMajor));
            genRows.add(buildScopeAchievementRow(scopeKey, scopeName, tgt5060, a.eval, a.gen5060));
            probRows.add(buildScopeAchievementRow(scopeKey, scopeName, tgtProb, a.eval, a.problemResolved));
        }

        return CsSatisfactionAdminDashboardKpiResponse.builder()
                .kpiYear(monthY)
                .kpiMonth(monthM)
                .centerAchievements(centerRows)
                .fiveMajorCities(sortScopeRows(fiveRows))
                .gen5060(sortScopeRows(genRows))
                .problemResolved(sortScopeRows(probRows))
                .build();
    }

    private static void accumulateScopeAgg(ScopeAgg agg, TbCsSatisfactionRecord rec) {
        agg.eval++;
        if (isYes(rec.getSatisfiedYn())) {
            agg.sat++;
            if (isYes(rec.getFiveMajorCitiesYn())) agg.fiveMajor++;
            if (isYes(rec.getGen5060Yn())) agg.gen5060++;
            if (isProblemResolvedYes(rec)) agg.problemResolved++;
        }
    }

    private static Double weightedOverallTarget(Map<Integer, ScopeAgg> aggByRoot, Map<Integer, Double> targetByRoot) {
        double num = 0.0;
        double den = 0.0;
        for (Map.Entry<Integer, ScopeAgg> e : aggByRoot.entrySet()) {
            Double target = targetByRoot.get(e.getKey());
            long eval = e.getValue().eval;
            if (target == null || target <= 0 || eval <= 0) continue;
            num += target * eval;
            den += eval;
        }
        if (den <= 0) return null;
        return round1(num / den);
    }

    private static CsSatisfactionAdminDashboardKpiResponse.ScopeAchievementRow buildScopeAchievementRow(
            String scopeKey, String scopeName, Double targetPercent, long evalCount, long successCount) {
        Double actualRate = evalCount > 0 ? round1(100.0 * successCount / evalCount) : null;
        Double achievement = null;
        if (actualRate != null && targetPercent != null && targetPercent > 0) {
            achievement = round1(100.0 * actualRate / targetPercent);
        }
        return CsSatisfactionAdminDashboardKpiResponse.ScopeAchievementRow.builder()
                .scopeKey(scopeKey)
                .scopeName(scopeName)
                .targetPercent(targetPercent)
                .actualRate(actualRate)
                .achievementRate(achievement)
                .targetMet(achievement != null && achievement >= 100.0)
                .build();
    }

    private static List<CsSatisfactionAdminDashboardKpiResponse.ScopeAchievementRow> sortScopeRows(
            List<CsSatisfactionAdminDashboardKpiResponse.ScopeAchievementRow> rows) {
        List<CsSatisfactionAdminDashboardKpiResponse.ScopeAchievementRow> out = new ArrayList<>(rows);
        out.sort(Comparator.comparingInt(r -> scopeOrder(r.getScopeName())));
        return out;
    }

    private static int scopeOrder(String name) {
        String n = name == null ? "" : name.trim();
        if ("종합".equals(n)) return 0;
        if (n.contains("부산")) return 1;
        if (n.contains("서부")) return 2;
        return 3;
    }

    /**
     * 해당 연도 만족도 레코드 기준 구성원별 건수 상위 N명(3종: 5대도시·5060·문제해결).
     * 각 지표는 {@code satisfied_yn=Y} 이면서 해당 항목도 Y 인 건만 합산합니다.
     * 집계 범위는 {@link #getMonthlyOverview(int)} 과 동일(평가대상자·2depth 루트·관리자 조직 스코프).
     */
    public CsSatisfactionRankingResponse getRanking(int year, int topN) {
        if (topN < 1) {
            topN = 3;
        }
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        List<TbCsSatisfactionRecord> records = recordRepository.findByEvalDateBetween(from, to);
        Set<String> skids = records.stream()
                .map(TbCsSatisfactionRecord::getSkid)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
        Map<String, TbLmsMember> memberBySkid = skids.isEmpty()
                ? Map.of()
                : memberRepository.findAllById(skids).stream()
                        .filter(m -> m.getSkid() != null)
                        .collect(Collectors.toMap(TbLmsMember::getSkid, m -> m, (a, b) -> a));

        List<TbLmsDept> allDepts = deptRepository.findAllWithParentFetched();
        Map<Integer, Integer> parentOf = buildParentMap(allDepts);
        Set<Integer> roots = new HashSet<>(adminProperties.getSecondDepthDeptIds());
        Set<Integer> allowedDeptIds = deptScopeResolver.resolveAllowedDeptIds();

        Map<String, long[]> agg = new HashMap<>();
        for (TbCsSatisfactionRecord rec : records) {
            TbLmsMember m = memberBySkid.get(rec.getSkid());
            if (m == null || m.getDeptIdx() == null || !isEvalTarget(m)) {
                continue;
            }
            if (!allowedDeptIds.contains(m.getDeptIdx())) {
                continue;
            }
            Integer root = resolveSecondDepthRoot(m.getDeptIdx(), parentOf, roots);
            if (root == null || !roots.contains(root)) {
                continue;
            }
            if (!isYes(rec.getSatisfiedYn())) {
                continue;
            }
            String skid = rec.getSkid();
            long[] a = agg.computeIfAbsent(skid, k -> new long[3]);
            if (isYes(rec.getFiveMajorCitiesYn())) {
                a[0]++;
            }
            if (isYes(rec.getGen5060Yn())) {
                a[1]++;
            }
            if (isProblemResolvedYes(rec)) {
                a[2]++;
            }
        }

        return CsSatisfactionRankingResponse.builder()
                .year(year)
                .topN(topN)
                .topByFiveMajorCities(buildTopRank(agg, memberBySkid, 0, topN))
                .topByGen5060(buildTopRank(agg, memberBySkid, 1, topN))
                .topByProblemResolved(buildTopRank(agg, memberBySkid, 2, topN))
                .build();
    }

    private List<CsSatisfactionRankingResponse.RankEntry> buildTopRank(
            Map<String, long[]> agg,
            Map<String, TbLmsMember> memberBySkid,
            int metricIndex,
            int topN) {
        return agg.entrySet().stream()
                .sorted((e1, e2) -> {
                    long c1 = e1.getValue()[metricIndex];
                    long c2 = e2.getValue()[metricIndex];
                    int cmp = Long.compare(c2, c1);
                    if (cmp != 0) {
                        return cmp;
                    }
                    TbLmsMember m1 = memberBySkid.get(e1.getKey());
                    TbLmsMember m2 = memberBySkid.get(e2.getKey());
                    String n1 = m1 != null && m1.getMbName() != null ? m1.getMbName() : "";
                    String n2 = m2 != null && m2.getMbName() != null ? m2.getMbName() : "";
                    int nameCmp = n1.compareToIgnoreCase(n2);
                    if (nameCmp != 0) {
                        return nameCmp;
                    }
                    return e1.getKey().compareTo(e2.getKey());
                })
                .filter(e -> e.getValue()[metricIndex] > 0)
                .limit(topN)
                .map(e -> {
                    TbLmsMember m = memberBySkid.get(e.getKey());
                    long cnt = e.getValue()[metricIndex];
                    String team = m != null && m.getDeptName() != null ? m.getDeptName().trim() : "";
                    String name = m != null && m.getMbName() != null ? m.getMbName().trim() : "";
                    return CsSatisfactionRankingResponse.RankEntry.builder()
                            .skid(e.getKey())
                            .memberName(name.isEmpty() ? null : name)
                            .teamName(team.isEmpty() ? null : team)
                            .count(cnt)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 만족도 요약 + 구성원(skid)별 건수. {@code month} 가 있으면 해당 월만, 없으면 {@code year} 년 1/1~12/31.
     * {@code secondDepthDeptId} 는 (1) {@code youpro.admin.second-depth-dept-ids} 에
     * 등록된 <em>센터</em> id 이거나,
     * (2) 연간 요약 표({@link #getSummary}) 각 행과 동일한 <em>리프 팀</em> dept id 여야 한다.
     */
    public CsSatisfactionCenterMonthDetailResponse getCenterMonthDetail(
            int secondDepthDeptId, Integer year, Integer month) {
        if (secondDepthDeptId == SECOND_DEPTH_UNMATCHED) {
            throw new IllegalArgumentException("상위 센터만 조회할 수 있습니다.");
        }

        List<TbLmsDept> allDepts = deptRepository.findAllWithParentFetched();
        Map<Integer, Integer> parentOf = buildParentMap(allDepts);
        Set<Integer> roots = new HashSet<>(adminProperties.getSecondDepthDeptIds());
        Set<Integer> allowedLeaves = leafTeamIdsExcludingFilterCenters();

        boolean scopeByCenter = adminProperties.getSecondDepthDeptIds().contains(secondDepthDeptId);
        boolean scopeByLeaf = false;
        if (!scopeByCenter) {
            scopeByLeaf = listLeafTeamsFromConfig(allDepts, null).stream()
                    .anyMatch(d -> d.getDeptId() == secondDepthDeptId);
            if (!scopeByLeaf) {
                throw new IllegalArgumentException("허용되지 않은 부서입니다: " + secondDepthDeptId);
            }
        }

        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        /** month 가 null 이면 해당 연도 1/1~12/31 (상단 연간 요약과 동일 범위로 구성원 집계) */
        final int mo;
        final LocalDate from;
        final LocalDate to;
        if (month != null) {
            mo = month;
            from = LocalDate.of(y, mo, 1);
            to = from.withDayOfMonth(from.lengthOfMonth());
        } else {
            mo = 0;
            from = LocalDate.of(y, 1, 1);
            to = LocalDate.of(y, 12, 31);
        }

        List<TbCsSatisfactionRecord> records = recordRepository.findByEvalDateBetween(from, to);
        Set<String> skids = records.stream()
                .map(TbCsSatisfactionRecord::getSkid)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
        Map<String, TbLmsMember> memberBySkid = skids.isEmpty()
                ? Map.of()
                : memberRepository.findAllById(skids).stream()
                        .filter(m -> m.getSkid() != null)
                        .collect(Collectors.toMap(TbLmsMember::getSkid, m -> m, (a, b) -> a));

        Agg total = new Agg();
        Map<String, Agg> bySkid = new LinkedHashMap<>();

        for (TbCsSatisfactionRecord rec : records) {
            TbLmsMember m = memberBySkid.get(rec.getSkid());
            if (m == null || m.getDeptIdx() == null || !isEvalTarget(m)) {
                continue;
            }
            if (scopeByCenter) {
                Integer root = resolveSecondDepthRoot(m.getDeptIdx(), parentOf, roots);
                if (root == null || !root.equals(secondDepthDeptId)) {
                    continue;
                }
            } else {
                Integer leafBucket = resolveLeafBucket(m.getDeptIdx(), allowedLeaves, parentOf);
                if (leafBucket == null || !leafBucket.equals(secondDepthDeptId)) {
                    continue;
                }
            }
            String skid = rec.getSkid();
            Agg rowAgg = bySkid.computeIfAbsent(skid, k -> new Agg());
            accumulateSatisfaction(rec, rowAgg);
            accumulateSatisfaction(rec, total);
        }

        String centerName;
        if (scopeByCenter) {
            Map<Integer, String> rootNames = loadRootNames(Set.of(secondDepthDeptId));
            centerName = rootNames.getOrDefault(secondDepthDeptId, String.valueOf(secondDepthDeptId));
        } else {
            centerName = allDepts.stream()
                    .filter(d -> d.getDeptId() == secondDepthDeptId)
                    .findFirst()
                    .map(d -> d.getDeptName() != null ? d.getDeptName() : String.valueOf(secondDepthDeptId))
                    .orElse(String.valueOf(secondDepthDeptId));
        }

        /*
         * 구성원 행: YOU PRO 팀 상세와 같이 LMS 평가대상자 로스터를 먼저 구한 뒤 실적을 합침.
         * (기존: 만족도 레코드에만 나온 SKID만 표시 → 실적 0명은 목록에서 누락됨)
         */
        Map<String, TbLmsMember> rosterBySkid = buildEvalTargetRosterMap(
                secondDepthDeptId, scopeByCenter, allDepts, parentOf, roots, allowedLeaves);

        List<CsSatisfactionCenterMonthDetailResponse.MemberMonthRow> memberRows = new ArrayList<>();
        for (TbLmsMember mem : rosterBySkid.values()) {
            String skid = mem.getSkid();
            Agg a = bySkid.getOrDefault(skid, new Agg());
            String name = mem.getMbName() != null ? mem.getMbName() : null;
            String dname = mem.getDeptName() != null ? mem.getDeptName() : null;
            Double rate = a.eval == 0 ? null : round1(100.0 * a.sat / a.eval);
            memberRows.add(CsSatisfactionCenterMonthDetailResponse.MemberMonthRow.builder()
                    .skid(skid)
                    .mbName(name)
                    .deptName(dname)
                    .evalCount(a.eval)
                    .satisfiedCount(a.sat)
                    .dissatisfiedCount(a.diss)
                    .satisfactionRate(rate)
                    .gen5060Count(a.gen5060)
                    .fiveMajorCitiesCount(a.fiveMajor)
                    .problemResolvedCount(a.problemResolved)
                    .build());
        }
        for (Map.Entry<String, Agg> e : bySkid.entrySet()) {
            String skid = e.getKey();
            if (rosterBySkid.containsKey(skid)) {
                continue;
            }
            Agg a = e.getValue();
            TbLmsMember mem = memberBySkid.get(skid);
            String name = mem != null && mem.getMbName() != null ? mem.getMbName() : null;
            String dname = mem != null && mem.getDeptName() != null ? mem.getDeptName() : null;
            Double rate = a.eval == 0 ? null : round1(100.0 * a.sat / a.eval);
            memberRows.add(CsSatisfactionCenterMonthDetailResponse.MemberMonthRow.builder()
                    .skid(skid)
                    .mbName(name)
                    .deptName(dname)
                    .evalCount(a.eval)
                    .satisfiedCount(a.sat)
                    .dissatisfiedCount(a.diss)
                    .satisfactionRate(rate)
                    .gen5060Count(a.gen5060)
                    .fiveMajorCitiesCount(a.fiveMajor)
                    .problemResolvedCount(a.problemResolved)
                    .build());
        }

        memberRows.sort(Comparator
                .comparingLong(CsSatisfactionCenterMonthDetailResponse.MemberMonthRow::getEvalCount)
                .reversed()
                .thenComparing(
                        r -> r.getMbName() != null ? r.getMbName() : "",
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(CsSatisfactionCenterMonthDetailResponse.MemberMonthRow::getSkid));

        Double totalRate = total.eval == 0 ? null : round1(100.0 * total.sat / total.eval);

        return CsSatisfactionCenterMonthDetailResponse.builder()
                .year(y)
                .month(mo)
                .secondDepthDeptId(secondDepthDeptId)
                .secondDepthName(centerName)
                .evalCount(total.eval)
                .satisfiedCount(total.sat)
                .dissatisfiedCount(total.diss)
                .satisfactionRate(totalRate)
                .gen5060Count(total.gen5060)
                .fiveMajorCitiesCount(total.fiveMajor)
                .problemResolvedCount(total.problemResolved)
                .members(memberRows)
                .build();
    }

    /**
     * 상단 요약 표의 팀(리프)·센터 버킷과 동일 기준의 평가대상자(you_yn=Y) 로스터.
     * {@link AdminService#getTeamDetail(Integer)} 처럼 LMS 구성원을 먼저 구한 뒤 만족도 실적을 붙이기 위함.
     */
    private Map<String, TbLmsMember> buildEvalTargetRosterMap(
            int secondDepthDeptId,
            boolean scopeByCenter,
            List<TbLmsDept> allDepts,
            Map<Integer, Integer> parentOf,
            Set<Integer> roots,
            Set<Integer> allowedLeaves) {
        Set<Integer> deptIdScope = new HashSet<>();
        if (scopeByCenter) {
            for (TbLmsDept leaf : listLeafTeamsFromConfig(allDepts, secondDepthDeptId)) {
                deptIdScope.addAll(AdminDeptScope.collectSubtreeDeptIds(allDepts, List.of(leaf.getDeptId())));
            }
        } else {
            deptIdScope.addAll(AdminDeptScope.collectSubtreeDeptIds(allDepts, List.of(secondDepthDeptId)));
        }
        if (deptIdScope.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<TbLmsMember> raw =
                memberRepository.findByUseYnAndDeptIdxInAndYouYn("Y", deptIdScope, EVAL_TARGET_YES);
        Map<String, TbLmsMember> out = new LinkedHashMap<>();
        for (TbLmsMember m : raw) {
            if (m.getSkid() == null || m.getSkid().isBlank() || m.getDeptIdx() == null) {
                continue;
            }
            if (scopeByCenter) {
                Integer root = resolveSecondDepthRoot(m.getDeptIdx(), parentOf, roots);
                if (root == null || !root.equals(secondDepthDeptId)) {
                    continue;
                }
            } else {
                Integer leafBucket = resolveLeafBucket(m.getDeptIdx(), allowedLeaves, parentOf);
                if (leafBucket == null || !leafBucket.equals(secondDepthDeptId)) {
                    continue;
                }
            }
            out.putIfAbsent(m.getSkid(), m);
        }
        return out;
    }

    private static void accumulateSatisfaction(TbCsSatisfactionRecord rec, Agg a) {
        a.eval++;
        if (isYes(rec.getSatisfiedYn())) {
            a.sat++;
        } else if (isNo(rec.getSatisfiedYn())) {
            a.diss++;
        }
        if (isYes(rec.getSatisfiedYn())) {
            if (isYes(rec.getFiveMajorCitiesYn())) {
                a.fiveMajor++;
            }
            if (isYes(rec.getGen5060Yn())) {
                a.gen5060++;
            }
            if (isProblemResolvedYes(rec)) {
                a.problemResolved++;
            }
        }
    }

    private static boolean isYes(String value) {
        return "Y".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private static boolean isNo(String value) {
        return "N".equalsIgnoreCase(String.valueOf(value).trim());
    }

    /**
     * 문제해결 여부는 상담유형3 컬럼의 Y 플래그로 집계합니다.
     * (모든 중점지표는 만족여부=Y 인 건에서만 추가 카운트)
     */
    private static boolean isProblemResolvedYes(TbCsSatisfactionRecord rec) {
        return isYes(rec.getConsultType3());
    }

    @Transactional
    public void upsertMonthlyTargets(CsSatisfactionMonthlyTargetsRequest req) {
        LocalDate monthKey = firstDayOfMonth(req.getYear(), req.getMonth());
        List<Integer> allowed = adminProperties.getSecondDepthDeptIds();
        for (CsSatisfactionMonthlyTargetsRequest.TargetRow r : req.getTargets()) {
            if (!allowed.contains(r.getSecondDepthDeptId())) {
                throw new IllegalArgumentException("허용되지 않은 2depth 부서: " + r.getSecondDepthDeptId());
            }
            TbCsSatisfactionDeptMonthlyTarget row = deptMonthlyTargetRepository
                    .findByTargetDateAndSecondDepthDeptId(monthKey, r.getSecondDepthDeptId())
                    .orElseGet(TbCsSatisfactionDeptMonthlyTarget::new);
            row.setTargetDate(monthKey);
            row.setSecondDepthDeptId(r.getSecondDepthDeptId());
            row.setTargetPercent(r.getTargetPercent().setScale(2, RoundingMode.HALF_UP));
            if (row.getId() == null) {
                row.setCreatedAt(Instant.now());
            }
            deptMonthlyTargetRepository.save(row);
        }
    }

    public CsSatisfactionMonthlyTargetsResponse getMonthlyTargets(Integer year, Integer month) {
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();
        LocalDate monthKey = firstDayOfMonth(y, m);
        List<Integer> centerIds = adminProperties.getSecondDepthDeptIds();
        Map<Integer, String> names = loadRootNames(new HashSet<>(centerIds));

        List<CsSatisfactionMonthlyTargetsResponse.CenterTargetRow> centers = new ArrayList<>();
        boolean allCentersSet = true;
        for (Integer centerId : centerIds) {
            if (centerId == null) {
                continue;
            }
            BigDecimal pct = deptMonthlyTargetRepository
                    .findByTargetDateAndSecondDepthDeptId(monthKey, centerId)
                    .map(TbCsSatisfactionDeptMonthlyTarget::getTargetPercent)
                    .orElse(null);
            if (pct == null) {
                allCentersSet = false;
            }
            centers.add(CsSatisfactionMonthlyTargetsResponse.CenterTargetRow.builder()
                    .secondDepthDeptId(centerId)
                    .secondDepthName(names.getOrDefault(centerId, String.valueOf(centerId)))
                    .targetPercent(pct)
                    .build());
        }

        return CsSatisfactionMonthlyTargetsResponse.builder()
                .year(y)
                .month(m)
                .allCentersSet(allCentersSet)
                .centers(centers)
                .build();
    }

    private static LocalDate firstDayOfMonth(int year, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("월은 1~12여야 합니다: " + month);
        }
        return LocalDate.of(year, month, 1);
    }

    private void validateSecondDepthFilter(Integer secondDepthDeptIdFilter) {
        if (secondDepthDeptIdFilter == null) {
            return;
        }
        if (secondDepthDeptIdFilter == SECOND_DEPTH_UNMATCHED) {
            return;
        }
        if (!adminProperties.getSecondDepthDeptIds().contains(secondDepthDeptIdFilter)) {
            throw new IllegalArgumentException("허용되지 않은 2depth 부서입니다: " + secondDepthDeptIdFilter);
        }
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

    private Map<Integer, Integer> buildParentMap(List<TbLmsDept> allDepts) {
        Map<Integer, Integer> parentOf = new HashMap<>();
        for (TbLmsDept d : allDepts) {
            parentOf.put(d.getDeptId(), d.getParent() != null ? d.getParent().getDeptId() : null);
        }
        return parentOf;
    }

    /**
     * 만족도 요약 표 행: {@code leaf-dept-ids-by-second-depth} 순서(키 생략 시 해당 센터의 leaf depth 전체)로 고정.
     * 건수 유무와 무관하게 설정된 리프 팀마다 1행.
     */
    private List<TbLmsDept> listLeafTeamsFromConfig(List<TbLmsDept> allDepts, Integer secondDepthCenterId) {
        Map<Integer, TbLmsDept> byId = allDepts.stream()
                .collect(Collectors.toMap(TbLmsDept::getDeptId, d -> d, (a, b) -> a));
        Map<Integer, List<Integer>> leafCfg = adminProperties.getLeafDeptIdsBySecondDepth();
        int leafDepth = adminProperties.getLeafTeamDepth();
        Set<Integer> seen = new LinkedHashSet<>();
        List<TbLmsDept> out = new ArrayList<>();

        for (Integer rootId : adminProperties.getSecondDepthDeptIds()) {
            if (rootId == null) {
                continue;
            }
            if (secondDepthCenterId != null && !secondDepthCenterId.equals(rootId)) {
                continue;
            }
            if (leafCfg == null || leafCfg.isEmpty()) {
                for (TbLmsDept d : AdminDeptScope.listDeptsOfDepthInSubtree(allDepts, rootId, leafDepth)) {
                    if (seen.add(d.getDeptId())) {
                        out.add(d);
                    }
                }
                continue;
            }
            if (!leafCfg.containsKey(rootId)) {
                for (TbLmsDept d : AdminDeptScope.listDeptsOfDepthInSubtree(allDepts, rootId, leafDepth)) {
                    if (seen.add(d.getDeptId())) {
                        out.add(d);
                    }
                }
                continue;
            }
            List<Integer> ids = leafCfg.get(rootId);
            if (ids == null || ids.isEmpty()) {
                continue;
            }
            for (Integer lid : ids) {
                if (lid == null) {
                    continue;
                }
                TbLmsDept d = byId.get(lid);
                if (d != null && seen.add(lid)) {
                    out.add(d);
                }
            }
        }

        Set<Integer> scopeIds = deptScopeResolver.resolveAllowedDeptIds();
        Set<Integer> filterCenterIds = new HashSet<>(adminProperties.getSecondDepthDeptIds());
        return out.stream()
                .filter(d -> scopeIds.contains(d.getDeptId()))
                .filter(d -> !filterCenterIds.contains(d.getDeptId()))
                .collect(Collectors.toList());
    }

    /**
     * 집계·표 행에 쓰는 리프 팀 ID — {@code second-depth-dept-ids} 는 셀렉트용 상위 센터이므로 버킷에서 제외.
     */
    private Set<Integer> leafTeamIdsExcludingFilterCenters() {
        Set<Integer> ids = new LinkedHashSet<>(deptScopeResolver.resolveLeafTeamDeptIds());
        for (Integer rootId : adminProperties.getSecondDepthDeptIds()) {
            if (rootId != null) {
                ids.remove(rootId);
            }
        }
        return ids;
    }

    /**
     * 구성원 부서에서 부모를 따라 올라가며, 처음 만나는 허용 리프 팀 dept_id 를 반환합니다.
     * (리프 본인이거나, 리프 하위 부서에 소속된 경우 리프로 귀속)
     */
    private static Integer resolveLeafBucket(
            Integer memberDeptIdx, Set<Integer> allowedLeaves, Map<Integer, Integer> parentOf) {
        if (memberDeptIdx == null || allowedLeaves.isEmpty()) {
            return null;
        }
        Integer cur = memberDeptIdx;
        for (int i = 0; i < 64 && cur != null; i++) {
            if (allowedLeaves.contains(cur)) {
                return cur;
            }
            cur = parentOf.get(cur);
        }
        return null;
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

    /**
     * 조직 상위 링크가 비어 있어도, 설정된 센터 서브트리에 리프가 포함되면 해당 센터 id를 반환합니다.
     */
    private static Integer resolveSecondDepthRootFallback(
            int leafDeptId,
            Map<Integer, Integer> parentOf,
            Set<Integer> roots,
            List<TbLmsDept> allDepts) {
        Integer fromWalk = resolveSecondDepthRoot(leafDeptId, parentOf, roots);
        if (fromWalk != null) {
            return fromWalk;
        }
        for (Integer root : roots) {
            if (root == null) {
                continue;
            }
            Set<Integer> subtree = AdminDeptScope.collectSubtreeDeptIds(allDepts, List.of(root));
            if (subtree.contains(leafDeptId)) {
                return root;
            }
        }
        return null;
    }

    /**
     * {@code youpro.admin.leaf-dept-ids-by-second-depth} 에서 리프 팀 id → 센터(2depth) id 역매핑.
     * LMS 부모 체인이 센터와 연결되지 않아도 표·목표%에 센터를 붙일 때 사용합니다.
     */
    private Map<Integer, Integer> buildLeafToConfiguredCenterMap() {
        Map<Integer, List<Integer>> cfg = adminProperties.getLeafDeptIdsBySecondDepth();
        if (cfg == null || cfg.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Integer> leafToRoot = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> e : cfg.entrySet()) {
            Integer centerId = e.getKey();
            List<Integer> leaves = e.getValue();
            if (centerId == null || leaves == null) {
                continue;
            }
            for (Integer lid : leaves) {
                if (lid != null) {
                    leafToRoot.putIfAbsent(lid, centerId);
                }
            }
        }
        return leafToRoot;
    }

    private Map<Integer, String> loadRootNames(Set<Integer> rootIds) {
        if (rootIds.isEmpty()) {
            return Map.of();
        }
        return deptRepository.findAllById(rootIds).stream()
                .collect(Collectors.toMap(
                        TbLmsDept::getDeptId,
                        d -> d.getDeptName() != null ? d.getDeptName() : String.valueOf(d.getDeptId()),
                        (a, b) -> a));
    }

    /** 연도 요약용 — 해당 연도 구간의 월 목표 행(각 월 1일) 평균. */
    private Double averageTargetPercentForYear(int secondDepthDeptId, LocalDate from, LocalDate to) {
        List<TbCsSatisfactionDeptMonthlyTarget> list = deptMonthlyTargetRepository
                .findBySecondDepthDeptIdAndTargetDateBetween(secondDepthDeptId, from, to);
        if (list.isEmpty()) {
            return null;
        }
        double sum = list.stream().mapToDouble(t -> t.getTargetPercent().doubleValue()).sum();
        return round1(sum / list.size());
    }

    private static Double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    // ========================================
    // 통합 목표 관리 (부서/스킬/연간)
    // ========================================

    /**
     * 통합 목표 조회: 부서(5/6) + 스킬(4개) + 연간과제(3개)
     */
    public CsSatisfactionTargetsUnifiedResponse getTargetsUnified(Integer year, Integer month) {
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();
        LocalDate monthKey = firstDayOfMonth(y, m);

        // 1) 부서별 월간 목표 (5번, 6번)
        List<Integer> deptIds = List.of(5, 8);
        Map<Integer, String> deptNames = loadRootNames(new HashSet<>(deptIds));
        List<CsSatisfactionTargetsUnifiedResponse.DeptMonthlyTargetRow> deptRows = new ArrayList<>();
        boolean allDeptsSet = true;
        for (Integer deptId : deptIds) {
            BigDecimal pct = deptMonthlyTargetRepository
                    .findByTargetDateAndSecondDepthDeptId(monthKey, deptId)
                    .map(TbCsSatisfactionDeptMonthlyTarget::getTargetPercent)
                    .orElse(null);
            if (pct == null) {
                allDeptsSet = false;
            }
            deptRows.add(CsSatisfactionTargetsUnifiedResponse.DeptMonthlyTargetRow.builder()
                    .deptId(deptId)
                    .deptName(deptNames.getOrDefault(deptId, deptId + "번 부서"))
                    .targetPercent(pct)
                    .build());
        }

        // 2) 스킬별 월간 목표 (4개)
        List<String> skillNames = List.of("리텐션", "일반", "이관", "멀티/기술");
        List<CsSatisfactionTargetsUnifiedResponse.SkillMonthlyTargetRow> skillRows = new ArrayList<>();
        boolean allSkillsSet = true;
        for (String skillName : skillNames) {
            BigDecimal pct = skillTargetRepository
                    .findByTargetDateAndSkillName(monthKey, skillName)
                    .map(TbCsSatisfactionSkillTarget::getTargetPercent)
                    .orElse(null);
            if (pct == null) {
                allSkillsSet = false;
            }
            skillRows.add(CsSatisfactionTargetsUnifiedResponse.SkillMonthlyTargetRow.builder()
                    .skillName(skillName)
                    .targetPercent(pct)
                    .build());
        }

        // 3) 중점추진과제 연간 목표 (3개)
        Map<String, String> taskCodeToName = Map.of(
                "FIVE_MAJOR_CITIES", "5대 도시",
                "GEN_5060", "5060",
                "PROBLEM_RESOLVED", "문제해결"
        );
        List<CsSatisfactionTargetsUnifiedResponse.AnnualTaskTargetRow> annualRows = new ArrayList<>();
        boolean allAnnualSet = true;
        for (Map.Entry<String, String> entry : taskCodeToName.entrySet()) {
            String code = entry.getKey();
            String name = entry.getValue();
            BigDecimal pct = annualTargetRepository
                    .findByTargetYearAndTaskCode(y, code)
                    .map(TbCsSatisfactionAnnualTarget::getTargetPercent)
                    .orElse(null);
            if (pct == null) {
                allAnnualSet = false;
            }
            annualRows.add(CsSatisfactionTargetsUnifiedResponse.AnnualTaskTargetRow.builder()
                    .taskCode(code)
                    .taskName(name)
                    .targetPercent(pct)
                    .build());
        }

        boolean allTargetsSet = allDeptsSet && allSkillsSet && allAnnualSet;

        return CsSatisfactionTargetsUnifiedResponse.builder()
                .year(y)
                .month(m)
                .allTargetsSet(allTargetsSet)
                .deptTargets(deptRows)
                .skillTargets(skillRows)
                .annualTargets(annualRows)
                .build();
    }

    /**
     * 통합 목표 저장: 부서(5/6) + 스킬(4개) + 연간과제(3개)
     */
    @Transactional
    public void upsertTargetsUnified(CsSatisfactionTargetsUnifiedRequest req) {
        LocalDate monthKey = firstDayOfMonth(req.getYear(), req.getMonth());

        // 1) 부서별 월간 목표
        if (req.getDeptTargets() != null) {
            for (CsSatisfactionTargetsUnifiedRequest.DeptMonthlyTarget dto : req.getDeptTargets()) {
                TbCsSatisfactionDeptMonthlyTarget row = deptMonthlyTargetRepository
                        .findByTargetDateAndSecondDepthDeptId(monthKey, dto.getDeptId())
                        .orElseGet(TbCsSatisfactionDeptMonthlyTarget::new);
                row.setTargetDate(monthKey);
                row.setSecondDepthDeptId(dto.getDeptId());
                row.setTargetPercent(dto.getTargetPercent().setScale(2, RoundingMode.HALF_UP));
                if (row.getId() == null) {
                    row.setCreatedAt(Instant.now());
                }
                deptMonthlyTargetRepository.save(row);
            }
        }

        // 2) 스킬별 월간 목표
        if (req.getSkillTargets() != null) {
            for (CsSatisfactionTargetsUnifiedRequest.SkillMonthlyTarget dto : req.getSkillTargets()) {
                TbCsSatisfactionSkillTarget row = skillTargetRepository
                        .findByTargetDateAndSkillName(monthKey, dto.getSkillName())
                        .orElseGet(TbCsSatisfactionSkillTarget::new);
                row.setTargetDate(monthKey);
                row.setSkillName(dto.getSkillName());
                row.setTargetPercent(dto.getTargetPercent().setScale(2, RoundingMode.HALF_UP));
                if (row.getId() == null) {
                    row.setCreatedAt(Instant.now());
                } else {
                    row.setUpdatedAt(Instant.now());
                }
                skillTargetRepository.save(row);
            }
        }

        // 3) 중점추진과제 연간 목표
        if (req.getAnnualTargets() != null) {
            Map<String, String> taskCodeToName = Map.of(
                    "FIVE_MAJOR_CITIES", "5대 도시",
                    "GEN_5060", "5060",
                    "PROBLEM_RESOLVED", "문제해결"
            );
            for (CsSatisfactionTargetsUnifiedRequest.AnnualTaskTarget dto : req.getAnnualTargets()) {
                TbCsSatisfactionAnnualTarget row = annualTargetRepository
                        .findByTargetYearAndTaskCode(req.getYear(), dto.getTaskCode())
                        .orElseGet(TbCsSatisfactionAnnualTarget::new);
                row.setTargetYear(req.getYear());
                row.setTaskCode(dto.getTaskCode());
                row.setTaskName(taskCodeToName.getOrDefault(dto.getTaskCode(), dto.getTaskCode()));
                row.setTargetPercent(dto.getTargetPercent().setScale(2, RoundingMode.HALF_UP));
                if (row.getId() == null) {
                    row.setCreatedAt(Instant.now());
                } else {
                    row.setUpdatedAt(Instant.now());
                }
                annualTargetRepository.save(row);
            }
        }
    }

    private static final class Agg {
        long eval;
        long sat;
        long diss;
        long fiveMajor;
        long gen5060;
        long problemResolved;
    }

    private static final class ScopeAgg {
        long eval;
        long sat;
        long fiveMajor;
        long gen5060;
        long problemResolved;
    }

}
