package com.flatmap.navigation.controller;

import com.flatmap.navigation.entity.Favorite;
import com.flatmap.navigation.repository.FavoriteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteRepository favoriteRepository;

    public FavoriteController(FavoriteRepository favoriteRepository) {
        this.favoriteRepository = favoriteRepository;
    }

    @GetMapping
    public ResponseEntity<List<Favorite>> getFavorites(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @PostMapping
    public ResponseEntity<Favorite> addFavorite(Authentication authentication, @RequestBody Map<String, Object> body) {
        Long userId = (Long) authentication.getPrincipal();

        String name = (String) body.get("name");
        String address = (String) body.get("address");
        Double latitude = body.get("latitude") == null ? null : ((Number) body.get("latitude")).doubleValue();
        Double longitude = body.get("longitude") == null ? null : ((Number) body.get("longitude")).doubleValue();

        // 중복 방지: 같은 좌표가 이미 즐겨찾기에 있으면 기존 것을 그대로 반환 (신규 저장 안 함)
        if (latitude != null && longitude != null) {
            Optional<Favorite> existing = favoriteRepository.findByUserIdAndLatitudeAndLongitude(userId, latitude, longitude);
            if (existing.isPresent()) {
                return ResponseEntity.ok(existing.get());
            }
        }

        Favorite favorite = new Favorite(userId, name, address, latitude, longitude);
        Favorite saved = favoriteRepository.save(favorite);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteFavorite(Authentication authentication, @PathVariable Long id) {
        Long userId = (Long) authentication.getPrincipal();

        return favoriteRepository.findByIdAndUserId(id, userId)
                .map(favorite -> {
                    favoriteRepository.delete(favorite);
                    return ResponseEntity.ok(Map.<String, Object>of("deleted", true));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "즐겨찾기를 찾을 수 없습니다.")));
    }
}
