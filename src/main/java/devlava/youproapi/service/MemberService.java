package devlava.youproapi.service;

import devlava.youproapi.domain.TbLmsMember;
import devlava.youproapi.dto.MemberHomeResponse;
import devlava.youproapi.repository.TbLmsMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private static final int MONTHLY_LIMIT = 3;
    private static final int ANNUAL_LIMIT  = 36;

    private final TbLmsMemberRepository memberRepository;
    private final CaseService caseService;

    /**
     * 구성원 홈 화면 데이터.
     * 팀 평균은 같은 deptIdx 소속 구성원들의 연간 선정 건수 평균으로 계산한다.
     */
    public MemberHomeResponse getMemberHome(String skid) {
        TbLmsMember member = memberRepository.findById(skid)
                .orElseThrow(() -> new IllegalArgumentException("구성원을 찾을 수 없습니다: " + skid));

        LocalDate now = LocalDate.now();
        int year  = now.getYear();
        int month = now.getMonthValue();

        long myTotalSelected   = caseService.countSelectedBySkidAndYear(skid, year);
        long monthlySelected   = caseService.countSelectedBySkidAndYearMonth(skid, year, month);
        long pendingCount      = caseService.countPendingBySkid(skid);

        // 팀 인당 평균: 동일 부서 구성원들의 연간 선정 평균
        double teamAvgSelected = calcTeamAvg(member.getDeptIdx(), year);

        return MemberHomeResponse.builder()
                .team(MemberHomeResponse.TeamInfo.builder()
                        .id(member.getDeptIdx())
                        .name(member.getDeptName())
                        .build())
                .teamAvgSelected(teamAvgSelected)
                .myTotalSelected(myTotalSelected)
                .monthlySelected(monthlySelected)
                .monthlyLimit(MONTHLY_LIMIT)
                .pendingCount(pendingCount)
                .annualLimit(ANNUAL_LIMIT)
                .build();
    }

    private double calcTeamAvg(Integer deptIdx, int year) {
        if (deptIdx == null) return 0.0;

        java.util.List<TbLmsMember> teamMembers =
                memberRepository.findByDeptIdxAndUseYn(deptIdx, "Y");

        if (teamMembers.isEmpty()) return 0.0;

        long totalSelected = teamMembers.stream()
                .mapToLong(m -> caseService.countSelectedBySkidAndYear(m.getSkid(), year))
                .sum();

        return (double) totalSelected / teamMembers.size();
    }
}
