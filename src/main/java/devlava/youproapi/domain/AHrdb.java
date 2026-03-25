package devlava.youproapi.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

/**
 * A_HRDB 테이블 (skid와 1:1 매핑, mobile은 base64 인코딩된 휴대폰 번호)
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "A_HRDB")
public class AHrdb {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "skid")
    private String skid;

    @Column(name = "mobile", length = 500)
    private String mobile; // base64 인코딩된 휴대폰 번호
}
