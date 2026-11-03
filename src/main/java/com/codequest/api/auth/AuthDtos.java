package com.codequest.api.auth;

import com.codequest.api.usuario.Perfil;
import com.codequest.api.usuario.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Informe o email.") @Email(message = "Email invalido.") String email,
            @NotBlank(message = "Informe a senha.") String senha) {
    }

    public record RegistroRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 100, message = "Nome muito longo.") String nome,
            @NotBlank(message = "Informe o email.") @Email(message = "Email invalido.") String email,
            @NotBlank(message = "Informe a senha.")
            @Size(min = 6, message = "A senha precisa ter pelo menos 6 caracteres.") String senha,
            @NotNull(message = "Informe o perfil.") Perfil perfil) {
    }

    public record RefreshRequest(@NotBlank(message = "Informe o refreshToken.") String refreshToken) {
    }

    /** Resposta de login/registro: o front guarda o objeto inteiro como "usuario". */
    public record AuthResponse(
            String token, String refreshToken,
            String id, String nome, String email, Perfil perfil, long xpTotal, int nivelAtual) {

        static AuthResponse of(String token, String refreshToken, Usuario u) {
            return new AuthResponse(token, refreshToken,
                    u.id(), u.nome(), u.email(), u.perfil(), u.xpTotal(), u.nivelAtual());
        }
    }

    public record TokenResponse(String token, String refreshToken) {
    }
}
