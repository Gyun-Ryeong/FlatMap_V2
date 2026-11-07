package com.flatmap.navigation.repository;

import com.flatmap.navigation.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Favorite> findByIdAndUserId(Long id, Long userId);
    Optional<Favorite> findByUserIdAndLatitudeAndLongitude(Long userId, Double latitude, Double longitude);
}
