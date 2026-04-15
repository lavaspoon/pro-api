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
import devlava.youproapi.dto.CsSatisfactionSummaryResponse;
import devlava.youproapi.dto.CsSatisfactionTargetsUnifiedRequest;
import devlava.youproapi.dto.CsSatisfactionTargetsUnifiedResponse;
import devlava.youproapi.dto.CsSatisfactionUploadResponse;
import devlava.youproapi.dto.MemberCsFocusTasksResponse;
import devlava.youproapi.dto.MemberCsUnsatisfiedDetailsResponse;
import devlava.youproapi.dto.MemberCsUnsatisfiedRecordItem;
import devlava.youproapi.repository.TbCsSatisfactionAnnualTargetRepository;
import devlava.youproapi.repository.TbCsSatisfactionDeptMonthlyTargetRepository;
import devlava.youproapi.repository.TbCsSatisfactionRecordRepository;
import devlava.youproapi.repository.TbCsSatisfactionSkillTargetRepository;
import devlava.youproapi.repository.TbLmsDeptRepository;
import devlava.youproapi.repository.TbLmsMemberRepository;
import devlava.youproapi.support.AdminDeptScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CsSatisfactionService {

    /** 요약·월별 API에서 「실 미매칭」만 보거나 집계할 때 사용하는 sentinel (DB 부서 ID와 겹치지 않게 음수). */
    public static final int SECOND_DEPTH_UNMATCHED = -1;

    private final TbCsSatisfactionRecordRepository recordRepository;
    private final TbCsSatisfactionDeptMonthlyTargetRepository deptMonthlyTargetRepository;
    private final TbCsSatisfactionSkillTargetRepository skillTargetRepository;
    private final TbCsSatisfactionAnnualTargetRepository annualTargetRepository;
    private final TbLmsMemberRepository memberRepository;
    private final TbLmsDeptRepository deptRepository;
    private final YouproAdminProperties adminProperties;
    private final AdminDeptScopeResolver deptScopeResolver;

    /**
     * 구성원 당월 중점추진과제(5대도시·5060·문제해결) 플래그 Y 건수.
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
        long problemResolved = 0;
        for (TbCsSatisfactionRecord r : records) {
            if ("Y".equalsIgnoreCase(r.getFiveMajorCitiesYn())) {
                fiveMajor++;
            }
            if ("Y".equalsIgnoreCase(r.getGen5060Yn())) {
                gen5060++;
            }
            if ("Y".equalsIgnoreCase(r.getProblemResolvedYn())) {
                problemResolved++;
            }
        }
        return new MemberCsFocusTasksResponse(fiveMajor, gen5060, problemResolved);
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
                r.getCenterName(),
                r.getGroupName(),
                r.getRoomName(),
                r.getConsultType1(),
                r.getConsultType2(),
                r.getConsultType3(),
                r.getSkill(),
                r.getSatisfiedYn(),
                r.getGoodMent(),
                r.getBadMent(),
                r.getFiveMajorCitiesYn(),
                r.getGen5060Yn(),
                r.getProblemResolvedYn());
    }

    public CsSatisfactionSummaryResponse getSummary(Integer year, Integer secondDepthDeptIdFilter) {
        int y = year != null ? year : LocalDate.now().getYear();
        validateSecondDepthFilter(secondDepthDeptIdFilter);

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
        /** application.yml 의 leaf-dept-ids-by-second-depth: 리프 팀 id → 소속 센터(2depth) id */
        Map<Integer, Integer> leafToConfiguredCenter = buildLeafToConfiguredCenterMap();
        Set<Integer> allowedLeaves = leafTeamIdsExcludingFilterCenters();

        boolean onlyUnmatched = secondDepthDeptIdFilter != null
                && secondDepthDeptIdFilter == SECOND_DEPTH_UNMATCHED;

        List<TbLmsDept> leafDepts = onlyUnmatched
                ? List.of()
                : listConfiguredLeafTeams(
                        allDepts,
                        secondDepthDeptIdFilter == null ? null : secondDepthDeptIdFilter);

        Map<Integer, Agg> aggByLeaf = new LinkedHashMap<>();
        for (TbLmsDept leaf : leafDepts) {
            aggByLeaf.put(leaf.getDeptId(), new Agg());
        }
        Agg aggOther = new Agg();
        /** 팀(리프)별 엑셀 센터·그룹 문자열 다수결 — LMS 트리가 끊기거나 팀이 센터 직속일 때 표시 보강 */
        Map<Integer, Map<String, Integer>> centerNameVotes = new HashMap<>();
        Map<Integer, Map<String, Integer>> groupNameVotes = new HashMap<>();
        Map<Integer, Map<String, Integer>> roomNameVotes = new HashMap<>();

        for (TbCsSatisfactionRecord rec : records) {
            TbLmsMember m = memberBySkid.get(rec.getSkid());
            Agg bucket;
            if (onlyUnmatched) {
                boolean unmatched = m == null
                        || m.getDeptIdx() == null
                        || resolveLeafBucket(m.getDeptIdx(), allowedLeaves, parentOf) == null;
                if (!unmatched) {
                    continue;
                }
                bucket = aggOther;
            } else {
                if (m == null || m.getDeptIdx() == null) {
                    bucket = aggOther;
                } else {
                    Integer leafId = resolveLeafBucket(m.getDeptIdx(), allowedLeaves, parentOf);
                    if (leafId != null && aggByLeaf.containsKey(leafId)) {
                        bucket = aggByLeaf.get(leafId);
                        mergeNameVote(centerNameVotes, leafId, rec.getCenterName());
                        mergeNameVote(groupNameVotes, leafId, rec.getGroupName());
                        mergeNameVote(roomNameVotes, leafId, rec.getRoomName());
                    } else {
                        bucket = aggOther;
                    }
                }
            }
            bucket.eval++;
            if ("Y".equalsIgnoreCase(rec.getSatisfiedYn())) {
                bucket.sat++;
            } else if ("N".equalsIgnoreCase(rec.getSatisfiedYn())) {
                bucket.diss++;
            }
        }

        List<CsSatisfactionSummaryResponse.SecondDepthSatisfactionRow> rows = new ArrayList<>();

        if (!onlyUnmatched) {
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
                    String v = pickTopNameVote(centerNameVotes, leaf.getDeptId());
                    centerName = v != null ? v : "—";
                }
                // 그룹: 팀(리프) 바로 위 부서(TB_LMS_DEPT 직속 부모) — 센터 직속이면 부모명(센터와 같을 수 있음)
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
                    String gv = pickTopNameVote(groupNameVotes, leaf.getDeptId());
                    if (gv == null) {
                        gv = pickTopNameVote(roomNameVotes, leaf.getDeptId());
                    }
                    groupName = gv != null ? gv : "—";
                }
                rows.add(CsSatisfactionSummaryResponse.SecondDepthSatisfactionRow.builder()
                        .secondDepthDeptId(leaf.getDeptId())
                        .centerName(centerName)
                        .groupName(groupName)
                        .secondDepthName(leafName)
                        .evalCount(a.eval)
                        .satisfiedCount(a.sat)
                        .dissatisfiedCount(a.diss)
                        .satisfactionRate(satRate)
                        .targetPercent(targetAvg)
                        .achievementRate(achievement)
                        .build());
            }
        }

        if ((secondDepthDeptIdFilter == null || onlyUnmatched) && aggOther.eval > 0) {
            Double satRate = round1(100.0 * aggOther.sat / aggOther.eval);
            rows.add(CsSatisfactionSummaryResponse.SecondDepthSatisfactionRow.builder()
                    .secondDepthDeptId(SECOND_DEPTH_UNMATCHED)
                    .centerName("—")
                    .groupName("—")
                    .secondDepthName("기타 (실 미매칭)")
                    .evalCount(aggOther.eval)
                    .satisfiedCount(aggOther.sat)
                    .dissatisfiedCount(aggOther.diss)
                    .satisfactionRate(satRate)
                    .targetPercent(null)
                    .achievementRate(null)
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
            boolean inUnmatched;
            if (secondDepthDeptId == SECOND_DEPTH_UNMATCHED) {
                if (m == null || m.getDeptIdx() == null) {
                    inUnmatched = true;
                } else {
                    inUnmatched = resolveLeafBucket(m.getDeptIdx(), allowedLeaves, parentOf) == null;
                }
            } else {
                inUnmatched = false;
            }
            if (secondDepthDeptId == SECOND_DEPTH_UNMATCHED) {
                if (!inUnmatched) {
                    continue;
                }
            } else {
                if (m == null || m.getDeptIdx() == null) {
                    continue;
                }
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
     * 중점추진과제(5대도시·5060·문제해결) Y 건수를 한 번에 반환합니다.
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
            if (m == null || m.getDeptIdx() == null) {
                continue;
            }
            Integer root = resolveSecondDepthRoot(m.getDeptIdx(), parentOf, roots);
            if (root == null || !roots.contains(root)) {
                continue;
            }
            int mo = rec.getEvalDate().getMonthValue();
            evalByMonth[mo]++;
            if ("Y".equalsIgnoreCase(rec.getSatisfiedYn())) {
                satByMonth[mo]++;
            } else if ("N".equalsIgnoreCase(rec.getSatisfiedYn())) {
                dissByMonth[mo]++;
            }
            if ("Y".equalsIgnoreCase(rec.getFiveMajorCitiesYn())) {
                fiveByMonth[mo]++;
            }
            if ("Y".equalsIgnoreCase(rec.getGen5060Yn())) {
                gen5060ByMonth[mo]++;
            }
            if ("Y".equalsIgnoreCase(rec.getProblemResolvedYn())) {
                probByMonth[mo]++;
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
            scopeByLeaf = listConfiguredLeafTeams(allDepts, null).stream()
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
            if (m == null || m.getDeptIdx() == null) {
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

        List<CsSatisfactionCenterMonthDetailResponse.MemberMonthRow> memberRows = new ArrayList<>();
        for (Map.Entry<String, Agg> e : bySkid.entrySet()) {
            String skid = e.getKey();
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

    private static void accumulateSatisfaction(TbCsSatisfactionRecord rec, Agg a) {
        a.eval++;
        if ("Y".equalsIgnoreCase(rec.getSatisfiedYn())) {
            a.sat++;
        } else if ("N".equalsIgnoreCase(rec.getSatisfiedYn())) {
            a.diss++;
        }
        if ("Y".equalsIgnoreCase(rec.getFiveMajorCitiesYn())) {
            a.fiveMajor++;
        }
        if ("Y".equalsIgnoreCase(rec.getGen5060Yn())) {
            a.gen5060++;
        }
        if ("Y".equalsIgnoreCase(rec.getProblemResolvedYn())) {
            a.problemResolved++;
        }
    }

    @Transactional
    public CsSatisfactionUploadResponse uploadExcel(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 비어 있습니다.");
        }

        List<TbCsSatisfactionRecord> toSave = new ArrayList<>();
        Set<LocalDate> datesInFile = new HashSet<>();
        List<String> warnings = new ArrayList<>();
        int skipped = 0;

        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            boolean header = detectHeader(sheet.getRow(0), fmt);
            int start = header ? 1 : 0;

            for (int ri = start; ri <= sheet.getLastRowNum(); ri++) {
                Row row = sheet.getRow(ri);
                if (row == null) {
                    continue;
                }
                try {
                    ParsedRow pr = parseDataRow(row, fmt);
                    if (pr == null) {
                        continue;
                    }

                    TbCsSatisfactionRecord entity = new TbCsSatisfactionRecord();
                    entity.setSubsidiaryType(pr.subsidiaryType);
                    entity.setCenterName(pr.centerName);
                    entity.setGroupName(pr.groupName);
                    entity.setRoomName(pr.roomName);
                    entity.setSkid(pr.skid);
                    entity.setEvalDate(pr.evalDate);
                    entity.setConsultTime(pr.consultTime);
                    entity.setConsultType1(pr.consultType1);
                    entity.setConsultType2(pr.consultType2);
                    entity.setConsultType3(pr.consultType3);
                    entity.setDissatisfactionType(pr.dissatisfactionType);
                    entity.setSkill(pr.skill);
                    entity.setGoodMent(pr.goodMent);
                    entity.setBadMent(pr.badMent);
                    entity.setSatisfiedYn(pr.satisfiedYn);
                    entity.setFiveMajorCitiesYn(pr.fiveMajor);
                    entity.setGen5060Yn(pr.gen5060);
                    entity.setCreatedAt(Instant.now());

                    toSave.add(entity);
                    datesInFile.add(pr.evalDate);
                } catch (Exception ex) {
                    skipped++;
                    warnings.add("행 " + (ri + 1) + ": " + ex.getMessage());
                }
            }
        }

        List<LocalDate> replaced = new ArrayList<>(datesInFile);
        replaced.sort(LocalDate::compareTo);
        if (!datesInFile.isEmpty()) {
            recordRepository.bulkDeleteByEvalDateIn(datesInFile);
        }
        if (!toSave.isEmpty()) {
            recordRepository.saveAll(toSave);
        }

        log.info("CS 만족도 업로드: 삽입 {}건, 스킵 {}건, 교체 일자 {}개", toSave.size(), skipped, datesInFile.size());

        return CsSatisfactionUploadResponse.builder()
                .inserted(toSave.size())
                .skipped(skipped)
                .replacedDates(replaced)
                .warnings(warnings.size() > 50 ? warnings.subList(0, 50) : warnings)
                .build();
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
     * {@link AdminService#getLeafTeamsForSecondDepth(Integer)} 와 동일 규칙으로 하위 팀 목록을
     * 만든 뒤,
     * {@code second-depth-dept-ids} 에 해당하는 상위 센터(필터 전용)는 행에서 제외한다.
     */
    private List<TbLmsDept> listConfiguredLeafTeams(List<TbLmsDept> allDepts, Integer secondDepthCenterId) {
        List<Integer> cfg = adminProperties.getSecondDepthDeptIds();
        int leafDepth = adminProperties.getLeafTeamDepth();
        Set<Integer> scopeIds = deptScopeResolver.resolveAllowedDeptIds();
        Set<Integer> filterCenterIds = new HashSet<>(cfg);

        List<TbLmsDept> leafDepts = secondDepthCenterId == null
                ? AdminDeptScope.listLeafDeptsUnderAnyRoot(allDepts, cfg, leafDepth)
                : AdminDeptScope.listDeptsOfDepthInSubtree(allDepts, secondDepthCenterId, leafDepth);
        return leafDepts.stream()
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

    private static void mergeNameVote(Map<Integer, Map<String, Integer>> votes, int leafId, String raw) {
        if (raw == null) {
            return;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return;
        }
        votes.computeIfAbsent(leafId, k -> new HashMap<>()).merge(s, 1, Integer::sum);
    }

    private static String pickTopNameVote(Map<Integer, Map<String, Integer>> votes, int leafId) {
        Map<String, Integer> m = votes.get(leafId);
        if (m == null || m.isEmpty()) {
            return null;
        }
        return m.entrySet().stream()
                .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(null);
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

    /**
     * 헤더 행 여부 판별.
     * 새 양식: 열4=상담사ID, 열5=상담일자 로 감지. 구양식 키워드도 하위호환 유지.
     */
    private static boolean detectHeader(Row row, DataFormatter fmt) {
        if (row == null) {
            return false;
        }
        String h4 = fmt.formatCellValue(row.getCell(4)).trim();
        String h5 = fmt.formatCellValue(row.getCell(5)).trim();
        if (h4.contains("상담사") || h5.contains("상담일")) {
            return true;
        }
        // 구양식 하위호환
        String h0 = fmt.formatCellValue(row.getCell(0)).trim();
        String h1 = fmt.formatCellValue(row.getCell(1)).trim();
        return h0.contains("날짜") || h1.contains("구성원")
                || h0.toLowerCase(Locale.ROOT).contains("date")
                || h1.toLowerCase(Locale.ROOT).contains("skid")
                || h1.contains("ID");
    }

    private static String cellStr(Row row, int idx, DataFormatter fmt) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(idx);
        return cell == null ? "" : fmt.formatCellValue(cell).trim();
    }

    /**
     * 새 엑셀 양식 열 순서 (0-indexed):
     *  0:자회사구분  1:센터  2:그룹  3:실  4:상담사ID  5:상담일자  6:상담시간
     *  7:상담유형1  8:상담유형2  9:상담유형3  10:불만족유형  11:스킬
     *  12:긍정코멘트  13:부정코멘트  14:만족여부  15:5대도시  16:5060
     */
    private static ParsedRow parseDataRow(Row row, DataFormatter fmt) {
        String c4 = cellStr(row, 4, fmt);  // 상담사ID
        String c5 = cellStr(row, 5, fmt);  // 상담일자
        if (c4.isEmpty() && c5.isEmpty()) {
            return null;
        }
        if (c4.isEmpty()) {
            throw new IllegalArgumentException("상담사ID(열5)가 비어 있습니다.");
        }
        if (c5.isEmpty()) {
            throw new IllegalArgumentException("상담일자(열6)가 비어 있습니다.");
        }

        String satRaw = cellStr(row, 14, fmt);  // 만족여부
        if (satRaw.isEmpty()) {
            throw new IllegalArgumentException("만족여부(열15)가 비어 있습니다.");
        }

        // 불만족유형 (1~5)
        Integer dissType = null;
        String dissRaw = cellStr(row, 10, fmt);
        if (!dissRaw.isEmpty()) {
            try {
                int v = Integer.parseInt(dissRaw.replace(",", "").trim());
                if (v < 1 || v > 5) {
                    throw new IllegalArgumentException("불만족유형은 1~5 사이여야 합니다: " + dissRaw);
                }
                dissType = v;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("불만족유형 형식 오류(숫자 1~5): " + dissRaw);
            }
        }

        String good = cellStr(row, 12, fmt);
        String bad  = cellStr(row, 13, fmt);

        ParsedRow pr = new ParsedRow();
        pr.subsidiaryType = nullIfEmpty(cellStr(row, 0, fmt));
        pr.centerName     = nullIfEmpty(cellStr(row, 1, fmt));
        pr.groupName      = nullIfEmpty(cellStr(row, 2, fmt));
        pr.roomName       = nullIfEmpty(cellStr(row, 3, fmt));
        pr.skid           = c4;
        pr.evalDate       = parseDateCell(row.getCell(5), c5);
        pr.consultTime    = nullIfEmpty(cellStr(row, 6, fmt));
        pr.consultType1   = nullIfEmpty(cellStr(row, 7, fmt));
        pr.consultType2   = nullIfEmpty(cellStr(row, 8, fmt));
        pr.consultType3   = nullIfEmpty(cellStr(row, 9, fmt));
        pr.dissatisfactionType = dissType;
        pr.skill          = nullIfEmpty(cellStr(row, 11, fmt));
        pr.goodMent       = nullIfEmpty(good);
        pr.badMent        = nullIfEmpty(bad);
        pr.satisfiedYn    = parseMandatoryYn(satRaw, "만족여부");
        pr.fiveMajor      = parseOptionalYn(cellStr(row, 15, fmt));
        pr.gen5060        = parseOptionalYn(cellStr(row, 16, fmt));
        return pr;
    }

    private static String nullIfEmpty(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    /**
     * 상담일자 파싱.
     * 우선순위: ① 8자리 숫자(YYYYMMDD) ② Excel 날짜 셀 ③ yyyy/MM/dd, yyyy-MM-dd 문자열
     */
    private static LocalDate parseDateCell(Cell cell, String asString) {
        String s = asString.trim();

        // ① YYYYMMDD 숫자 문자열 (예: "20260401")
        if (s.matches("\\d{8}")) {
            int y = Integer.parseInt(s.substring(0, 4));
            int m = Integer.parseInt(s.substring(4, 6));
            int d = Integer.parseInt(s.substring(6, 8));
            return LocalDate.of(y, m, d);
        }

        // ② 숫자 셀 처리
        if (cell != null && cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
            double dv = cell.getNumericCellValue();
            // YYYYMMDD 형태의 숫자 (예: 20260401.0)
            long lv = (long) dv;
            if (lv >= 10_000_101L && lv <= 99_991_231L) {
                int y = (int) (lv / 10000);
                int m = (int) ((lv % 10000) / 100);
                int d = (int) (lv % 100);
                return LocalDate.of(y, m, d);
            }
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            if (DateUtil.isValidExcelDate(dv)) {
                java.util.Date ud = DateUtil.getJavaDate(dv, false);
                return ud.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        }

        if (s.isEmpty()) {
            throw new IllegalArgumentException("날짜가 비어 있습니다.");
        }

        // ③ 구분자 있는 날짜 문자열
        try {
            if (s.contains("/")) {
                String[] p = s.split("/");
                if (p.length == 3) {
                    int y = Integer.parseInt(p[0].trim());
                    int m = Integer.parseInt(p[1].trim());
                    int d = Integer.parseInt(p[2].trim());
                    if (y < 100) {
                        y += 2000;
                    }
                    return LocalDate.of(y, m, d);
                }
            }
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("날짜 파싱 실패: " + s);
        }
    }

    private static String parseMandatoryYn(String raw, String label) {
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.equals("Y") || s.equals("YES") || s.equals("O") || s.equals("예") || s.equals("1") || s.equals("TRUE")) {
            return "Y";
        }
        if (s.equals("N") || s.equals("NO") || s.equals("X") || s.equals("아니오") || s.equals("0") || s.equals("FALSE")) {
            return "N";
        }
        throw new IllegalArgumentException(label + "는 Y/N 이어야 합니다: " + raw);
    }

    private static String parseOptionalYn(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return parseMandatoryYn(raw, "YN");
        } catch (IllegalArgumentException e) {
            return null;
        }
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
        List<Integer> deptIds = List.of(5, 6);
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

    private static final class ParsedRow {
        String subsidiaryType;   // 열 0 — 자회사구분
        String centerName;       // 열 1 — 센터
        String groupName;        // 열 2 — 그룹
        String roomName;         // 열 3 — 실
        String skid;             // 열 4 — 상담사ID
        LocalDate evalDate;      // 열 5 — 상담일자
        String consultTime;      // 열 6 — 상담시간
        String consultType1;     // 열 7 — 상담유형1
        String consultType2;     // 열 8 — 상담유형2
        String consultType3;     // 열 9 — 상담유형3
        Integer dissatisfactionType; // 열 10 — 불만족유형 (1~5)
        String skill;            // 열 11 — 스킬
        String goodMent;         // 열 12 — 긍정코멘트
        String badMent;          // 열 13 — 부정코멘트
        String satisfiedYn;      // 열 14 — 만족여부
        String fiveMajor;        // 열 15 — 5대도시
        String gen5060;          // 열 16 — 5060
    }
}
