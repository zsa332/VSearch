package com.example.server.service;

import com.example.server.entity.ObjectClass;
import com.example.server.entity.Video;
import com.example.server.exception.NotFoundException;
import com.example.server.exception.VideoNotFoundException;
import com.example.server.repository.ObjectClassRepository;
import org.springframework.stereotype.Service;

@Service
public class ObjectClassService {

    private final ObjectClassRepository objectClassRepository;

    public ObjectClassService(ObjectClassRepository objectClassRepository) {
        this.objectClassRepository = objectClassRepository;
    }

    public ObjectClass getObject(String className) {
        return objectClassRepository.findByClassName(className)
                .orElseThrow(() -> new NotFoundException("Not found with id " + className));
    }
}
