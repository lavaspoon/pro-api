package devlava.youproapi.repository;

import devlava.youproapi.domain.TbCsDissatisfactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TbCsDissatisfactionTypeRepository extends JpaRepository<TbCsDissatisfactionType, Long> {

    Optional<TbCsDissatisfactionType> findByTypeName(String typeName);
}
