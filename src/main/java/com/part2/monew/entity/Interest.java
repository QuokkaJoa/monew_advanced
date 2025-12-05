package com.part2.monew.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "interests")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Interest {

    @Id
    @UuidGenerator
    @Column(name = "interest_id")
    private UUID id;

    @Column(name = "name", nullable = false, length = 100, unique = true)
    private String name;

    @Column(name = "subscriber_counts", nullable = false)
    @ColumnDefault("0")
    private Integer subscriberCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @OneToMany(mappedBy = "interest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InterestKeyword> interestKeywords = new ArrayList<>();

    @OneToMany(mappedBy = "interest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InterestNewsArticle> interestNewsArticles = new ArrayList<>();

    @OneToMany(mappedBy = "interest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserSubscriber> userSubscribers = new ArrayList<>();
}
