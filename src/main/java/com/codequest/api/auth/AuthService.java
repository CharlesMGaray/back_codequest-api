package com.codequest.api.auth;

import com.codequest.api.auth.AuthDtos.AuthResponse;
import com.codequest.api.auth.AuthDtos.LoginRequest;
import com.codequest.api.auth.AuthDtos.RegistroRequest;
import com.codequest.api.auth.AuthDtos.TokenResponse;
import com.codequest.api.common.ApiException;
import com.codequest.api.usuario.Perfil;
import com.codequest.api.usuario.Usuario;
import com.codequest.api.usuario.UsuarioRepository;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final FirebaseAuth firebaseAuth;
    private final FirebaseAuthRestClient authRest;
    private final UsuarioRepository usuarios;

    public AuthService(FirebaseAuth firebaseAuth, FirebaseAuthRestClient authRest, UsuarioRepository usuarios) {
        this.firebaseAuth = firebaseAuth;
        this.authRest = authRest;
        this.usuarios = usuarios;
    }

    public AuthResponse registrar(RegistroRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String nome = req.nome().trim();

        UserRecord user;
        try {
            user = firebaseAuth.createUser(new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(req.senha())
                    .setDisplayName(nome));
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                throw new ApiException(HttpStatus.CONFLICT, "Ja existe uma conta com este email.");
            }
            throw new IllegalStateException("Falha ao criar usuario no Firebase Auth", e);
        }

        Usuario usuario;
        try {
            usuario = usuarios.criar(user.getUid(), nome, email, req.perfil());
        } catch (RuntimeException e) {
            // Desfaz a conta no Auth para nao deixar usuario sem perfil.
            try {
                firebaseAuth.deleteUser(user.getUid());
            } catch (FirebaseAuthException rollback) {
                log.error("Nao foi possivel desfazer o usuario {} no Auth", user.getUid(), rollback);
            }
            throw e;
        }

        var tokens = authRest.signIn(email, req.senha());
        return AuthResponse.of(tokens.idToken(), tokens.refreshToken(), usuario);
    }

    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        var tokens = authRest.signIn(email, req.senha());
        // Conta criada direto no console do Firebase ainda nao tem perfil: cria um padrao.
        Usuario usuario = usuarios.buscar(tokens.uid())
                .orElseGet(() -> usuarios.criar(tokens.uid(), email.substring(0, email.indexOf('@')), email,
                        Perfil.ALUNO));
        return AuthResponse.of(tokens.idToken(), tokens.refreshToken(), usuario);
    }

    public TokenResponse refresh(String refreshToken) {
        var tokens = authRest.refresh(refreshToken);
        return new TokenResponse(tokens.idToken(), tokens.refreshToken());
    }

    public Usuario me(String uid) {
        return usuarios.buscar(uid)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario nao encontrado."));
    }
}
