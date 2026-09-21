CREATE TABLE users (
                       user_id UUID PRIMARY KEY,
                       nickname VARCHAR(255),
                       email VARCHAR(50) NOT NULL UNIQUE,
                       password VARCHAR(30) NOT NULL,
                       active BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Interests table
CREATE TABLE interests (
                           interest_id UUID PRIMARY KEY ,
                           name VARCHAR(100) NOT NULL UNIQUE,
                           subscriber_counts INTEGER NOT NULL DEFAULT 0,
                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Keywords table
CREATE TABLE keywords (
                          keyword_id UUID PRIMARY KEY ,
                          name VARCHAR(255) NOT NULL UNIQUE,
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- News Articles table
CREATE TABLE news_articles (
                               news_article_id UUID PRIMARY KEY ,
                               source_in VARCHAR(100),
                               source_url VARCHAR(2048) NOT NULL,
                               title VARCHAR(500) NOT NULL,
                               published_date TIMESTAMP,
                               summary TEXT NOT NULL,
                               view_counts BIGINT DEFAULT 0,
                               comment_counts BIGINT DEFAULT 0,
                               is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Users Subscribers (Many-to-Many between Users and Interests)
CREATE TABLE users_subscribes (
                                  user_subscribe_id UUID PRIMARY KEY ,
                                  user_id UUID NOT NULL,
                                  interest_id UUID NOT NULL,
                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  CONSTRAINT fk_users_subscribers_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                                  CONSTRAINT fk_users_subscribers_interest FOREIGN KEY (interest_id) REFERENCES interests(interest_id) ON DELETE CASCADE,
                                  CONSTRAINT uk_users_subscribers UNIQUE (user_id, interest_id)
);

-- Interests Keywords (Many-to-Many between Interests and Keywords)
CREATE TABLE interests_keywords (
                                    interest_keyword_id UUID PRIMARY KEY ,
                                    interest_id UUID NOT NULL,
                                    keyword_id UUID NOT NULL,
                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                    CONSTRAINT fk_interests_keywords_interest FOREIGN KEY (interest_id) REFERENCES interests(interest_id) ON DELETE CASCADE,
                                    CONSTRAINT fk_interests_keywords_keyword FOREIGN KEY (keyword_id) REFERENCES keywords(keyword_id) ON DELETE CASCADE
);

-- Interests News Articles (Many-to-Many between Interests and News Articles)
CREATE TABLE interests_news_articles (
                                         interest_news_article_id UUID PRIMARY KEY ,
                                         interest_id UUID NOT NULL,
                                         news_article_id UUID NOT NULL,
                                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                         CONSTRAINT fk_interests_news_articles_interest FOREIGN KEY (interest_id) REFERENCES interests(interest_id) ON DELETE CASCADE,
                                         CONSTRAINT fk_interests_news_articles_news FOREIGN KEY (news_article_id) REFERENCES news_articles(news_article_id) ON DELETE CASCADE
);

-- Comments Management table
CREATE TABLE comments_managements (
                                      comment_management_id UUID PRIMARY KEY ,
                                      user_id UUID,
                                      news_article_id UUID,
                                      content TEXT,
                                      like_count INTEGER DEFAULT 0,
                                      active BOOLEAN NOT NULL DEFAULT TRUE,
                                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                      CONSTRAINT fk_comments_managements_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
                                      CONSTRAINT fk_comments_managements_news FOREIGN KEY (news_article_id) REFERENCES news_articles(news_article_id) ON DELETE CASCADE
);

-- Comments Like table
CREATE TABLE comments_like (
                               comment_like_id UUID PRIMARY KEY ,
                               user_id UUID,
                               comment_management_id UUID,
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT fk_comments_like_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
                               CONSTRAINT fk_comments_like_comment FOREIGN KEY (comment_management_id) REFERENCES comments_managements(comment_management_id) ON DELETE CASCADE
);

-- Activity Details table
CREATE TABLE activity_details (
                                  activity_detail_id UUID PRIMARY KEY ,
                                  user_id UUID NOT NULL,
                                  interest_id UUID,
                                  comment_management_id UUID,
                                  comment_like_id UUID,
                                  news_article_id UUID,
                                  views_at TIMESTAMP NOT NULL,
                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  CONSTRAINT fk_activity_details_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                                  CONSTRAINT fk_activity_details_interest FOREIGN KEY (interest_id) REFERENCES interests(interest_id) ON DELETE SET NULL,
                                  CONSTRAINT fk_activity_details_comment FOREIGN KEY (comment_management_id) REFERENCES comments_managements(comment_management_id) ON DELETE SET NULL,
                                  CONSTRAINT fk_activity_details_comment_like FOREIGN KEY (comment_like_id) REFERENCES comments_like(comment_like_id) ON DELETE SET NULL,
                                  CONSTRAINT fk_activity_details_news FOREIGN KEY (news_article_id) REFERENCES news_articles(news_article_id) ON DELETE SET NULL
);

-- Notifications table
CREATE TABLE notifications (
                               notification_id UUID PRIMARY KEY ,
                               user_id UUID NOT NULL,
                               content TEXT NOT NULL,
                               resource_type VARCHAR(255) NOT NULL,
                               resource_id UUID NOT NULL,
                               confirmed BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_comments_article_created
    ON comments_managements (news_article_id, created_at, comment_management_id)
    WHERE active = TRUE;
