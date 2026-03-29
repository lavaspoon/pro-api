package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 선택한 2depth 부서 하위의 leaf depth 팀별 집계 (대시보드 {@link AdminDashboardResponse.TeamSummary} 재사용).
 */
@Getter
@Builder
public class AdminLeafTeamsResponse {

    private Integer secondDepthDeptId;
    private List<AdminDashboardResponse.TeamSummary> teams;
}
