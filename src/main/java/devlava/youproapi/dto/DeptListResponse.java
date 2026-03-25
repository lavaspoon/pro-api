package devlava.youproapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeptListResponse {
    private Integer id;
    private String deptName;
    private Integer depth;
    private Integer parentDeptId;
}
