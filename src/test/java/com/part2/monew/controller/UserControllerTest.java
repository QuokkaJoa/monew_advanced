package com.part2.monew.controller;

import com.part2.monew.dto.request.UserCreateRequest;
import com.part2.monew.dto.request.UserLoginRequest;
import com.part2.monew.dto.request.UserUpdateRequest;
import com.part2.monew.dto.response.UserResponse;
import com.part2.monew.entity.User;
import com.part2.monew.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@ActiveProfiles("test")
class UserControllerTest extends ControllerTestSupport {
    private UserCreateRequest createReq;
    private UserLoginRequest loginReq;
    private UserUpdateRequest updateReq;
    private User user;
    private UserResponse userResponse;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        createReq = new UserCreateRequest("woody@naver.com", "woody", "123456");
        loginReq = new UserLoginRequest("woody@naver.com", "123456");
        updateReq = new UserUpdateRequest("updatedWoody");
        user = User.builder()
                .id(userId)
                .email("woody@naver.com")
                .nickname("woody").build();
        userResponse = new UserResponse(
                userId,
                "woody@naver.com",
                "woody",
                Timestamp.from(Instant.now())
        );
    }

    @Test
    void createUser() throws Exception {
        // given
        given(userService.createUser(any(UserCreateRequest.class)))
                .willReturn(userResponse);

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("woody@naver.com"))
                .andExpect(jsonPath("$.nickname").value("woody"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void loginUser() throws Exception {
        // given
        given(userService.loginUser(any(UserLoginRequest.class)))
                .willReturn(user);
        given(userMapper.toResponse(any(User.class)))
                .willReturn(userResponse);

        // when & then
        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(header().string("MoNew-Request-User-ID", userId.toString()))
                .andExpect(jsonPath("$.email").value("woody@naver.com"))
                .andExpect(jsonPath("$.nickname").value("woody"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void updateNickname() throws Exception {
        // given
        UserResponse updatedUserResponse = new UserResponse(
                userId,
                "woody@naver.com",
                "updatedWoody",
                Timestamp.from(Instant.now())
        );
        given(userService.updateNickname(any(UUID.class), any(UUID.class), any(UserUpdateRequest.class)))
                .willReturn(updatedUserResponse);

        // when & then
        mockMvc.perform(patch("/api/users/" + userId)
                        .header("MoNew-Request-User-ID", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("updatedWoody"));
    }

    @Test
    void deleteUser() throws Exception {
        // given
        doNothing().when(userService).delete(any(UUID.class), any(UUID.class));

        // when & then
        mockMvc.perform(delete("/api/users/" + userId)
                        .header("MoNew-Request-User-ID", userId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUserHard() throws Exception {
        // given
        doNothing().when(userService).deleteHard(any(UUID.class), any(UUID.class));

        // when & then
        mockMvc.perform(delete("/api/users/" + userId + "/hard")
                        .header("MoNew-Request-User-ID", userId.toString()))
                .andExpect(status().isNoContent());
    }
}
