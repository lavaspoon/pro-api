package devlava.youproapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * YouPro API 메인 애플리케이션
 *
 * <h3>다중 DataSource 구성</h3>
 * <p>Primary(ragdb)와 STT(st_etc) 두 개의 DataSource를 직접 관리하므로
 * Spring Boot의 JPA/DataSource 자동 구성을 제외한다.
 * p6spy({@code DataSourceDecoratorAutoConfiguration})는 제외하지 않아
 * SQL 로깅이 두 DataSource 모두에 적용된다.
 *
 * <ul>
 *   <li>Primary DataSource 설정 : {@link devlava.youproapi.config.datasource.PrimaryDataSourceConfig}</li>
 *   <li>STT DataSource 설정     : {@link devlava.youproapi.config.datasource.SttDataSourceConfig}</li>
 * </ul>
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
public class YouproApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(YouproApiApplication.class, args);
    }
}
