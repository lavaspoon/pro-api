package devlava.youproapi.domain;

import lombok.*;

import javax.persistence.*;

/**
 * STT(Speech-To-Text) 결과 — {@code ragdb.TB_YOU_PRO_STT}
 *
 * <p>통화 매칭 키: 사례 {@code skid}, {@code call_date}에서 정규화한 일시 숫자열과 {@code reg_date} 비교.
 */
@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "TB_YOU_PRO_STT")
public class TbYouProStt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stt_id")
    private Long sttId;

    @Column(name = "skid", length = 30)
    private String skid;

    @Column(name = "reg_date", length = 30)
    private String regDate;

    @Lob
    @Column(name = "stt", columnDefinition = "TEXT")
    private String stt;
}
