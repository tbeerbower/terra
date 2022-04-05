package com.github.tbeerbower.entities;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Department {

    @Id
    private Long id;
    private String name;

    // TODO : relationships
//    @OneToMany
//    private Set<Employee> employees;
}
