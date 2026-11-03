package com.codequest.api.usuario;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.codequest.api.common.Futures.await;

@Repository
public class UsuarioRepository {

    public static final String COLECAO = "usuarios";

    private final Firestore db;

    public UsuarioRepository(Firestore db) {
        this.db = db;
    }

    public CollectionReference colecao() {
        return db.collection(COLECAO);
    }

    public Optional<Usuario> buscar(String uid) {
        DocumentSnapshot doc = await(colecao().document(uid).get());
        return doc.exists() ? Optional.of(fromDoc(doc)) : Optional.empty();
    }

    public Usuario criar(String uid, String nome, String email, Perfil perfil) {
        Map<String, Object> data = new HashMap<>();
        data.put("nome", nome);
        data.put("email", email);
        data.put("perfil", perfil.name());
        data.put("xpTotal", 0L);
        data.put("nivelAtual", Usuario.nivelPara(0));
        data.put("criadoEm", FieldValue.serverTimestamp());
        await(colecao().document(uid).set(data));
        return new Usuario(uid, nome, email, perfil, 0, Usuario.nivelPara(0));
    }

    public List<Usuario> topPorXp(int limite) {
        return await(colecao().orderBy("xpTotal", Query.Direction.DESCENDING).limit(limite).get())
                .getDocuments().stream()
                .map(UsuarioRepository::fromDoc)
                .toList();
    }

    public static Usuario fromDoc(DocumentSnapshot doc) {
        Long xp = doc.getLong("xpTotal");
        long xpTotal = xp == null ? 0 : xp;
        String perfil = doc.getString("perfil");
        return new Usuario(
                doc.getId(),
                doc.getString("nome"),
                doc.getString("email"),
                perfil == null ? Perfil.ALUNO : Perfil.valueOf(perfil),
                xpTotal,
                Usuario.nivelPara(xpTotal));
    }
}
