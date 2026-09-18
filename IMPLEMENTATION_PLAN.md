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

### Etapas e situação atual

- [x] ERP Service
- [x] Cliente HTTP do ERP no Order Service
- [x] Processamento manual e agendado (paralelo por lote; validação descrita em `PARALLEL_PROCESSING.md`)
- [x] Processamento paralelo com limite de concorrência (testes executados pelo usuário e aprovados)
- [x] Proteção contra duas execuções processarem o mesmo pedido (reserva transacional e testes PostgreSQL validados)
- [x] Timeout e tratamento de falha do ERP (categorias seguras e testes aprovados)
- [x] Frontend Angular (build concluído, 5 testes aprovados e fluxo visual validado)
- [ ] Dockerfiles e Docker Compose (arquivos criados e configuração validada; build e execução em containers pendentes)
- [x] Testes automatizados atuais dos serviços e frontend (execução local confirmada pelo usuário)
- [x] README principal e roteiro do vídeo criados (`README.md` e `VIDEO_GUIDE.md`)
- [ ] Gravar vídeo com tela e rosto e disponibilizar link aos avaliadores

## Validação registrada em 17/09/2026

- [x] Testes do Order Service e ERP Service: execução local e aprovação confirmadas pelo usuário.
- [x] Build Angular: concluído em 2,431 segundos; saída em `frontend/dist/frontend`.
- [x] Testes Angular: 1 arquivo e 5 testes aprovados, conforme saída enviada pelo usuário.
- [x] Validação pela tela: pedido de teste #3 falhou, teve identificador, cliente e valor corrigidos, voltou a PENDING e terminou SUCCESS com 2 tentativas e o mesmo ID interno.
- [x] Confirmado bloqueio de reenvio sem marcar a confirmação de não integração no ERP.

Avisos não bloqueantes do build: `app.css` atingiu 4,87 kB, 868 bytes acima do limite de aviso de 4 kB; Node 25.6.0 emitiu recomendação de uso de uma versão LTS. Esses avisos não impediram o build nem os testes. A execução automatizada pelo agente ficou limitada por permissões locais; a aprovação dos comandos completos foi confirmada pelo usuário em seu terminal.

### Edição e reprocessamento concluídos

- [x] Permitir edição somente de pedidos em ERROR.
- [x] Validar versão e bloquear a linha na transação para rejeitar edições concorrentes desatualizadas.
- [x] Preservar ID interno e contador de tentativas ao reenfileirar.
- [x] Validar unicidade do identificador externo corrigido.
- [x] Adicionar testes de reprocessamento no backend e no frontend.
- [x] Proteger o simulador ERP contra repetição do mesmo identificador aceito durante sua execução.

Detalhes e roteiro: `REPROCESSING.md`. A deduplicação do simulador é em memória e não sobrevive a reinícios. A confirmação manual não substitui reconciliação automática com o ERP.

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
- Nesta primeira versão, não fazer retry automático. O pedido com erro pode ser corrigido e reenfileirado explicitamente por `POST /orders/{id}/retry`, após confirmação de que não foi integrado no ERP.
- O ERP Service terá falha determinística para testes e uma taxa aleatória opcional configurável.

## Fases de implementação

### Fase 1 — Consolidar o Order Service

- [x] Adicionar Flyway e criar a migração inicial da tabela `orders`
- [x] Adicionar métodos de domínio para iniciar, concluir e falhar o processamento
- [x] Revisar respostas e códigos HTTP (400/404/409 padronizados; testes dos dois serviços aprovados em execução local, conforme confirmação do usuário)
- [x] Adicionar logs com ID interno, status, tentativas e categoria de falha, sem payload ou externalId bruto
- [x] Usar proxy Angular em desenvolvimento para acessar o Order Service

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
- [x] Criar consulta para reservar pedidos com bloqueio concorrente (testes PostgreSQL aprovados)
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

- [x] Criar projeto Angular
- [x] Criar modelo e service HTTP de pedidos
- [x] Criar formulário de cadastro com validação
- [x] Criar tabela de pedidos
- [x] Criar botão para processar pedidos pendentes
- [x] Criar atualização manual da listagem
- [x] Exibir carregamento, sucesso e erros
- [x] Atualizar a listagem a cada 5 segundos

Critério de conclusão:

- Usuário cadastra um pedido.
- Pedido aparece na tabela.
- Botão inicia o processamento.
- Status é atualizado na interface.
- Erros de validação, duplicidade e integração são apresentados claramente.

### Fase 5 — Docker

- [x] Dockerfile do Order Service
- [x] Dockerfile do ERP Service
- [x] Dockerfile do frontend com Nginx
- [x] `compose.yaml` com os quatro containers
- [x] Variáveis de ambiente para URLs, banco, paralelismo e simulação de falhas
- [x] Healthchecks e dependências de inicialização

Critério de conclusão:

- Um único comando sobe toda a aplicação.
- Frontend acessa o Order Service.
- Order Service acessa o ERP Service.
- Dados permanecem no volume do PostgreSQL após reiniciar os containers.

### Fase 6 — Testes

#### Order Service

- [ ] Teste de criação de pedido válido
- [x] Teste de validação dos campos
- [x] Teste de `externalId` duplicado
- [x] Teste dos métodos de mudança de status
- [x] Teste de sucesso na integração
- [x] Teste de erro na integração
- [x] Teste de concorrência com duas chamadas de processamento

#### ERP Service

- [ ] Teste do atraso configurado sem depender de tempo aleatório
- [x] Teste da resposta de sucesso
- [x] Teste da falha determinística

#### Frontend

- [x] Teste do formulário e validações
- [x] Validar chamadas HTTP do frontend com HttpTestingController nos testes do componente
- [x] Teste dos estados de carregamento e erro

#### Aplicação completa

- [ ] Subir o Docker Compose
- [ ] Cadastrar pedidos
- [ ] Confirmar bloqueio de duplicidade
- [ ] Processar um lote com sucessos e falhas
- [ ] Disparar duas chamadas de processamento simultâneas
- [ ] Confirmar que nenhum pedido foi enviado duas vezes simultaneamente

## Próximos passos pendentes

1. Concluir revisões e cenários específicos ainda desmarcados nas fases 1 e 6.
2. Construir e subir os quatro containers conforme `DOCKER.md`; arquivos e validação estática concluídos.
3. Validar a aplicação completa em containers, incluindo concorrência e persistência.
4. Gravar o vídeo com base em `VIDEO_GUIDE.md`, revisar a documentação após validar Docker e publicar a entrega.

## Ordem de trabalho original

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
