package com.retailshop.web;

import com.retailshop.common.BusinessException;
import com.retailshop.common.NotFoundException;
import com.retailshop.domain.User;
import com.retailshop.dto.Requests;
import com.retailshop.dto.Responses.UserView;
import com.retailshop.repository.UserRepository;
import com.retailshop.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize(Roles.ADMIN)
@Transactional
public class UserController {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping
    public List<UserView> list() {
        return users.findAll().stream().map(UserView::of).toList();
    }

    @PostMapping
    public UserView create(@Valid @RequestBody Requests.UserReq req) {
        if (users.existsByUsername(req.username())) throw new BusinessException("Username already taken");
        if (req.password() == null || req.password().length() < 6) throw new BusinessException("Password must be at least 6 characters");
        User u = new User();
        u.setUsername(req.username().trim());
        u.setPassword(encoder.encode(req.password()));
        apply(u, req);
        return UserView.of(users.save(u));
    }

    @PutMapping("/{id}")
    public UserView update(@PathVariable Long id, @Valid @RequestBody Requests.UserReq req) {
        User u = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        if (u.getUsername().equals(CurrentUser.username()) && (Boolean.FALSE.equals(req.active()) || req.role() != u.getRole())) {
            throw new BusinessException("You cannot deactivate yourself or change your own role");
        }
        if (req.password() != null && !req.password().isBlank()) {
            if (req.password().length() < 6) throw new BusinessException("Password must be at least 6 characters");
            u.setPassword(encoder.encode(req.password()));
        }
        apply(u, req);
        return UserView.of(u);
    }

    private static void apply(User u, Requests.UserReq r) {
        u.setFullName(r.fullName());
        u.setEmail(r.email());
        u.setPhone(r.phone());
        u.setRole(r.role());
        if (r.active() != null) u.setActive(r.active());
    }
}
