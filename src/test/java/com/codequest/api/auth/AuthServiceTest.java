package com.codequest.api.auth;

import com.codequest.api.auth.AuthDtos.AuthResponse;
import com.codequest.api.auth.AuthDtos.RegistroRequest;
import com.codequest.api.usuario.Perfil;
import com.codequest.api.usuario.Usuario;
import com.codequest.api.usuario.UsuarioRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** O perfil de uma conta nova e sempre ALUNO, mesmo que o cliente envie outro. */
class AuthServiceTest {

    FirebaseAuth firebaseAuth;
    FirebaseAuthRestClient authRest;
    UsuarioRepository usuarios;
    AuthService service;

    @BeforeEach
    void setUp() throws Exception {
        firebaseAuth = mock(FirebaseAuth.class);
        authRest = mock(FirebaseAuthRestClient.class);
        usuarios = mock(UsuarioRepository.class);
        service = new AuthService(firebaseAuth, authRest, usuarios);

        UserRecord conta = mock(UserRecord.class);
        when(conta.getUid()).thenReturn("uid-1");
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(conta);
        when(usuarios.criar(eq("uid-1"), eq("Ana"), eq("ana@exemplo.com"), any(Perfil.class)))
                .thenReturn(new Usuario("uid-1", "Ana", "ana@exemplo.com", Perfil.ALUNO, 0, 1));
        when(authRest.signIn("ana@exemplo.com", "senha123"))
                .thenReturn(new FirebaseAuthRestClient.SignInResult("uid-1", "token", "refresh"));
    }

    @Test
    void cadastroIgnoraPerfilAdminEnviadoPeloCliente() {
        AuthResponse resposta = service.registrar(new RegistroRequest("Ana", "ana@exemplo.com", "senha123", Perfil.ADMIN));

        verify(usuarios).criar("uid-1", "Ana", "ana@exemplo.com", Perfil.ALUNO);
        verify(usuarios, never()).criar(any(), any(), any(), eq(Perfil.ADMIN));
        assertEquals(Perfil.ALUNO, resposta.perfil());
    }

    @Test
    void cadastroIgnoraPerfilProfessorEnviadoPeloCliente() {
        service.registrar(new RegistroRequest("Ana", "ana@exemplo.com", "senha123", Perfil.PROFESSOR));

        verify(usuarios).criar("uid-1", "Ana", "ana@exemplo.com", Perfil.ALUNO);
        verify(usuarios, never()).criar(any(), any(), any(), eq(Perfil.PROFESSOR));
    }

    @Test
    void cadastroSemPerfilCriaAluno() {
        AuthResponse resposta = service.registrar(new RegistroRequest("Ana", "ana@exemplo.com", "senha123", null));

        verify(usuarios).criar("uid-1", "Ana", "ana@exemplo.com", Perfil.ALUNO);
        assertEquals(Perfil.ALUNO, resposta.perfil());
    }
}