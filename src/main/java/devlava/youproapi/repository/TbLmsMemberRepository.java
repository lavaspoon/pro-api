package devlava.youproapi.repository;

import devlava.youproapi.domain.TbLmsMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TbLmsMemberRepository extends JpaRepository<TbLmsMember, String> {
    /**
     * 특정 부서의 특정 상태 구성원 조회
     * - 단일 쿼리 실행
     * - N+1 문제 없음
     */
    List<TbLmsMember> findByDeptIdxAndUseYn(Integer deptIdx, String useYn);
    
    /**
     * 모든 활성 구성원 조회
     * - 단일 쿼리 실행
     * - N+1 문제 없음
     */
    List<TbLmsMember> findByUseYn(String useYn);

    /**
     * 부서 ID 집합에 속한 활성 구성원 (IN 단일 쿼리)
     */
    List<TbLmsMember> findByUseYnAndDeptIdxIn(String useYn, Collection<Integer> deptIdxes);

    /**
     * 부서가 집합에 속하고 평가대상자({@code you_yn})인 구성원 수 — 리프 팀 서브트리 dept_idx 집합에 대해 합산
     */
    long countByDeptIdxInAndYouYn(Collection<Integer> deptIdxes, String youYn);

    /**
     * 활성 구성원 중 부서가 집합에 속하고 {@code you_yn} 이 일치하는 목록 — 팀별 상세·만족도 구성원 로스터용
     */
    List<TbLmsMember> findByUseYnAndDeptIdxInAndYouYn(
            String useYn, Collection<Integer> deptIdxes, String youYn);
}