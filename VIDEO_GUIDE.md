# Roteiro de apresentação

Grave a tela e seu rosto, conforme o enunciado. Duração sugerida: 8 a 12 minutos; não é uma exigência do teste. Não afirme validações que ainda não executou.

## Preparação

- Subir os quatro containers e confirmar healthy em docker compose ps.
- Abrir localhost:8088 e a IDE nas classes OrderProcessor, OrderProcessingTransactions e OrderRepository.
- Separar os resultados dos testes, incluindo confirmação de que os testes PostgreSQL não foram ignorados.
- Usar somente nomes e dados fictícios na demonstração.

## 1. Contexto e arquitetura (1 minuto)

Explique o objetivo e mostre os quatro serviços. Diferencie o Order Service do ERP simulado. Explique PostgreSQL/Flyway, Angular/Nginx e a comunicação REST.

## 2. Demonstração (3 minutos)

Cadastre um pedido normal e um FAIL-DEMO. Mostre a rejeição de externalId duplicado. Processe e explique PENDING, PROCESSING, SUCCESS e ERROR. Mostre a correção/reprocessamento, preservação do ID interno e aumento das tentativas. Demonstre o job com um novo pedido sem clicar no botão; prepare essa espera antes de explicar o código.

## 3. Paralelismo e concorrência (2 minutos)

Mostre o executor com 4 workers configuráveis, lote de 20 e fila limitada. Explique que synchronized coordena os lotes na mesma instância, mas os workers executam pedidos em paralelo. Mostre FOR UPDATE SKIP LOCKED e a mudança para PROCESSING dentro de uma transação curta. A proteção entre instâncias vem do banco, não do synchronized.

Explique por que a chamada HTTP fica fora da transação e por que a conclusão é salva em outra transação.

## 4. Falhas e limitações (1 a 2 minutos)

Mostre timeout, ERROR, mensagens protegidas e continuidade dos demais pedidos. Diferencie timeout de confirmação de falha: o ERP pode ter aceitado. Explique a confirmação manual de não integração e a deduplicação em memória do simulador.

Reconheça as melhorias: recuperação de PROCESSING após queda, idempotência durável, reconciliação e API assíncrona de jobs. Não prometa garantia de exactly-once.

## 5. Testes e execução (1 minuto)

Mostre comandos e resultados Java/Angular, testes PostgreSQL com Testcontainers e Docker Compose. Demonstre persistência após reiniciar, se essa validação já tiver sido concluída. Explique que cada banco local/Docker possui seus próprios dados.

## 6. Uso de IA (1 minuto)

Relate com suas próprias palavras:

- Quais ferramentas realmente usou, por exemplo ChatGPT/Codex.
- Em quais etapas houve apoio: estrutura, implementação, explicações, revisão, testes e documentação.
- O que revisou pessoalmente e quais comandos/cenários executou.
- Um exemplo de decisão que você compreendeu e validou: reserva transacional, externalId único ou timeout.

Não diga que escreveu tudo sozinho ou que apenas aceitou sugestões: descreva o processo real. Finalize indicando o repositório e o README.

## Checklist de entrega

- [ ] Execução completa Docker validada.
- [ ] Vídeo gravado com tela e rosto.
- [ ] Paralelismo, concorrência e falhas explicados.
- [ ] Uso de IA declarado e validação descrita.
- [ ] Vídeo revisado e link acessível aos avaliadores.
- [ ] Repositório atualizado e acessível aos avaliadores.
