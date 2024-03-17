package com.example.server.controller;


import com.example.server.dto.JwtResponse;
import com.example.server.dto.LoginDto;
import com.example.server.dto.SignUpDto;
import com.example.server.dto.UserDto;
import com.example.server.entity.Role;
import com.example.server.entity.User;
import com.example.server.jwt.JwtTokenProvider;
import com.example.server.repository.RoleRepository;
import com.example.server.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final AuthenticationManager authenticationManager;

    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtTokenProvider tokenProvider;

    public UserController(AuthenticationManager authenticationManager, UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> authenticateUser(@RequestBody LoginDto loginDto){
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDto.getUsername(), loginDto.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // tokenProvider 를 통해 token 받음
        String token = tokenProvider.generateToken(authentication);

        return ResponseEntity.ok(new JwtResponse(loginDto.getUsername(), token));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody SignUpDto signUpDto){
        // 사용자 이름이 DB에 있는지 확인 추가
        if(userRepository.existsByUsername(signUpDto.getUsername())){
            return new ResponseEntity<>("이미 사용중인 username 입니다", HttpStatus.BAD_REQUEST);
        }

        // 객체 생성
        User user = new User();
        user.setUsername(signUpDto.getUsername());
        user.setPassword(passwordEncoder.encode(signUpDto.getPassword()));

        Role roles = roleRepository.findByName("ROLE_USER").get();
        user.setRoles(Collections.singleton(roles));

        userRepository.save(user);

        return new ResponseEntity<>("가입 성공", HttpStatus.OK);
    }

    @GetMapping("/username")
    public String currentUserName(Principal principal) {
        //  principal 을 이용해 접속중인 user 의 정보를 가져올수 있음
        return principal.getName();
    }

    @GetMapping("/auth")
    public Set<Role> currentUserAuth(Principal principal){
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() ->
                    new UsernameNotFoundException("User not found username: " + principal.getName())
                );

        return user.getRoles();
    }

    @GetMapping("/list")
    public ResponseEntity<List<UserDto>> getAllUsers(){
        try{
            List<UserDto> users = new ArrayList<UserDto>();
            for (User user : userRepository.findAll()) {
                UserDto userDto = new UserDto();
                userDto.setId(user.getId());
                userDto.setUsername(user.getUsername());
                userDto.setRoles(user.getRoles());
                users.add(userDto);
            }
            if(users.isEmpty()){
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<>(users, HttpStatus.OK);
        }catch(Exception e){
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
