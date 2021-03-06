package com.github.tbeerbower.entities;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Employee {

    @Id
    private Long id;

    private String firstName;
    private String lastName;
    private String email;

    // TODO : relationships
//    @ManyToOne
//    private Department department;
//
//    @ManyToMany
//    private Set<Project> projects;
}
