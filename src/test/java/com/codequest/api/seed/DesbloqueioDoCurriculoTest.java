package com.codequest.api.seed;

import com.codequest.api.trilha.LicaoDesbloqueio;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Aplica a regra de desbloqueio ao curriculo real: cada trilha libera as licoes uma a uma, na ordem. */
class DesbloqueioDoCurriculoTest {

    @Test
    void cadaTrilhaLiberaAsLicoesUmaAUmaNaOrdem() {
        for (var trilha : DataSeeder.curriculum()) {
            Map<String, Integer> ordens = new LinkedHashMap<>();
            trilha.licoes().forEach(licao -> ordens.put(licao.id(), licao.ordem()));
            assertEquals(trilha.licoes().size(), new HashSet<>(ordens.values()).size(),
                    trilha.titulo() + " tem licoes com a mesma ordem");

            var emOrdem = trilha.licoes().stream()
                    .sorted(Comparator.comparingInt(DataSeeder.LicaoSeed::ordem))
                    .toList();
            Set<String> concluidas = new HashSet<>();
            for (int i = 0; i < emOrdem.size(); i++) {
                assertTrue(LicaoDesbloqueio.liberada(emOrdem.get(i).id(), ordens, concluidas),
                        trilha.titulo() + ": licao " + (i + 1) + " deveria estar liberada");
                for (int j = i + 1; j < emOrdem.size(); j++) {
                    assertFalse(LicaoDesbloqueio.liberada(emOrdem.get(j).id(), ordens, concluidas),
                            trilha.titulo() + ": licao " + (j + 1) + " deveria estar bloqueada");
                }
                concluidas.add(emOrdem.get(i).id());
            }
        }
    }
}