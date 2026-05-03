package devlava.youproapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CsSatisfactionExcludeTimeResponse {

    private String skill;
    private String startAt;
    private String endAt;
    private long updatedCount;
}
