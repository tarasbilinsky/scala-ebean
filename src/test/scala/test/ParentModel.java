package test;

import io.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import java.util.List;


@Entity
public class ParentModel extends Model {
    public String name;
    @ManyToOne
    public ChildModel child;

    @ManyToMany
    public List<ChildModel> children;
}
