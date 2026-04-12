package devlava.youproapi.repository;

import devlava.youproapi.domain.TbCsSatisfactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface TbCsSatisfactionRecordRepository extends JpaRepository<TbCsSatisfactionRecord, Long> {

    List<TbCsSatisfactionRecord> findByEvalDateBetweenAndSkidIn(
            LocalDate from,
            LocalDate to,
            Collection<String> skids);

    List<TbCsSatisfactionRecord> findByEvalDateBetween(LocalDate from, LocalDate to);

    /**
     * 파일에 포함된 평가일의 기존 행만 제거.
     * 파생 메서드 deleteBy…In 은 선조회 후 엔티티 단위 삭제라, 플러시 타이밍에 새로 넣은 행까지 잡히는 경우가 있어
     * 단일 벌크 DELETE 로 처리한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TbCsSatisfactionRecord r WHERE r.evalDate IN :dates")
    int bulkDeleteByEvalDateIn(@Param("dates") Collection<LocalDate> dates);
}
