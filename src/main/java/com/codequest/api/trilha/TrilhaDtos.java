package com.codequest.api.trilha;

import java.util.List;

public final class TrilhaDtos {

    private TrilhaDtos() {
    }

    public record TrilhaResponse(String id, String titulo, String descricao, int ordem, List<LicaoResponse> licoes) {
    }

    public record LicaoResponse(String id, String trilhaId, int ordem, String titulo, String resumo, int xpBonus,
                                boolean concluida, List<PerguntaResponse> perguntas) {
    }

    /** A alternativa correta nao e enviada ao cliente. */
    public record PerguntaResponse(String id, String enunciado, List<OpcaoResponse> opcoes) {
    }

    public record OpcaoResponse(String id, String texto) {
    }

    public record ConclusaoResponse(String licaoId, int xpGanho, long xpTotal, int nivelAtual) {
    }
}
