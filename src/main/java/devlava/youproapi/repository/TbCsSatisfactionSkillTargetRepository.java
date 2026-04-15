package devlava.youproapi.repository;

import devlava.youproapi.domain.TbCsSatisfactionSkillTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TbCsSatisfactionSkillTargetRepository extends JpaRepository<TbCsSatisfactionSkillTarget, Long> {

    Optional<TbCsSatisfactionSkillTarget> findByTargetDateAndSkillName(LocalDate targetDate, String skillName);

    List<TbCsSatisfactionSkillTarget> findByTargetDate(LocalDate targetDate);
}
