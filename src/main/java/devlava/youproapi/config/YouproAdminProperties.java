package devlava.youproapi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 화면 부서 필터(2depth 루트 ID, 4depth 팀 목록) — 운영에서만 application 설정으로 조정.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "youpro.admin")
public class YouproAdminProperties {

    /**
     * 2depth 필터에 쓰는 TB_LMS_DEPT.dept_id 목록 (고정 후보).
     */
    private List<Integer> secondDepthDeptIds = new ArrayList<>(List.of(5, 6, 7));

    /**
     * 필터 선택 시 하단에 나열할 팀의 depth (예: 4).
     */
    private int leafTeamDepth = 4;
}
