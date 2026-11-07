package com.flatmap.navigation.repository;

import com.flatmap.navigation.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByProviderAndProviderId(User.AuthProvider provider, String providerId);
}
