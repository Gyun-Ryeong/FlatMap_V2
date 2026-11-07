package com.flatmap.navigation.service;

import com.flatmap.navigation.dto.KakaoTokenResponse;
import com.flatmap.navigation.dto.KakaoUserInfoResponse;
import com.flatmap.navigation.entity.User;
import com.flatmap.navigation.repository.UserRepository;
import com.flatmap.navigation.util.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final KakaoOAuthService kakaoOAuthService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public AuthService(KakaoOAuthService kakaoOAuthService, UserRepository userRepository, JwtUtil jwtUtil) {
        this.kakaoOAuthService = kakaoOAuthService;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public String loginWithKakao(String code) {
        KakaoTokenResponse token = kakaoOAuthService.exchangeCodeForToken(code);
        KakaoUserInfoResponse userInfo = kakaoOAuthService.getUserInfo(token.getAccessToken());

        String providerId = String.valueOf(userInfo.getId());
        String nickname = userInfo.getKakaoAccount() != null && userInfo.getKakaoAccount().getProfile() != null
                ? userInfo.getKakaoAccount().getProfile().getNickname()
                : null;
        String profileImageUrl = userInfo.getKakaoAccount() != null && userInfo.getKakaoAccount().getProfile() != null
                ? userInfo.getKakaoAccount().getProfile().getProfileImageUrl()
                : null;

        User user = userRepository.findByProviderAndProviderId(User.AuthProvider.KAKAO, providerId)
                .orElseGet(() -> new User(User.AuthProvider.KAKAO, providerId, nickname, profileImageUrl));

        user.setNickname(nickname);
        user.setProfileImageUrl(profileImageUrl);
        User saved = userRepository.save(user);

        return jwtUtil.generateToken(saved.getId());
    }
}
