package com.codequest.api;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class CodequestApiApplicationTests {

    @MockitoBean
    FirebaseApp firebaseApp;

    @MockitoBean
    Firestore firestore;

    @MockitoBean
    FirebaseAuth firebaseAuth;

    @Test
    void contextLoads() {
    }

}
