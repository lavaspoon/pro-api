package devlava.youproapi.repository;

import devlava.youproapi.domain.TbYouStt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * {@code 유선_유프로_STT} 조회 (단일 DB ragdb).
 * 일자·상담시간은 사용자 입력(통화 일시)에서 정규화한 YYYYMMDD / HHMM 과 매칭한다.
 */
public interface TbYouSttRepository extends JpaRepository<TbYouStt, Long> {

    @Query(value = """
            SELECT * FROM "유선_유프로_STT" t
            WHERE regexp_replace(COALESCE(t."일자", ''), '[^0-9]', '', 'g') = :ilja
              AND regexp_replace(COALESCE(t."상담시간", ''), '[^0-9]', '', 'g') = :sangdamSiGan
            ORDER BY t."Num" ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<TbYouStt> findFirstByIlJaAndSangdamSiGan(@Param("ilja") String iljaYyyymmdd,
                                                    @Param("sangdamSiGan") String sangdamHhmm);

    @Query(value = """
            SELECT * FROM "유선_유프로_STT" t
            WHERE regexp_replace(COALESCE(t."일자", ''), '[^0-9]', '', 'g') = :ilja
              AND regexp_replace(COALESCE(t."상담시간", ''), '[^0-9]', '', 'g') LIKE :sangdamPrefix
            ORDER BY t."Num" ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<TbYouStt> findFirstByIlJaAndSangdamSiGanPrefix(@Param("ilja") String iljaYyyymmdd,
                                                          @Param("sangdamPrefix") String sangdamPrefix);
}
