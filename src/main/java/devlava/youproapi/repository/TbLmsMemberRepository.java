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
}