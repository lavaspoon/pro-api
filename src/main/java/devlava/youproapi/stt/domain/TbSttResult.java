package devlava.youproapi.stt.domain;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * STT(Speech-To-Text) 통화 결과 엔티티 — {@code st_etc.TB_STT_RESULT}
 *
 * <p><b>패키지 분리 주의:</b> 이 엔티티는 {@code devlava.youproapi.stt.domain} 에 위치한다.
 * Primary DB({@code ragdb}) 의 EMF 스캔 경로({@code devlava.youproapi.domain})와
 * 완전히 분리되어 있으므로, ragdb 에 DDL 오염이 발생하지 않는다.
 *
 * <h3>call_time VARCHAR 포맷 대응</h3>
 * <pre>
 *   "20260305093000"      → 14자리 순수 숫자
 *   "2026-03-05 09:30:00" → 정규화 후 "20260305093000" 으로 비교
 * </pre>
 * {@link devlava.youproapi.stt.service.SttService#normalizeCallTime(String)} 참조.
 */
@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "TB_STT_RESULT",
    indexes = {
        @Index(name = "IDX_STT_RESULT_CALL_TIME",  columnList = "call_time"),
        @Index(name = "IDX_STT_RESULT_AGENT_SKID", columnList = "agent_skid"),
        @Index(name = "IDX_STT_RESULT_AGENT_TIME", columnList = "agent_skid, call_time")
    }
)
public class TbSttResult {

    /** STT 결과 고유 ID (외부 STT 시스템 채번) */
    @Id
    @Column(name = "stt_id", length = 50, nullable = false)
    private String sttId;

    /**
     * 통화 시작 시각 (VARCHAR).
     * 우수사례 접수 시 입력한 callTime 과 조인 키로 사용한다.
     */
    @Column(name = "call_time", length = 50, nullable = false)
    private String callTime;

    /** 상담사 SK ID */
    @Column(name = "agent_skid", length = 50, nullable = false)
    private String agentSkid;

    /** 고객 번호 (개인정보 마스킹 처리 후 저장) */
    @Column(name = "customer_no", length = 100)
    private String customerNo;

    /** 통화 전체 전사본 (상담사 + 고객 통합 텍스트) */
    @Lob
    @Column(name = "full_transcript", columnDefinition = "TEXT")
    private String fullTranscript;

    /** 통화 총 시간 (예: {@code "00:18:32"}) */
    @Column(name = "call_duration", length = 50)
    private String callDuration;

    /** STT 처리 완료 시각 */
    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
