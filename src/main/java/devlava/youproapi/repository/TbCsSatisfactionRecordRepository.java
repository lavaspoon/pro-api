package devlava.youproapi.repository;

import devlava.youproapi.domain.TbCsSatisfactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TbCsSatisfactionRecordRepository extends JpaRepository<TbCsSatisfactionRecord, Long> {

    List<TbCsSatisfactionRecord> findByEvalDateBetweenAndSkidIn(
            LocalDateTime from,
            LocalDateTime to,
            Collection<String> skids);

    List<TbCsSatisfactionRecord> findByEvalDateBetween(LocalDateTime from, LocalDateTime to);

    List<TbCsSatisfactionRecord> findByEvalDateBetweenAndSkid(
            LocalDateTime from,
            LocalDateTime to,
            String skid);

    /** 구성원 당월 만족도 집계 — {@code useYn='Y'} 인 레코드만 포함 */
    List<TbCsSatisfactionRecord> findByEvalDateBetweenAndSkidAndUseYn(
            LocalDateTime from,
            LocalDateTime to,
            String skid,
            String useYn);

    List<TbCsSatisfactionRecord> findByEvalDateBetweenAndSkidOrderByEvalDateDescIdDesc(
            LocalDateTime from,
            LocalDateTime to,
            String skid);

    List<TbCsSatisfactionRecord> findByEvalDateBetweenAndSkidAndDissatisfactionTypeOrderByEvalDateDescIdDesc(
            LocalDateTime from,
            LocalDateTime to,
            String skid,
            String dissatisfactionType);

    /**
     * {@code tb_you_cs}: 스킬·상담일시 구간 내 행의 평가시간(useYn)을 일괄 {@code N} 처리.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TbCsSatisfactionRecord r SET r.useYn = 'N' "
            + "WHERE r.skill = :skill AND r.evalDate >= :startAt AND r.evalDate <= :endAt")
    int setUseYnNForSkillAndEvalDateRange(
            @Param("skill") String skill,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

}
