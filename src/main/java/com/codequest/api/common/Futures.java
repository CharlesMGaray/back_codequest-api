package com.codequest.api.common;

import com.google.api.core.ApiFuture;

import java.util.concurrent.ExecutionException;

public final class Futures {

    private Futures() {
    }

    /** Aguarda uma operacao do Firestore, repassando ApiException e embrulhando o resto. */
    public static <T> T await(ApiFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Operacao interrompida", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            // Transacoes do Firestore podem embrulhar a excecao lancada dentro delas.
            while (cause != null && !(cause instanceof ApiException) && cause.getCause() != null
                    && cause != cause.getCause()) {
                cause = cause.getCause();
            }
            if (cause instanceof ApiException api) {
                throw api;
            }
            throw new IllegalStateException("Falha ao acessar o Firestore", e.getCause());
        }
    }
}
