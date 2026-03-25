package devlava.youproapi.service;

import devlava.youproapi.domain.TbLmsMember;
import devlava.youproapi.domain.TbYouProCase;
import devlava.youproapi.dto.CaseJudgeRequest;
import devlava.youproapi.dto.CaseResponse;
import devlava.youproapi.dto.CaseSubmitRequest;
import devlava.youproapi.repository.TbLmsMemberRepository;
import devlava.youproapi.repository.TbYouProCaseRepository;
import devlava.youproapi.stt.dto.SttResultDto;
import devlava.youproapi.stt.service.SttService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
                req.getEditedTranscript(), req.getAiSnapshotJson());
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
