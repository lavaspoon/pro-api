package devlava.youproapi.service;

import devlava.youproapi.domain.TbLmsDept;
import devlava.youproapi.domain.TbLmsMember;
import devlava.youproapi.dto.DeptListResponse;
import devlava.youproapi.dto.DeptResponse;
import devlava.youproapi.dto.MemberListResponse;
import devlava.youproapi.repository.TbLmsDeptRepository;
import devlava.youproapi.repository.TbLmsMemberRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeptService {

    private final TbLmsDeptRepository deptRepository;
    private final TbLmsMemberRepository memberRepository;

    /**
     * 계층형 부서 구조 조회 (N+1 문제 없음)
     * 1. 모든 부서를 한 번의 쿼리로 조회
     * 2. 메모리에서 트리 구조 생성
     */
    public List<DeptResponse> getDeptHierarchy() {
        // 1. 모든 부서를 한 번에 조회 (단일 쿼리)
        List<TbLmsDept> allDepts = deptRepository.findAll();

        // 2. DTO로 변환하면서 Map에 저장
        Map<Integer, DeptResponse> deptMap = new HashMap<>();
        for (TbLmsDept dept : allDepts) {
            DeptResponse deptResponse = DeptResponse.builder()
                    .id(dept.getDeptId())
                    .deptName(dept.getDeptName())
                    .parentDeptId(dept.getParent() != null ? dept.getParent().getDeptId() : null)
                    .depth(dept.getDepth())
                    .useYn(dept.getUseYn())
                    .children(new ArrayList<>())
                    .build();
            deptMap.put(dept.getDeptId(), deptResponse);
        }

        // 3. 부모-자식 관계 설정 및 최상위 부서 추출
        List<DeptResponse> rootDepts = new ArrayList<>();
        for (DeptResponse dept : deptMap.values()) {
            if (dept.getParentDeptId() == null) {
                // 최상위 부서
                rootDepts.add(dept);
            } else {
                // 자식 부서를 부모의 children에 추가
                DeptResponse parent = deptMap.get(dept.getParentDeptId());
                if (parent != null) {
                    parent.getChildren().add(dept);
                }
            }
        }

        return rootDepts;
    }

    /**
     * 특정 부서와 하위 부서 조회
     */
    public DeptResponse getDeptWithChildren(Integer deptId) {
        List<TbLmsDept> allDepts = deptRepository.findAll();

        Map<Integer, DeptResponse> deptMap = new HashMap<>();
        for (TbLmsDept dept : allDepts) {
            DeptResponse deptResponse = DeptResponse.builder()
                    .id(dept.getDeptId())
                    .deptName(dept.getDeptName())
                    .parentDeptId(dept.getParent() != null ? dept.getParent().getDeptId() : null)
                    .depth(dept.getDepth())
                    .useYn(dept.getUseYn())
                    .children(new ArrayList<>())
                    .build();
            deptMap.put(dept.getDeptId(), deptResponse);
        }

        // 부모-자식 관계 설정
        for (DeptResponse dept : deptMap.values()) {
            if (dept.getParentDeptId() != null) {
                DeptResponse parent = deptMap.get(dept.getParentDeptId());
                if (parent != null) {
                    parent.getChildren().add(dept);
                }
            }
        }

        return deptMap.get(deptId);
    }

    /**
     * 전체 부서 목록 조회 (단순 목록)
     * - 단일 쿼리로 모든 부서 조회
     * - N+1 문제 없음
     */
    public List<DeptListResponse> getAllDepts() {
        // 1. 단일 쿼리로 모든 활성 부서 조회
        List<TbLmsDept> depts = deptRepository.findByUseYn("Y");

        // 2. DTO 변환 (메모리에서 처리)
        return depts.stream()
                .map(dept -> DeptListResponse.builder()
                        .id(dept.getDeptId())
                        .deptName(dept.getDeptName())
                        .depth(dept.getDepth())
                        .parentDeptId(dept.getParent() != null ? dept.getParent().getDeptId() : null)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 특정 부서의 구성원 조회
     * - 단일 쿼리로 해당 부서의 활성 구성원 조회
     * - N+1 문제 없음
     */
    public List<MemberListResponse> getDeptMembers(Integer deptId) {
        // 1. 단일 쿼리로 해당 부서의 활성 구성원 조회
        List<TbLmsMember> members = memberRepository.findByDeptIdxAndUseYn(deptId, "Y");

        // 2. DTO 변환 (메모리에서 처리)
        return members.stream()
                .map(member -> MemberListResponse.builder()
                        .userId(member.getSkid())
                        .mbName(member.getMbName())
                        .deptName(member.getDeptName())
                        .deptIdx(member.getDeptIdx())
                        .mbPositionName(member.getMbPositionName())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 모든 활성 구성원 조회
     * - 단일 쿼리로 모든 활성 구성원 조회
     * - N+1 문제 없음
     * - 검색 기능 등에서 사용
     */
    public List<MemberListResponse> getAllMembers() {
        // 1. 단일 쿼리로 모든 활성 구성원 조회
        List<TbLmsMember> members = memberRepository.findByUseYn("Y");

        // 2. DTO 변환 (메모리에서 처리)
        return members.stream()
                .map(member -> MemberListResponse.builder()
                        .userId(member.getSkid())
                        .mbName(member.getMbName())
                        .deptName(member.getDeptName())
                        .deptIdx(member.getDeptIdx())
                        .mbPositionName(member.getMbPositionName())
                        .build())
                .collect(Collectors.toList());
    }
}
