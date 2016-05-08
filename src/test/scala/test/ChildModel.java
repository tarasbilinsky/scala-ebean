package test;

import com.avaje.ebean.Model;

import javax.persistence.Entity;


@Entity
public class ChildModel extends Model {
    public String fld;
}
