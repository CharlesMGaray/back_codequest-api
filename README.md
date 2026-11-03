# CodeQuest API

API Spring Boot que usa **Firebase Authentication** (login/cadastro) e **Cloud Firestore** (dados).

## Configuração

No [Firebase Console](https://console.firebase.google.com):

1. **Authentication > Método de login**: ative **E-mail/senha**.
2. **Firestore Database**: crie o banco.
3. **Configurações do projeto > Contas de serviço > Gerar nova chave privada**: baixe o JSON.
   Guarde-o **fora do repositório** (ele dá acesso total ao projeto).
4. **Configurações do projeto > Geral**: copie o *ID do projeto* e a *Chave de API da Web*.

Variáveis de ambiente:

| Variável | Descrição |
|---|---|
| `FIREBASE_PROJECT_ID` | ID do projeto |
| `FIREBASE_CREDENTIALS_PATH` | Caminho do JSON da service account (ou use `GOOGLE_APPLICATION_CREDENTIALS`) |
| `FIREBASE_WEB_API_KEY` | Chave de API da Web (usada no login por e-mail/senha) |
| `CODEQUEST_SEED_ENABLED` | `true` (padrão) cria trilhas/lições de exemplo se `trilhas` estiver vazia |

```bash
export FIREBASE_PROJECT_ID=meu-projeto
export FIREBASE_CREDENTIALS_PATH=$HOME/.secrets/codequest-firebase.json
export FIREBASE_WEB_API_KEY=AIza...
mvn spring-boot:run
```

### Rodando com os emuladores (sem tocar no projeto real)

```bash
npx firebase-tools emulators:start --only auth,firestore --project demo-codequest
# em outro terminal
FIREBASE_PROJECT_ID=demo-codequest FIREBASE_WEB_API_KEY=fake \
FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 \
mvn spring-boot:run
```

## Endpoints

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| POST | `/api/auth/registrar` | – | `{nome, email, senha, perfil}` → token + usuário |
| POST | `/api/auth/login` | – | `{email, senha}` → token + usuário |
| POST | `/api/auth/refresh` | – | `{refreshToken}` → novo token (o token do Firebase expira em 1h) |
| GET | `/api/auth/me` | Bearer | Perfil do usuário logado |
| GET | `/api/trilhas` | Bearer | Trilhas com lições, perguntas e flag `concluida` |
| POST | `/api/licoes/{id}/concluir` | Bearer | Soma o XP uma única vez (transação) |
| GET | `/api/ranking?limite=10` | Bearer | Top usuários por XP |

Erros sempre retornam `{"message": "..."}`.

## Estrutura no Firestore

```
usuarios/{uid}                             nome, email, perfil, xpTotal, nivelAtual, criadoEm
usuarios/{uid}/licoesConcluidas/{licaoId}  trilhaId, xpGanho, concluidaEm
trilhas/{trilhaId}                         titulo, descricao, ordem
licoes/{licaoId}                           trilhaId, ordem, titulo, resumo, xpBonus,
                                           perguntas[{id, enunciado, opcoes[{id, texto, correta}]}]
```

Nível = `xpTotal / 100 + 1`. Só a API acessa o Firestore (via Admin SDK), então as regras
de segurança do Firestore podem bloquear todo acesso direto de clientes:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} { allow read, write: if false; }
  }
}
```
