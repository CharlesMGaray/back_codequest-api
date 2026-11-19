package com.codequest.api.security;

import com.codequest.api.trilha.TrilhaService;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que o usuario autenticado so alcanca os proprios dados: o UID usado pelas rotas de
 * progresso vem sempre do token validado, e nunca de parametros enviados pelo cliente.
 */
@SpringBootTest
@ActiveProfiles("test")
class AcessoAosProprioDadosTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @MockitoBean
    FirebaseApp firebaseApp;

    @MockitoBean
    Firestore firestore;

    @MockitoBean
    FirebaseAuth firebaseAuth;

    @MockitoBean
    TrilhaService trilhaService;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        FirebaseToken tokenA = mock(FirebaseToken.class);
        when(tokenA.getUid()).thenReturn("uid-a");
        when(firebaseAuth.verifyIdToken("token-a")).thenReturn(tokenA);

        FirebaseToken tokenB = mock(FirebaseToken.class);
        when(tokenB.getUid()).thenReturn("uid-b");
        when(firebaseAuth.verifyIdToken("token-b")).thenReturn(tokenB);
    }

    @Test
    void cadaUsuarioConsultaApenasOProprioProgresso() throws Exception {
        mvc.perform(get("/api/progresso").header(HttpHeaders.AUTHORIZATION, "Bearer token-a"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/progresso").header(HttpHeaders.AUTHORIZATION, "Bearer token-b"))
                .andExpect(status().isOk());

        verify(trilhaService, times(1)).listarProgresso("uid-a");
        verify(trilhaService, times(1)).listarProgresso("uid-b");
        verifyNoMoreInteractions(trilhaService);
    }

    @Test
    void uidEnviadoNoCorpoOuNaUrlNaoSubstituiOUidDoToken() throws Exception {
        mvc.perform(post("/api/licoes/logica-1/responder")
                        .param("uid", "uid-b")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"p1\",\"optionId\":\"a\",\"tentativa\":1,\"uid\":\"uid-b\"}"))
                .andExpect(status().isOk());

        verify(trilhaService).registrarResposta(eq("uid-a"), eq("logica-1"), any());
        verify(trilhaService, never()).registrarResposta(eq("uid-b"), any(), any());
    }

    @Test
    void conclusaoDeLicaoUsaOUidDoToken() throws Exception {
        mvc.perform(post("/api/licoes/logica-1/concluir")
                        .param("uid", "uid-b")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"respostas\":{\"p1\":\"a\"},\"uid\":\"uid-b\"}"))
                .andExpect(status().isOk());

        verify(trilhaService).concluirLicao(eq("uid-a"), eq("logica-1"), any());
        verify(trilhaService, never()).concluirLicao(eq("uid-b"), any(), any());
    }

    @Test
    void tokenInvalidoNaoChegaAoService() throws Exception {
        when(firebaseAuth.verifyIdToken("token-ruim")).thenThrow(new IllegalArgumentException("token invalido"));

        mvc.perform(get("/api/progresso").header(HttpHeaders.AUTHORIZATION, "Bearer token-ruim"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/licoes/logica-1/concluir")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-ruim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"respostas\":{}}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(trilhaService);
    }
}