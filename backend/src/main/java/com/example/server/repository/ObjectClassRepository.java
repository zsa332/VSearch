package com.example.server.repository;

import com.example.server.entity.ObjectClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ObjectClassRepository extends JpaRepository<ObjectClass, Long> {
    Optional<ObjectClass> findByClassName(String className);
}
