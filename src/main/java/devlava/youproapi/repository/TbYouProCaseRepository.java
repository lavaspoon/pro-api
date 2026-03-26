package devlava.youproapi.repository;

import devlava.youproapi.domain.TbYouProCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TbYouProCaseRepository extends JpaRepository<TbYouProCase, Long> {

    /** 특정 구성원의 전체 사례 (최신순) */
    List<TbYouProCase> findBySkidOrderBySubmittedAtDesc(String skid);

    /** 특정 구성원 + 상태별 사례 */
    List<TbYouProCase> findBySkidAndStatus(String skid, String status);

    /** 전체 검토 대기(pending) 사례 (오래된 것 먼저) */
    List<TbYouProCase> findByStatusOrderBySubmittedAtAsc(String status);

    /** 특정 구성원 집합의 검토 대기 사례 */
    List<TbYouProCase> findByStatusAndSkidInOrderBySubmittedAtAsc(String status, Collection<String> skids);

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

    // ─── 관리자 스코프(구성원 IN) 배치 통계 — N+1 방지 ───────────────────────

    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND FUNCTION('YEAR', c.submittedAt) = :year
           """)
    long countSubmittedBySkidsAndYear(@Param("skids") Collection<String> skids, @Param("year") int year);

    /** 소속 구성원 기준, 해당 연·월 접수 건수(상태 무관) */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND FUNCTION('YEAR', c.submittedAt) = :year
             AND FUNCTION('MONTH', c.submittedAt) = :month
           """)
    long countSubmittedBySkidsAndYearMonth(
            @Param("skids") Collection<String> skids,
            @Param("year") int year,
            @Param("month") int month);

    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.status = 'selected'
             AND FUNCTION('YEAR', c.submittedAt) = :year
           """)
    long countSelectedBySkidsAndYear(@Param("skids") Collection<String> skids, @Param("year") int year);

    @Query("""
           SELECT FUNCTION('MONTH', c.submittedAt), COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND FUNCTION('YEAR', c.submittedAt) = :year
           GROUP BY FUNCTION('MONTH', c.submittedAt)
           """)
    List<Object[]> countSubmittedBySkidsGroupByMonth(@Param("skids") Collection<String> skids, @Param("year") int year);

    @Query("""
           SELECT FUNCTION('MONTH', c.submittedAt), COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.status = 'selected'
             AND FUNCTION('YEAR', c.submittedAt) = :year
           GROUP BY FUNCTION('MONTH', c.submittedAt)
           """)
    List<Object[]> countSelectedBySkidsGroupByMonth(@Param("skids") Collection<String> skids, @Param("year") int year);

    @Query("""
           SELECT c.skid, COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND FUNCTION('YEAR', c.submittedAt) = :year
           GROUP BY c.skid
           """)
    List<Object[]> countSubmittedBySkidsAndYearGroupBySkid(
            @Param("skids") Collection<String> skids,
            @Param("year") int year);

    @Query("""
           SELECT c.skid, COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND FUNCTION('YEAR', c.submittedAt) = :year
             AND FUNCTION('MONTH', c.submittedAt) = :month
           GROUP BY c.skid
           """)
    List<Object[]> countSubmittedBySkidsAndYearMonthGroupBySkid(
            @Param("skids") Collection<String> skids,
            @Param("year") int year,
            @Param("month") int month);

    @Query("""
           SELECT c.skid, COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.status = 'selected'
             AND FUNCTION('YEAR', c.submittedAt) = :year
           GROUP BY c.skid
           """)
    List<Object[]> countSelectedBySkidsAndYearGroupBySkid(@Param("skids") Collection<String> skids, @Param("year") int year);

    @Query("""
           SELECT c.skid, COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.status = 'selected'
             AND FUNCTION('YEAR', c.submittedAt) = :year
             AND FUNCTION('MONTH', c.submittedAt) = :month
           GROUP BY c.skid
           """)
    List<Object[]> countSelectedBySkidsAndYearMonthGroupBySkid(
            @Param("skids") Collection<String> skids,
            @Param("year") int year,
            @Param("month") int month);

    @Query("""
           SELECT c.skid, COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.status = 'pending'
           GROUP BY c.skid
           """)
    List<Object[]> countPendingBySkidsGroupBySkid(@Param("skids") Collection<String> skids);

    @Query("""
           SELECT c.skid, COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.status IN ('selected', 'rejected')
             AND FUNCTION('YEAR', c.submittedAt) = :year
           GROUP BY c.skid
           """)
    List<Object[]> countJudgedBySkidsAndYearGroupBySkid(@Param("skids") Collection<String> skids, @Param("year") int year);

    // ─── PostgreSQL 전용: 관리자 대시보드 통계 배치 (스코프 skids) ─────────────

    /**
     * 연도·스코프 기준 월별 접수(전체) / 선정 건수를 한 번에 집계.
     * (기존 countSubmittedBySkidsGroupByMonth + countSelectedBySkidsGroupByMonth 2회 → 1회)
     */
    @Query(value = """
            SELECT CAST(EXTRACT(MONTH FROM submitted_at) AS INTEGER) AS m,
                   COUNT(*) AS submitted,
                   COUNT(*) FILTER (WHERE status = 'selected') AS selected
            FROM tb_youpro_case
            WHERE skid IN (:skids)
              AND EXTRACT(YEAR FROM submitted_at) = :year
            GROUP BY EXTRACT(MONTH FROM submitted_at)
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> aggregateMonthlySubmittedAndSelectedBySkidsAndYear(
            @Param("skids") Collection<String> skids,
            @Param("year") int year);

    /**
     * 스코프 구성원별 선정(연/월)·대기·연도판정 건수를 한 번에 집계.
     * (기존 skid별 GROUP BY 쿼리 4회 → 1회)
     */
    @Query(value = """
            SELECT skid,
                   COUNT(*) FILTER (WHERE status = 'selected'
                       AND EXTRACT(YEAR FROM submitted_at) = :year) AS sel_year,
                   COUNT(*) FILTER (WHERE status = 'selected'
                       AND EXTRACT(YEAR FROM submitted_at) = :year
                       AND EXTRACT(MONTH FROM submitted_at) = :month) AS sel_month,
                   COUNT(*) FILTER (WHERE status = 'pending') AS pending,
                   COUNT(*) FILTER (WHERE status IN ('selected', 'rejected')
                       AND EXTRACT(YEAR FROM submitted_at) = :year) AS judged
            FROM tb_youpro_case
            WHERE skid IN (:skids)
            GROUP BY skid
            """, nativeQuery = true)
    List<Object[]> aggregatePerSkidDashboardMetrics(
            @Param("skids") Collection<String> skids,
            @Param("year") int year,
            @Param("month") int month);
}
