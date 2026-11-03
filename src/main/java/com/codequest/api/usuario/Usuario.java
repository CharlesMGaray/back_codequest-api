package com.codequest.api.usuario;

/** Documento em usuarios/{uid}. O id e o UID do Firebase Auth. */
public record Usuario(String id, String nome, String email, Perfil perfil, long xpTotal, int nivelAtual) {

    public static final int XP_POR_NIVEL = 100;

    public static int nivelPara(long xpTotal) {
        return (int) (xpTotal / XP_POR_NIVEL) + 1;
    }
}
