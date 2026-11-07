package com.codequest.api.trilha;

import com.codequest.api.common.ApiException;
import com.codequest.api.trilha.TrilhaDtos.ConclusaoResponse;
import com.codequest.api.trilha.TrilhaDtos.LicaoResponse;
import com.codequest.api.trilha.TrilhaDtos.OpcaoResponse;
import com.codequest.api.trilha.TrilhaDtos.PerguntaResponse;
import com.codequest.api.trilha.TrilhaDtos.TrilhaResponse;
import com.codequest.api.usuario.Usuario;
import com.codequest.api.usuario.UsuarioRepository;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.codequest.api.common.Futures.await;

/**
 * Estrutura no Firestore:
 * <pre>
 * trilhas/{trilhaId}                       titulo, descricao, ordem
 * licoes/{licaoId}                         trilhaId, ordem, titulo, resumo, xpBonus, perguntas[]
 * usuarios/{uid}/licoesConcluidas/{licaoId} trilhaId, xpGanho, concluidaEm
 * </pre>
 */
@Service
public class TrilhaService {

    public static final String TRILHAS = "trilhas";
    public static final String LICOES = "licoes";
    public static final String CONCLUIDAS = "licoesConcluidas";

    private final Firestore db;

    public TrilhaService(Firestore db) {
        this.db = db;
    }

    public List<TrilhaResponse> listar(String uid) {
        var trilhasFuture = db.collection(TRILHAS).get();
        var licoesFuture = db.collection(LICOES).get();
        var concluidasFuture = concluidas(uid).get();

        Set<String> concluidas = await(concluidasFuture).getDocuments().stream()
                .map(DocumentSnapshot::getId)
                .collect(Collectors.toSet());

        Map<String, List<LicaoResponse>> licoesPorTrilha = await(licoesFuture).getDocuments().stream()
                .map(doc -> toLicao(doc, concluidas.contains(doc.getId())))
                .sorted(Comparator.comparingInt(LicaoResponse::ordem))
                .collect(Collectors.groupingBy(LicaoResponse::trilhaId));

        return await(trilhasFuture).getDocuments().stream()
                .map(doc -> new TrilhaResponse(
                        doc.getId(),
                        doc.getString("titulo"),
                        doc.getString("descricao"),
                        intOf(doc.getLong("ordem")),
                        licoesPorTrilha.getOrDefault(doc.getId(), List.of())))
                .sorted(Comparator.comparingInt(TrilhaResponse::ordem))
                .toList();
    }

    /** Conclui a licao uma unica vez por usuario, somando o XP de forma atomica. */
    public ConclusaoResponse concluirLicao(String uid, String licaoId) {
        return concluirLicao(uid, licaoId, null);
    }

    @SuppressWarnings("unchecked")
    public ConclusaoResponse concluirLicao(String uid, String licaoId, TrilhaDtos.ConclusaoRequest request) {
        DocumentReference licaoRef = db.collection(LICOES).document(licaoId);
        DocumentReference usuarioRef = db.collection(UsuarioRepository.COLECAO).document(uid);
        DocumentReference conclusaoRef = concluidas(uid).document(licaoId);

        return await(db.runTransaction(tx -> {
            DocumentSnapshot licao = tx.get(licaoRef).get();
            DocumentSnapshot usuario = tx.get(usuarioRef).get();
            DocumentSnapshot conclusao = tx.get(conclusaoRef).get();

            if (!licao.exists()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Licao nao encontrada.");
            }
            if (!usuario.exists()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Usuario nao encontrado.");
            }
            if (conclusao.exists()) {
                throw new ApiException(HttpStatus.CONFLICT, "Licao ja concluida.");
            }

            List<Map<String, Object>> perguntas = (List<Map<String, Object>>) licao.get("perguntas");
            if (perguntas != null && !perguntas.isEmpty()) {
                if (request == null || request.respostas() == null
                        || request.respostas().size() != perguntas.size()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Responda corretamente a todas as perguntas.");
                }

                for (var p : perguntas) {
                    String pId = String.valueOf(p.get("id"));
                    String selectedOpId = request.respostas().get(pId);
                    List<Map<String, Object>> opcoes = (List<Map<String, Object>>) p.get("opcoes");
                    boolean acertou = opcoes != null && opcoes.stream().anyMatch(o ->
                            String.valueOf(o.get("id")).equals(selectedOpId) && Boolean.TRUE.equals(o.get("correta")));
                    if (!acertou) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "Uma ou mais respostas estao incorretas.");
                    }
                }
            }

            int xpGanho = intOf(licao.getLong("xpBonus"));
            Long xpAtual = usuario.getLong("xpTotal");
            long xpTotal = (xpAtual == null ? 0 : xpAtual) + xpGanho;
            int nivel = Usuario.nivelPara(xpTotal);

            tx.set(conclusaoRef, Map.of(
                    "trilhaId", String.valueOf(licao.getString("trilhaId")),
                    "xpGanho", xpGanho,
                    "concluidaEm", FieldValue.serverTimestamp()));
            tx.update(usuarioRef, Map.of("xpTotal", xpTotal, "nivelAtual", nivel));

            return new ConclusaoResponse(licaoId, xpGanho, xpTotal, nivel);
        }));
    }

    private com.google.cloud.firestore.CollectionReference concluidas(String uid) {
        return db.collection(UsuarioRepository.COLECAO).document(uid).collection(CONCLUIDAS);
    }

    @SuppressWarnings("unchecked")
    private static LicaoResponse toLicao(DocumentSnapshot doc, boolean concluida) {
        List<Map<String, Object>> perguntas = (List<Map<String, Object>>) doc.get("perguntas");
        List<PerguntaResponse> perguntasDto = perguntas == null ? List.of() : perguntas.stream()
                .map(p -> {
                    List<Map<String, Object>> opcoes = (List<Map<String, Object>>) p.get("opcoes");
                    List<OpcaoResponse> opcoesDto = opcoes == null ? List.of() : opcoes.stream()
                            .map(o -> new OpcaoResponse(
                                    String.valueOf(o.get("id")),
                                    (String) o.get("texto"),
                                    Boolean.TRUE.equals(o.get("correta"))))
                            .toList();
                    return new PerguntaResponse(String.valueOf(p.get("id")), (String) p.get("enunciado"), opcoesDto);
                })
                .toList();

        return new LicaoResponse(
                doc.getId(),
                doc.getString("trilhaId"),
                intOf(doc.getLong("ordem")),
                doc.getString("titulo"),
                doc.getString("resumo"),
                intOf(doc.getLong("xpBonus")),
                concluida,
                perguntasDto);
    }

    private static int intOf(Long value) {
        return value == null ? 0 : value.intValue();
    }
}
