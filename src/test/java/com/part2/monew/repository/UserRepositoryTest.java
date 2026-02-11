package com.part2.monew.repository;

import com.part2.monew.config.QueryDslConfig;
import com.part2.monew.entity.User;
import com.part2.monew.mapper.InterestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QueryDslConfig.class)
@ActiveProfiles("test")
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private InterestMapper interestMapper;

    private User saved;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .nickname("tester")
                .email("test@example.com")
                .password("123456")
                .active(true).build();
        saved = userRepository.save(user);
    }

    @Test
    void testExistsByEmail() {
        // when
        boolean exists = userRepository.existsByEmail("test@example.com");

        // then
        assertThat(exists).isTrue();
    }

    @Test
    void testfindByIdAndActiveTrue() {

        // when
        Optional<User> result = userRepository.findByIdAndActiveTrue(saved.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("test@example.com");
    }


    @Test
    void testFindByEmailAndActiveTrue() {
        // when
        Optional<User> result = userRepository.findByEmailAndActiveTrue("test@example.com");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getNickname()).isEqualTo("tester");
    }

}
