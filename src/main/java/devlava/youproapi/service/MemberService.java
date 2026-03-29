package devlava.youproapi.service;

import devlava.youproapi.config.YouproAdminProperties;
import devlava.youproapi.domain.TbLmsDept;
import devlava.youproapi.domain.TbLmsMember;
import devlava.youproapi.dto.MemberHomeResponse;
import devlava.youproapi.repository.TbLmsDeptRepository;
import devlava.youproapi.repository.TbLmsMemberRepository;
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
    private static final int ANNUAL_LIMIT = 36;

    private final TbLmsMemberRepository memberRepository;
    private final TbLmsDeptRepository deptRepository;
    private final CaseService caseService;
    private final YouproAdminProperties adminProperties;

    /**
     * 구성원 홈 화면 데이터.
     * 팀 평균은 같은 deptIdx 소속 구성원들의 연간 선정 건수 평균으로 계산한다.
     */
    public MemberHomeResponse getMemberHome(String skid) {
        TbLmsMember member = memberRepository.findById(skid)
                .orElseThrow(() -> new IllegalArgumentException("구성원을 찾을 수 없습니다: " + skid));

        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        long myTotalSelected = caseService.countSelectedBySkidAndYear(skid, year);
        long monthlySelected = caseService.countSelectedBySkidAndYearMonth(skid, year, month);
        long pendingCount = caseService.countPendingBySkid(skid);

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
     * 설정된 2depth(예: 5·6·7) 하위 전체 팀 중, 팀별 연간 선정 건수 합으로 순위를 계산한다.
     * 부서 트리·부모 맵은 메모리에서 처리 — 구성원별 부서 조회 N+1 없음.
     */
    private EvalCenterRank computeEvalCenterTeamRank(TbLmsMember member, int year) {
        Integer myDept = member.getDeptIdx();
        List<TbLmsDept> allDepts = deptRepository.findAllWithParentFetched();
        Map<Integer, Integer> parentOf = buildParentMap(allDepts);
        Set<Integer> evalRoots = new HashSet<>(adminProperties.getSecondDepthDeptIds());

        if (myDept == null || !isUnderAnyRoot(myDept, parentOf, evalRoots)) {
            return new EvalCenterRank(false, null, null, null);
        }

        Map<Integer, Boolean> evalCache = new HashMap<>();
        List<TbLmsMember> scoped = memberRepository.findByUseYn("Y").stream()
                .filter(m -> m.getDeptIdx() != null)
                .filter(m -> evalCache.computeIfAbsent(
                        m.getDeptIdx(),
                        did -> isUnderAnyRoot(did, parentOf, evalRoots)))
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

    private static Map<Integer, Integer> buildParentMap(List<TbLmsDept> allDepts) {
        Map<Integer, Integer> parentOf = new HashMap<>();
        for (TbLmsDept d : allDepts) {
            Integer id = d.getDeptId();
            parentOf.put(id, d.getParent() != null ? d.getParent().getDeptId() : null);
        }
        return parentOf;
    }

    /** 부모 포인터만 따라가며 평가 루트(2depth 설정 ID) 도달 여부 — DB 루프 조회 없음 */
    private static boolean isUnderAnyRoot(Integer deptId, Map<Integer, Integer> parentOf, Set<Integer> evalRoots) {
        if (deptId == null) {
            return false;
        }
        Integer cur = deptId;
        for (int i = 0; i < 64 && cur != null; i++) {
            if (evalRoots.contains(cur)) {
                return true;
            }
            cur = parentOf.get(cur);
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

    /** 동일 부서 구성원 선정 건수 합 / 인원 — 배치 map 한 번으로 N+1 방지 */
    private double calcTeamAvg(Integer deptIdx, int year) {
        if (deptIdx == null) {
            return 0.0;
        }

        List<TbLmsMember> teamMembers = memberRepository.findByDeptIdxAndUseYn(deptIdx, "Y");
        if (teamMembers.isEmpty()) {
            return 0.0;
        }

        List<String> skids = teamMembers.stream().map(TbLmsMember::getSkid).collect(Collectors.toList());
        Map<String, Long> selectedBySkid = caseService.mapSelectedBySkidForYear(skids, year);
        long totalSelected = teamMembers.stream()
                .mapToLong(m -> selectedBySkid.getOrDefault(m.getSkid(), 0L))
                .sum();

        return (double) totalSelected / teamMembers.size();
    }
}
