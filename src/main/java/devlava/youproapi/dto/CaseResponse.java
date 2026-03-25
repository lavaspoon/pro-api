package devlava.youproapi.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import devlava.youproapi.domain.TbYouProCase;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 사례 응답 DTO — 구성원/관리자 화면 공용.
 * 프론트엔드의 case 객체 형태와 정확히 일치하도록 필드명을 맞춘다.
 */
@Getter
@Builder
public class CaseResponse {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static Object parseAiInsightJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
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

    /** 통화 일시 (STT 조회 키) */
    private String callDate;

    /** 통화 길이 (STT, 없으면 null) */
    private String callDuration;

    /** 고객 유형 */
    private String customerType;

    /** 판정 사유 */
    private String judgmentReason;

    /** 판정 시각 ISO 포맷 */
    private String judgedAt;

    /** 접수 월 "yyyy-MM" (CaseListPage: c.month) */
    private String month;

    /**
     * STT 통화 전체 전사본 (개인정보보호: 발화 단위 세그먼트는 저장하지 않음).
     * STT 미연동 시 null.
     */
    private String fullTranscript;

    /** AI 인사이트 (현재는 null, 추후 확장) */
    private Object aiInsight;

    /** 관리자가 확정한 STT 대화 텍스트 (판정 후 저장) */
    private String adminEditedTranscript;

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
                .aiInsight(parseAiInsightJson(c.getAiSnapshotJson()))
                .adminEditedTranscript(c.getAdminEditedTranscript())
                .build();
    }

    /** STT 없이 기본 사례 응답 생성 */
    public static CaseResponse fromSimple(TbYouProCase c, String memberName, String teamName) {
        return from(c, memberName, teamName, null, null);
    }
}
