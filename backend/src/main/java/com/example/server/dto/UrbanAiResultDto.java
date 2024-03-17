package com.example.server.dto;


import lombok.Data;

import java.util.List;

@Data
public class UrbanAiResultDto {

    private String taskId;

    private String taskStatus;

    private List<List<String>> result;

}
