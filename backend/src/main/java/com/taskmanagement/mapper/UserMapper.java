package com.taskmanagement.mapper;

import com.taskmanagement.dto.user.CreateUserRequest;
import com.taskmanagement.dto.user.UserResponse;
import com.taskmanagement.model.User;
import org.mapstruct.Mapping;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    public UserResponse toUserResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deactivated", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "profilePictureUrl", ignore = true)
    @Mapping(target = "securityStamp", ignore = true)
    @Mapping(target = "rowVersion", ignore = true)

    public User toUser(CreateUserRequest request);
}
