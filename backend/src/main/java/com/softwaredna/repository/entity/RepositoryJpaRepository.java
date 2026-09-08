package com.softwaredna.repository.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RepositoryJpaRepository extends JpaRepository<RepositoryEntity, UUID> {

    /** GitHub identifiers are case-insensitive, so the lookup must be too. */
    Optional<RepositoryEntity> findByProviderAndOwnerIgnoreCaseAndNameIgnoreCase(
            String provider, String owner, String name);
}
