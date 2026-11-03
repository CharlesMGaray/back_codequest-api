package com.codequest.api.auth;

import com.codequest.api.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Login por email/senha e renovacao de token usam a API REST do Firebase Auth
 * (o Admin SDK nao verifica senhas).
 */
@Component
public class FirebaseAuthRestClient {

    private final RestClient identityToolkit;
    private final RestClient secureToken;
    private final String apiKey;

    public FirebaseAuthRestClient(
            @Value("${firebase.web-api-key}") String apiKey,
            @Value("${firebase.auth-emulator-host}") String emulatorHost) {
        this.apiKey = apiKey;
        boolean emulator = emulatorHost != null && !emulatorHost.isBlank();
        String identityBase = emulator
                ? "http://" + emulatorHost + "/identitytoolkit.googleapis.com/v1"
                : "https://identitytoolkit.googleapis.com/v1";
        String tokenBase = emulator
                ? "http://" + emulatorHost + "/securetoken.googleapis.com/v1"
                : "https://securetoken.googleapis.com/v1";
        this.identityToolkit = RestClient.create(identityBase);
        this.secureToken = RestClient.create(tokenBase);
    }

    public record SignInResult(String uid, String idToken, String refreshToken) {
    }

    public SignInResult signIn(String email, String senha) {
        requireApiKey();
        try {
            Map<?, ?> res = identityToolkit.post()
                    .uri(u -> u.path("/accounts:signInWithPassword").queryParam("key", apiKey).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "password", senha, "returnSecureToken", true))
                    .retrieve()
                    .body(Map.class);
            return new SignInResult(
                    (String) res.get("localId"),
                    (String) res.get("idToken"),
                    (String) res.get("refreshToken"));
        } catch (RestClientResponseException e) {
            String code = e.getResponseBodyAsString();
            if (code.contains("INVALID_LOGIN_CREDENTIALS") || code.contains("EMAIL_NOT_FOUND")
                    || code.contains("INVALID_PASSWORD") || code.contains("INVALID_EMAIL")) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Email ou senha invalidos.");
            }
            if (code.contains("USER_DISABLED")) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Usuario desativado.");
            }
            if (code.contains("TOO_MANY_ATTEMPTS")) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "Muitas tentativas. Tente novamente em alguns minutos.");
            }
            throw new IllegalStateException("Falha no login do Firebase: " + code, e);
        }
    }

    public SignInResult refresh(String refreshToken) {
        requireApiKey();
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        try {
            Map<?, ?> res = secureToken.post()
                    .uri(u -> u.path("/token").queryParam("key", apiKey).build())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            return new SignInResult(
                    (String) res.get("user_id"),
                    (String) res.get("id_token"),
                    (String) res.get("refresh_token"));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Sessao expirada. Faca login novamente.");
            }
            throw new IllegalStateException("Falha ao renovar token: " + e.getResponseBodyAsString(), e);
        }
    }

    private void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Defina FIREBASE_WEB_API_KEY (firebase.web-api-key).");
        }
    }
}
