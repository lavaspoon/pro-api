package devlava.youproapi.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import devlava.youproapi.domain.TbYouProCase;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

/**
 * 사례 응답 DTO — 구성원/관리자 화면 공용.
 * 프론트엔드 case 객체와 필드명을 맞춘다 (aiKeyPhrase, aiKeyPoint 등).
 */
@Getter
@Builder
public class CaseResponse {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** JSON이면 파싱, 아니면(일반 텍스트) 그대로 문자열로 노출 */
    private static Object parseAiKeyPoint(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(raw.trim(), Object.class);
        } catch (Exception e) {
            return raw.trim();
        }
    }

    /** 사례 ID (frontend: c.id) */
    private Long id;

    /** 제출자 SKID (frontend: c.skid / c.memberId) */
    private String skid;
    private String memberId;     // skid 와 동일 — 기존 컴포넌트 호환용

    /** 제출자 이름 */
    private String memberName;

    /** 소속 팀명 */
    private String teamName;

    private String title;
    private String description;

    /** ISO 포맷 문자열 (frontend: new Date(c.submittedAt)) */
    private String submittedAt;

    /** "pending" | "selected" | "rejected" */
    private String status;

    /** 통화 일시 */
    private String callDate;

    /** 통화 길이 (레거시 필드, 미사용 시 null) */
    private String callDuration;

    /** 고객 유형 */
    private String customerType;

    /** 판정 사유 */
    private String judgmentReason;

    /** 판정 시각 ISO 포맷 */
    private String judgedAt;

    /** 접수 월 "yyyy-MM" (CaseListPage: c.month) */
    private String month;

    /** 레거시 필드 (항상 null) */
    private String fullTranscript;

    /** AI 1차 판단 ({@code ai_key_point}) — JSON이면 파싱, 아니면 문자열 */
    private Object aiKeyPoint;

    /** AI 핵심 멘트 (선택) */
    private String aiKeyPhrase;

    // ─── Factory ──────────────────────────────────────────────────────────

    public static CaseResponse from(TbYouProCase c, String memberName, String teamName,
                                    String fullTranscript, String callDuration) {
        return CaseResponse.builder()
                .id(c.getCaseId())
                .skid(c.getSkid())
                .memberId(c.getSkid())
                .memberName(memberName)
                .teamName(teamName)
                .title(c.getTitle())
                .description(c.getDescription())
                .submittedAt(c.getSubmittedAt() != null ? c.getSubmittedAt().format(ISO) : null)
                .status(c.getStatus())
                .callDate(c.getCallDate())
                .callDuration(callDuration)
                .customerType(c.getCustomerType())
                .judgmentReason(c.getJudgmentReason())
                .judgedAt(c.getJudgedAt() != null ? c.getJudgedAt().format(ISO) : null)
                .month(c.getSubmittedAt() != null ? c.getSubmittedAt().format(MONTH_FMT) : null)
                .fullTranscript(fullTranscript)
                .aiKeyPoint(parseAiKeyPoint(c.getAiKeyPoint()))
                .aiKeyPhrase(c.getAiKeyPhrase())
                .build();
    }

    /** 상세 없이 목록용 응답 */
    public static CaseResponse fromSimple(TbYouProCase c, String memberName, String teamName) {
        return from(c, memberName, teamName, null, null);
    }
}
