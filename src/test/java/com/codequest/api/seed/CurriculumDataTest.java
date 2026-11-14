package com.codequest.api.seed;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurriculumDataTest {

    @Test
    void cadaLicaoTemCincoQuestoesAtivasEUmaExtraEmTresNiveis() {
        var trilhas = DataSeeder.curriculum();

        assertEquals(6, trilhas.size());
        for (var trilha : trilhas) {
            int totalQuestoes = trilha.licoes().stream()
                    .mapToInt(licao -> licao.perguntas().size())
                    .sum();

            assertTrue(totalQuestoes >= 18 && totalQuestoes <= 24,
                    () -> trilha.titulo() + " tem " + totalQuestoes + " questoes");
            assertTrue(!trilha.licoes().isEmpty(), trilha.titulo() + " precisa ter licoes");

            for (var licao : trilha.licoes()) {
                assertEquals(6, licao.perguntas().size(),
                        licao.titulo() + " precisa ter cinco questoes e uma extra");
                Set<Integer> niveis = new HashSet<>();
                for (int index = 0; index < licao.perguntas().size(); index++) {
                    var pergunta = licao.perguntas().get(index);
                    assertEquals(4, pergunta.opcoes().size(), pergunta.enunciado());
                    assertEquals(1, pergunta.opcoes().stream().filter(DataSeeder.OpcaoSeed::correta).count(),
                            pergunta.enunciado());
                    int dificuldade = DataSeeder.difficultyForQuestion(index, licao.perguntas().size());
                    niveis.add(dificuldade);
                    var seeded = DataSeeder.toMap(pergunta, licao.id(), dificuldade);
                    assertTrue(((String) seeded.get("dica1")).length() > 0, pergunta.enunciado());
                    assertNotEquals(seeded.get("dica1"), seeded.get("dica2"),
                            pergunta.enunciado());
                    assertTrue(((String) seeded.get("explicacao")).contains(
                                    pergunta.opcoes().stream().filter(DataSeeder.OpcaoSeed::correta)
                                            .findFirst().orElseThrow().texto()),
                            pergunta.enunciado());
                }
                assertEquals(Set.of(1, 2, 3), niveis,
                        licao.titulo() + " precisa incluir facil, medio e dificil");
            }
        }
    }
}
