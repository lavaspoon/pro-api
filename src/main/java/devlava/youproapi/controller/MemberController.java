package devlava.youproapi.controller;

import devlava.youproapi.dto.CaseResponse;
import devlava.youproapi.dto.CaseSubmitRequest;
import devlava.youproapi.dto.MemberCsFocusTasksResponse;
import devlava.youproapi.dto.MemberCsInsightPromptMentsResponse;
import devlava.youproapi.dto.MemberCsUnsatisfiedDetailsResponse;
import devlava.youproapi.dto.MemberHomeResponse;
import devlava.youproapi.dto.MemberSatisfactionResponse;
import devlava.youproapi.service.CaseService;
import devlava.youproapi.service.CsSatisfactionService;
import devlava.youproapi.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final CaseService   caseService;
    private final CsSatisfactionService csSatisfactionService;

    /**
     * 구성원 홈 화면 데이터
     * GET /api/member/home?skid={skid}
     */
    @GetMapping("/home")
    public ResponseEntity<MemberHomeResponse> getHome(@RequestParam String skid) {
        return ResponseEntity.ok(memberService.getMemberHome(skid));
    }

    /**
     * 내 사례 목록
     * GET /api/member/cases?skid={skid}
     */
    @GetMapping("/cases")
    public ResponseEntity<List<CaseResponse>> getMyCases(@RequestParam String skid) {
        return ResponseEntity.ok(caseService.getMyCases(skid));
    }

    /**
     * 구성원 당월 만족도 요약
     * GET /api/member/satisfaction?skid=&year=&month=
     */
    @GetMapping("/satisfaction")
    public ResponseEntity<MemberSatisfactionResponse> getMemberSatisfaction(
            @RequestParam String skid,
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(csSatisfactionService.getMemberSatisfaction(skid, year, month));
    }

    /**
     * 사례 상세 (STT 대화 포함)
     * GET /api/member/cases/{caseId}
     */
    @GetMapping("/cases/{caseId}")
    public ResponseEntity<CaseResponse> getCaseDetail(@PathVariable Long caseId) {
        return ResponseEntity.ok(caseService.getCaseDetail(caseId));
    }

    /**
     * 우수사례 접수
     * POST /api/member/cases
     */
    @PostMapping("/cases")
    public ResponseEntity<CaseResponse> submitCase(@Valid @RequestBody CaseSubmitRequest req) {
        return ResponseEntity.ok(caseService.submitCase(req));
    }

    /**
     * 구성원 CS 만족도 — 당월 중점추진과제(5대도시·5060·문제해결) Y 건수
     * GET /api/member/satisfaction/focus-tasks?skid=&year=&month=
     */
    @GetMapping("/satisfaction/focus-tasks")
    public ResponseEntity<MemberCsFocusTasksResponse> getMemberFocusTasks(
            @RequestParam String skid,
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(csSatisfactionService.getMemberFocusTaskCounts(skid, year, month));
    }

    /**
     * AI 인사이트 프롬프트용 멘트 — 평가시간 적용 건만, 해당 월 중 상담일이 가장 최근인 날짜 행만
     * GET /api/member/satisfaction/ai-insight-ments?skid=&year=&month=
     */
    @GetMapping("/satisfaction/ai-insight-ments")
    public ResponseEntity<MemberCsInsightPromptMentsResponse> getMemberInsightPromptMents(
            @RequestParam String skid,
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(csSatisfactionService.getMemberInsightPromptMents(skid, year, month));
    }

    /**
     * 구성원 당월 불만족 유형(1~5)별 상담 상세
     * GET /api/member/satisfaction/unsatisfied-details?skid=&year=&month=&dissatisfactionType=
     */
    @GetMapping("/satisfaction/unsatisfied-details")
    public ResponseEntity<MemberCsUnsatisfiedDetailsResponse> getMemberUnsatisfiedDetails(
            @RequestParam String skid,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam int dissatisfactionType) {
        return ResponseEntity.ok(
                csSatisfactionService.getMemberUnsatisfiedDetails(skid, year, month, dissatisfactionType));
    }
}
