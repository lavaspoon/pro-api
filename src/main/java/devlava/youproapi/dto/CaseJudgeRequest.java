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

    /** AI가 추출한 STT 중 핵심 멘트 (선택) */
    private String aiKeyPhrase;

    /** AI가 전하는 피드백 (JSON 등, 선택) */
    private String aiKeyPoint;
}
