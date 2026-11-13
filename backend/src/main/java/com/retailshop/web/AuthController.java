package com.retailshop.web;

import com.retailshop.common.BusinessException;
import com.retailshop.dto.Requests;
import com.retailshop.dto.Responses.*;
import com.retailshop.repository.UserRepository;
import com.retailshop.security.CurrentUser;
import com.retailshop.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthController(AuthenticationManager authManager, JwtService jwt, UserRepository users, PasswordEncoder encoder) {
        this.authManager = authManager;
        this.jwt = jwt;
        this.users = users;
        this.encoder = encoder;
    }

    @PostMapping("/login")
    public LoginResp login(@Valid @RequestBody Requests.Login req) {
        authManager.authenticate(new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        var u = users.findByUsername(req.username()).orElseThrow();
        return new LoginResp(jwt.generate(u.getUsername(), u.getRole().name()), UserView.of(u));
    }

    @GetMapping("/me")
    public UserView me() {
        return UserView.of(users.findByUsername(CurrentUser.username()).orElseThrow());
    }

    @PostMapping("/change-password")
    @Transactional
    public void changePassword(@Valid @RequestBody Requests.ChangePassword req) {
        var u = users.findByUsername(CurrentUser.username()).orElseThrow();
        if (!encoder.matches(req.currentPassword(), u.getPassword())) throw new BusinessException("Current password is incorrect");
        u.setPassword(encoder.encode(req.newPassword()));
    }
}
