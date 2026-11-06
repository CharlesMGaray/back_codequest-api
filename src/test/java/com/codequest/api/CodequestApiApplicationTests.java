package com.codequest.api;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class CodequestApiApplicationTests {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @MockitoBean
    FirebaseApp firebaseApp;

    @MockitoBean
    Firestore firestore;

    @MockitoBean
    FirebaseAuth firebaseAuth;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void permitePreflightDoFrontendSemAutenticacao() throws Exception {
        mvc.perform(options("/api/auth/registrar")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));

        verifyNoInteractions(firebaseAuth, firestore);
    }

    @Test
    void rejeitaPreflightDeOutraOrigem() throws Exception {
        mvc.perform(options("/api/auth/registrar")
                        .header(HttpHeaders.ORIGIN, "http://origem-nao-permitida.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        verifyNoInteractions(firebaseAuth, firestore);
    }

    @Test
    void mantemAutenticacaoNasRotasProtegidasComCors() throws Exception {
        mvc.perform(get("/api/trilhas").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

        verifyNoInteractions(firebaseAuth, firestore);
    }

}
