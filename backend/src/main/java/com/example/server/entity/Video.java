package com.example.server.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.lang.Nullable;

import javax.persistence.*;

@Data
@Entity
@Table(name = "videos")
@AllArgsConstructor
@NoArgsConstructor
public class Video {
    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    private String id;

    @ManyToOne
    @JoinColumn
    private User writer;

    private String fileType;

    private String filePath;

    private String fileName;

    private String thumbnailPath;

    private boolean privacyStatus;

    @Column(nullable = true)
    private int maxFrame;

    @Column(nullable = true)
    private int fps;

    @Column(nullable = true)
    private int width;

    @Column(nullable = true)
    private int height;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "result_id")
    private UrbanResult result;

    private String tag;

}
