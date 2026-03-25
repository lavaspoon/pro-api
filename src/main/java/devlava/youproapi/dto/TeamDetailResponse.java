package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TeamDetailResponse {

    private TeamInfo team;
    private List<MemberDetail> members;

    @Getter
    @Builder
    public static class TeamInfo {
        private Integer id;
        private String name;
    }

    @Getter
    @Builder
    public static class MemberDetail {
        private String id;             // skid
        private String name;
        private String position;
        private long totalSelected;
        private long monthlySelected;
        private int monthlyLimit;
        private long pendingCount;
        private List<CaseResponse> cases;
    }
}
