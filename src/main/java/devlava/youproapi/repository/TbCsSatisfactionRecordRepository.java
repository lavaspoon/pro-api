package devlava.youproapi.repository;

import devlava.youproapi.domain.TbCsSatisfactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface TbCsSatisfactionRecordRepository extends JpaRepository<TbCsSatisfactionRecord, Long> {

    List<TbCsSatisfactionRecord> findByEvalDateBetweenAndSkidIn(
            LocalDate from,
            LocalDate to,
            Collection<String> skids);

    List<TbCsSatisfactionRecord> findByEvalDateBetween(LocalDate from, LocalDate to);

    List<TbCsSatisfactionRecord> findByEvalDateBetweenAndSkid(
            LocalDate from,
            LocalDate to,
            String skid);

    List<TbCsSatisfactionRecord> findByEvalDateBetweenAndSkidAndDissatisfactionTypeOrderByEvalDateDescIdDesc(
            LocalDate from,
            LocalDate to,
            String skid,
            Integer dissatisfactionType);

}
