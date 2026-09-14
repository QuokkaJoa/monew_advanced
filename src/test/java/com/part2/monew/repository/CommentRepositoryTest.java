package com.part2.monew.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.part2.monew.config.QueryDslConfig;
import com.part2.monew.entity.CommentsManagement;
import com.part2.monew.entity.NewsArticle;
import com.part2.monew.entity.User;
import com.part2.monew.mapper.InterestMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(QueryDslConfig.class)
@ActiveProfiles("test")
@Transactional(readOnly = true)
class CommentRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CommentRepository commentRepository;

    @MockitoBean
    private InterestMapper interestMapper;


    @Autowired
    private UserRepository userRepository;

    @DisplayName("게시물에 작성된 댓글을 페이지 사이즈 10~6 5개 내림차순 조회한다.")
    @Transactional
    @Test
    void findCommentsByArticleId() {
        //given
        User user = User.builder()
                .nickname("tester")
                .email("test@example.com")
                .password("123456")
                .active(true).build();

        userRepository.save(user);

        NewsArticle newsArticle = new NewsArticle("https://example.com/foo", "제목입니다", Timestamp.from(Instant.now()), "요약입니다", 0L);
        em.persist(newsArticle);

        Instant baseTime = Instant.parse("2025-06-01T00:00:00Z");

        CommentsManagement cm1 = CommentsManagement.create(user, newsArticle, "Content1", 0, Timestamp.from(baseTime.plus(0, ChronoUnit.DAYS)));
        CommentsManagement cm2 = CommentsManagement.create(user, newsArticle, "Content2", 0, Timestamp.from(baseTime.plus(1, ChronoUnit.DAYS)));
        CommentsManagement cm3 = CommentsManagement.create(user, newsArticle, "Content3", 0, Timestamp.from(baseTime.plus(2, ChronoUnit.DAYS)));
        CommentsManagement cm4 = CommentsManagement.create(user, newsArticle, "Content4", 0, Timestamp.from(baseTime.plus(3, ChronoUnit.DAYS)));
        CommentsManagement cm5 = CommentsManagement.create(user, newsArticle, "Content5", 0, Timestamp.from(baseTime.plus(4, ChronoUnit.DAYS)));
        CommentsManagement cm6 = CommentsManagement.create(user, newsArticle, "Content6", 0, Timestamp.from(baseTime.plus(5, ChronoUnit.DAYS)));
        CommentsManagement cm7 = CommentsManagement.create(user, newsArticle, "Content7", 0, Timestamp.from(baseTime.plus(6, ChronoUnit.DAYS)));
        CommentsManagement cm8 = CommentsManagement.create(user, newsArticle, "Content8", 0, Timestamp.from(baseTime.plus(7, ChronoUnit.DAYS)));
        CommentsManagement cm9 = CommentsManagement.create(user, newsArticle, "Content9", 0, Timestamp.from(baseTime.plus(8, ChronoUnit.DAYS)));
        CommentsManagement cm10 = CommentsManagement.create(user, newsArticle, "Content10", 0, Timestamp.from(baseTime.plus(9, ChronoUnit.DAYS)));

        em.persist(cm1);
        em.persist(cm2);
        em.persist(cm3);
        em.persist(cm4);
        em.persist(cm5);
        em.persist(cm6);
        em.persist(cm7);
        em.persist(cm8);
        em.persist(cm9);
        em.persist(cm10);

        int limit = 5;

        // when
        List<CommentsManagement> results = commentRepository.findCommentsByArticleId(newsArticle.getId(), null, limit, user.getId());

        // then
        assertThat(results)
                .hasSize(6)
                .extracting(CommentsManagement::getContent)
                .containsExactly("Content10", "Content9", "Content8", "Content7", "Content6", "Content5");
    }

    @DisplayName("같은 시각 댓글이 페이지 경계에 걸려도 두 페이지를 합치면 모든 댓글이 한 번씩 나온다.")
    @Transactional
    @Test
    void findCommentsPage_sameCreatedAtOnPageBoundary() {
        // given: 같은 기사에 댓글 4개, B와 C는 같은 시각
        User user = User.builder()
            .nickname("jh")
            .email("jh@example.com")
            .password("123456")
            .active(true).build();
        em.persist(user);

        NewsArticle article = new NewsArticle("https://example.com/jh", "제목",
            Timestamp.from(Instant.now()), "요약", 0L);
        em.persist(article);

        CommentsManagement a = CommentsManagement.create(user, article, "A", 0);
        CommentsManagement b = CommentsManagement.create(user, article, "B", 0);
        CommentsManagement c = CommentsManagement.create(user, article, "C", 0);
        CommentsManagement d = CommentsManagement.create(user, article, "D", 0);
        em.persist(a);
        em.persist(b);
        em.persist(c);
        em.persist(d);
        em.flush();

        setCreatedAt(a, "2026-01-01T18:40:00Z");
        setCreatedAt(b, "2026-01-01T18:30:00Z");
        setCreatedAt(c, "2026-01-01T18:30:00Z");
        setCreatedAt(d, "2026-01-01T18:20:00Z");
        em.clear();

        int limit = 2;

        List<CommentsManagement> firstFetch = commentRepository.findCommentsPage(article.getId(),
            null, null, limit);
        List<CommentsManagement> page1 = firstFetch.subList(0, Math.min(limit, firstFetch.size()));

        Timestamp nextAfter = page1.get(page1.size() - 1).getCreatedAt();
        UUID nextCursorId = page1.get(page1.size() - 1).getId();

        List<CommentsManagement> secondFetch = commentRepository.findCommentsPage(article.getId(),
            nextAfter, nextCursorId, limit);
        List<CommentsManagement> page2 = secondFetch.subList(0,
            Math.min(limit, secondFetch.size()));

        List<String> allContents = new ArrayList<>();

        page1.forEach(cm -> allContents.add(cm.getContent()));
        page2.forEach(cm -> allContents.add(cm.getContent()));

        assertThat(allContents).containsExactlyInAnyOrder("A", "B", "C", "D");
    }

    private void setCreatedAt(CommentsManagement comment, String isoTime) {
        em.getEntityManager()
                .createQuery("update CommentsManagement cm set cm.createdAt = :createdAt where cm.id = :id")
                .setParameter("createdAt", Timestamp.from(Instant.parse(isoTime)))
                .setParameter("id", comment.getId())
                .executeUpdate();
    }

    @DisplayName("댓글을 저장한다.")
    @Test
    @Transactional
    void create() {
        // given
        User user = new User("tester", "test@example.com", "pass123", true, Timestamp.from(Instant.now()));
        em.persist(user);

        NewsArticle article = new NewsArticle(
                "http://url.com",
                "제목",
                Timestamp.from(Instant.now()),
                "요약",
                0L
        );
        em.persist(article);

        CommentsManagement comment = CommentsManagement.create(user, article, "내용", 0);

        // when
        CommentsManagement saved = commentRepository.save(comment);
        em.flush();
        em.clear();

        // then
        CommentsManagement fetched = commentRepository.findById(saved.getId())
                .orElseThrow(() -> new RuntimeException("Saved comment not found"));

        assertThat(fetched)
                .extracting("user.id", "newsArticle.id", "content", "likeCount", "active")
                .containsExactly(user.getId(), article.getId(), "내용", 0, true);

        assertThat(fetched.getCreatedAt()).isNotNull();
    }
}
