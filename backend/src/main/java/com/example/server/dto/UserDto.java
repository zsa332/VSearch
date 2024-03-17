package com.example.server.dto;

import com.example.server.entity.Role;
import lombok.Data;

import java.util.Set;

@Data
public class UserDto {

    private Long id;

    private String username;

    private Set<Role> roles;

    public void setRoles(Set<Role> roles) {
    }
}
