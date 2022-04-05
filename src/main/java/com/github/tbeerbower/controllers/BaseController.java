package com.github.tbeerbower.controllers;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import javax.transaction.Transactional;
import java.util.List;

public class BaseController <T, I, R extends JpaRepository<T, I>>{

    private final R repository;

    public BaseController(R repository) {
        this.repository = repository;
    }

    @PostMapping
    @Transactional
    public T create(@RequestBody T entity) {
        return repository.save(entity);
    }

    @GetMapping("/{id}")
    public T get(@PathVariable I id) {
        return repository.findById(id).orElse(null);
    }

    @GetMapping
    public List<T> getAll() {
        return repository.findAll();
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable I id) {
        repository.delete(get(id));
    }

    @PutMapping("/{id}")
    @Transactional
    public T update(@RequestBody T entity, @PathVariable I id) {
        //TODO : generate an impl that sets the id on the entity and saves it
        //return repository.save(entity);
        return entity;
    }
}
