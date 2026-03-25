package devlava.youproapi.stt.dto;

import devlava.youproapi.stt.domain.TbSttResult;
import lombok.Builder;
import lombok.Getter;

/**
 * STT 조회 결과 응답 DTO.
 *
 * <p>우수사례 접수 API 응답에 포함되어 통화 전사본·녹취 시간을 제공한다.
 * ({@code found=false}이면 STT 데이터 없음)
 */
@Getter
@Builder
public class SttResultDto {

    private String sttId;

    /** 통화 시작 시각 (VARCHAR 원본 값) */
    private String callTime;

    /** 통화 전체 전사본 */
    private String fullTranscript;

    /** 통화 시간 (예: "18분 32초") */
    private String callDuration;

    /** STT 데이터 존재 여부 */
    private boolean found;

    // ─── Factory ────────────────────────────────────────────────────────────

    public static SttResultDto from(TbSttResult result) {
        return SttResultDto.builder()
                .sttId(result.getSttId())
                .callTime(result.getCallTime())
                .fullTranscript(result.getFullTranscript())
                .callDuration(result.getCallDuration())
                .found(true)
                .build();
    }

    /** STT 데이터를 찾지 못한 경우 */
    public static SttResultDto notFound(String callTime) {
        return SttResultDto.builder()
                .callTime(callTime)
                .found(false)
                .build();
    }
}
