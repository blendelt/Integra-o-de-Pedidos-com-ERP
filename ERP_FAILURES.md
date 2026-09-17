# Timeout e falhas do ERP

O cliente usa os limites já existentes: `ERP_CONNECT_TIMEOUT=2s` e `ERP_READ_TIMEOUT=3s`. Conexão e espera de resposta são etapas diferentes, não um único prazo global do lote.

`HttpErpClient` traduz erros técnicos em `ErpClientException` com categoria `ErpFailure`. O processor salva apenas a mensagem fixa dessa categoria em lastError e marca ERROR. Corpos HTTP e mensagens arbitrárias de exceção não são persistidos.

| Categoria | Significado |
| --- | --- |
| TIMEOUT | Prazo excedido na conexão ou resposta; resultado remoto não confirmado |
| UNAVAILABLE | Outra falha de comunicação; resultado remoto não confirmado |
| HTTP_ERROR | Resposta HTTP de erro; status aparece no log do client |
| INVALID_RESPONSE | Corpo ilegível, vazio, identificador divergente ou aceitação ausente |
| UNKNOWN | Compatibilidade com exceções genéricas de integração |

Não há retry automático. Timeout não desfaz o que o ERP pode já ter executado. Uma nova tentativa exige reconciliação ou idempotência no destino. A falha conhecida não cancela outros pedidos do lote; falhas de programação ou persistência continuam sendo propagadas.

## Validação

Execute `ErpTimeoutTest`, `HttpErpClientTest` e `OrderProcessorTest` no IntelliJ com Java 17. Esses testes não exigem Docker.

- `ErpTimeoutTest`: usa servidor HTTP local real que retém a resposta e o mesmo cliente configurado em produção; verifica timeout de leitura e uma única requisição.
- `HttpErpClientTest`: inclui falha de conexão simulada, além de erro HTTP e respostas inválidas.
- `OrderProcessorTest`: verifica registro seguro do timeout e continuidade para o próximo pedido.

Não há teste real de timeout de conexão: simular um destino que descarta pacotes dependeria da rede/firewall. Não confundir o teste de conexão recusada com expiração de conexão.

Os novos testes ainda não foram confirmados neste ambiente; executar no IntelliJ antes de considerar a etapa validada.
