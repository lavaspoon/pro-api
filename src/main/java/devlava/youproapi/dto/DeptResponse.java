package devlava.youproapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeptResponse {
    private Integer id;
    private String deptName;
    private Integer parentDeptId;
    private Integer depth;
    private String useYn;

    @Builder.Default
    private List<DeptResponse> children = new ArrayList<>();
}
