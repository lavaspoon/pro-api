package devlava.youproapi.domain;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 우수 상담 사례 엔티티 — {@code ragdb.TB_YOU_PRO_CASE}
 *
 * <p>구성원이 접수한 우수 상담 사례와 관리자의 판정 결과를 저장한다.
 * STT 조회 시 저장된 통화 일시 문자열과 {@code skid} 로 STT 를 조회한다.
 */
@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "TB_YOU_PRO_CASE",
    indexes = {
        @Index(name = "IDX_YOU_PRO_CASE_SKID", columnList = "skid"),
        @Index(name = "IDX_YOU_PRO_CASE_STATUS", columnList = "status"),
        @Index(name = "IDX_YOU_PRO_CASE_MONTH", columnList = "skid, status, submitted_at")
    }
)
public class TbYouProCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_id")
    private Long caseId;

    /** 접수자 SK ID */
    @Column(name = "skid", length = 50, nullable = false)
    private String skid;

    /** 사례 제목 */
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    /** 응대 내용 요약 */
    @Lob
    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    /** 접수 시각 */
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    /**
     * 상태.
     * <ul>
     *   <li>{@code "pending"} — 대기중</li>
     *   <li>{@code "selected"} — 선정</li>
     *   <li>{@code "rejected"} — 비선정</li>
     * </ul>
     */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /** 통화 일시 (STT 조회 키) — 예: {@code "2026-03-05 09:30:00"} */
    @Column(name = "call_date", length = 50)
    private String callDate;

    /** 고객 유형 (레거시·미사용 시 null) */
    @Column(name = "customer_type", length = 100)
    private String customerType;

    /** 판정 사유 */
    @Lob
    @Column(name = "judgment_reason", columnDefinition = "TEXT")
    private String judgmentReason;

    /** 판정 시각 */
    @Column(name = "judged_at")
    private LocalDateTime judgedAt;

    /** 판정자 SK ID */
    @Column(name = "judged_by", length = 50)
    private String judgedBy;

    /** AI가 추출한 STT 중 핵심 멘트 */
    @Lob
    @Column(name = "ai_key_phrase", columnDefinition = "TEXT")
    private String aiKeyPhrase;

    /** AI가 전하는 피드백 (JSON 문자열 등 저장 가능) */
    @Lob
    @Column(name = "ai_key_point", columnDefinition = "TEXT")
    private String aiKeyPoint;

    // ─── 상태 변경 메서드 ───────────────────────────────────────────────────

    public void judge(String decision, String reason, String adminSkid,
                      String aiKeyPhrase, String aiKeyPoint) {
        this.status = decision;
        this.judgmentReason = reason;
        this.judgedAt = LocalDateTime.now();
        this.judgedBy = adminSkid;
        if (aiKeyPhrase != null) {
            this.aiKeyPhrase = aiKeyPhrase.isBlank() ? null : aiKeyPhrase;
        }
        if (aiKeyPoint != null && !aiKeyPoint.isBlank()) {
            this.aiKeyPoint = aiKeyPoint;
        }
    }
}
