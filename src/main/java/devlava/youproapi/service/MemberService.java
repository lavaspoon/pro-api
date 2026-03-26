package devlava.youproapi.service;

import devlava.youproapi.domain.TbLmsDept;
import devlava.youproapi.domain.TbLmsMember;
import devlava.youproapi.dto.MemberHomeResponse;
import devlava.youproapi.repository.TbLmsDeptRepository;
import devlava.youproapi.repository.TbLmsMemberRepository;
import devlava.youproapi.support.AdminDeptScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private static final int MONTHLY_LIMIT = 3;
    private static final int ANNUAL_LIMIT  = 36;

    private final TbLmsMemberRepository memberRepository;
    private final TbLmsDeptRepository deptRepository;
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

        EvalCenterRank evalRank = computeEvalCenterTeamRank(member, year);

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
                .evalCenterInScope(evalRank.inScope)
                .evalCenterTeamRank(evalRank.rank)
                .evalCenterTeamTotal(evalRank.totalTeams)
                .evalCenterTeamSelectedYear(evalRank.teamSelectedYear)
                .build();
    }

    /**
     * 부서 5·7 하위 전체 팀 중, 팀별 연간 선정 건수 합으로 순위를 계산한다.
     * <p>
     * 포함 여부는 {@link AdminDeptScope#ROOT_DEPT_IDS} 서브트리 ID 집합뿐 아니라,
     * {@code TB_LMS_DEPT} 부모 체인을 따라 올라가 5 또는 7을 만나는지로 판별한다
     * (트리·캐시와 member.dept_idx 불일치로 하위 팀이 누락되는 경우를 방지).
     */
    private EvalCenterRank computeEvalCenterTeamRank(TbLmsMember member, int year) {
        Integer myDept = member.getDeptIdx();
        if (myDept == null || !isDeptUnderEvaluationCenters(myDept)) {
            return new EvalCenterRank(false, null, null, null);
        }

        Map<Integer, Boolean> evalCache = new HashMap<>();
        List<TbLmsMember> scoped = memberRepository.findByUseYn("Y").stream()
                .filter(m -> m.getDeptIdx() != null)
                .filter(m -> evalCache.computeIfAbsent(m.getDeptIdx(), this::isDeptUnderEvaluationCenters))
                .collect(Collectors.toList());
        if (scoped.isEmpty()) {
            return new EvalCenterRank(true, null, 0, 0L);
        }

        List<String> skids = scoped.stream().map(TbLmsMember::getSkid).collect(Collectors.toList());
        Map<String, Long> selectedBySkid = caseService.mapSelectedBySkidForYear(skids, year);

        Map<Integer, Long> totalByDept = new HashMap<>();
        for (TbLmsMember m : scoped) {
            Integer d = m.getDeptIdx();
            if (d == null) {
                continue;
            }
            long v = selectedBySkid.getOrDefault(m.getSkid(), 0L);
            totalByDept.merge(d, v, Long::sum);
        }

        List<Map.Entry<Integer, Long>> sorted = totalByDept.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .collect(Collectors.toList());

        int totalTeams = sorted.size();
        long myTeamTotal = totalByDept.getOrDefault(myDept, 0L);

        int rank = 1;
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0 && sorted.get(i).getValue() < sorted.get(i - 1).getValue()) {
                rank = i + 1;
            }
            if (myDept.equals(sorted.get(i).getKey())) {
                return new EvalCenterRank(true, rank, totalTeams, myTeamTotal);
            }
        }

        return new EvalCenterRank(true, null, totalTeams, myTeamTotal);
    }

    /**
     * 부서 트리에서 상위로 올라가며 평가 대상 센터({@link AdminDeptScope#ROOT_DEPT_IDS})를 거치는지 확인한다.
     */
    private boolean isDeptUnderEvaluationCenters(Integer deptId) {
        if (deptId == null) {
            return false;
        }
        Integer cur = deptId;
        for (int depth = 0; depth < 64 && cur != null; depth++) {
            if (AdminDeptScope.ROOT_DEPT_IDS.contains(cur)) {
                return true;
            }
            TbLmsDept d = deptRepository.findById(cur).orElse(null);
            if (d == null) {
                return false;
            }
            if (d.getParent() == null) {
                break;
            }
            cur = d.getParent().getDeptId();
        }
        return false;
    }

    private static final class EvalCenterRank {
        final boolean inScope;
        final Integer rank;
        final Integer totalTeams;
        final Long teamSelectedYear;

        EvalCenterRank(boolean inScope, Integer rank, Integer totalTeams, Long teamSelectedYear) {
            this.inScope = inScope;
            this.rank = rank;
            this.totalTeams = totalTeams;
            this.teamSelectedYear = teamSelectedYear;
        }
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
