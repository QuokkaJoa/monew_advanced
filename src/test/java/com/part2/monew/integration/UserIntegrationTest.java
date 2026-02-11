package com.part2.monew.integration;


import com.part2.monew.global.aop.DistributedCacheAspect;
import com.part2.monew.support.IntegrationTestSupport;
import com.part2.monew.dto.request.UserCreateRequest;
import com.part2.monew.dto.request.UserLoginRequest;
import com.part2.monew.dto.request.UserUpdateRequest;
import com.part2.monew.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.util.UUID;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
public class UserIntegrationTest extends IntegrationTestSupport {

    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private DistributedCacheAspect distributedCacheAspect;

    private UserCreateRequest createReq;
    private UserUpdateRequest updateReq;

    @BeforeEach
    void setUp() {
        createReq = new UserCreateRequest("test@naver.com", "tester", "123456");
        updateReq = new UserUpdateRequest("updatedNick");
    }

    @Test
    void createUser_integration() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@naver.com"))
                .andExpect(jsonPath("$.nickname").value("tester"));

        User saved = userRepository.findByEmailAndActiveTrue("test@naver.com").orElse(null);
        assertThat(saved).isNotNull();
        assertThat(saved.getNickname()).isEqualTo("tester");
    }

    @Test
    void loginUser_integration() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk());

        UserLoginRequest loginReq = new UserLoginRequest("test@naver.com", "123456");

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@naver.com"))
                .andExpect(header().exists("MoNew-Request-User-ID"));
    }

    @Test
    void updateNickname_integration() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk());

        User saved = userRepository.findByEmailAndActiveTrue("test@naver.com").orElse(null);
        UUID userId = saved.getId();


        mockMvc.perform(patch("/api/users/" + userId)
                        .header("MoNew-Request-User-ID", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("updatedNick"));
    }

    @Test
    void deleteUser_integration() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk());

        User saved = userRepository.findByEmailAndActiveTrue("test@naver.com").orElse(null);
        UUID userId = saved.getId();

        mockMvc.perform(delete("/api/users/" + userId)
                        .header("MoNew-Request-User-ID", userId.toString()))
                .andExpect(status().isNoContent());

        User after = userRepository.findById(userId).orElse(null);
        assertThat(after).isNotNull();
        assertThat(after.isActive()).isFalse();
    }

    @Test
    void deleteUserHard_integration() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk());

        User saved = userRepository.findByEmailAndActiveTrue("test@naver.com").orElse(null);
        UUID userId = saved.getId();

        mockMvc.perform(delete("/api/users/" + userId + "/hard")
                        .header("MoNew-Request-User-ID", userId.toString()))
                .andExpect(status().isNoContent());

        boolean exists = userRepository.findById(userId).isPresent();
        assertThat(exists).isFalse();
    }

}
