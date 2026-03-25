package devlava.youproapi.config.datasource;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * STT DataSource 설정 (st_etc DB)
 *
 * <p>동일한 PostgreSQL 인스턴스의 {@code st_etc} 데이터베이스에 연결.
 * STT(Speech-To-Text) 결과 테이블을 읽기 전용으로 조회한다.
 *
 * <ul>
 *   <li>엔티티 패키지: {@code devlava.youproapi.domain.stt}</li>
 *   <li>리포지토리 패키지: {@code devlava.youproapi.repository.stt}</li>
 *   <li>DDL: {@code none} (구조 변경 불가 — 외부 STT 시스템 소유 테이블)</li>
 * </ul>
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "devlava.youproapi.stt.repository",
        entityManagerFactoryRef = "sttEntityManagerFactory",
        transactionManagerRef = "sttTransactionManager"
)
public class SttDataSourceConfig {

    // ─── DataSource ──────────────────────────────────────────────────────────

    /**
     * st_etc DB 에 연결하는 HikariCP DataSource.
     * p6spy-spring-boot-starter 가 이 Bean도 자동으로 래핑한다.
     */
    @Bean("sttDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.stt")
    public DataSource sttDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }

    // ─── EntityManagerFactory ────────────────────────────────────────────────

    @Bean("sttEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean sttEntityManagerFactory(
            @Qualifier("sttDataSource") DataSource dataSource) {

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(true);

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setJpaVendorAdapter(vendorAdapter);
        // domain.stt 는 primary 스캔 경로(domain)의 하위 패키지이므로 독립 패키지로 분리
        em.setPackagesToScan("devlava.youproapi.stt.domain");
        em.setPersistenceUnitName("stt");
        em.setJpaPropertyMap(sttJpaProperties());
        return em;
    }

    private Map<String, Object> sttJpaProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.hbm2ddl.auto", "none");
        props.put("hibernate.show_sql", true);
        props.put("hibernate.format_sql", true);
        props.put("hibernate.use_sql_comments", true);
        props.put("hibernate.jdbc.lob.non_contextual_creation", true);
        return props;
    }

    // ─── TransactionManager ──────────────────────────────────────────────────

    @Bean("sttTransactionManager")
    public PlatformTransactionManager sttTransactionManager(
            @Qualifier("sttEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    // ─── QueryDSL ────────────────────────────────────────────────────────────

    /**
     * STT DB 용 JPAQueryFactory.
     * 사용 시 {@code @Qualifier("sttQueryFactory")} 로 주입.
     */
    @Bean("sttQueryFactory")
    public JPAQueryFactory sttQueryFactory(
            @Qualifier("sttEntityManagerFactory") EntityManagerFactory emf) {
        EntityManager sharedEm =
                org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(emf);
        return new JPAQueryFactory(sharedEm);
    }
}
