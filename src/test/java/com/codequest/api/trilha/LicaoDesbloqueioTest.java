package com.codequest.api.trilha;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicaoDesbloqueioTest {

    private static final Map<String, Integer> TRILHA = Map.of("l1", 1, "l2", 2, "l3", 3);

    @Test
    void primeiraLicaoDaTrilhaEstaSempreLiberada() {
        assertTrue(LicaoDesbloqueio.liberada("l1", TRILHA, Set.of()));
    }

    @Test
    void segundaLicaoFicaBloqueadaAteConcluirAPrimeira() {
        assertFalse(LicaoDesbloqueio.liberada("l2", TRILHA, Set.of()));
        assertTrue(LicaoDesbloqueio.liberada("l2", TRILHA, Set.of("l1")));
    }

    @Test
    void naoEPossivelPularLicoes() {
        assertFalse(LicaoDesbloqueio.liberada("l3", TRILHA, Set.of("l1")));
        assertFalse(LicaoDesbloqueio.liberada("l3", TRILHA, Set.of("l2")));
        assertTrue(LicaoDesbloqueio.liberada("l3", TRILHA, Set.of("l1", "l2")));
    }

    @Test
    void licaoPosteriorConcluidaForaDeOrdemNaoLiberaAsDemais() {
        assertFalse(LicaoDesbloqueio.liberada("l2", TRILHA, Set.of("l3")));
    }

    @Test
    void licoesConcluidasEmOutraTrilhaNaoLiberamEstaLicao() {
        assertFalse(LicaoDesbloqueio.liberada("l2", TRILHA, Set.of("outra-trilha-1", "outra-trilha-2")));
    }

    @Test
    void licaoJaConcluidaContinuaLiberada() {
        assertTrue(LicaoDesbloqueio.liberada("l2", TRILHA, Set.of("l1", "l2")));
    }

    @Test
    void licaoQueNaoPertenceATrilhaNaoELiberada() {
        assertFalse(LicaoDesbloqueio.liberada("inexistente", TRILHA, Set.of("l1", "l2", "l3")));
    }
}