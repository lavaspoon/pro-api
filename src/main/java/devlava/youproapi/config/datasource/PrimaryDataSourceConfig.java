package devlava.youproapi.config.datasource;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
 * Primary DataSource 설정 (ragdb)
 *
 * <p>기존 애플리케이션의 메인 DB 커넥션. {@code devlava.youproapi.domain} 패키지의
 * 핵심 도메인 엔티티(TbLmsMember, AHrdb 등)를 관리한다.
 *
 * <p><b>패키지 분리 원칙:</b>
 * <ul>
 *   <li>Primary 엔티티 : {@code devlava.youproapi.domain}</li>
 *   <li>STT 엔티티     : {@code devlava.youproapi.stt.domain} (별도 패키지 — 스캔 경로 중복 없음)</li>
 *   <li>Primary 리포지토리 : {@code devlava.youproapi.repository}</li>
 *   <li>STT 리포지토리    : {@code devlava.youproapi.stt.repository} (별도 패키지)</li>
 * </ul>
 * STT 관련 패키지가 Primary 스캔 경로와 겹치지 않으므로 excludeFilter 불필요.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "devlava.youproapi.repository",
        entityManagerFactoryRef = "primaryEntityManagerFactory",
        transactionManagerRef = "primaryTransactionManager"
)
public class PrimaryDataSourceConfig {

    // ─── DataSource ──────────────────────────────────────────────────────────

    /**
     * ragdb 에 연결하는 Primary HikariCP DataSource.
     * p6spy-spring-boot-starter 가 이 Bean을 자동으로 래핑한다.
     */
    @Primary
    @Bean("primaryDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.primary")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }

    // ─── EntityManagerFactory ────────────────────────────────────────────────

    @Primary
    @Bean("primaryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            @Qualifier("primaryDataSource") DataSource dataSource) {

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(true);

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setJpaVendorAdapter(vendorAdapter);
        // stt.domain 은 별도 패키지이므로 재귀 스캔 시에도 포함되지 않음
        em.setPackagesToScan("devlava.youproapi.domain");
        em.setPersistenceUnitName("primary");
        em.setJpaPropertyMap(primaryJpaProperties());
        return em;
    }

    private Map<String, Object> primaryJpaProperties() {
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

    @Primary
    @Bean("primaryTransactionManager")
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    // ─── QueryDSL ────────────────────────────────────────────────────────────

    /**
     * Primary DB 용 JPAQueryFactory.
     * {@code @Primary} 이므로 타입 주입 시 자동으로 선택됨.
     */
    @Primary
    @Bean("primaryQueryFactory")
    public JPAQueryFactory primaryQueryFactory(
            @Qualifier("primaryEntityManagerFactory") EntityManagerFactory emf) {
        EntityManager sharedEm =
                org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(emf);
        return new JPAQueryFactory(sharedEm);
    }
}
