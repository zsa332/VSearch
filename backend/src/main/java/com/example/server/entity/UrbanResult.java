package com.example.server.entity;

import com.vladmihalcea.hibernate.type.array.ListArrayType;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.util.List;

@Data
@Entity
@Table(name = "urban_results")
@NoArgsConstructor
@TypeDef(name = "list-array", typeClass = ListArrayType.class)
public class UrbanResult {
    @Id
    private String id;

    @OneToOne(mappedBy = "result")
    private Video video;

    private String status;

    @Type(type = "list-array")
    @Column(columnDefinition = "integer[]")
    private List<Integer> objectId;

    @Type(type = "list-array")
    @Column(columnDefinition = "text[]")
    private List<String> className;

    @Type(type = "list-array")
    @Column(columnDefinition = "text[]")
    private List<String> frameId;

    @Type(type = "list-array")
    @Column(columnDefinition = "text[]")
    private List<String> score;

    @Type(type = "list-array")
    @Column(columnDefinition = "text[]")
    private List<String> bbox;

    @Type(type = "list-array")
    @Column(columnDefinition = "text[]")
    private List<String> location;

    @Type(type = "list-array")
    @Column(columnDefinition = "text[]")
    private List<String> objectList;

    @Type(type = "list-array")
    @Column(columnDefinition = "text[]")
    private List<String> frameByObjStr;

    @Type(type = "list-array")
    @Column(columnDefinition = "integer[]")
    private List<Integer> frameByObj;

    @Type(type = "list-array")
    @Column(columnDefinition = "text[]")
    private List<String> frameByObjBitNum;

    private String vid;

    public UrbanResult(String id, Video video){
        this.id = id;
        this.video = video;
    }

}
