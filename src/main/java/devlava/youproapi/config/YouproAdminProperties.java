package devlava.youproapi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 화면 부서 필터(2depth 루트 ID, leaf depth 팀) — 운영에서만 application 설정으로 조정.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "youpro.admin")
public class YouproAdminProperties {

    /**
     * 2depth 필터에 쓰는 TB_LMS_DEPT.dept_id 목록 (고정 후보).
     */
    private List<Integer> secondDepthDeptIds = new ArrayList<>(List.of(5, 6));

    /**
     * 필터 선택 시 하단에 나열할 팀의 depth (예: 5).
     */
    private int leafTeamDepth = 2;

    /**
     * 비어 있으면: 설정된 각 2depth 루트 하위 전체 서브트리가 스코프.
     * 비어 있지 않으면: 키 = second-depth-dept-ids 에 있는 2depth dept_id, 값 = 그 센터 하위에서 허용할 leaf({@link #leafTeamDepth}) dept_id 목록.
     * 키를 생략한 센터는 leaf depth 팀 전체(해당 센터 서브트리)를 사용한다. 키는 있으나 목록이 {@code []} 이면 그 센터에서 leaf 팀 없음.
     */
    private Map<Integer, List<Integer>> leafDeptIdsBySecondDepth = new LinkedHashMap<>();
}
