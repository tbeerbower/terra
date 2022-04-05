package com.github.tbeerbower.entities;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Project {
    @Id
    private Integer projectId;
    private String name;

    // TODO : relationships
//    @ManyToMany
//    private Set<Employee> employees;
}
