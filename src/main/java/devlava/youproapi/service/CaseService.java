package devlava.youproapi.service;

import devlava.youproapi.domain.TbLmsMember;
import devlava.youproapi.domain.TbYouProCase;
import devlava.youproapi.dto.AdminScopedCaseStats;
import devlava.youproapi.dto.CaseJudgeRequest;
import devlava.youproapi.dto.CaseResponse;
import devlava.youproapi.dto.CaseSubmitRequest;
import devlava.youproapi.dto.SttResultDto;
import devlava.youproapi.repository.TbLmsMemberRepository;
import devlava.youproapi.repository.TbYouProCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CaseService {

    private static final int MONTHLY_LIMIT = 3;

    private final TbYouProCaseRepository caseRepository;
    private final TbLmsMemberRepository memberRepository;
    private final SttService sttService;

    // ─── 구성원 ────────────────────────────────────────────────────────────

    /** 내 사례 목록 (최신순) */
    public List<CaseResponse> getMyCases(String skid) {
        TbLmsMember member = findMember(skid);
        return caseRepository.findBySkidOrderBySubmittedAtDesc(skid).stream()
                .map(c -> CaseResponse.fromSimple(c, member.getMbName(), member.getDeptName()))
                .collect(Collectors.toList());
    }

    /** 사례 상세 (STT 대화 포함) */
    public CaseResponse getCaseDetail(Long caseId) {
        TbYouProCase c = findCase(caseId);
        TbLmsMember member = findMember(c.getSkid());

        SttResultDto stt = sttService.findByCaseInfo(c.getCallDate(), c.getSkid());
        String callDuration = stt.isFound() ? stt.getCallDuration() : null;
        String fullTranscript = stt.isFound() ? stt.getFullTranscript() : null;

        return CaseResponse.from(c, member.getMbName(), member.getDeptName(),
                fullTranscript, callDuration);
    }

    /** 우수사례 접수 */
    @Transactional
    public CaseResponse submitCase(CaseSubmitRequest req) {
        TbLmsMember member = findMember(req.getSkid());

        TbYouProCase saved = caseRepository.save(TbYouProCase.builder()
                .skid(req.getSkid())
                .title(req.getTitle())
                .description(req.getDescription())
                .submittedAt(LocalDateTime.now())
                .status("pending")
                .callDate(req.getCallDate())
                .customerType(null)
                .build());

        return CaseResponse.fromSimple(saved, member.getMbName(), member.getDeptName());
    }

    // ─── 관리자 ────────────────────────────────────────────────────────────

    /** 검토 대기 전체 사례 */
    public List<CaseResponse> getPendingCases() {
        return caseRepository.findByStatusOrderBySubmittedAtAsc("pending").stream()
                .map(c -> {
                    TbLmsMember m = findMemberSafe(c.getSkid());
                    return CaseResponse.fromSimple(c,
                            m != null ? m.getMbName() : c.getSkid(),
                            m != null ? m.getDeptName() : "");
                })
                .collect(Collectors.toList());
    }

    /** 검토 대기 사례 — 구성원 SKID 집합으로 제한 (관리자 조직 스코프) */
    public List<CaseResponse> getPendingCasesForSkids(Collection<String> skids) {
        if (skids == null || skids.isEmpty()) {
            return List.of();
        }
        return caseRepository.findByStatusAndSkidInOrderBySubmittedAtAsc("pending", skids).stream()
                .map(c -> {
                    TbLmsMember m = findMemberSafe(c.getSkid());
                    return CaseResponse.fromSimple(c,
                            m != null ? m.getMbName() : c.getSkid(),
                            m != null ? m.getDeptName() : "");
                })
                .collect(Collectors.toList());
    }

    /** 관리자 사례 조회 (STT 포함) */
    public CaseResponse getCaseForReview(Long caseId) {
        return getCaseDetail(caseId);
    }

    /** 사례 판정 */
    @Transactional
    public CaseResponse judgeCase(Long caseId, CaseJudgeRequest req) {
        TbYouProCase c = findCase(caseId);
        TbLmsMember member = findMember(c.getSkid());

        if ("selected".equals(req.getDecision())) {
            LocalDateTime now = LocalDateTime.now();
            long monthlyCount = caseRepository.countSelectedBySkidAndYearMonth(
                    c.getSkid(), now.getYear(), now.getMonthValue());
            if (monthlyCount >= MONTHLY_LIMIT) {
                throw new IllegalStateException(
                        "해당 구성원은 이번 달 선정 한도(" + MONTHLY_LIMIT + "회)에 도달했습니다.");
            }
        }

        c.judge(req.getDecision(), req.getReason(), req.getAdminSkid(),
                req.getAiKeyPhrase(), req.getAiKeyPoint());
        TbYouProCase updated = caseRepository.save(c);

        return getCaseDetail(caseId);
    }

    // ─── 통계 헬퍼 ─────────────────────────────────────────────────────────

    public long countSelectedBySkidAndYear(String skid, int year) {
        return caseRepository.countSelectedBySkidAndYear(skid, year);
    }

    public long countSelectedBySkidAndYearMonth(String skid, int year, int month) {
        return caseRepository.countSelectedBySkidAndYearMonth(skid, year, month);
    }

    public long countPendingBySkid(String skid) {
        return caseRepository.countBySkidAndStatus(skid, "pending");
    }

    /** 팀 소속 SKID들 기준, 해당 연도 판정 완료(선정+비선정) 건수 */
    public long countJudgedBySkidsAndYear(List<String> skids, int year) {
        if (skids == null || skids.isEmpty()) {
            return 0L;
        }
        return caseRepository.countJudgedBySkidsInYear(skids, year);
    }

    public long countSelectedByYear(int year) {
        return caseRepository.countSelectedByYear(year);
    }

    /** 연도별 전체 신청(접수) 건수 */
    public long countSubmittedByYear(int year) {
        return caseRepository.countSubmittedByYear(year);
    }

    /** 전 센터 월별 신청 건수 */
    public long countSubmittedByYearMonth(int year, int month) {
        return caseRepository.countSubmittedByYearMonth(year, month);
    }

    /** 전 센터 월별 선정 건수 */
    public long countSelectedByYearMonth(int year, int month) {
        return caseRepository.countSelectedByYearMonth(year, month);
    }

    // ─── 관리자 스코프 배치 통계 (N+1 방지) ─────────────────────────────────

    public long countSubmittedBySkidsAndYear(Collection<String> skids, int year) {
        if (skids == null || skids.isEmpty()) {
            return 0L;
        }
        return caseRepository.countSubmittedBySkidsAndYear(skids, year);
    }

    /** 팀(구성원 집합) 기준 해당 연·월 접수 건수 */
    public long countSubmittedBySkidsAndYearMonth(Collection<String> skids, int year, int month) {
        if (skids == null || skids.isEmpty()) {
            return 0L;
        }
        return caseRepository.countSubmittedBySkidsAndYearMonth(skids, year, month);
    }

    public long countSelectedBySkidsAndYear(Collection<String> skids, int year) {
        if (skids == null || skids.isEmpty()) {
            return 0L;
        }
        return caseRepository.countSelectedBySkidsAndYear(skids, year);
    }

    /** 월(1~12) → 해당 월 접수 건수 */
    public Map<Integer, Long> mapSubmittedByMonthForSkidsAndYear(Collection<String> skids, int year) {
        if (skids == null || skids.isEmpty()) {
            return Map.of();
        }
        return rowsToMonthMap(caseRepository.countSubmittedBySkidsGroupByMonth(skids, year));
    }

    /** 월(1~12) → 해당 월 선정 건수 */
    public Map<Integer, Long> mapSelectedByMonthForSkidsAndYear(Collection<String> skids, int year) {
        if (skids == null || skids.isEmpty()) {
            return Map.of();
        }
        return rowsToMonthMap(caseRepository.countSelectedBySkidsGroupByMonth(skids, year));
    }

    /** 구성원별 해당 연도 접수(신청) 건수 */
    public Map<String, Long> mapSubmittedBySkidForYear(Collection<String> skids, int year) {
        if (skids == null || skids.isEmpty()) {
            return Map.of();
        }
        return rowsToSkidMap(caseRepository.countSubmittedBySkidsAndYearGroupBySkid(skids, year));
    }

    /** 구성원별 해당 연·월 접수(신청) 건수 */
    public Map<String, Long> mapSubmittedBySkidForYearMonth(Collection<String> skids, int year, int month) {
        if (skids == null || skids.isEmpty()) {
            return Map.of();
        }
        return rowsToSkidMap(caseRepository.countSubmittedBySkidsAndYearMonthGroupBySkid(skids, year, month));
    }

    public Map<String, Long> mapSelectedBySkidForYear(Collection<String> skids, int year) {
        if (skids == null || skids.isEmpty()) {
            return Map.of();
        }
        return rowsToSkidMap(caseRepository.countSelectedBySkidsAndYearGroupBySkid(skids, year));
    }

    public Map<String, Long> mapSelectedBySkidForYearMonth(Collection<String> skids, int year, int month) {
        if (skids == null || skids.isEmpty()) {
            return Map.of();
        }
        return rowsToSkidMap(caseRepository.countSelectedBySkidsAndYearMonthGroupBySkid(skids, year, month));
    }

    public Map<String, Long> mapPendingBySkid(Collection<String> skids) {
        if (skids == null || skids.isEmpty()) {
            return Map.of();
        }
        return rowsToSkidMap(caseRepository.countPendingBySkidsGroupBySkid(skids));
    }

    public Map<String, Long> mapJudgedBySkidForYear(Collection<String> skids, int year) {
        if (skids == null || skids.isEmpty()) {
            return Map.of();
        }
        return rowsToSkidMap(caseRepository.countJudgedBySkidsAndYearGroupBySkid(skids, year));
    }

    /**
     * 관리자 스코프 대시보드용 집계를 2회의 네이티브 쿼리로 로드 (기존 8회 대비).
     */
    public AdminScopedCaseStats loadScopedDashboardStats(Collection<String> skids, int year, int month) {
        if (skids == null || skids.isEmpty()) {
            return AdminScopedCaseStats.builder()
                    .totalSubmittedYear(0L)
                    .totalSelectedYear(0L)
                    .submittedByMonth(Map.of())
                    .selectedByMonth(Map.of())
                    .selectedBySkidYear(Map.of())
                    .selectedBySkidYearMonth(Map.of())
                    .pendingBySkid(Map.of())
                    .judgedBySkidYear(Map.of())
                    .build();
        }

        List<Object[]> monthly = caseRepository.aggregateMonthlySubmittedAndSelectedBySkidsAndYear(skids, year);
        Map<Integer, Long> submittedByMonth = new HashMap<>();
        Map<Integer, Long> selectedByMonth = new HashMap<>();
        long totalSubmitted = 0L;
        long totalSelected = 0L;
        for (Object[] row : monthly) {
            if (row[0] == null) {
                continue;
            }
            int m = ((Number) row[0]).intValue();
            long sub = ((Number) row[1]).longValue();
            long sel = ((Number) row[2]).longValue();
            submittedByMonth.put(m, sub);
            selectedByMonth.put(m, sel);
            totalSubmitted += sub;
            totalSelected += sel;
        }

        List<Object[]> perSkid = caseRepository.aggregatePerSkidDashboardMetrics(skids, year, month);
        Map<String, Long> selYear = new HashMap<>();
        Map<String, Long> selMonth = new HashMap<>();
        Map<String, Long> pending = new HashMap<>();
        Map<String, Long> judged = new HashMap<>();
        for (Object[] row : perSkid) {
            String skid = (String) row[0];
            selYear.put(skid, ((Number) row[1]).longValue());
            selMonth.put(skid, ((Number) row[2]).longValue());
            pending.put(skid, ((Number) row[3]).longValue());
            judged.put(skid, ((Number) row[4]).longValue());
        }

        return AdminScopedCaseStats.builder()
                .totalSubmittedYear(totalSubmitted)
                .totalSelectedYear(totalSelected)
                .submittedByMonth(submittedByMonth)
                .selectedByMonth(selectedByMonth)
                .selectedBySkidYear(selYear)
                .selectedBySkidYearMonth(selMonth)
                .pendingBySkid(pending)
                .judgedBySkidYear(judged)
                .build();
    }

    public TbYouProCase getCaseEntityOrThrow(Long caseId) {
        return findCase(caseId);
    }

    private static Map<Integer, Long> rowsToMonthMap(List<Object[]> rows) {
        Map<Integer, Long> m = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null) {
                continue;
            }
            m.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return m;
    }

    private static Map<String, Long> rowsToSkidMap(List<Object[]> rows) {
        Map<String, Long> m = new HashMap<>();
        for (Object[] row : rows) {
            m.put((String) row[0], ((Number) row[1]).longValue());
        }
        return m;
    }

    public List<CaseResponse> getCasesBySkids(List<String> skids,
                                               java.util.function.Function<String, TbLmsMember> memberFn) {
        return caseRepository.findBySkidIn(skids).stream()
                .map(c -> {
                    TbLmsMember m = memberFn.apply(c.getSkid());
                    return CaseResponse.fromSimple(c,
                            m != null ? m.getMbName() : c.getSkid(),
                            m != null ? m.getDeptName() : "");
                })
                .collect(Collectors.toList());
    }

    // ─── 내부 유틸 ─────────────────────────────────────────────────────────

    private TbYouProCase findCase(Long caseId) {
        return caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("사례를 찾을 수 없습니다: " + caseId));
    }

    private TbLmsMember findMember(String skid) {
        return memberRepository.findById(skid)
                .orElseThrow(() -> new IllegalArgumentException("구성원을 찾을 수 없습니다: " + skid));
    }

    private TbLmsMember findMemberSafe(String skid) {
        return memberRepository.findById(skid).orElse(null);
    }
}
