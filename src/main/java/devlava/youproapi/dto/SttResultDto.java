package devlava.youproapi.dto;

import devlava.youproapi.domain.TbYouStt;
import lombok.Builder;
import lombok.Getter;

/**
 * STT 조회 결과 — {@code 유선_유프로_STT} 매칭 시 전사·표시용 통화 시각.
 */
@Getter
@Builder
public class SttResultDto {

    private String sttId;
    private String callTime;
    private String fullTranscript;
    private String callDuration;
    private boolean found;

    public static SttResultDto from(TbYouStt row) {
        return SttResultDto.builder()
                .sttId(String.valueOf(row.getNum()))
                .callTime(combineDateTime(row.getIlja(), row.getSangdamSiGan()))
                .fullTranscript(row.getSttOriginal())
                .callDuration(null)
                .found(true)
                .build();
    }

    public static SttResultDto notFound(String callTime) {
        return SttResultDto.builder()
                .callTime(callTime)
                .found(false)
                .build();
    }

    private static String combineDateTime(String date, String time) {
        if (date == null && time == null) {
            return "";
        }
        if (time == null || time.isBlank()) {
            return date != null ? date : "";
        }
        if (date == null || date.isBlank()) {
            return time;
        }
        return date.trim() + " " + time.trim();
    }
}
