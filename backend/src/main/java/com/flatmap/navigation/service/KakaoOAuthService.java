package com.flatmap.navigation.service;

import com.flatmap.navigation.dto.KakaoTokenResponse;
import com.flatmap.navigation.dto.KakaoUserInfoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class KakaoOAuthService {

    private static final Logger log = LoggerFactory.getLogger(KakaoOAuthService.class);
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestTemplate restTemplate;

    @Value("${kakao.rest-api-key}")
    private String kakaoRestApiKey;

    @Value("${kakao.oauth.redirect-uri}")
    private String redirectUri;

    @Value("${kakao.oauth.client-secret}")
    private String clientSecret;

    public KakaoOAuthService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public KakaoTokenResponse exchangeCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", kakaoRestApiKey);
        body.add("redirect_uri", redirectUri);
        body.add("code", code);
        body.add("client_secret", clientSecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            return restTemplate.postForObject(TOKEN_URL, request, KakaoTokenResponse.class);
        } catch (Exception e) {
            log.error("카카오 토큰 교환 실패", e);
            throw new IllegalStateException("카카오 로그인에 실패했습니다.", e);
        }
    }

    public KakaoUserInfoResponse getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            return restTemplate.exchange(USER_INFO_URL, HttpMethod.GET, request, KakaoUserInfoResponse.class)
                    .getBody();
        } catch (Exception e) {
            log.error("카카오 사용자 정보 조회 실패", e);
            throw new IllegalStateException("카카오 사용자 정보를 가져오지 못했습니다.", e);
        }
    }
}
