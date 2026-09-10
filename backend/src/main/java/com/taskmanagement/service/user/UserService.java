package com.taskmanagement.service.user;
import org.springframework.stereotype.Service;
import com.taskmanagement.dto.Response;
import com.taskmanagement.dto.page.PageResponse;
import com.taskmanagement.model.User;
import com.taskmanagement.model.UserRole;
import com.taskmanagement.dto.user.UserResponse;
import com.taskmanagement.mapper.UserMapper;
import com.taskmanagement.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import com.taskmanagement.dto.user.CreateUserRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Set;
import com.taskmanagement.utils.PageableValidator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.taskmanagement.exception.DuplicatedResourceException;
import com.taskmanagement.exception.ForbiddenException;
import com.taskmanagement.exception.ResourceNotFoundException;
import com.taskmanagement.exception.BadRequestException;
import com.taskmanagement.dto.user.UpdateUserRequest;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final Set<String> ALLOWED_SORTS = Set.of(
            "username", "displayName", "email", "role", "createdAt", "isDeactivated"
    );
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
//_____________________________________________UTILS______________________________________________________________

    private void validateUserRequest(CreateUserRequest request) {
        ensureUsernameAvailable(request.username());
        ensureEmailAvailable(request.email());
    }

    private User findUserOrThrow(Long id) {
        if (id == null) {
            throw new BadRequestException("User id is required");
        }

        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private User findUserOrThrow(String username) { // overloading
        if (username == null || username.isBlank()) {
            throw new BadRequestException("User id is required");
        }

        return userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }

    private void ensureEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicatedResourceException("Email already exists: " + email);
        }
    }

    private void ensureUsernameAvailable(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicatedResourceException("Username already exists: " + username);
        }
    }

    private User updateUser(User user, UpdateUserRequest request) {
        if (request.password() != null || request.username() != null || request.email() != null) {
            throw new BadRequestException("Login details require a separate verified security change");
        }
        updateUsernameIfChanged(user, request);
        updateEmailIfChanged(user, request);
        updateDisplayNameIfChanged(user, request);
        updatePasswordIfChanged(user, request);
        updateProfilePictureIfChanged(user, request);
        return userRepository.save(user);
    }

    private void updateUsernameIfChanged(User user, UpdateUserRequest request) {
        if (request.username() == null || request.username().equals(user.getUsername())) return;

        ensureUsernameAvailable(request.username());
        user.setUsername(request.username());
    }

    private void updateEmailIfChanged(User user, UpdateUserRequest request) {
        if (request.email() == null ||request.email().equals(user.getEmail())) return;

        ensureEmailAvailable(request.email());
        user.setEmail(request.email());
    }

    private void updateDisplayNameIfChanged(User user, UpdateUserRequest request) {
        if (request.displayName() == null) return;
        String name = request.displayName().trim();
        if (name.length() < 2 || name.length() > 50) throw new BadRequestException("Display name must contain 2 to 50 characters");
        user.setDisplayName(name);
    }

    private void updatePasswordIfChanged(User user, UpdateUserRequest request) {
        if (request.password() == null || request.password().isBlank()) return;
        user.setPassword(passwordEncoder.encode(request.password()));
    }

    private void updateProfilePictureIfChanged(User user, UpdateUserRequest request) {
        if (request.profilePictureUrl() == null) return;
        String normalizedUrl = request.profilePictureUrl().trim();
        user.setProfilePictureUrl(normalizedUrl.isEmpty() ? null : normalizedUrl);
    }

    private Response<UserResponse> saveAndReturn(User user, String message) {
        User savedUser = userRepository.save(user);
        UserResponse userResponse = userMapper.toUserResponse(savedUser);
        return Response.success(userResponse, message);
    }

//_________________________________________________________________________________________________________________
    public Response<PageResponse<UserResponse>> getAllUsers(Pageable pageable) {
        PageableValidator.requireAllowedSorts(pageable, ALLOWED_SORTS);
        Page<User> users = userRepository.findAll(pageable);
        var responses = users.getContent().stream()
                .map(userMapper::toUserResponse)
                .toList();
        return Response.success(PageResponse.from(users, responses), "All users retrieved successfully!");
    }

    public Response<UserResponse> createUser(CreateUserRequest user) {
        validateUserRequest(user);
        
        User userEntity = userMapper.toUser(user); // Use the mapper to convert the request to a User entity, no need to set fields manually

        userEntity.setPassword(passwordEncoder.encode(user.password()));
        userEntity.setRole(UserRole.USER);         // Set a default role, adjust as needed

        return saveAndReturn(userEntity, "User created successfully!");
    }

    public Response<UserResponse> getUserById(Long id) {
        User user = findUserOrThrow(id);
        UserResponse userResponse = userMapper.toUserResponse(user);
        return Response.success(userResponse, "User retrieved successfully!");
    }

    public Response<UserResponse> getUserByUsername(String username) {
        User user = findUserOrThrow(username);
        UserResponse userResponse = userMapper.toUserResponse(user);
        return Response.success(userResponse, "User retrieved successfully!");
    }

    public Response<Void> deleteUserById(Long id) {
        User user = findUserOrThrow(id);
        String currentUsername = SecurityContextHolder.getContext()
            .getAuthentication()
            .getName();

        if (user.getUsername().equals(currentUsername)) {
            throw new ForbiddenException("You cannot delete your own account.");
        }
        
        userRepository.delete(user);
        return Response.success(null, "User deleted successfully!");
    }

    public Response<UserResponse> updateUserById(Long id, UpdateUserRequest userRequest) {
        User user = findUserOrThrow(id);
        User savedUser = updateUser(user, userRequest);
        UserResponse userResponse = userMapper.toUserResponse(savedUser);
        return Response.success(userResponse, "User updated successfully!");
    }

    @org.springframework.transaction.annotation.Transactional
    public Response<UserResponse> updateUserByUsername(String username, UpdateUserRequest userRequest) {
        User user = userRepository.findForSecurityUpdate(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User savedUser = updateUser(user, userRequest);
        UserResponse userResponse = userMapper.toUserResponse(savedUser);
        return Response.success(userResponse, "User updated successfully!");
    }

    public Response<UserResponse> deactivateUser(Long id) {
        User user = findUserOrThrow(id);
        String currentUsername = SecurityContextHolder.getContext()
            .getAuthentication()
            .getName();

        if (user.getUsername().equals(currentUsername)) {
            throw new ForbiddenException("You cannot deactivate your own account.");
        }
        if (user.isDeactivated()) {
            throw new BadRequestException("User is already deactivated.");
        }
        user.deactivate();
        return saveAndReturn(user, "User deactivated!");
    }

    public Response<UserResponse> activateUser(Long id) {
        User user = findUserOrThrow(id);
        if (!user.isDeactivated()) {
            throw new BadRequestException("User is not deactivated.");
        }
        user.activate();
        return saveAndReturn(user, "User activated!");
    }



}
