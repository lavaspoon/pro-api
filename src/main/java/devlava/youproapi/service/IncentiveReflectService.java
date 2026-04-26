package devlava.youproapi.service;

import devlava.youproapi.domain.TbCsSatisfactionRecord;
import devlava.youproapi.domain.TbCsSatisfactionSkillTarget;
import devlava.youproapi.domain.TbLmsDept;
import devlava.youproapi.domain.TbLmsMember;
import devlava.youproapi.domain.TbYouIncentiveReflect;
import devlava.youproapi.repository.TbCsSatisfactionRecordRepository;
import devlava.youproapi.repository.TbCsSatisfactionSkillTargetRepository;
import devlava.youproapi.repository.TbLmsDeptRepository;
import devlava.youproapi.repository.TbLmsMemberRepository;
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
 * 스케줄러({@link IncentiveReflectScheduler})가 2~10월 1일에 전달의 데이터를 처리한다.
 *
 * <h3>반영 기준</h3>
 * <ol>
 *   <li>구성원 소속 부서({@code TbLmsDept.skill})의 해당 월 CS 만족도 목표({@code TbCsSatisfactionSkillTarget})를
 *       달성한 경우 → 해당 월 선정 건수를 누적 실적에 반영.</li>
 *   <li>목표 미달성인 경우 → 해당 월 선정 건수를 반영하지 않음.</li>
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
    private final TbYouIncentiveReflectRepository    reflectRepository;

    // ────────────────────────────────────────────────────────────────
    // 외부 조회 API (MemberService에서 사용)
    // ────────────────────────────────────────────────────────────────

    /** 해당 연도 누적 반영 건수 합계 */
    public long sumReflectedForYear(String skid, int year) {
        return reflectRepository.sumReflectedCountForYear(skid, year);
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
        List<String> skids = targets.stream().map(TbLmsMember::getSkid).collect(Collectors.toList());

        // ── 2. 부서 정보 일괄 로드 ────────────────────────────────────
        List<TbLmsDept> allDepts = deptRepository.findAll();
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

        // ── (g) 등급 및 이달 지급액 산출 ─────────────────────────────
        int monthlyPayoutWon = calculateMonthlyPayout(cumulativeCount);

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
     * 누적 건수 기준 이달 지급 예정 금액을 반환한다.
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
