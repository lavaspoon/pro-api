package devlava.youproapi.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Getter
@Setter
@NoArgsConstructor
public class CaseJudgeRequest {

    @NotBlank(message = "관리자 SKID는 필수입니다.")
    private String adminSkid;

    @NotBlank(message = "판정 결과는 필수입니다.")
    @Pattern(regexp = "selected|rejected", message = "판정 결과는 selected 또는 rejected여야 합니다.")
    private String decision;

    @NotBlank(message = "판정 사유는 필수입니다.")
    private String reason;

    /** 관리자가 녹취 검토 후 확정한 STT 대화 텍스트 (선택) */
    private String editedTranscript;

    /** 1차 AI 분석 결과 JSON 문자열 (선택, 판정과 함께 저장) */
    private String aiSnapshotJson;
}
