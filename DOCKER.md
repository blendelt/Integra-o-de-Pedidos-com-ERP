# Execução com Docker

Na raiz `order-integration`, com Docker Desktop ativo:

```powershell
docker compose up --build -d --wait
docker compose ps
```

Abra http://localhost:8088. As portas externas foram separadas das usadas no IntelliJ:

| Componente | Endereço local |
|---|---|
| Frontend | http://localhost:8088 |
| Order Service | http://localhost:18080/orders |
| ERP Service | http://localhost:18081/health |
| PostgreSQL | localhost:5433, banco orders, usuário orders, senha padrão orders |

O banco do Compose usa um volume próprio; não importa automaticamente os dados do PostgreSQL local. Mantivemos o volume `postgres-data` do Compose anterior. Se ele já foi inicializado, suas credenciais existentes continuam valendo: mudar DB_PASSWORD não altera a senha de um volume existente.

## Decisões

- Builds em estágios: Maven e Node ficam nos estágios de compilação; as imagens finais usam Java 17 JRE e Nginx.
- Java executa com usuário sem privilégios. Dependências e caches locais da IDE não entram no contexto de build.
- Node 24 é usado no build Angular. O aviso de orçamento CSS continua não bloqueante.
- Nginx serve o Angular e encaminha `/orders` ao Order Service na rede Docker. O frontend usa a mesma origem, sem precisar de CORS.
- As chamadas POST não são repetidas automaticamente pelo proxy.
- O Compose aguarda os healthchecks antes de iniciar componentes dependentes. No Order Service, GET /orders verifica também o acesso ao banco; para maior escala, substituir por endpoint de readiness dedicado.
- O healthcheck do frontend testa o Nginx; não garante a disponibilidade posterior da API.
- Flyway cria/valida o esquema na inicialização. O volume mantém dados entre reinícios.
- As portas publicadas ficam restritas a localhost. Credenciais padrão são apenas para desenvolvimento.
- A imagem Java é empacotada com testes desabilitados no build; isso não substitui a execução das suítes já validada separadamente.

## Configuração

O Compose aceita variáveis do ambiente ou de um arquivo `.env` na raiz. Principais opções: DB_PASSWORD, POSTGRES_PORT, ORDER_PORT, ERP_PORT, FRONTEND_PORT, ERP_CONNECT_TIMEOUT, ERP_READ_TIMEOUT, ORDER_PROCESSING_WORKERS, ORDER_PROCESSING_BATCH_SIZE, ORDER_PROCESSING_SCHEDULING_ENABLED, ORDER_PROCESSING_FIXED_DELAY, ORDER_PROCESSING_INITIAL_DELAY, ERP_MINIMUM_DELAY_MS, ERP_MAXIMUM_DELAY_MS, ERP_FAILURE_PREFIX e ERP_RANDOM_FAILURE_RATE. Os valores padrão estão em compose.yaml.

A chamada manual de lote é síncrona e o proxy espera até 120 segundos. Ao aumentar muito lote/atrasos ou reduzir workers, ajuste esse limite ou evolua o endpoint para processamento assíncrono. Um timeout no navegador não cancela necessariamente o lote.

## Validação após subir

1. Confirme os quatro serviços como healthy em `docker compose ps`.
2. Cadastre um pedido comum e um com prefixo FAIL-.
3. Processe e confira SUCCESS e ERROR.
4. Corrija e reprocessse o pedido com erro.
5. Execute `docker compose restart` e confirme que os pedidos permanecem no banco.

```powershell
docker compose logs --tail=100 order-service erp-service frontend
docker compose down
```

`down` preserva o volume. Não use `down -v` se quiser manter os dados. A deduplicação do simulador ERP continua em memória e se perde ao reiniciar o ERP.

## Situação da validação

A configuração foi validada com `docker compose config --quiet`. Build das imagens, inicialização e fluxo em containers ainda pendentes: o ambiente do agente não tem acesso ao canal do Docker Desktop.
