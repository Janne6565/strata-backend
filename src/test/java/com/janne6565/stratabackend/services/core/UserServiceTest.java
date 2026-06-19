package com.janne6565.stratabackend.services.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.action.CreateUserRequest;
import com.janne6565.stratabackend.model.core.Role;
import com.janne6565.stratabackend.model.core.UserResponse;
import com.janne6565.stratabackend.model.exception.BadRequestException;
import com.janne6565.stratabackend.model.exception.ConflictException;
import com.janne6565.stratabackend.model.exception.NotFoundException;
import com.janne6565.stratabackend.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @InjectMocks private UserService userService;

    @Test
    void createRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                userService.create(
                                        new CreateUserRequest("alice", "password1", Role.USER)))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createEncodesPasswordAndPersists() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("ENC");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response =
                userService.create(new CreateUserRequest("alice", "password1", Role.ADMIN));

        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void changeRoleRejectsDemotingTheLastOwner() {
        UserEntity owner = new UserEntity("owner", "h", Role.OWNER);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(userRepository.countByRole(Role.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> userService.changeRole(owner.getId(), Role.ADMIN))
                .isInstanceOf(BadRequestException.class);
        assertThat(owner.getRole()).isEqualTo(Role.OWNER);
    }

    @Test
    void changeRoleAllowsDemotingAnOwnerWhenAnotherExists() {
        UserEntity owner = new UserEntity("owner", "h", Role.OWNER);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(userRepository.countByRole(Role.OWNER)).thenReturn(2L);

        UserResponse response = userService.changeRole(owner.getId(), Role.ADMIN);

        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(owner.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void changeRoleOnUnknownUser404s() {
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changeRole(missing, Role.USER))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRejectsSelfDeletion() {
        UserEntity caller = new UserEntity("me", "h", Role.OWNER);
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> userService.delete(caller.getId(), caller))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteRejectsRemovingTheLastOwner() {
        UserEntity caller = new UserEntity("admin", "h", Role.ADMIN);
        UserEntity lastOwner = new UserEntity("owner", "h", Role.OWNER);
        when(userRepository.findById(lastOwner.getId())).thenReturn(Optional.of(lastOwner));
        when(userRepository.countByRole(Role.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> userService.delete(lastOwner.getId(), caller))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteRemovesAnOrdinaryUser() {
        UserEntity caller = new UserEntity("admin", "h", Role.ADMIN);
        UserEntity target = new UserEntity("victim", "h", Role.USER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        userService.delete(target.getId(), caller);

        verify(userRepository).delete(target);
    }
}
