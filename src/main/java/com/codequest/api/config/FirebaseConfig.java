package com.codequest.api.config;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

@Configuration
public class FirebaseConfig {

    @Bean
    FirebaseApp firebaseApp(
            @Value("${firebase.project-id}") String projectId,
            @Value("${firebase.credentials-path}") String credentialsPath) throws IOException {

        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("Defina FIREBASE_PROJECT_ID (firebase.project-id).");
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setProjectId(projectId)
                .setCredentials(loadCredentials(credentialsPath))
                .build();

        return FirebaseApp.getApps().stream()
                .filter(app -> app.getName().equals(FirebaseApp.DEFAULT_APP_NAME))
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(options));
    }

    @Bean
    Firestore firestore(FirebaseApp app) {
        return FirestoreClient.getFirestore(app);
    }

    @Bean
    FirebaseAuth firebaseAuth(FirebaseApp app) {
        return FirebaseAuth.getInstance(app);
    }

    private GoogleCredentials loadCredentials(String credentialsPath) throws IOException {
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            try (InputStream in = new FileInputStream(credentialsPath)) {
                return GoogleCredentials.fromStream(in);
            }
        }
        if (isEmulator()) {
            // Os emuladores nao validam credenciais.
            return GoogleCredentials.create(new AccessToken("owner", new Date(Long.MAX_VALUE)));
        }
        return GoogleCredentials.getApplicationDefault();
    }

    private static boolean isEmulator() {
        return notBlank(System.getenv("FIRESTORE_EMULATOR_HOST"))
                || notBlank(System.getenv("FIREBASE_AUTH_EMULATOR_HOST"));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
