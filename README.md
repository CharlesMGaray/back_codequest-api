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
| `CODEQUEST_SEED_ENABLED` | `true` (padrão) sincroniza o currículo inicial de trilhas e lições ao iniciar a API |

O currículo inicial contém seis trilhas, cada uma com 10 a 15 questões de múltipla
escolha. As trilhas e lições mantidas pelo seed são atualizadas ao iniciar a API,
inclusive quando já existem dados no Firestore; o progresso dos usuários fica
preservado. Defina `CODEQUEST_SEED_ENABLED=false` para desativar essa sincronização.

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
| GET | `/api/progresso` | ****** Domínio, tentativas e próxima revisão por habilidade |
| POST | `/api/licoes/{id}/responder` | ****** Valida uma alternativa, aplica até três tentativas e atualiza o domínio |
| GET | `/api/auth/me` | Bearer | Perfil do usuário logado |
| GET | `/api/trilhas` | Bearer | Trilhas com lições, perguntas e flag `concluida` |
| POST | `/api/licoes/{id}/concluir` | Bearer | Soma o XP uma única vez (transação) |
| GET | `/api/ranking?limite=10` | Bearer | Top usuários por XP |

Erros sempre retornam `{"message": "..."}`.
O endpoint de resposta recebe `{questionId, optionId, tentativa}`; envie tentativa
`1`, `2` ou `3` em sequência para cada questão.

## Estrutura no Firestore

```
usuarios/{uid}                             nome, email, perfil, xpTotal, nivelAtual, criadoEm
usuarios/{uid}/licoesConcluidas/{licaoId}  trilhaId, xpGanho, concluidaEm
trilhas/{trilhaId}                         titulo, descricao, ordem
licoes/{licaoId}                           trilhaId, ordem, titulo, resumo, xpBonus,
                                           perguntas[{id, enunciado, skillId, dificuldade,
                                                      opcoes[{id, texto, correta}]}]
usuarios/{uid}/habilidades/{skillId}       dominio, nivelDominio, tentativas, acertos,
                                           ultimaRespostaEm, proximaRevisao
usuarios/{uid}/tentativas/{tentativaId}    questionId, licaoId, skillId, correta,
                                           dificuldade, respondidaEm
usuarios/{uid}/questoesRespondidas/{id}    tentativa, resolvida, correta, opcaoId
```

Nível = `xpTotal / 100 + 1`. O domínio começa em 50 e é atualizado por média móvel
exponencial após cada resposta. A API recomenda revisar em 1 dia para domínio abaixo
de 60, em 3 dias abaixo de 80 e em 7 dias para domínio igual ou superior a 80. O
domínio considera acerto na primeira, segunda ou terceira tentativa, ou esgotamento
das tentativas. As questões são enviadas sem o gabarito; a correção é feita no backend.
O quiz prioriza questões mais acessíveis para habilidades que precisam de revisão e
desafios mais difíceis para habilidades dominadas. Respostas erradas exibem duas dicas
progressivas; após o terceiro erro, a API revela a resposta e a explicação. Só a API
acessa o Firestore (via
Admin SDK), então as regras de segurança do Firestore podem bloquear todo acesso direto:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} { allow read, write: if false; }
  }
}
```
