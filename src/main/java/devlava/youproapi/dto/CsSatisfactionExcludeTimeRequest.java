package devlava.youproapi.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Getter
@Setter
public class CsSatisfactionExcludeTimeRequest {

    @NotBlank(message = "skill은 필수입니다.")
    private String skill;

    /** 구간 시작(상담일시 evalDate, 포함) */
    @NotNull(message = "startAt은 필수입니다.")
    private LocalDateTime startAt;

    /** 구간 종료(상담일시 evalDate, 포함) */
    @NotNull(message = "endAt은 필수입니다.")
    private LocalDateTime endAt;

    /** 적용 관리자 skid (이력 저장용, 생략 가능) */
    private String excludedBySkid;
}
