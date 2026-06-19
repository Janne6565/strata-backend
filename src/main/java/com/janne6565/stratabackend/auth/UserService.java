package com.janne6565.stratabackend.auth;

import com.janne6565.stratabackend.auth.dto.CreateUserRequest;
import com.janne6565.stratabackend.auth.dto.UserResponse;
import com.janne6565.stratabackend.common.BadRequestException;
import com.janne6565.stratabackend.common.ConflictException;
import com.janne6565.stratabackend.common.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User-management operations for admins. Enforces the cross-cutting invariant that at least one
 * {@code OWNER} always exists (AUTH.md): the last owner can be neither demoted nor deleted, and
 * a user cannot delete themselves.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already taken: " + request.username());
        }
        User user =
                new User(
                        request.username(),
                        passwordEncoder.encode(request.password()),
                        request.role());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse changeRole(UUID id, Role newRole) {
        User user = require(id);
        if (isLastOwner(user) && newRole != Role.OWNER) {
            throw new BadRequestException("Cannot demote the last remaining owner");
        }
        user.setRole(newRole);
        return UserResponse.from(user);
    }

    @Transactional
    public void delete(UUID id, User caller) {
        User user = require(id);
        if (user.getId().equals(caller.getId())) {
            throw new BadRequestException("You cannot delete your own account");
        }
        if (isLastOwner(user)) {
            throw new BadRequestException("Cannot delete the last remaining owner");
        }
        userRepository.delete(user);
    }

    private boolean isLastOwner(User user) {
        return user.getRole() == Role.OWNER && userRepository.countByRole(Role.OWNER) <= 1;
    }

    private User require(UUID id) {
        return userRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }
}
