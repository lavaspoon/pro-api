package devlava.youproapi.repository;

import devlava.youproapi.domain.TbLmsDept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TbLmsDeptRepository extends JpaRepository<TbLmsDept, Integer> {

    /**
     * 부서 전체 + parent 한 번에 로드 (트리 구성 시 N+1 방지)
     */
    @Query("SELECT DISTINCT d FROM TbLmsDept d LEFT JOIN FETCH d.parent")
    List<TbLmsDept> findAllWithParentFetched();
    /**
     * 특정 상태의 부서 조회
     * - 단일 쿼리 실행
     * - N+1 문제 없음
     */
    List<TbLmsDept> findByUseYn(String useYn);

    /**
     * 부서명으로 부서 조회
     */
    List<TbLmsDept> findByDeptName(String deptName);
}
