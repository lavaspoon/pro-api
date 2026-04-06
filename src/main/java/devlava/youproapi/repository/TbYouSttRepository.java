package devlava.youproapi.repository;

import devlava.youproapi.domain.TbYouProStt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * {@code TB_YOU_PRO_STT} 조회 (단일 DB ragdb).
 * <p>PostgreSQL / MS SQL 모두에서 동작하도록 DB별 {@code regexp_replace} 등은 사용하지 않고,
 * {@code skid} 로만 적재한 뒤 {@link devlava.youproapi.service.SttService} 에서 {@code reg_date} 숫자 정규화 매칭을 수행한다.
 */
public interface TbYouSttRepository extends JpaRepository<TbYouProStt, Long> {

    List<TbYouProStt> findBySkidOrderBySttIdAsc(String skid);
}
