# Plano de implementação — Integração de pedidos com ERP

## Estado atual

### Já implementado

- [x] Projeto Maven do Order Service com Java 17 e Spring Boot
- [x] Dependências Web, JPA, Validation e PostgreSQL
- [x] Organização em camadas familiares: controller, DTO, entity, enum, exception, repository e service
- [x] Entidade `Order` e enum `OrderStatus`
- [x] `POST /orders`
- [x] `GET /orders`
- [x] Validação básica do cadastro
- [x] Restrição única de `externalId`
- [x] Resposta HTTP 409 para pedido duplicado
- [x] Tratamento centralizado dos erros de validação
- [x] Migração inicial versionada com Flyway
- [x] ERP Service independente na porta 8081
- [x] Simulação de atraso configurável e falha determinística
- [x] Endpoint de saúde do ERP

### Ainda não implementado

- [x] ERP Service
- [x] Cliente HTTP do ERP no Order Service
- [x] Processamento manual e agendado (paralelo por lote; validação descrita em `PARALLEL_PROCESSING.md`)
- [x] Processamento paralelo com limite de concorrência (testes adicionados; execução pendente)
- [x] Proteção contra duas execuções processarem o mesmo pedido (reserva transacional implementada; execução do teste PostgreSQL pendente)
- [ ] Timeout e tratamento de falha do ERP
- [ ] Frontend Angular
- [ ] Dockerfiles e Docker Compose
- [ ] Testes relevantes
- [ ] README final e roteiro do vídeo

## Decisões principais


### Banco e idempotência

- Usar PostgreSQL.
- `external_id` terá restrição `UNIQUE` no banco.
- A consulta `existsByExternalId` melhora a mensagem, mas a restrição do banco é a garantia contra concorrência.
- Usar Flyway para criar e evoluir as tabelas; remover `ddl-auto: update` da entrega final.

### Processamento paralelo

- Usar um `ThreadPoolTaskExecutor` com quantidade fixa de workers configurável.
- Selecionar pedidos pendentes em lotes.
- Reservar cada pedido em uma transação curta, alterando `PENDING` para `PROCESSING`.
- Usar bloqueio do PostgreSQL com `FOR UPDATE SKIP LOCKED` para que duas chamadas de processamento não reservem o mesmo pedido.
- Executar a chamada HTTP ao ERP fora da transação que reserva o pedido.
- Atualizar o pedido para `SUCCESS` ou `ERROR` em uma nova transação curta.

### Falhas

- Configurar timeout de conexão e de resposta no cliente HTTP.
- Registrar número de tentativas e última mensagem de erro protegida.
- Nesta primeira versão, não fazer retry automático. O pedido com erro poderá ser processado novamente por uma operação explícita futura.
- O ERP Service terá falha determinística para testes e uma taxa aleatória opcional configurável.

## Fases de implementação

### Fase 1 — Consolidar o Order Service

- [x] Adicionar Flyway e criar a migração inicial da tabela `orders`
- [x] Adicionar métodos de domínio para iniciar, concluir e falhar o processamento
- [ ] Revisar respostas e códigos HTTP
- [ ] Adicionar logs com identificador do pedido, sem registrar dados sensíveis
- [ ] Configurar CORS apenas para desenvolvimento, caso não seja usado proxy no frontend

Critério de conclusão:

- Cadastrar pedido válido retorna 201.
- `externalId` repetido retorna 409.
- Listagem retorna os pedidos ordenados pela data.
- Entrada inválida retorna 400 com os campos inválidos.

### Fase 2 — Criar o ERP Service

- [x] Criar projeto Maven independente com Java 17 e Spring Boot
- [x] Implementar `POST /erp/orders`
- [x] Simular atraso configurável entre 500 ms e 2 segundos
- [x] Simular falha determinística por `externalId`
- [x] Adicionar endpoint de saúde

Critério de conclusão:

- Pedido normal retorna sucesso após o atraso.
- Pedido preparado para falha retorna HTTP 500.
- O comportamento pode ser demonstrado de forma repetível.

