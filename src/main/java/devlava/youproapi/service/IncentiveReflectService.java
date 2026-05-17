package devlava.youproapi.service;

import devlava.youproapi.domain.TbCsSatisfactionRecord;
import devlava.youproapi.domain.TbLmsDept;
import devlava.youproapi.domain.TbLmsMember;
import devlava.youproapi.config.YouproAdminProperties;
import devlava.youproapi.domain.TbYouIncentiveMonthStat;
import devlava.youproapi.domain.TbYouIncentiveReflect;
import devlava.youproapi.dto.MemberHomeResponse;
import devlava.youproapi.support.AdminDeptScope;
import devlava.youproapi.repository.TbCsSatisfactionRecordRepository;
import devlava.youproapi.repository.TbCsSatisfactionSkillTargetRepository;
import devlava.youproapi.repository.TbLmsDeptRepository;
import devlava.youproapi.repository.TbLmsMemberRepository;
import devlava.youproapi.repository.TbYouIncentiveMonthStatRepository;
import devlava.youproapi.repository.TbYouIncentiveReflectRepository;
import devlava.youproapi.repository.TbYouProCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * YOU PRO 인센티브 반영 서비스.
 *
 * <p>이벤트 기간: 매년 1~9월.
 * 스케줄러({@link IncentiveReflectScheduler})가 2~10월 매달 1일 18시에 전월 데이터를 처리한다.
 *
 * <h3>반영 기준</h3>
 * <ol>
 *   <li>구성원 소속 부서({@code TbLmsDept.skill})의 해당 월 CS 만족도 목표({@code TbCsSatisfactionSkillTarget})를
 *       달성한 경우 → 해당 월 선정 건수를 누적 실적에 반영.</li>
 *   <li>목표 미달성인 경우 → 해당 월 선정 건수를 반영하지 않음, 해당 월 지급액 0.</li>
 *   <li>목표 달성이나 선정 건이 0건인 경우 → 누적 증가 없음·해당 월 지급액 0 (등급 유지).</li>
 *   <li>스킬 또는 목표가 미설정된 경우 → 게이트 없이 반영(선정 건수 그대로 반영).</li>
 *   <li>해당 월 CS 접수 건수가 0인 경우 → 목표 미달성으로 간주.</li>
 * </ol>
 *
 * <h3>등급 기준 (연간 누적 건수)</h3>
 * <ul>
 *   <li>YOU 망주    : 1~9건   → 3만원/월</li>
 *   <li>YOU 플레이어 : 10~18건 → 5만원/월</li>
 *   <li>YOU 토피아  : 19건~   → 7만원/월</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IncentiveReflectService {

    /** 이벤트 진행 월 범위 */
    private static final int EVENT_MONTH_START = 1;
    private static final int EVENT_MONTH_END   = 9;

    private final TbLmsMemberRepository              memberRepository;
    private final TbLmsDeptRepository                deptRepository;
    private final TbCsSatisfactionRecordRepository   recordRepository;
    private final TbCsSatisfactionSkillTargetRepository skillTargetRepository;
    private final TbYouProCaseRepository             caseRepository;
    private final TbYouIncentiveReflectRepository     reflectRepository;
    private final TbYouIncentiveMonthStatRepository   monthStatRepository;
    private final YouproAdminProperties               adminProperties;

    // ────────────────────────────────────────────────────────────────
    // 외부 조회 API (MemberService에서 사용)
    // ────────────────────────────────────────────────────────────────

    /** 해당 연도 누적 반영 건수 합계 */
    public long sumReflectedForYear(String skid, int year) {
        return reflectRepository.sumReflectedCountForYear(skid, year);
    }

    /**
     * 해당 연도 가장 최근 반영 월({@code reflect_month} 최대) 행의 {@code cumulative_count}.
     * 행이 없으면 0. (월별 {@code reflected_count} 의 연간 합과 일치하도록 스케줄러가 유지한다.)
     */
    public long latestCumulativeCountForYear(String skid, int year) {
        return reflectRepository
                .findFirstBySkidAndReflectYearOrderByReflectMonthDesc(skid, year)
                .map(TbYouIncentiveReflect::getCumulativeCount)
                .map(Integer::longValue)
                .orElse(0L);
    }

    /**
     * 해당 연도 1~9월 각각에 대해 {@code tb_you_incentive_reflect} 행이 있으면
     * {@code cs_target_met}, {@code selected_count_raw} 를 채우고, 스케줄 미처리 월은 null.
     */
    public List<MemberHomeResponse.ReflectMonthRow> reflectMonthsJanSep(String skid, int year) {
        List<TbYouIncentiveReflect> rows =
                reflectRepository.findBySkidAndReflectYearOrderByReflectMonth(skid, year);
        Map<Integer, TbYouIncentiveReflect> byMonth = rows.stream()
                .collect(Collectors.toMap(TbYouIncentiveReflect::getReflectMonth, r -> r, (a, b) -> b));
        List<MemberHomeResponse.ReflectMonthRow> out = new ArrayList<>(9);
        for (int m = 1; m <= 9; m++) {
            TbYouIncentiveReflect r = byMonth.get(m);
            if (r == null) {
                out.add(MemberHomeResponse.ReflectMonthRow.builder()
                        .month(m)
                        .csTargetMet(null)
                        .selectedCountRaw(null)
                        .build());
            } else {
                boolean met = "Y".equalsIgnoreCase(r.getCsTargetMet());
                out.add(MemberHomeResponse.ReflectMonthRow.builder()
                        .month(m)
                        .csTargetMet(met)
                        .selectedCountRaw(r.getSelectedCountRaw())
                        .build());
            }
        }
        return out;
    }

    /** 해당 연도 지급 예정 금액 합계 */
    public long sumPayoutForYear(String skid, int year) {
        return reflectRepository.sumMonthlyPayoutWonForYear(skid, year);
    }

    /**
     * 해당 연도에서 반영 건수가 가장 많은 구성원의 누적 건수
     * (프로그레스 바 '1위' 마커에 사용).
     */
    public long maxReflectedForYear(int year) {
        List<Object[]> rows = reflectRepository.sumReflectedCountGroupBySkidForYear(year);
        return rows.stream()
                .mapToLong(r -> ((Number) r[1]).longValue())
                .max()
                .orElse(0L);
    }

    /**
     * 해당 연도 반영 건수 기준 구성원별 누적 합계 맵 (skid → 건수).
     */
    public Map<String, Long> reflectedCountMapForYear(int year) {
        List<Object[]> rows = reflectRepository.sumReflectedCountGroupBySkidForYear(year);
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    /**
     * 스코프 구성원별 해당 연도 최신 반영 월 {@code cumulative_count}.
     * ({@link devlava.youproapi.service.AdminService} 랭킹과 동일 기준)
     */
    public Map<String, Long> latestCumulativeCountMapForYear(int year, Collection<String> skids) {
        if (skids == null || skids.isEmpty()) {
            return Map.of();
        }
        List<TbYouIncentiveReflect> rows =
                reflectRepository.findByReflectYearAndSkidIn(year, skids);
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

    /** 만족도 달성·반영된 월(1~9) 집합 */
    public Set<Integer> certifiedReflectMonthsForYear(String skid, int year) {
        return reflectRepository.findBySkidAndReflectYearOrderByReflectMonth(skid, year).stream()
                .filter(r -> "Y".equalsIgnoreCase(r.getCsTargetMet()))
                .filter(r -> r.getReflectedCount() != null && r.getReflectedCount() > 0)
                .map(TbYouIncentiveReflect::getReflectMonth)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // ────────────────────────────────────────────────────────────────
    // 핵심: 월 처리
    // ────────────────────────────────────────────────────────────────

    /**
     * 특정 연·월의 인센티브 반영 처리.
     *
     * <p>youYn='Y' 인 전체 구성원에 대해 CS 만족도 달성 여부를 확인하고
     * {@code tb_you_incentive_reflect} 테이블에 Upsert 한다.
     *
     * @param year  처리 연도
     * @param month 처리 월 (1~9)
     */
    @Transactional
    public void processMonth(int year, int month) {
        if (month < EVENT_MONTH_START || month > EVENT_MONTH_END) {
            log.warn("[IncentiveReflect] 이벤트 기간({}~{}월) 외 요청 무시 — {}년 {}월",
                    EVENT_MONTH_START, EVENT_MONTH_END, year, month);
            return;
        }

        log.info("[IncentiveReflect] 처리 시작 — {}년 {}월", year, month);

        LocalDate from       = LocalDate.of(year, month, 1);
        LocalDate to         = from.withDayOfMonth(from.lengthOfMonth());
        LocalDate monthKey   = from;
        String    yearStr    = String.valueOf(year);
        String    monthStr   = String.format("%02d", month);

        // ── 1. 평가 대상 구성원 전체 로드 ─────────────────────────────
        List<TbLmsMember> targets = memberRepository.findByUseYn("Y").stream()
                .filter(m -> "Y".equalsIgnoreCase(m.getYouYn()))
                .collect(Collectors.toList());
        if (targets.isEmpty()) {
            log.info("[IncentiveReflect] 처리 대상 구성원 없음");
            return;
        }

        // ── 1b. 부서 트리(센터별 평가대상 스냅샷용) ───────────────────────
        List<TbLmsDept> allDepts = deptRepository.findAllWithParentFetched();
        saveEvalTargetSnapshotsPerCenter(year, month, targets, allDepts);

        List<String> skids = targets.stream().map(TbLmsMember::getSkid).collect(Collectors.toList());

        // ── 2. 부서 정보(스킬 해석) — 이미 로드됨 ────────────────────────
        Map<Integer, TbLmsDept> deptById = allDepts.stream()
                .collect(Collectors.toMap(TbLmsDept::getDeptId, d -> d, (a, b) -> a));

        // ── 3. 해당 월 CS 레코드 일괄 로드 (N+1 방지) ─────────────────
        List<TbCsSatisfactionRecord> csRecords = recordRepository.findByEvalDateBetweenAndSkidIn(
                atStartOfDay(from), atEndOfDay(to), skids);
        Map<String, List<TbCsSatisfactionRecord>> csRecordsBySkid = csRecords.stream()
                .collect(Collectors.groupingBy(TbCsSatisfactionRecord::getSkid));

        // ── 4. 해당 월 선정 건수 일괄 로드 ────────────────────────────
        List<Object[]> rawCounts = caseRepository.countSelectedBySkidsAndYearMonthGroupBySkid(
                skids, yearStr, monthStr);
        Map<String, Long> selectedCountMap = new HashMap<>();
        for (Object[] row : rawCounts) {
            selectedCountMap.put((String) row[0], ((Number) row[1]).longValue());
        }

        // ── 5. 스킬 목표 캐시 ─────────────────────────────────────────
        Map<String, Double> skillTargetCache = new HashMap<>();

        // ── 6. 구성원별 처리 ──────────────────────────────────────────
        int processed = 0, skipped = 0;
        for (TbLmsMember member : targets) {
            try {
                processMember(member, year, month, monthKey, deptById,
                        csRecordsBySkid, selectedCountMap, skillTargetCache);
                processed++;
            } catch (Exception e) {
                log.error("[IncentiveReflect] 구성원 처리 실패 skid={}: {}", member.getSkid(), e.getMessage(), e);
                skipped++;
            }
        }

        log.info("[IncentiveReflect] 완료 — {}년 {}월 | 처리={}, 오류={}", year, month, processed, skipped);
    }

    /**
     * 설정된 각 2depth 센터 서브트리별로, 해당 시점 YOU 평가대상자 인원을 {@code tb_you_incentive_month_stat} 에 저장한다.
     */
    private void saveEvalTargetSnapshotsPerCenter(
            int year, int month, List<TbLmsMember> targets, List<TbLmsDept> allDepts) {
        for (Integer rootId : adminProperties.getSecondDepthDeptIds()) {
            if (rootId == null) {
                continue;
            }
            Set<Integer> subtree = AdminDeptScope.collectSubtreeDeptIds(allDepts, List.of(rootId));
            int count = (int) targets.stream()
                    .filter(m -> m.getDeptIdx() != null && subtree.contains(m.getDeptIdx()))
                    .count();
            saveEvalTargetSnapshot(year, month, rootId, count);
        }
    }

    private void saveEvalTargetSnapshot(int year, int month, int secondDepthDeptId, int evalTargetCount) {
        TbYouIncentiveMonthStat row = TbYouIncentiveMonthStat.builder()
                .reflectYear(year)
                .reflectMonth(month)
                .secondDepthDeptId(secondDepthDeptId)
                .evalTargetCount(evalTargetCount)
                .processedAt(Instant.now())
                .build();
        monthStatRepository.save(row);
        log.info("[IncentiveReflect] 평가대상자 스냅샷 — {}년 {}월 | 센터(dept_id={}) | {}명",
                year, month, secondDepthDeptId, evalTargetCount);
    }

    // ────────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ────────────────────────────────────────────────────────────────

    private void processMember(
            TbLmsMember member,
            int year, int month,
            LocalDate monthKey,
            Map<Integer, TbLmsDept> deptById,
            Map<String, List<TbCsSatisfactionRecord>> csRecordsBySkid,
            Map<String, Long> selectedCountMap,
            Map<String, Double> skillTargetCache) {

        String skid = member.getSkid();

        // ── (a) 부서 스킬 확인 ────────────────────────────────────────
        String skill = resolveDeptSkill(member, deptById);

        // ── (b) CS 만족도 목표 조회 ──────────────────────────────────
        Double targetPct = null;
        if (skill != null) {
            targetPct = skillTargetCache.computeIfAbsent(skill, sk ->
                    skillTargetRepository.findByTargetDateAndSkillName(monthKey, sk)
                            .map(t -> t.getTargetPercent().doubleValue())
                            .orElse(null));
        }

        // ── (c) CS 만족도 실적 계산 ─────────────────────────────────
        List<TbCsSatisfactionRecord> records = csRecordsBySkid.getOrDefault(skid, List.of());
        long received  = records.size();
        long satisfied = records.stream()
                .filter(r -> "Y".equalsIgnoreCase(r.getSatisfiedYn()))
                .count();

        boolean csTargetMet = determineCsTargetMet(received, satisfied, targetPct);

        // ── (d) 해당 월 선정 건수 ───────────────────────────────────
        int selectedCountRaw = selectedCountMap.getOrDefault(skid, 0L).intValue();

        // ── (e) 반영 건수 ─────────────────────────────────────────────
        int reflectedCount = csTargetMet ? selectedCountRaw : 0;

        // ── (f) 이달 이전까지의 누적 반영 건수 ──────────────────────
        int prevCumulative = reflectRepository.sumReflectedCountBeforeMonth(skid, year, month);
        int cumulativeCount = prevCumulative + reflectedCount;

        // ── (g) 이달 지급액 — 반영 건수가 1건 이상일 때만 등급 단가 적용 (달성+선정 0건이면 0원)
        int monthlyPayoutWon = reflectedCount > 0 ? calculateMonthlyPayout(cumulativeCount) : 0;

        // ── (h) Upsert ─────────────────────────────────────────────
        TbYouIncentiveReflect reflect = reflectRepository
                .findBySkidAndReflectYearAndReflectMonth(skid, year, month)
                .orElseGet(TbYouIncentiveReflect::new);

        reflect.setSkid(skid);
        reflect.setReflectYear(year);
        reflect.setReflectMonth(month);
        reflect.setCsTargetMet(csTargetMet ? "Y" : "N");
        reflect.setSelectedCountRaw(selectedCountRaw);
        reflect.setReflectedCount(reflectedCount);
        reflect.setCumulativeCount(cumulativeCount);
        reflect.setMonthlyPayoutWon(monthlyPayoutWon);
        reflect.setProcessedAt(Instant.now());

        reflectRepository.save(reflect);

        log.debug("[IncentiveReflect] skid={} {}년{}월 csTargetMet={} raw={} reflected={} cumulative={} payout={}",
                skid, year, month, csTargetMet, selectedCountRaw, reflectedCount, cumulativeCount, monthlyPayoutWon);
    }

    /**
     * CS 만족도 목표 달성 여부를 판단한다.
     *
     * <ul>
     *   <li>스킬 또는 목표 미설정({@code targetPct == null}) → 게이트 없이 달성으로 간주.</li>
     *   <li>CS 접수 건수 0 → 달성 불가.</li>
     *   <li>그 외 → 실제 만족도% >= 목표% 여부.</li>
     * </ul>
     */
    private static boolean determineCsTargetMet(long received, long satisfied, Double targetPct) {
        if (targetPct == null || targetPct <= 0) {
            // 목표 미설정 → 게이트 없이 반영
            return true;
        }
        if (received <= 0) {
            return false;
        }
        double actualPct = 100.0 * satisfied / received;
        return actualPct >= targetPct;
    }

    /**
     * 누적 인증 건수 기준 등급 단가(해당 월 1회분).
     * 월별 행에서는 {@code reflectedCount > 0} 일 때만 이 값을 저장한다.
     *
     * <ul>
     *   <li>1~9건  (YOU 망주)    → 30,000원</li>
     *   <li>10~18건(YOU 플레이어) → 50,000원</li>
     *   <li>19건~  (YOU 토피아)  → 70,000원</li>
     *   <li>0건    (등급 없음)   → 0원</li>
     * </ul>
     */
    public static int calculateMonthlyPayout(int cumulativeCount) {
        if (cumulativeCount >= 19) return 70_000;
        if (cumulativeCount >= 10) return 50_000;
        if (cumulativeCount >= 1)  return 30_000;
        return 0;
    }

    /** 누적 인증 건수 기준 YOU PRO 등급 표시명. 0건이면 {@code null}. */
    public static String tierDisplayName(long cumulativeCount) {
        if (cumulativeCount >= 19) return "YOU 토피아";
        if (cumulativeCount >= 10) return "YOU 플레이어";
        if (cumulativeCount >= 1) return "YOU 망주";
        return null;
    }

    // ────────────────────────────────────────────────────────────────
    // 유틸
    // ────────────────────────────────────────────────────────────────

    private static String resolveDeptSkill(TbLmsMember member, Map<Integer, TbLmsDept> deptById) {
        if (member == null || member.getDeptIdx() == null) {
            return null;
        }
        TbLmsDept dept = deptById.get(member.getDeptIdx());
        if (dept == null || dept.getSkill() == null || dept.getSkill().isBlank()) {
            return null;
        }
        return dept.getSkill().trim();
    }

    private static LocalDateTime atStartOfDay(LocalDate day) {
        return day.atStartOfDay();
    }

    private static LocalDateTime atEndOfDay(LocalDate day) {
        return day.plusDays(1).atStartOfDay().minusNanos(1);
    }
}
