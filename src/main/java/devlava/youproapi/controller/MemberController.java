package devlava.youproapi.controller;

import devlava.youproapi.dto.CaseResponse;
import devlava.youproapi.dto.CaseSubmitRequest;
import devlava.youproapi.dto.MemberCsFocusTasksResponse;
import devlava.youproapi.dto.MemberCsUnsatisfiedDetailsResponse;
import devlava.youproapi.dto.MemberHomeResponse;
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
