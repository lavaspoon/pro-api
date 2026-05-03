package devlava.youproapi.repository;

import devlava.youproapi.domain.TbCsSatisfactionEvalExcludeLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TbCsSatisfactionEvalExcludeLogRepository extends JpaRepository<TbCsSatisfactionEvalExcludeLog, Long> {

    List<TbCsSatisfactionEvalExcludeLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
