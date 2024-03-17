package com.example.server.repository;


import com.example.server.entity.Video;
import com.example.server.entity.VideoMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, String> {
    Optional<Video> findByWriter(String username);

    @Query(value = "SELECT v.id as id, v.file_name as fileName, v.file_type as fileType FROM videos v WHERE id = :reid", nativeQuery=true)
    VideoMapping findByUrban(@Param("reid") String reid);
}
