package devlava.youproapi.stt.repository;

import devlava.youproapi.stt.domain.TbSttResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * STT 결과 리포지토리 — {@code st_etc} DB 사용
 *
 * <p>{@code transactionManager = "sttTransactionManager"} 를 명시하여
 * Primary 트랜잭션 관리자가 잘못 바인딩되는 것을 방지한다.
 */
@Transactional(transactionManager = "sttTransactionManager", readOnly = true)
public interface TbSttResultRepository extends JpaRepository<TbSttResult, String> {

    /**
     * 통화시간 + 상담사 skid 정확 일치 조회.
     *
     * <p>STT 테이블의 {@code call_time}은 VARCHAR 이므로 문자열 비교.
     * {@link devlava.youproapi.stt.service.SttService}에서 정규화된 값을 전달한다.
     *
     * @param callTime  정규화된 통화시각 문자열 (숫자만 — 예: {@code "20260305093000"})
     * @param agentSkid 상담사 skid
     */
    Optional<TbSttResult> findByCallTimeAndAgentSkid(String callTime, String agentSkid);

    /**
     * 통화시간 LIKE 검색 — 포맷 또는 초 단위가 일치하지 않을 때 사용.
     *
     * <p>예: DB 값이 {@code "20260305093000"} 이고 사용자 입력이 {@code "2026-03-05 09:30"}
     * → 정규화 후 {@code "202603050930%"} 로 검색.
     *
     * @param callTimePrefix {@code %} 는 서비스 레이어에서 붙임
     * @param agentSkid      상담사 skid
     */
    @Query("""
            SELECT r FROM TbSttResult r
            WHERE r.callTime LIKE :callTimePrefix
              AND r.agentSkid = :agentSkid
            ORDER BY r.callTime ASC
            """)
    List<TbSttResult> findByCallTimeLikeAndAgentSkid(
            @Param("callTimePrefix") String callTimePrefix,
            @Param("agentSkid") String agentSkid);

    /**
     * 상담사 skid 기준 최근 10건 (수동 선택 UI 제공용).
     *
     * @param agentSkid 상담사 skid
     */
    List<TbSttResult> findTop10ByAgentSkidOrderByCallTimeDesc(String agentSkid);
}
