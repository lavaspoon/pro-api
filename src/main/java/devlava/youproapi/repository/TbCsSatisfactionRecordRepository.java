package devlava.youproapi.repository;

import devlava.youproapi.domain.TbCsSatisfactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

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

    List<TbCsSatisfactionRecord> findByEvalDateBetweenAndSkidOrderByEvalDateDescIdDesc(
            LocalDateTime from,
            LocalDateTime to,
            String skid);

    List<TbCsSatisfactionRecord> findByEvalDateBetweenAndSkidAndDissatisfactionTypeOrderByEvalDateDescIdDesc(
            LocalDateTime from,
            LocalDateTime to,
            String skid,
            Integer dissatisfactionType);

}
