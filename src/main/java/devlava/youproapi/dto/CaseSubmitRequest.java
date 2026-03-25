package devlava.youproapi.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Getter
@NoArgsConstructor
public class CaseSubmitRequest {

    @NotBlank(message = "SKID는 필수입니다.")
    private String skid;

    @NotBlank(message = "사례 제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자를 초과할 수 없습니다.")
    private String title;

    @NotBlank(message = "응대 내용은 필수입니다.")
    private String description;

    /**
     * 통화 일시 — {@code TB_STT_RESULT.call_time} 과 매칭.
     * 예: {@code "2026-03-05 09:30:00"} (정규화 시 {@code 20260305093000}).
     */
    @NotBlank(message = "통화 일시는 필수입니다.")
    @Size(max = 50, message = "통화 일시는 50자를 초과할 수 없습니다.")
    private String callDate;
}