### Fase 3 — Integrar e processar pedidos

- [x] Criar `ErpClient`
- [x] Configurar timeouts
- [x] Criar consulta para reservar pedidos com bloqueio concorrente (teste real de concorrência pendente)
- [x] Criar `OrderProcessor`
- [x] Configurar executor com limite de paralelismo
- [x] Implementar `POST /orders/process`
- [x] Implementar job agendado usando o mesmo serviço de processamento
- [x] Atualizar status e tentativas após cada resultado

Critério de conclusão:

- Vários pedidos são enviados simultaneamente ao ERP.
- Duas chamadas concorrentes ao endpoint não processam o mesmo pedido ao mesmo tempo.
- Sucesso muda o status para `SUCCESS`.
- Falha muda o status para `ERROR` e não interrompe os demais pedidos.

### Fase 4 — Criar o frontend Angular

- [ ] Criar projeto Angular
- [ ] Criar modelo e service HTTP de pedidos
- [ ] Criar formulário de cadastro com validação
- [ ] Criar tabela de pedidos
- [ ] Criar botão para processar pedidos pendentes
- [ ] Criar atualização manual da listagem
- [ ] Exibir carregamento, sucesso e erros
- [ ] Fazer atualização periódica enquanto houver pedidos em processamento

Critério de conclusão:

- Usuário cadastra um pedido.
- Pedido aparece na tabela.
- Botão inicia o processamento.
- Status é atualizado na interface.
- Erros de validação, duplicidade e integração são apresentados claramente.

### Fase 5 — Docker

- [ ] Dockerfile do Order Service
- [ ] Dockerfile do ERP Service
- [ ] Dockerfile do frontend com Nginx
- [ ] `docker-compose.yml` com os quatro containers
- [ ] Variáveis de ambiente para URLs, banco, paralelismo e simulação de falhas
- [ ] Healthchecks e dependências de inicialização

Critério de conclusão:

- Um único comando sobe toda a aplicação.
- Frontend acessa o Order Service.
- Order Service acessa o ERP Service.
- Dados permanecem no volume do PostgreSQL após reiniciar os containers.

### Fase 6 — Testes

#### Order Service

- [ ] Teste de criação de pedido válido
- [ ] Teste de validação dos campos
- [ ] Teste de `externalId` duplicado
- [ ] Teste dos métodos de mudança de status
- [ ] Teste de sucesso na integração
- [ ] Teste de erro na integração
- [ ] Teste de concorrência com duas chamadas de processamento

#### ERP Service

- [ ] Teste do atraso configurado sem depender de tempo aleatório
- [ ] Teste da resposta de sucesso
- [ ] Teste da falha determinística

#### Frontend

- [ ] Teste do formulário e validações
- [ ] Teste do service HTTP
- [ ] Teste dos estados de carregamento e erro

#### Aplicação completa

- [ ] Subir o Docker Compose
- [ ] Cadastrar pedidos
- [ ] Confirmar bloqueio de duplicidade
- [ ] Processar um lote com sucessos e falhas
- [ ] Disparar duas chamadas de processamento simultâneas
- [ ] Confirmar que nenhum pedido foi enviado duas vezes simultaneamente

## Ordem de trabalho

1. Concluir a Fase 1 e testar o cadastro.
2. Criar e testar isoladamente o ERP Service.
3. Implementar a integração sem paralelismo.
4. Adicionar paralelismo e controle de concorrência.
5. Criar o frontend Angular.
6. Empacotar tudo com Docker.
7. Executar os testes de ponta a ponta.
8. Escrever o README e gravar o vídeo.

## Forma de trabalho em conjunto

- Implementamos uma fase por vez.
- Ao fim de cada fase, executamos os testes correspondentes.
- Antes de avançar, você revisa o código na IDE e explica o fluxo com suas palavras.
- Se houver um trecho que você não consiga explicar, simplificamos ou estudamos antes de mantê-lo.
- No final, fazemos uma simulação do vídeo e perguntas sobre concorrência, transações, falhas e decisões técnicas.
