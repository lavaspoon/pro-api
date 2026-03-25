
package devlava.youproapi.domain;

import lombok.Getter;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "TB_LMS_DEPT")
public class TbLmsDept {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dept_id")
    private Integer deptId;

    @Column(name = "dept_name")
    private String deptName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_dept_id")
    private TbLmsDept parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private List<TbLmsDept> childs = new ArrayList<>();

    @Column(name = "depth")
    private Integer depth;

    @Column(name = "use_yn")
    private String useYn;
}