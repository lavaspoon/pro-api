package devlava.youproapi.domain;

import lombok.*;

import javax.persistence.*;

/**
 * STT(Speech-To-Text) 결과 — {@code ragdb."유선_유프로_STT"}
 *
 * <p>통화 매칭 키: {@code 일자}(YYYYMMDD), {@code 상담시간}(HHMM, 예: 1600).
 */
@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "유선_유프로_STT")
public class TbYouStt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Num")
    private Long num;

    @Lob
    @Column(name = "STT원본", columnDefinition = "TEXT")
    private String sttOriginal;

    @Column(name = "일자", length = 20)
    private String ilja;

    @Column(name = "상담시간", length = 20)
    private String sangdamSiGan;

    @Column(name = "상담사ID", length = 20)
    private String sangdamSaId;
}
