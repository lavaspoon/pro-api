package devlava.youproapi.config;

/**
 * QueryDSL 설정은 각 DataSource Config 에서 관리합니다.
 *
 * <ul>
 *   <li>Primary(ragdb) : {@code primaryQueryFactory} — {@link devlava.youproapi.config.datasource.PrimaryDataSourceConfig}</li>
 *   <li>STT(st_etc)    : {@code sttQueryFactory}     — {@link devlava.youproapi.config.datasource.SttDataSourceConfig}</li>
 * </ul>
 *
 * <p>주입 시 {@code @Primary} Bean(primaryQueryFactory)은 타입만으로 주입 가능하며,
 * STT QueryFactory 는 {@code @Qualifier("sttQueryFactory")} 로 구분합니다.
 */
public class QueryDslConfig {
    // 빈 정의는 PrimaryDataSourceConfig / SttDataSourceConfig 에 위임
}
