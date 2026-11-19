package com.codequest.api.trilha;

import java.util.Map;
import java.util.Set;

/**
 * Regra de desbloqueio das licoes: uma licao so e liberada quando todas as licoes anteriores
 * da mesma trilha (de menor ordem) ja foram concluidas pelo usuario. A primeira licao da trilha
 * esta sempre liberada. E a mesma regra que a interface usa para mostrar as licoes bloqueadas.
 */
public final class LicaoDesbloqueio {

    private LicaoDesbloqueio() {
    }

    /**
     * @param licaoId        licao que o usuario quer acessar
     * @param ordensDaTrilha id e ordem de todas as licoes da trilha a que a licao pertence
     * @param concluidas     ids das licoes que o usuario ja concluiu
     */
    public static boolean liberada(String licaoId, Map<String, Integer> ordensDaTrilha, Set<String> concluidas) {
        Integer ordem = ordensDaTrilha.get(licaoId);
        if (ordem == null) {
            return false;
        }
        return ordensDaTrilha.entrySet().stream()
                .filter(outra -> outra.getValue() < ordem)
                .allMatch(anterior -> concluidas.contains(anterior.getKey()));
    }
}