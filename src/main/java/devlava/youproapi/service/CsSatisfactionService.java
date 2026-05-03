package devlava.youproapi.service;

import devlava.youproapi.config.YouproAdminProperties;
import devlava.youproapi.domain.TbCsSatisfactionAnnualTarget;
import devlava.youproapi.domain.TbCsSatisfactionDeptMonthlyTarget;
import devlava.youproapi.domain.TbCsSatisfactionEvalExcludeLog;
import devlava.youproapi.domain.TbCsSatisfactionRecord;
import devlava.youproapi.domain.TbCsSatisfactionSkillTarget;
import devlava.youproapi.domain.TbLmsDept;
import devlava.youproapi.domain.TbLmsMember;
import devlava.youproapi.dto.AdminFilterMetaResponse;
import devlava.youproapi.dto.CsSatisfactionCenterMonthDetailResponse;
import devlava.youproapi.dto.CsSatisfactionExcludeLogResponse;
import devlava.youproapi.dto.CsSatisfactionExcludeTimeRequest;
import devlava.youproapi.dto.CsSatisfactionExcludeTimeResponse;
import devlava.youproapi.dto.CsSatisfactionMemberMonthlyRowsResponse;
import devlava.youproapi.dto.CsSatisfactionMonthlyTargetsRequest;
import devlava.youproapi.dto.CsSatisfactionMonthlyTargetsResponse;
import devlava.youproapi.dto.CsSatisfactionMonthlyOverviewResponse;
import devlava.youproapi.dto.CsSatisfactionMonthlyTrendResponse;
import devlava.youproapi.dto.CsSatisfactionRankingResponse;
import devlava.youproapi.dto.CsSatisfactionAdminDashboardKpiResponse;
import devlava.youproapi.dto.CsSatisfactionSummaryResponse;
import devlava.youproapi.dto.CsSatisfactionTodayHourlyResponse;
import devlava.youproapi.dto.CsSatisfactionTargetsUnifiedRequest;
import devlava.youproapi.dto.CsSatisfactionTargetsUnifiedResponse;
import devlava.youproapi.dto.MemberCsFocusTasksResponse;
import devlava.youproapi.dto.MemberCsInsightPromptMentsResponse;
import devlava.youproapi.dto.MemberCsUnsatisfiedDetailsResponse;
import devlava.youproapi.dto.MemberCsUnsatisfiedRecordItem;
import devlava.youproapi.dto.MemberSatisfactionResponse;
import devlava.youproapi.repository.TbCsSatisfactionAnnualTargetRepository;
import devlava.youproapi.repository.TbCsSatisfactionDeptMonthlyTargetRepository;
import devlava.youproapi.repository.TbCsSatisfactionEvalExcludeLogRepository;
import devlava.youproapi.repository.TbCsSatisfactionRecordRepository;
import devlava.youproapi.repository.TbCsSatisfactionSkillTargetRepository;
import devlava.youproapi.repository.TbLmsDeptRepository;
import devlava.youproapi.repository.TbLmsMemberRepository;
import devlava.youproapi.support.AdminDeptScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

    /** 평가 제외 API·화면에서 허용하는 스킬 — {@code TB_YOU_CS."스킬"} 값과 동일해야 함 */
    private static final List<String> EXCLUDABLE_SKILLS = List.of("일반", "리텐션", "이관", "멀티/기술");

    private static boolean isEvalTarget(TbLmsMember m) {
        return m != null && EVAL_TARGET_YES.equalsIgnoreCase(m.getYouYn());
    }

    /**
     * 불만족유형 컬럼: {@code null}, 빈 문자열, 또는 {@code 1}~{@code 5}에 대응하는 문자열.
     * 그 외 값은 집계·카운트에서 제외합니다.
     */
    private static Integer normalizedDissatisfactionTypeOrdinal(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            int v = Integer.parseInt(s);
            if (v >= 1 && v <= 5) {
                return v;
            }
        } catch (NumberFormatException ignored) {
            // ignore
        }
        return null;
    }

    private static String resolveDeptSkill(TbLmsMember member, Map<Integer, TbLmsDept> deptById) {
        if (member == null || member.getDeptIdx() == null || deptById == null || deptById.isEmpty()) {
            return null;
        }
        TbLmsDept dept = deptById.get(member.getDeptIdx());
        if (dept == null || dept.getSkill() == null || dept.getSkill().isBlank()) {
            return null;
        }
        return dept.getSkill().trim();
    }

    private final TbCsSatisfactionRecordRepository recordRepository;
    private final TbCsSatisfactionDeptMonthlyTargetRepository deptMonthlyTargetRepository;
    private final TbCsSatisfactionSkillTargetRepository skillTargetRepository;
    private final TbCsSatisfactionAnnualTargetRepository annualTargetRepository;
    private final TbCsSatisfactionEvalExcludeLogRepository evalExcludeLogRepository;
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
        List<TbCsSatisfactionRecord> records = recordRepository.findByEvalDateBetweenAndSkidAndUseYn(
                atStartOfDay(from), atEndOfDay(to), skid, "Y");
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

        List<TbCsSatisfactionRecord> records = recordRepository.findByEvalDateBetweenAndSkidAndUseYn(
                atStartOfDay(from), atEndOfDay(to), skid, "Y");
        long received = records.size();
        long sat = records.stream().filter(r -> "Y".equalsIgnoreCase(r.getSatisfiedYn())).count();
        long unsat = records.stream().filter(r -> "N".equalsIgnoreCase(r.getSatisfiedYn())).count();
        long fiveMajorY = records.stream()
                .filter(r -> "Y".equalsIgnoreCase(r.getFiveMajorCitiesYn()))
                .count();
        long gen5060Y = records.stream().filter(r -> "Y".equalsIgnoreCase(r.getGen5060Yn())).count();
        long problemResolvedY = records.stream()
                .filter(r -> "Y".equalsIgnoreCase(r.getProblemResolvedYn()))
                .count();

        TbLmsMember member = memberRepository.findById(skid).orElse(null);
        Double targetPct = null;
        String deptSkill = null;
        if (member != null && member.getDeptIdx() != null) {
            deptSkill = deptRepository.findById(member.getDeptIdx())
                    .map(TbLmsDept::getSkill)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .orElse(null);
        }
        if (deptSkill != null) {
            targetPct = skillTargetRepository
                    .findByTargetDateAndSkillName(monthKey, deptSkill)
                    .map(t -> t.getTargetPercent().doubleValue())
                    .orElse(null);
        }

        Double actualPct = received > 0 ? round1(100.0 * sat / received) : null;
        Double unsatisfiedPct = received > 0 ? round1(100.0 * unsat / received) : null;
        Double fiveMajorCitiesPct = received > 0 ? round1(100.0 * fiveMajorY / received) : null;
        Double gen5060Pct = received > 0 ? round1(100.0 * gen5060Y / received) : null;
        Double problemResolvedPct =
                received > 0 ? round1(100.0 * problemResolvedY / received) : null;
        Double achievement = null;
        Boolean met = computeMonthlySkillTargetMet(received, sat, targetPct);
        if (actualPct != null && targetPct != null && targetPct > 0) {
            achievement = round1(100.0 * actualPct / targetPct);
        }

        Map<Integer, Long> dissCountByType = records.stream()
                .map(TbCsSatisfactionRecord::getDissatisfactionType)
                .map(CsSatisfactionService::normalizedDissatisfactionTypeOrdinal)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(i -> i, Collectors.counting()));
        List<MemberSatisfactionResponse.UnsatisfiedCategory> unsatisfiedCategories = List.of(
                buildUnsatisfiedCategory(1, "서비스 지식부족", dissCountByType),
                buildUnsatisfiedCategory(2, "성의 없는 태도", dissCountByType),
                buildUnsatisfiedCategory(3, "적절하지 않는 혜택 안내", dissCountByType),
                buildUnsatisfiedCategory(4, "알아듣기 어려운 설명", dissCountByType),
                buildUnsatisfiedCategory(5, "문의내용 이해 못함", dissCountByType)
        );

        Map<LocalDate, List<TbCsSatisfactionRecord>> byDay = records.stream()
                .filter(r -> r.getEvalDate() != null)
                .collect(Collectors.groupingBy(r -> r.getEvalDate().toLocalDate()));
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
                .skill(deptSkill)
                .monthlyTargetPct(targetPct)
                .target(targetPct)
                .receivedCount(received)
                .totalSamples(received)
                .satisfiedCount(sat)
                .unsatisfiedCount(unsat)
                .monthlyActualPct(actualPct)
                .monthlyAchievementRate(achievement)
                .monthlyTargetMet(met)
                .unsatisfiedPct(unsatisfiedPct)
                .fiveMajorCitiesPct(fiveMajorCitiesPct)
                .gen5060Pct(gen5060Pct)
                .problemResolvedPct(problemResolvedPct)
                .unsatisfiedCategories(unsatisfiedCategories)
                .dailyTrend(dailyTrend)
                .build();
    }

    /**
     * 부서 스킬 월간 목표 대비 달성 여부.
     * 인센티브 반영({@link IncentiveReflectService})과 동일하게 실제 만족도%와 목표%를 비교한다.
     * 목표 미설정 또는 0 이하이면 {@code null}, 유효 목표가 있는데 접수 건이 0이면 {@code false}.
     */
    private static Boolean computeMonthlySkillTargetMet(long received, long satisfied, Double targetPct) {
        if (targetPct == null || targetPct <= 0) {
            return null;
        }
        if (received <= 0) {
            return false;
        }
        double actualPct = 100.0 * satisfied / received;
        return actualPct >= targetPct;
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
                        atStartOfDay(from), atEndOfDay(to), skid, Integer.toString(dissatisfactionType));
        List<MemberCsUnsatisfiedRecordItem> items = rows.stream()
                .map(this::toUnsatisfiedItem)
                .collect(Collectors.toList());
        return new MemberCsUnsatisfiedDetailsResponse(items);
    }

    public CsSatisfactionMemberMonthlyRowsResponse getMemberMonthlyRows(String skid, Integer year) {
        if (skid == null || skid.isBlank()) {
            throw new IllegalArgumentException("skid가 필요합니다.");
        }
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        LocalDate from = LocalDate.of(y, 1, 1);
        LocalDate to = LocalDate.of(y, 12, 31);

        String normalizedSkid = skid.trim();
        List<TbCsSatisfactionRecord> records = recordRepository.findByEvalDateBetweenAndSkidOrderByEvalDateDescIdDesc(
                atStartOfDay(from), atEndOfDay(to), normalizedSkid);
        String memberName = memberRepository.findById(normalizedSkid)
                .map(TbLmsMember::getMbName)
                .orElse(null);

        Map<Integer, List<TbCsSatisfactionRecord>> byMonth = new LinkedHashMap<>();
        for (TbCsSatisfactionRecord rec : records) {
            if (rec.getEvalDate() == null) continue;
            int monthKey = rec.getEvalDate().getMonthValue();
            byMonth.computeIfAbsent(monthKey, k -> new ArrayList<>()).add(rec);
        }

        List<CsSatisfactionMemberMonthlyRowsResponse.MonthBucket> months = new ArrayList<>();
        for (Map.Entry<Integer, List<TbCsSatisfactionRecord>> e : byMonth.entrySet()) {
            List<CsSatisfactionMemberMonthlyRowsResponse.RowItem> rows = e.getValue().stream()
                    .map(r -> CsSatisfactionMemberMonthlyRowsResponse.RowItem.builder()
                            .id(r.getId())
                            .consultDateTime(r.getEvalDate())
                            .consultType1(r.getConsultType1())
                            .consultType2(r.getConsultType2())
                            .consultType3(r.getConsultType3())
                            .satisfiedYn(r.getSatisfiedYn())
                            .fiveMajorCitiesYn(r.getFiveMajorCitiesYn())
                            .gen5060Yn(r.getGen5060Yn())
                            .problemResolvedYn(r.getProblemResolvedYn())
                            .goodMent(r.getGoodMent())
                            .badMent(r.getBadMent())
                            .build())
                    .collect(Collectors.toList());
            months.add(CsSatisfactionMemberMonthlyRowsResponse.MonthBucket.builder()
                    .month(e.getKey())
                    .count(rows.size())
                    .rows(rows)
                    .build());
        }

        return CsSatisfactionMemberMonthlyRowsResponse.builder()
                .year(y)
                .skid(normalizedSkid)
                .memberName(memberName)
                .totalCount(records.size())
                .months(months)
                .build();
    }

    /**
     * 스킬·상담일시 구간 내 {@code TB_YOU_CS} 행의 평가시간({@code useYn})을 일괄 {@code N} 처리합니다.
     * 구간은 시작·종료 일시 모두 포함입니다.
     */
    @Transactional(readOnly = false)
    public CsSatisfactionExcludeTimeResponse excludeTime(CsSatisfactionExcludeTimeRequest req) {
        String skill = req.getSkill() == null ? "" : req.getSkill().trim();
        if (skill.isEmpty()) {
            throw new IllegalArgumentException("skill이 필요합니다.");
        }
        if (!EXCLUDABLE_SKILLS.contains(skill)) {
            throw new IllegalArgumentException("허용되지 않은 스킬입니다. 다음 중 하나만 가능합니다: " + EXCLUDABLE_SKILLS);
        }
        LocalDateTime startAt = req.getStartAt();
        LocalDateTime endAt = req.getEndAt();
        if (startAt.isAfter(endAt)) {
            throw new IllegalArgumentException("시작일시가 종료일시보다 늦을 수 없습니다.");
        }
        int updated = recordRepository.setUseYnNForSkillAndEvalDateRange(skill, startAt, endAt);
        TbCsSatisfactionEvalExcludeLog logRow = new TbCsSatisfactionEvalExcludeLog();
        logRow.setSkillName(skill);
        logRow.setStartAt(startAt);
        logRow.setEndAt(endAt);
        String by = req.getExcludedBySkid();
        logRow.setExcludedBySkid(by != null && !by.isBlank() ? by.trim() : null);
        logRow.setUpdatedRowCount(updated);
        logRow.setCreatedAt(LocalDateTime.now());
        evalExcludeLogRepository.save(logRow);
        return CsSatisfactionExcludeTimeResponse.builder()
                .skill(skill)
                .startAt(startAt.toString())
                .endAt(endAt.toString())
                .updatedCount(updated)
                .build();
    }

    /**
     * 평가 제외 적용 이력(최근 N건, 신규순).
     */
    public CsSatisfactionExcludeLogResponse getExcludeLogRecent(int limit) {
        int lim = limit < 1 ? 50 : Math.min(limit, 200);
        var page = PageRequest.of(0, lim, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<TbCsSatisfactionEvalExcludeLog> rows = evalExcludeLogRepository.findAllByOrderByCreatedAtDesc(page);
        List<CsSatisfactionExcludeLogResponse.Entry> entries = rows.stream()
                .map(r -> CsSatisfactionExcludeLogResponse.Entry.builder()
                        .id(r.getId())
                        .skill(r.getSkillName())
                        .startAt(r.getStartAt().toString())
                        .endAt(r.getEndAt().toString())
                        .excludedBySkid(r.getExcludedBySkid())
                        .updatedRowCount(r.getUpdatedRowCount())
                        .createdAt(r.getCreatedAt().toString())
                        .build())
                .collect(Collectors.toList());
        return CsSatisfactionExcludeLogResponse.builder()
                .entries(entries)
                .build();
    }

    /**
     * 금일(한국시간) 09:00~18:59 구간, 시간(시) 단위 만족도 스냅샷.
     * 응답 {@code hours} 는 <strong>현재 시각(KST)보다 이전 시</strong>만 포함합니다(진행 중인 시간대 제외).
     * 평가대상자(you_yn=Y)·평가시간(useYn=Y)·관리자 조직 스코프와 동일하게 필터합니다.
     */
    public CsSatisfactionTodayHourlyResponse getTodayHourly(
            Integer secondDepthDeptId,
            String skillParam,
            String adminSkid) {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        ZonedDateTime nowZ = ZonedDateTime.now(zone);
        LocalDate today = nowZ.toLocalDate();
        int currentHour = nowZ.getHour();
        LocalDateTime windowStart = today.atTime(9, 0);
        LocalDateTime windowEnd = today.atTime(18, 59, 59, 999_999_999);

        AdminHourlyProfile profile = resolveAdminHourlyProfile(adminSkid);

        Integer effCenter;
        if (secondDepthDeptId == null) {
            effCenter = profile.unscoped() ? null : profile.centerId();
        } else if (secondDepthDeptId == 0) {
            effCenter = null;
        } else {
            effCenter = secondDepthDeptId;
        }

        String effSkill;
        if (skillParam != null) {
            effSkill = skillParam.isBlank() ? null : skillParam.trim();
        } else {
            if (profile.unscoped()) {
                effSkill = null;
            } else {
                String ps = profile.skill();
                effSkill = ps != null && !ps.isBlank() ? ps.trim() : null;
            }
        }

        Set<Integer> roots = new HashSet<>(adminProperties.getSecondDepthDeptIds());
        if (effCenter != null && !roots.contains(effCenter)) {
            throw new IllegalArgumentException("허용되지 않은 센터입니다: " + effCenter);
        }
        if (effSkill != null && !EXCLUDABLE_SKILLS.contains(effSkill)) {
            throw new IllegalArgumentException("허용되지 않은 스킬입니다: " + effSkill);
        }

        List<TbCsSatisfactionRecord> raw = recordRepository.findByEvalDateBetween(windowStart, windowEnd);
        Set<String> skids = raw.stream()
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
        Set<Integer> allowedDeptIds = deptScopeResolver.resolveAllowedDeptIds();

        Map<Integer, HourlyAgg> byHour = new LinkedHashMap<>();
        for (int h = 9; h <= 18; h++) {
            byHour.put(h, new HourlyAgg());
        }

        for (TbCsSatisfactionRecord rec : raw) {
            if (!isActiveUseYn(rec) || rec.getEvalDate() == null) {
                continue;
            }
            int h = rec.getEvalDate().getHour();
            if (h < 9 || h > 18) {
                continue;
            }
            TbLmsMember m = memberBySkid.get(rec.getSkid());
            if (m == null || m.getDeptIdx() == null || !isEvalTarget(m)) {
                continue;
            }
            if (!allowedDeptIds.contains(m.getDeptIdx())) {
                continue;
            }
            if (effCenter != null) {
                Integer root = resolveSecondDepthRoot(m.getDeptIdx(), parentOf, roots);
                if (root == null || !root.equals(effCenter)) {
                    continue;
                }
            }
            if (effSkill != null) {
                String rSkill = rec.getSkill() != null ? rec.getSkill().trim() : "";
                if (!effSkill.equals(rSkill)) {
                    continue;
                }
            }
            HourlyAgg agg = byHour.get(h);
            if (agg == null) {
                continue;
            }
            agg.eval++;
            if (isYes(rec.getSatisfiedYn())) {
                agg.sat++;
                if (isYes(rec.getFiveMajorCitiesYn())) {
                    agg.five++;
                }
                if (isYes(rec.getGen5060Yn())) {
                    agg.gen5060++;
                }
                if (isProblemResolvedYes(rec)) {
                    agg.prob++;
                }
            } else if (isNo(rec.getSatisfiedYn())) {
                agg.diss++;
            }
        }

        List<CsSatisfactionTodayHourlyResponse.HourSlot> hours = new ArrayList<>();
        for (int h = 9; h <= 18; h++) {
            if (h >= currentHour) {
                break;
            }
            HourlyAgg a = byHour.get(h);
            long n = a.eval;
            hours.add(CsSatisfactionTodayHourlyResponse.HourSlot.builder()
                    .hour(h)
                    .label(String.format("%02d:00", h))
                    .sampleCount((int) Math.min(n, Integer.MAX_VALUE))
                    .satisfiedPct(n > 0 ? round1(100.0 * a.sat / n) : null)
                    .dissatisfiedPct(n > 0 ? round1(100.0 * a.diss / n) : null)
                    .fiveMajorCitiesPct(n > 0 ? round1(100.0 * a.five / n) : null)
                    .gen5060Pct(n > 0 ? round1(100.0 * a.gen5060 / n) : null)
                    .problemResolvedPct(n > 0 ? round1(100.0 * a.prob / n) : null)
                    .build());
        }

        List<CsSatisfactionTodayHourlyResponse.CenterOption> centers = new ArrayList<>();
        centers.add(CsSatisfactionTodayHourlyResponse.CenterOption.builder()
                .id(0)
                .name("전체")
                .build());
        Map<Integer, String> rootNames = loadRootNames(roots);
        for (Integer rid : adminProperties.getSecondDepthDeptIds()) {
            if (rid == null) {
                continue;
            }
            centers.add(CsSatisfactionTodayHourlyResponse.CenterOption.builder()
                    .id(rid)
                    .name(rootNames.getOrDefault(rid, String.valueOf(rid)))
                    .build());
        }

        return CsSatisfactionTodayHourlyResponse.builder()
                .date(today.toString())
                .windowStart("09:00")
                .windowEnd("18:59")
                .zoneId(zone.getId())
                .appliedSecondDepthDeptId(effCenter)
                .appliedSkill(effSkill)
                .profileSuggestedCenterId(profile.centerId())
                .profileSuggestedSkill(profile.skill())
                .profileUnscoped(profile.unscoped())
                .centers(centers)
                .skillOptions(new ArrayList<>(EXCLUDABLE_SKILLS))
                .hours(hours)
                .build();
    }

    private AdminHourlyProfile resolveAdminHourlyProfile(String adminSkid) {
        if (adminSkid == null || adminSkid.isBlank()) {
            return new AdminHourlyProfile(null, null, true);
        }
        TbLmsMember m = memberRepository.findById(adminSkid.trim()).orElse(null);
        if (m == null || m.getDeptIdx() == null) {
            return new AdminHourlyProfile(null, null, true);
        }
        List<TbLmsDept> allDepts = deptRepository.findAllWithParentFetched();
        Map<Integer, Integer> parentOf = buildParentMap(allDepts);
        Set<Integer> roots = new HashSet<>(adminProperties.getSecondDepthDeptIds());
        Integer root = resolveSecondDepthRoot(m.getDeptIdx(), parentOf, roots);
        if (root == null || !roots.contains(root)) {
            return new AdminHourlyProfile(null, null, true);
        }
        Map<Integer, TbLmsDept> deptById = allDepts.stream()
                .collect(Collectors.toMap(TbLmsDept::getDeptId, d -> d, (a, b) -> a));
        String sk = resolveDeptSkill(m, deptById);
        return new AdminHourlyProfile(root, sk, false);
    }

    private record AdminHourlyProfile(Integer centerId, String skill, boolean unscoped) {}

    private static final class HourlyAgg {
        private long eval;
        private long sat;
        private long diss;
        private long five;
        private long gen5060;
        private long prob;
    }

    /**
     * AI 인사이트 프롬프트용 Good/Bad 멘트.
     * {@code tb_you_cs} 중 평가시간({@link TbCsSatisfactionRecord#getUseYn()})이 {@code Y}이고
     * 상담일시가 해당 연·월인 행만 풀로 두고, 그중 상담일시({@code evalDate})의 날짜가 가장 최근인 날에
     * 해당하는 행만 포함합니다. 상담일시가 없는 행만 있으면 풀 전체를 사용합니다.
     */
    public MemberCsInsightPromptMentsResponse getMemberInsightPromptMents(String skid, int year, int month) {
        if (skid == null || skid.isBlank()) {
            throw new IllegalArgumentException("skid가 필요합니다.");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month는 1~12입니다.");
        }
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        String normalizedSkid = skid.trim();

        List<TbCsSatisfactionRecord> pool = recordRepository.findByEvalDateBetweenAndSkidAndUseYn(
                atStartOfDay(from), atEndOfDay(to), normalizedSkid, "Y");

        if (pool.isEmpty()) {
            return MemberCsInsightPromptMentsResponse.builder()
                    .goodMents(List.of())
                    .badMents(List.of())
                    .latestConsultDate(null)
                    .build();
        }

        Optional<LocalDate> maxConsultDay = pool.stream()
                .map(TbCsSatisfactionRecord::getEvalDate)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .max(Comparator.naturalOrder());

        List<TbCsSatisfactionRecord> slice;
        String latestConsultDateStr = null;
        if (maxConsultDay.isPresent()) {
            LocalDate d = maxConsultDay.get();
            latestConsultDateStr = d.format(DateTimeFormatter.ISO_LOCAL_DATE);
            slice = pool.stream()
                    .filter(r -> r.getEvalDate() != null && r.getEvalDate().toLocalDate().equals(d))
                    .collect(Collectors.toList());
        } else {
            slice = pool;
        }

        return MemberCsInsightPromptMentsResponse.builder()
                .goodMents(collectDistinctComments(slice, true))
                .badMents(collectDistinctComments(slice, false))
                .latestConsultDate(latestConsultDateStr)
                .build();
    }

    private static List<String> collectDistinctComments(List<TbCsSatisfactionRecord> rows, boolean goodColumn) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (TbCsSatisfactionRecord r : rows) {
            String raw = goodColumn ? r.getGoodMent() : r.getBadMent();
            if (raw == null) {
                continue;
            }
            String t = raw.trim();
            if (t.isEmpty() || seen.contains(t)) {
                continue;
            }
            seen.add(t);
            out.add(t);
        }
        return out;
    }

    private MemberCsUnsatisfiedRecordItem toUnsatisfiedItem(TbCsSatisfactionRecord r) {
        return new MemberCsUnsatisfiedRecordItem(
                r.getId(),
                r.getEvalDate() != null ? r.getEvalDate().toLocalDate() : null,
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
                r.getProblemResolvedYn());
    }

    public CsSatisfactionSummaryResponse getSummary(
            Integer year, Integer month, Integer secondDepthDeptIdFilter, boolean rollingThroughYesterday) {
        validateSecondDepthFilter(secondDepthDeptIdFilter);
        CsSatPeriod ctx = resolveCsSatPeriod(year, month, rollingThroughYesterday);
        Double problemAnnualTarget = loadProblemResolvedAnnualTargetPercent(ctx.to().getYear());

        if (secondDepthDeptIdFilter != null && secondDepthDeptIdFilter == SECOND_DEPTH_UNMATCHED) {
            return CsSatisfactionSummaryResponse.builder()
                    .year(ctx.displayYear())
                    .statFrom(ctx.from().toString())
                    .statTo(ctx.to().toString())
                    .rollingThroughYesterday(ctx.rolling())
                    .problemResolvedAnnualTargetPercent(problemAnnualTarget)
                    .filterMeta(buildFilterMeta())
                    .rows(Collections.emptyList())
                    .build();
        }

        LocalDate from = ctx.from();
        LocalDate to = ctx.to();
        int y = ctx.displayYear();
        int selectedMonth = ctx.displayMonth();

        List<TbCsSatisfactionRecord> records = recordRepository.findByEvalDateBetween(
                atStartOfDay(from), atEndOfDay(to));
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
        LocalDate thisMonthFrom = from;
        LocalDate thisMonthTo = to;
        LocalDate thisMonthKey = firstDayOfMonth(y, selectedMonth);

        List<TbLmsDept> leafDepts = listLeafTeamsFromConfig(
                allDepts,
                secondDepthDeptIdFilter == null ? null : secondDepthDeptIdFilter);

        Map<Integer, Agg> aggByLeaf = new LinkedHashMap<>();
        for (TbLmsDept leaf : leafDepts) {
            aggByLeaf.put(leaf.getDeptId(), new Agg());
        }

        List<TbCsSatisfactionRecord> monthRecords = recordRepository.findByEvalDateBetween(
                atStartOfDay(thisMonthFrom), atEndOfDay(thisMonthTo));
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
            if (!isActiveUseYn(rec)) continue;
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
        Map<String, Double> rowSkillTargetCache = new HashMap<>();
        for (TbCsSatisfactionRecord rec : records) {
            if (!isActiveUseYn(rec)) continue;
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
                if (isYes(rec.getFiveMajorCitiesYn())) {
                    bucket.fiveMajor++;
                }
                if (isYes(rec.getGen5060Yn())) {
                    bucket.gen5060++;
                }
                if (isProblemResolvedYes(rec)) {
                    bucket.problemResolved++;
                }
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
            Double targetAvg = null;
            Double achievement = null;
            String rowSkillRaw = leaf.getSkill();
            String rowSkill = (rowSkillRaw != null && !rowSkillRaw.isBlank())
                    ? rowSkillRaw.trim()
                    : "—";
            if (!"—".equals(rowSkill)) {
                targetAvg = rowSkillTargetCache.computeIfAbsent(rowSkill, sk ->
                        skillTargetRepository.findByTargetDateAndSkillName(thisMonthKey, sk)
                                .map(t -> t.getTargetPercent().doubleValue())
                                .orElse(null));
            }
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
                String skill = resolveDeptSkill(mm, deptById);
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

            Double fivePct = a.eval == 0 ? null : round1(100.0 * a.fiveMajor / a.eval);
            Double genPct = a.eval == 0 ? null : round1(100.0 * a.gen5060 / a.eval);
            Double probPct = a.eval == 0 ? null : round1(100.0 * a.problemResolved / a.eval);
            Double probInv = problemInverseAchievementPct(probPct, problemAnnualTarget);

            rows.add(CsSatisfactionSummaryResponse.SecondDepthSatisfactionRow.builder()
                    .secondDepthDeptId(leaf.getDeptId())
                    .centerName(centerName)
                    .groupName(groupName)
                    .skill(rowSkill)
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
                    .fiveMajorCitiesCount(a.fiveMajor)
                    .gen5060Count(a.gen5060)
                    .problemResolvedCount(a.problemResolved)
                    .fiveMajorCitiesPct(fivePct)
                    .gen5060Pct(genPct)
                    .problemResolvedPct(probPct)
                    .problemResolvedInverseAchievementPct(probInv)
                    .build());
        }

        return CsSatisfactionSummaryResponse.builder()
                .year(y)
                .statFrom(from.toString())
                .statTo(to.toString())
                .rollingThroughYesterday(ctx.rolling())
                .problemResolvedAnnualTargetPercent(problemAnnualTarget)
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

        List<TbCsSatisfactionRecord> records = recordRepository.findByEvalDateBetween(
                atStartOfDay(from), atEndOfDay(to));
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
            if (!isActiveUseYn(rec)) continue;
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

        List<TbCsSatisfactionRecord> records = recordRepository.findByEvalDateBetween(
                atStartOfDay(from), atEndOfDay(to));
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
            if (!isActiveUseYn(rec)) continue;
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
    public CsSatisfactionAdminDashboardKpiResponse getAdminDashboardKpis(Integer year, Integer month) {
        LocalDate now = LocalDate.now();
        int monthY = year != null ? year : now.getYear();
        int monthM = month != null ? month : now.getMonthValue();
        LocalDate fromMonth = LocalDate.of(monthY, monthM, 1);
        LocalDate toMonth = fromMonth.withDayOfMonth(fromMonth.lengthOfMonth());
        LocalDate monthKey = firstDayOfMonth(monthY, monthM);

        List<TbCsSatisfactionRecord> recordsMonth = recordRepository.findByEvalDateBetween(
                atStartOfDay(fromMonth), atEndOfDay(toMonth));

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
            if (!isActiveUseYn(rec)) continue;
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
    public CsSatisfactionRankingResponse getRanking(Integer year, Integer month, int topN) {
        if (topN < 1) {
            topN = 3;
        }
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int selectedMonth = month != null ? month : now.getMonthValue();
        LocalDate from = LocalDate.of(y, selectedMonth, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());

        List<TbCsSatisfactionRecord> records = recordRepository.findByEvalDateBetween(
                atStartOfDay(from), atEndOfDay(to));
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
            if (!isActiveUseYn(rec)) continue;
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
                .year(y)
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
     * 만족도 요약 + 구성원(skid)별 건수. {@code rollingThroughYesterday=true} 이면 KST 「당월 1일~전일」(1일이면 전월)과
     * {@link #getSummary} 롤링 모드와 동일 구간. 그렇지 않으면 {@code month} 가 있으면 해당 월만, 없으면 {@code year} 년 1/1~12/31.
     * {@code secondDepthDeptId} 는 (1) {@code youpro.admin.second-depth-dept-ids} 에
     * 등록된 <em>센터</em> id 이거나,
     * (2) 연간 요약 표({@link #getSummary}) 각 행과 동일한 <em>리프 팀</em> dept id 여야 한다.
     */
    public CsSatisfactionCenterMonthDetailResponse getCenterMonthDetail(
            int secondDepthDeptId, Integer year, Integer month, boolean rollingThroughYesterday) {
        if (secondDepthDeptId == SECOND_DEPTH_UNMATCHED) {
            throw new IllegalArgumentException("상위 센터만 조회할 수 있습니다.");
        }

        List<TbLmsDept> allDepts = deptRepository.findAllWithParentFetched();
        Map<Integer, TbLmsDept> deptById = allDepts.stream()
                .collect(Collectors.toMap(TbLmsDept::getDeptId, d -> d, (a, b) -> a));
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

        CenterDetailPeriod period = resolveCenterDetailPeriod(year, month, rollingThroughYesterday);
        LocalDate from = period.from();
        LocalDate to = period.to();
        int y = period.displayYear();
        int mo = period.displayMonth();
        ZoneId z = ZoneId.of("Asia/Seoul");
        LocalDate todayKst = ZonedDateTime.now(z).toLocalDate();
        final LocalDate targetMonthKey;
        if (period.rolling()) {
            targetMonthKey = firstDayOfMonth(y, mo);
        } else if (mo > 0) {
            targetMonthKey = firstDayOfMonth(y, mo);
        } else {
            targetMonthKey = firstDayOfMonth(todayKst.getYear(), todayKst.getMonthValue());
        }
        Double problemAnnualTarget = loadProblemResolvedAnnualTargetPercent(to.getYear());
        Map<String, Double> monthSkillTargetCache = new HashMap<>();

        List<TbCsSatisfactionRecord> records = recordRepository.findByEvalDateBetween(
                atStartOfDay(from), atEndOfDay(to));
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
            if (!isActiveUseYn(rec)) continue;
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
            String skill = resolveDeptSkill(mem, deptById);
            Double targetPct = skill == null ? null : monthSkillTargetCache.computeIfAbsent(skill, sk ->
                    skillTargetRepository.findByTargetDateAndSkillName(targetMonthKey, sk)
                            .map(t -> t.getTargetPercent().doubleValue())
                            .orElse(null));
            Double rate = a.eval == 0 ? null : round1(100.0 * a.sat / a.eval);
            Double fivePctM = a.eval == 0 ? null : round1(100.0 * a.fiveMajor / a.eval);
            Double genPctM = a.eval == 0 ? null : round1(100.0 * a.gen5060 / a.eval);
            Double probPctM = a.eval == 0 ? null : round1(100.0 * a.problemResolved / a.eval);
            Double probInvM = problemInverseAchievementPct(probPctM, problemAnnualTarget);
            memberRows.add(CsSatisfactionCenterMonthDetailResponse.MemberMonthRow.builder()
                    .skid(skid)
                    .mbName(name)
                    .deptName(dname)
                    .targetPercent(targetPct)
                    .evalCount(a.eval)
                    .satisfiedCount(a.sat)
                    .dissatisfiedCount(a.diss)
                    .satisfactionRate(rate)
                    .gen5060Count(a.gen5060)
                    .fiveMajorCitiesCount(a.fiveMajor)
                    .problemResolvedCount(a.problemResolved)
                    .fiveMajorCitiesPct(fivePctM)
                    .gen5060Pct(genPctM)
                    .problemResolvedPct(probPctM)
                    .problemResolvedInverseAchievementPct(probInvM)
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
            String skill = resolveDeptSkill(mem, deptById);
            Double targetPct = skill == null ? null : monthSkillTargetCache.computeIfAbsent(skill, sk ->
                    skillTargetRepository.findByTargetDateAndSkillName(targetMonthKey, sk)
                            .map(t -> t.getTargetPercent().doubleValue())
                            .orElse(null));
            Double rate = a.eval == 0 ? null : round1(100.0 * a.sat / a.eval);
            Double fivePctM = a.eval == 0 ? null : round1(100.0 * a.fiveMajor / a.eval);
            Double genPctM = a.eval == 0 ? null : round1(100.0 * a.gen5060 / a.eval);
            Double probPctM = a.eval == 0 ? null : round1(100.0 * a.problemResolved / a.eval);
            Double probInvM = problemInverseAchievementPct(probPctM, problemAnnualTarget);
            memberRows.add(CsSatisfactionCenterMonthDetailResponse.MemberMonthRow.builder()
                    .skid(skid)
                    .mbName(name)
                    .deptName(dname)
                    .targetPercent(targetPct)
                    .evalCount(a.eval)
                    .satisfiedCount(a.sat)
                    .dissatisfiedCount(a.diss)
                    .satisfactionRate(rate)
                    .gen5060Count(a.gen5060)
                    .fiveMajorCitiesCount(a.fiveMajor)
                    .problemResolvedCount(a.problemResolved)
                    .fiveMajorCitiesPct(fivePctM)
                    .gen5060Pct(genPctM)
                    .problemResolvedPct(probPctM)
                    .problemResolvedInverseAchievementPct(probInvM)
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
        Double fivePctT = total.eval == 0 ? null : round1(100.0 * total.fiveMajor / total.eval);
        Double genPctT = total.eval == 0 ? null : round1(100.0 * total.gen5060 / total.eval);
        Double probPctT = total.eval == 0 ? null : round1(100.0 * total.problemResolved / total.eval);
        Double probInvT = problemInverseAchievementPct(probPctT, problemAnnualTarget);

        return CsSatisfactionCenterMonthDetailResponse.builder()
                .year(y)
                .month(mo)
                .secondDepthDeptId(secondDepthDeptId)
                .secondDepthName(centerName)
                .statFrom(from.toString())
                .statTo(to.toString())
                .rollingThroughYesterday(period.rolling())
                .evalCount(total.eval)
                .satisfiedCount(total.sat)
                .dissatisfiedCount(total.diss)
                .satisfactionRate(totalRate)
                .gen5060Count(total.gen5060)
                .fiveMajorCitiesCount(total.fiveMajor)
                .problemResolvedCount(total.problemResolved)
                .fiveMajorCitiesPct(fivePctT)
                .gen5060Pct(genPctT)
                .problemResolvedPct(probPctT)
                .problemResolvedInverseAchievementPct(probInvT)
                .problemResolvedAnnualTargetPercent(problemAnnualTarget)
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

    private static boolean isActiveUseYn(TbCsSatisfactionRecord rec) {
        return rec != null && isYes(rec.getUseYn());
    }

    /**
     * 문제해결 여부는 레코드 {@code 문제해결} 컬럼의 Y 플래그로 집계합니다.
     * (모든 중점지표는 만족여부=Y 인 건에서만 추가 카운트)
     */
    private static boolean isProblemResolvedYes(TbCsSatisfactionRecord rec) {
        return rec != null && isYes(rec.getProblemResolvedYn());
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

    private static LocalDateTime atStartOfDay(LocalDate day) {
        return day.atStartOfDay();
    }

    private static LocalDateTime atEndOfDay(LocalDate day) {
        return day.plusDays(1).atStartOfDay().minusNanos(1);
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

    /** KST 기준 요약 집계: rolling 이면 당월 1일~전일(당일이 1일이면 전월 전체), 아니면 지정 월 전체. */
    private record CsSatPeriod(LocalDate from, LocalDate to, int displayYear, int displayMonth, boolean rolling) {}

    private static CsSatPeriod resolveCsSatPeriod(Integer year, Integer month, boolean rollingThroughYesterday) {
        ZoneId z = ZoneId.of("Asia/Seoul");
        LocalDate today = ZonedDateTime.now(z).toLocalDate();
        if (rollingThroughYesterday) {
            if (today.getDayOfMonth() == 1) {
                LocalDate prev = today.minusMonths(1);
                int ly = prev.getYear();
                int lm = prev.getMonthValue();
                LocalDate from = prev.withDayOfMonth(1);
                LocalDate to = prev.withDayOfMonth(prev.lengthOfMonth());
                return new CsSatPeriod(from, to, ly, lm, true);
            }
            int ly = today.getYear();
            int lm = today.getMonthValue();
            LocalDate from = today.withDayOfMonth(1);
            LocalDate to = today.minusDays(1);
            return new CsSatPeriod(from, to, ly, lm, true);
        }
        int y = year != null ? year : today.getYear();
        int m = month != null ? month : today.getMonthValue();
        LocalDate from = LocalDate.of(y, m, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        return new CsSatPeriod(from, to, y, m, false);
    }

    /** 구성원 상세: rolling / 단일 월 / 연 전체. */
    private record CenterDetailPeriod(
            LocalDate from, LocalDate to, int displayYear, int displayMonth, boolean rolling) {}

    private static CenterDetailPeriod resolveCenterDetailPeriod(
            Integer year, Integer month, boolean rollingThroughYesterday) {
        if (rollingThroughYesterday) {
            CsSatPeriod ctx = resolveCsSatPeriod(null, null, true);
            return new CenterDetailPeriod(
                    ctx.from(), ctx.to(), ctx.displayYear(), ctx.displayMonth(), true);
        }
        ZoneId z = ZoneId.of("Asia/Seoul");
        LocalDate today = ZonedDateTime.now(z).toLocalDate();
        int y = year != null ? year : today.getYear();
        if (month != null) {
            LocalDate from = LocalDate.of(y, month, 1);
            LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
            return new CenterDetailPeriod(from, to, y, month, false);
        }
        LocalDate from = LocalDate.of(y, 1, 1);
        LocalDate to = LocalDate.of(y, 12, 31);
        return new CenterDetailPeriod(from, to, y, 0, false);
    }

    private Double loadProblemResolvedAnnualTargetPercent(int targetYear) {
        return annualTargetRepository
                .findByTargetYearAndTaskCode(targetYear, "PROBLEM_RESOLVED")
                .map(TbCsSatisfactionAnnualTarget::getTargetPercent)
                .map(BigDecimal::doubleValue)
                .orElse(null);
    }

    /**
     * 문제해결률이 낮을수록 불리한 지표에 대해, 연간 목표가 허용하는 미달성 비중(targetGap) 대비
     * 실제 미달성 비중(actualGap)을 역산한 달성률(%). 상한 100.
     */
    private Double problemInverseAchievementPct(Double actualResolvedPct, Double targetResolvedPct) {
        if (actualResolvedPct == null || targetResolvedPct == null) {
            return null;
        }
        double t = targetResolvedPct;
        if (t <= 0 || t >= 100) {
            return null;
        }
        double a = actualResolvedPct;
        double targetGap = 100.0 - t;
        double actualGap = 100.0 - a;
        if (actualGap <= 0) {
            return 100.0;
        }
        if (targetGap <= 0) {
            return null;
        }
        double raw = 100.0 * targetGap / actualGap;
        return round1(Math.min(100.0, raw));
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
