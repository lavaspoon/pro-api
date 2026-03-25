package devlava.youproapi.repository;

import devlava.youproapi.domain.TbYouProRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TbStmsRoleRepository extends JpaRepository<TbYouProRole, String> {
    /**
     * skid로 역할 조회
     */
    Optional<TbYouProRole> findBySkid(String skid);
}
