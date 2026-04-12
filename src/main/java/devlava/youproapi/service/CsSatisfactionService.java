package devlava.youproapi.service;

import devlava.youproapi.config.YouproAdminProperties;
import devlava.youproapi.domain.TbCsDissatisfactionType;
import devlava.youproapi.domain.TbCsSatisfactionDailyTarget;
import devlava.youproapi.domain.TbCsSatisfactionRecord;
import devlava.youproapi.domain.TbLmsDept;
import devlava.youproapi.domain.TbLmsMember;
import devlava.youproapi.dto.AdminFilterMetaResponse;
import devlava.youproapi.dto.CsSatisfactionCenterMonthDetailResponse;
import devlava.youproapi.dto.CsSatisfactionMonthlyTargetsRequest;
import devlava.youproapi.dto.CsSatisfactionMonthlyTargetsResponse;
import devlava.youproapi.dto.CsSatisfactionMonthlyTrendResponse;
import devlava.youproapi.dto.CsSatisfactionSummaryResponse;
import devlava.youproapi.dto.CsSatisfactionUploadResponse;
import devlava.youproapi.repository.TbCsDissatisfactionTypeRepository;
import devlava.youproapi.repository.TbCsSatisfactionDailyTargetRepository;
import devlava.youproapi.repository.TbCsSatisfactionRecordRepository;
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
    private final TbCsDissatisfactionTypeRepository dissatisfactionTypeRepository;
    private final TbCsSatisfactionDailyTargetRepository dailyTargetRepository;
    private final TbLmsMemberRepository memberRepository;
    private final TbLmsDeptRepository deptRepository;
    private final YouproAdminProperties adminProperties;
    private final AdminDeptScopeResolver deptScopeResolver;

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
        Set<Integer> roots = new HashSet<>(adminProperties.getSecondDepthDeptIds());
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
                Integer rootForTarget = resolveSecondDepthRoot(leaf.getDeptId(), parentOf, roots);
                Double targetAvg = rootForTarget != null
                        ? averageTargetPercentForYear(rootForTarget, from, to)
                        : null;
                Double achievement = null;
                if (targetAvg != null && targetAvg > 0 && satRate != null) {
                    achievement = round1(100.0 * satRate / targetAvg);
                }
                String leafName = leaf.getDeptName() != null ? leaf.getDeptName() : String.valueOf(leaf.getDeptId());
                rows.add(CsSatisfactionSummaryResponse.SecondDepthSatisfactionRow.builder()
                        .secondDepthDeptId(leaf.getDeptId())
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
     * <strong>해당 연·월</strong> 만족도 요약 + 구성원(skid)별 건수.
     * {@code secondDepthDeptId} 는 (1) {@code youpro.admin.second-depth-dept-ids} 에 등록된 <em>센터</em> id 이거나,
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
        int mo = month != null ? month : now.getMonthValue();
        LocalDate from = LocalDate.of(y, mo, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());

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
    }

    @Transactional
    public CsSatisfactionUploadResponse uploadExcel(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 비어 있습니다.");
        }

        Map<String, TbCsDissatisfactionType> dissTypesByName = new HashMap<>();

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

                    TbCsDissatisfactionType dissType = null;
                    if (pr.dissTypeLabel != null && !pr.dissTypeLabel.isBlank()) {
                        String key = pr.dissTypeLabel.trim();
                        dissType = dissTypesByName.computeIfAbsent(key, k ->
                                dissatisfactionTypeRepository.findByTypeName(k).orElseGet(() -> {
                                    TbCsDissatisfactionType n = new TbCsDissatisfactionType();
                                    n.setTypeCode("AUTO_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
                                    n.setTypeName(k);
                                    n.setCreatedAt(Instant.now());
                                    return dissatisfactionTypeRepository.save(n);
                                }));
                    }

                    TbCsSatisfactionRecord entity = new TbCsSatisfactionRecord();
                    entity.setEvalDate(pr.evalDate);
                    entity.setSkid(pr.skid);
                    entity.setSatisfiedYn(pr.satisfiedYn);
                    entity.setScore(pr.score);
                    entity.setDissatisfactionType(dissType);
                    entity.setFiveMajorCitiesYn(pr.fiveMajor);
                    entity.setGen5060Yn(pr.gen5060);
                    entity.setProblemResolvedYn(pr.problemResolved);
                    entity.setGoodMent(pr.goodMent);
                    entity.setBadMent(pr.badMent);
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
            TbCsSatisfactionDailyTarget row = dailyTargetRepository
                    .findByTargetDateAndSecondDepthDeptId(monthKey, r.getSecondDepthDeptId())
                    .orElseGet(TbCsSatisfactionDailyTarget::new);
            row.setTargetDate(monthKey);
            row.setSecondDepthDeptId(r.getSecondDepthDeptId());
            row.setTargetPercent(r.getTargetPercent().setScale(2, RoundingMode.HALF_UP));
            if (row.getId() == null) {
                row.setCreatedAt(Instant.now());
            }
            dailyTargetRepository.save(row);
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
            BigDecimal pct = dailyTargetRepository
                    .findByTargetDateAndSecondDepthDeptId(monthKey, centerId)
                    .map(TbCsSatisfactionDailyTarget::getTargetPercent)
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
     * {@link AdminService#getLeafTeamsForSecondDepth(Integer)} 와 동일 규칙으로 하위 팀 목록을 만든 뒤,
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

    /** {@code ancestorDeptId} 가 {@code leafDeptId} 의 조상(본인 포함)이면 true. */
    private static boolean isAncestorDept(int ancestorDeptId, int leafDeptId, Map<Integer, Integer> parentOf) {
        Integer cur = leafDeptId;
        for (int i = 0; i < 64 && cur != null; i++) {
            if (cur == ancestorDeptId) {
                return true;
            }
            cur = parentOf.get(cur);
        }
        return false;
    }

    /**
     * 구성원 부서를 {@code youpro.admin} 리프 팀 ID로 귀속. 부서가 리프 본인이거나, 그 하위에 속한 단일 리프 후보가 있으면
     * (여러 개면 dept_id 최소) 반환.
     */
    private static Integer resolveLeafBucket(
            Integer memberDeptIdx, Set<Integer> allowedLeaves, Map<Integer, Integer> parentOf) {
        if (memberDeptIdx == null || allowedLeaves.isEmpty()) {
            return null;
        }
        if (allowedLeaves.contains(memberDeptIdx)) {
            return memberDeptIdx;
        }
        Integer best = null;
        for (Integer leafId : allowedLeaves) {
            if (isAncestorDept(memberDeptIdx, leafId, parentOf)) {
                if (best == null || leafId < best) {
                    best = leafId;
                }
            }
        }
        return best;
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
        List<TbCsSatisfactionDailyTarget> list =
                dailyTargetRepository.findBySecondDepthDeptIdAndTargetDateBetween(secondDepthDeptId, from, to);
        if (list.isEmpty()) {
            return null;
        }
        double sum = list.stream().mapToDouble(t -> t.getTargetPercent().doubleValue()).sum();
        return round1(sum / list.size());
    }

    private static Double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static boolean detectHeader(Row row, DataFormatter fmt) {
        if (row == null) {
            return false;
        }
        String h0 = fmt.formatCellValue(row.getCell(0)).trim();
        String h1 = fmt.formatCellValue(row.getCell(1)).trim();
        return h0.contains("날짜") || h1.contains("구성원") || h0.toLowerCase(Locale.ROOT).contains("date")
                || h1.toLowerCase(Locale.ROOT).contains("skid") || h1.contains("ID");
    }

    private static String cellStr(Row row, int idx, DataFormatter fmt) {
        if (row == null) {
            return "";
        }
        return fmt.formatCellValue(row.getCell(idx)).trim();
    }

    private ParsedRow parseDataRow(Row row, DataFormatter fmt) {
        String c0 = cellStr(row, 0, fmt);
        String c1 = cellStr(row, 1, fmt);
        if (c1.isEmpty() && c0.isEmpty()) {
            return null;
        }
        if (c1.isEmpty()) {
            throw new IllegalArgumentException("구성원ID가 비어 있습니다.");
        }

        LocalDate evalDate = parseDateCell(row.getCell(0), c0);
        String skid = c1.trim();
        String satRaw = cellStr(row, 2, fmt);
        if (satRaw.isEmpty()) {
            throw new IllegalArgumentException("만족여부가 비어 있습니다.");
        }
        String satisfiedYn = parseMandatoryYn(satRaw, "만족여부");

        BigDecimal score = null;
        String scoreStr = cellStr(row, 3, fmt);
        if (!scoreStr.isEmpty()) {
            try {
                score = new BigDecimal(scoreStr.replace(",", ""));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("만족도 점수 형식 오류: " + scoreStr);
            }
        }

        String dissLabel = cellStr(row, 4, fmt);
        String five = parseOptionalYn(cellStr(row, 5, fmt));
        String g5060 = parseOptionalYn(cellStr(row, 6, fmt));
        String prob = parseOptionalYn(cellStr(row, 7, fmt));
        String good = cellStr(row, 8, fmt);
        String bad = cellStr(row, 9, fmt);
        if (good.isEmpty()) {
            good = null;
        }
        if (bad.isEmpty()) {
            bad = null;
        }

        ParsedRow pr = new ParsedRow();
        pr.evalDate = evalDate;
        pr.skid = skid;
        pr.satisfiedYn = satisfiedYn;
        pr.score = score;
        pr.dissTypeLabel = dissLabel.isEmpty() ? null : dissLabel;
        pr.fiveMajor = five;
        pr.gen5060 = g5060;
        pr.problemResolved = prob;
        pr.goodMent = good;
        pr.badMent = bad;
        return pr;
    }

    private static LocalDate parseDateCell(Cell cell, String asString) {
        if (cell != null && cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
            double dv = cell.getNumericCellValue();
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            if (DateUtil.isValidExcelDate(dv)) {
                java.util.Date ud = DateUtil.getJavaDate(dv, false);
                return ud.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        }
        String s = asString.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("날짜가 비어 있습니다.");
        }
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

    private static final class Agg {
        long eval;
        long sat;
        long diss;
    }

    private static final class ParsedRow {
        LocalDate evalDate;
        String skid;
        String satisfiedYn;
        BigDecimal score;
        String dissTypeLabel;
        String fiveMajor;
        String gen5060;
        String problemResolved;
        String goodMent;
        String badMent;
    }
}
