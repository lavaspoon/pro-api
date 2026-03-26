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
        /** 해당 연도 접수(신청) 건수 */
        private long totalSubmitted;
        private long totalSelected;
        /** 이번 달 접수 건수 */
        private long monthlySubmitted;
        private long monthlySelected;
        private int monthlyLimit;
        private long pendingCount;
        private List<CaseResponse> cases;
    }
}
