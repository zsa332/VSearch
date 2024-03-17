package com.example.server.entity;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "object_classes")
public class ObjectClass {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private int binaryInt;

    private String className;

    private String korClassName;

}
