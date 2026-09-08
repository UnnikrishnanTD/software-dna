package com.softwaredna.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A repository that has been submitted for analysis at least once.
 *
 * <p>One of only three JPA entities in the system. The aggregate roots have a
 * lifecycle — they are updated, re-read and reasoned about — which is what JPA
 * is good at. The bulk analysis output does not: it is written once and read
 * back as projections, so it goes through batched JDBC instead.
 */
@Entity
@Table(name = "repository")
public class RepositoryEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String name;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(name = "default_branch", nullable = false)
    private String defaultBranch;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "primary_language", length = 64)
    private String primaryLanguage;

    @Column(nullable = false)
    private int stars;

    @Column(nullable = false)
    private int forks;

    @Column(name = "remote_created_at")
    private Instant remoteCreatedAt;

    @Column(name = "last_commit_at")
    private Instant lastCommitAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RepositoryEntity() {
    }

    public RepositoryEntity(UUID id, String provider, String owner, String name, String url) {
        this.id = id;
        this.provider = provider;
        this.owner = owner;
        this.name = name;
        this.fullName = owner + "/" + name;
        this.url = url;
        this.defaultBranch = "main";
    }

    @PrePersist
    void onInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        return fullName;
    }

    public String getUrl() {
        return url;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPrimaryLanguage() {
        return primaryLanguage;
    }

    public void setPrimaryLanguage(String primaryLanguage) {
        this.primaryLanguage = primaryLanguage;
    }

    public int getStars() {
        return stars;
    }

    public void setStars(int stars) {
        this.stars = stars;
    }

    public int getForks() {
        return forks;
    }

    public void setForks(int forks) {
        this.forks = forks;
    }

    public Instant getRemoteCreatedAt() {
        return remoteCreatedAt;
    }

    public void setRemoteCreatedAt(Instant remoteCreatedAt) {
        this.remoteCreatedAt = remoteCreatedAt;
    }

    public Instant getLastCommitAt() {
        return lastCommitAt;
    }

    public void setLastCommitAt(Instant lastCommitAt) {
        this.lastCommitAt = lastCommitAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
