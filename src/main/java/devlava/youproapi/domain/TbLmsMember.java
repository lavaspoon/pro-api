package devlava.youproapi.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "TB_LMS_MEMBER")
public class TbLmsMember {

    @Id
    @Column(name = "skid")
    private String skid;

    @Column(name = "company")
    private String company;

    @Column(name = "mb_name")
    private String mbName;

    @Column(name = "mb_position")
    private Integer mbPosition;

    @Column(name = "dept_name")
    private String deptName;

    @Column(name = "mb_position_name")
    private String mbPositionName;

    @Column(name = "email")
    private String email;

    @Column(name = "use_yn")
    private String useYn;

    @Column(name = "dept_idx")
    private Integer deptIdx;

    @Column(name = "revel")
    private String revel;

    @Column(name = "com_code")
    private String comCode;

    @Column(name = "you_yn") //평가 대상자 여부
    private String youYn;

    @Column(name = "you_skill") // 평가 대상자 직무
    private String skill;

}
