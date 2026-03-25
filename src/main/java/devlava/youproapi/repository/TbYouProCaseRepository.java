package devlava.youproapi.repository;

import devlava.youproapi.domain.TbYouProCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TbYouProCaseRepository extends JpaRepository<TbYouProCase, Long> {

    /** 특정 구성원의 전체 사례 (최신순) */
    List<TbYouProCase> findBySkidOrderBySubmittedAtDesc(String skid);

    /** 특정 구성원 + 상태별 사례 */
    List<TbYouProCase> findBySkidAndStatus(String skid, String status);

    /** 전체 검토 대기(pending) 사례 (오래된 것 먼저) */
    List<TbYouProCase> findByStatusOrderBySubmittedAtAsc(String status);

    /** 연도별 접수(신청) 건수 — 상태 무관, 해당 연도 제출분 */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE FUNCTION('YEAR', c.submittedAt) = :year
           """)
    long countSubmittedByYear(@Param("year") int year);

    /** 연도별 선정 건수 (전체) */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.status = 'selected'
             AND FUNCTION('YEAR', c.submittedAt) = :year
           """)
    long countSelectedByYear(@Param("year") int year);

    /** 전 센터 월별 신청(접수) 건수 */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE FUNCTION('YEAR', c.submittedAt) = :year
             AND FUNCTION('MONTH', c.submittedAt) = :month
           """)
    long countSubmittedByYearMonth(@Param("year") int year, @Param("month") int month);

    /** 전 센터 월별 선정 건수 */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.status = 'selected'
             AND FUNCTION('YEAR', c.submittedAt) = :year
             AND FUNCTION('MONTH', c.submittedAt) = :month
           """)
    long countSelectedByYearMonth(@Param("year") int year, @Param("month") int month);

    /** 구성원별 연간 선정 건수 */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.skid = :skid
             AND c.status = 'selected'
             AND FUNCTION('YEAR', c.submittedAt) = :year
           """)
    long countSelectedBySkidAndYear(@Param("skid") String skid, @Param("year") int year);

    /** 구성원 월간 선정 건수 */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.skid = :skid
             AND c.status = 'selected'
             AND FUNCTION('YEAR', c.submittedAt) = :year
             AND FUNCTION('MONTH', c.submittedAt) = :month
           """)
    long countSelectedBySkidAndYearMonth(
            @Param("skid") String skid,
            @Param("year") int year,
            @Param("month") int month);

    /** 구성원 월간 검토 대기 건수 */
    long countBySkidAndStatus(String skid, String status);

    /** 특정 실(deptIdx) 소속 구성원들의 사례 목록 */
    @Query("""
           SELECT c FROM TbYouProCase c
           WHERE c.skid IN :skids
           ORDER BY c.submittedAt DESC
           """)
    List<TbYouProCase> findBySkidIn(@Param("skids") List<String> skids);

    /** 소속 구성원 기준, 해당 연도 판정 완료(선정+비선정) 건수 */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.status IN ('selected', 'rejected')
             AND FUNCTION('YEAR', c.submittedAt) = :year
           """)
    long countJudgedBySkidsInYear(@Param("skids") List<String> skids, @Param("year") int year);
}
