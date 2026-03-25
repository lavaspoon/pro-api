package devlava.youproapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberListResponse {
    private String userId;
    private String mbName;
    private String deptName;
    private Integer deptIdx;
    private String mbPositionName;
}
