package devlava.youproapi.service;

import devlava.youproapi.domain.TbLmsMember;
import devlava.youproapi.dto.AdminDashboardResponse;
import devlava.youproapi.dto.AdminDashboardResponse.MemberSummary;
import devlava.youproapi.dto.AdminDashboardResponse.TeamSummary;
import devlava.youproapi.dto.CaseResponse;
import devlava.youproapi.dto.TeamDetailResponse;
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
public class AdminService {

    private static final int MONTHLY_LIMIT = 3;

    private final TbLmsMemberRepository memberRepository;
    private final CaseService           caseService;

    // ─── 관리자 대시보드 ────────────────────────────────────────────────────

    public AdminDashboardResponse getDashboard() {
        LocalDate now   = LocalDate.now();
        int year        = now.getYear();
        int month       = now.getMonthValue();

        List<TbLmsMember> allMembers = memberRepository.findByUseYn("Y");

        long totalSubmitted = caseService.countSubmittedByYear(year);
        long totalSelected = caseService.countSelectedByYear(year); // 전체 연간 선정
        int  memberCount   = allMembers.size();
        double centerAvg   = memberCount == 0 ? 0.0 :
                (double) totalSelected / memberCount;

        // 부서별(deptIdx) 그룹핑
        Map<Integer, List<TbLmsMember>> byDept = allMembers.stream()
                .filter(m -> m.getDeptIdx() != null)
                .collect(Collectors.groupingBy(TbLmsMember::getDeptIdx));

        List<TeamSummary> teams = byDept.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> buildTeamSummary(e.getKey(), e.getValue(), year, month))
                .collect(Collectors.toList());

        List<AdminDashboardResponse.MonthlyTrendPoint> monthlyTrend = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            monthlyTrend.add(AdminDashboardResponse.MonthlyTrendPoint.builder()
                    .month(m)
                    .submitted(caseService.countSubmittedByYearMonth(year, m))
                    .selected(caseService.countSelectedByYearMonth(year, m))
                    .build());
        }

        return AdminDashboardResponse.builder()
                .year(year)
                .centerAvg(Math.round(centerAvg * 10.0) / 10.0)
                .totalSubmitted(totalSubmitted)
                .totalSelected(totalSelected)
                .memberCount(memberCount)
                .monthlyTrend(monthlyTrend)
                .teams(teams)
                .build();
    }

    private TeamSummary buildTeamSummary(Integer deptIdx, List<TbLmsMember> members,
                                          int year, int month) {
        String teamName = members.isEmpty() ? "" : members.get(0).getDeptName();

        long teamTotal = members.stream()
                .mapToLong(m -> caseService.countSelectedBySkidAndYear(m.getSkid(), year))
                .sum();

        double avg = members.isEmpty() ? 0.0 : (double) teamTotal / members.size();

        List<MemberSummary> memberSummaries = members.stream()
                .map(m -> MemberSummary.builder()
                        .id(m.getSkid())
                        .name(m.getMbName())
                        .position(m.getMbPositionName())
                        .totalSelected(caseService.countSelectedBySkidAndYear(m.getSkid(), year))
                        .monthlySelected(caseService.countSelectedBySkidAndYearMonth(
                                m.getSkid(), year, month))
                        .pendingCount(caseService.countPendingBySkid(m.getSkid()))
                        .build())
                .collect(Collectors.toList());

        long teamPending = memberSummaries.stream().mapToLong(MemberSummary::getPendingCount).sum();
        List<String> skids = members.stream().map(TbLmsMember::getSkid).collect(Collectors.toList());
        long teamJudged = caseService.countJudgedBySkidsAndYear(skids, year);

        return TeamSummary.builder()
                .id(deptIdx)
                .name(teamName)
                .memberCount(members.size())
                .totalSelected(teamTotal)
                .avgSelected(Math.round(avg * 10.0) / 10.0)
                .pendingCount(teamPending)
                .judgedCount(teamJudged)
                .members(memberSummaries)
                .build();
    }

    // ─── 팀 상세 ────────────────────────────────────────────────────────────

    public TeamDetailResponse getTeamDetail(Integer deptIdx) {
        LocalDate now   = LocalDate.now();
        int year        = now.getYear();
        int month       = now.getMonthValue();

        List<TbLmsMember> members = memberRepository.findByDeptIdxAndUseYn(deptIdx, "Y");
        if (members.isEmpty()) {
            throw new IllegalArgumentException("팀을 찾을 수 없습니다: " + deptIdx);
        }

        String teamName = members.get(0).getDeptName();

        // 팀 구성원 SKID 목록으로 사례 일괄 조회
        List<String> skids = members.stream()
                .map(TbLmsMember::getSkid)
                .collect(Collectors.toList());

        Map<String, TbLmsMember> memberMap = members.stream()
                .collect(Collectors.toMap(TbLmsMember::getSkid, m -> m));

        List<CaseResponse> allCases = caseService.getCasesBySkids(skids, memberMap::get);

        // 구성원별 사례 그룹핑
        Map<String, List<CaseResponse>> casesBySkid = allCases.stream()
                .collect(Collectors.groupingBy(CaseResponse::getSkid));

        List<TeamDetailResponse.MemberDetail> memberDetails = members.stream()
                .map(m -> TeamDetailResponse.MemberDetail.builder()
                        .id(m.getSkid())
                        .name(m.getMbName())
                        .position(m.getMbPositionName())
                        .totalSelected(caseService.countSelectedBySkidAndYear(m.getSkid(), year))
                        .monthlySelected(caseService.countSelectedBySkidAndYearMonth(
                                m.getSkid(), year, month))
                        .monthlyLimit(MONTHLY_LIMIT)
                        .pendingCount(caseService.countPendingBySkid(m.getSkid()))
                        .cases(casesBySkid.getOrDefault(m.getSkid(), List.of()))
                        .build())
                .collect(Collectors.toList());

        return TeamDetailResponse.builder()
                .team(TeamDetailResponse.TeamInfo.builder()
                        .id(deptIdx)
                        .name(teamName)
                        .build())
                .members(memberDetails)
                .build();
    }

}
