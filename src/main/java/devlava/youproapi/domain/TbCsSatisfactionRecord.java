package devlava.youproapi.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "tb_cs_satisfaction_record")
public class TbCsSatisfactionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 열 0 — 자회사구분 */
    @Column(name = "subsidiary_type", length = 100)
    private String subsidiaryType;

    /** 열 1 — 센터 */
    @Column(name = "center_name", length = 100)
    private String centerName;

    /** 열 2 — 그룹 */
    @Column(name = "group_name", length = 100)
    private String groupName;

    /** 열 3 — 실 */
    @Column(name = "room_name", length = 100)
    private String roomName;

    /** 열 4 — 상담사ID */
    @Column(name = "skid", nullable = false, length = 64)
    private String skid;

    /** 열 5 — 상담일자 (YYYYMMDD → LocalDate) */
    @Column(name = "eval_date", nullable = false)
    private LocalDate evalDate;

    /** 열 6 — 상담시간 (숫자 문자열, 예: 82817) */
    @Column(name = "consult_time", length = 20)
    private String consultTime;

    /** 열 7 — 상담유형1 */
    @Column(name = "consult_type1", length = 200)
    private String consultType1;

    /** 열 8 — 상담유형2 */
    @Column(name = "consult_type2", length = 200)
    private String consultType2;

    /** 열 9 — 상담유형3 */
    @Column(name = "consult_type3", length = 200)
    private String consultType3;

    /** 열 10 — 불만족유형 (1~5) */
    @Column(name = "dissatisfaction_type")
    private Integer dissatisfactionType;

    /** 열 11 — 스킬 */
    @Column(name = "skill", length = 200)
    private String skill;

    /** 열 12 — 긍정코멘트 */
    @Column(name = "good_ment", columnDefinition = "text")
    private String goodMent;

    /** 열 13 — 부정코멘트 */
    @Column(name = "bad_ment", columnDefinition = "text")
    private String badMent;

    /** 열 14 — 만족여부 (Y/N) */
    @Column(name = "satisfied_yn", nullable = false, length = 1)
    private String satisfiedYn;

    /** 열 15 — 5대도시 (Y/N) */
    @Column(name = "five_major_cities_yn", length = 1)
    private String fiveMajorCitiesYn;

    /** 열 16 — 5060 (Y/N) */
    @Column(name = "gen_5060_yn", length = 1)
    private String gen5060Yn;

    @Column(name = "problem_resolved_yn", length = 1)
    private String problemResolvedYn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
