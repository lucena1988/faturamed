# Regressão dos cenários de homologação

Executar na raiz do projeto, com Docker disponível:

```powershell
& "$env:USERPROFILE/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe" scripts/regressao_completa.py
```

O comando constrói imagens exclusivas de QA, executa os cenários HTTP com dados fictícios, roda a suíte Java em outro banco isolado e os testes JavaScript. O resultado fica em `outputs/evidencias-auto-AAAAMMDD-HHMMSS`. Código de saída 1 indica reprovação ou impedimento; não deve ser interpretado como execução aprovada. Requer as dependências Python já disponíveis neste ambiente e o modelo XLSX em `outputs/homologacao/03-hospital-duas-visitas-legitimas.xlsx`.

Ao terminar, abra `outputs/dashboard-regressao.html` com dois cliques. O painel mostra os resultados da rodada mais recente, com filtros por camada/status, busca, detalhes das falhas e links para evidências. Cada pasta de execução também guarda seu próprio `dashboard.html`, incluído no ZIP; extraia o ZIP inteiro antes de abrir os links. O painel funciona sem servidor e não executa testes ao ser aberto. Para atualizar o painel com resultados já existentes, sem repetir a regressão, execute `python scripts/gerar_dashboard_regressao.py` usando o mesmo Python do comando acima.

As instâncias manuais na porta 8080 e seu banco não são reiniciadas nem recebem dados. As instâncias HTTP usam a porta local 18081; não execute duas regressões simultaneamente. Containers exclusivos são parados ao finalizar. Os bancos temporários usam tmpfs; o dump fictício fica em `privado` e é excluído do pacote compartilhável.

## Matriz dos cenários executados anteriormente

As linhas agrupam variações do mesmo fluxo. HTTP significa requisições reais à aplicação e banco; Java significa testes de unidade/integração; JS significa scripts reais em DOM simulado, sem navegador real. A aprovação de uma camada não substitui a de outra.

| Fluxo manual anterior | Cobertura automática | Limite |
|---|---|---|
| Cadastro de hospital, médico e acesso vinculado | Preparação HTTP; HospitalCadastroTest, MedicoCadastroServiceTest, SegurancaTest | Aparência dos formulários permanece visual |
| Importação hospitalar, arquivo original e ID fixo | AUTO01, AUTO11, AUTO25; BaseHospitalarIntegracaoTest | Fixtures menores com registros equivalentes |
| Produção individual e coletiva | AUTO03, AUTO11, AUTO20, AUTO28; IdentificacaoProducaoTest | Não reutiliza o banco manual |
| Produção sem nome e inferência por atendimento/data | AUTO20, AUTO28; integração | Nomes desconhecidos preenchidos não são substituídos |
| Dois médicos no mesmo atendimento/data | AUTO11, AUTO27; integração | AUTO27 também verifica o cadastro após exclusividade |
| Revisão de ambiguidade e resolução da segunda visita | AUTO27; AplicadorRevisaoVisitaTest, ReavaliadorVisitasTest | Cadastro ausente na resolução automática é tratado como falha |
| Arredondamento do repasse por visita | ArredondamentoRepasseTest; integração | Não compara valores com outra competência real |
| Produção ausente no hospital e fila de cobrança | AUTO20–AUTO24; FilaCobrancaTest | Valores desconhecidos continuam desconhecidos |
| Contato individual, lote e histórico por visita | AUTO21, AUTO23; AcompanhamentoCobrancaTest | Eventos fictícios |
| Prioridades hoje, atrasadas, não cobradas e aguardando | AUTO21, AUTO22; PrioridadesCobrancaTest | Datas relativas ao dia em São Paulo |
| Retorno do hospital não equivale a faturamento | AUTO23 | Registro continua na fila até complemento |
| Próximo contato anterior à cobrança | AUTO23; AcompanhamentoCobrancaTest | Bloqueio validado pela API |
| Exportação de cobrança com filtro | AUTO22; FilaCobrancaTest | Conferência visual da planilha é separada |
| Complemento hospitalar e nova versão | AUTO01, AUTO24; ResumoAtualizacaoTest | IDs e recebimentos preservados |
| Histórico fora da fila somente para consulta | AUTO24; AcompanhamentoCobrancaTest | Consulta permitida; alteração bloqueada |
| Arquivo hospitalar idêntico reenviado | AUTO25; integração | Sem duplicação de visitas |
| Planilha corrompida, vazia, cabeçalho ou data inválida | AUTO02, AUTO25; DiagnosticoPlanilhasTest | Rollback inclui linha válida anterior à inválida |
| Competência inválida e médico individual obrigatório | AUTO25; integração validaModoDeProducaoESelecaoDoMedico | Balão nativo do navegador é visual |
| Histórico antigo bloqueia revisão/gravação | AUTO24; integração, AcompanhamentoCobrancaTest | Comportamento de abrir modal é JS/visual |
| Recebimento individual e separação por médico | AUTO11, AUTO17; RecebimentoTest | Não transfere pagamento entre médicos |
| Pagamento parcial distribuído em duas visitas | AUTO13; PagamentoDistribuidoTest | Valores equivalentes aos exemplos manuais |
| Soma divergente, saldo excedido, lote vazio, linha repetida ou médicos misturados | AUTO12; PagamentoDistribuidoTest | Todos rejeitados sem gravação parcial |
| Referência duplicada | AUTO14; PagamentoDistribuidoTest | HTTP 409 e saldo inalterado |
| Estorno isolado, justificativa e tentativa repetida | AUTO15; RecebimentoTest | Histórico preservado; outra alocação intacta |
| Zero, negativo, excesso de saldo e três decimais | AUTO07; RecebimentoTest | API validada independentemente de HTML |
| Data futura no recebimento | AUTO19; RecebimentoTest | Data calculada relativamente ao dia atual |
| Concorrência entre abas e versão obsoleta | AUTO04 simultâneo; AUTO17 sequencial obsoleto | Duas sessões HTTP; não dois navegadores reais |
| Quitação integral e bloqueio de novo recebimento | AUTO17 | Saldo zero e registro indisponível |
| Consulta de pagamentos por referência, estado, datas e limpeza | AUTO16; ConsultaPagamentosTest | Inclui períodos sem resultado e invertidos |
| Anexar, baixar e verificar comprovante | AUTO18; ComprovantePagamentoTest | Hash SHA-256 confirma igualdade dos bytes |
| Comprovante duplicado renomeado, formato errado, vazio e acima de 5 MB | AUTO18 | Validação real da API, além do seletor do navegador |
| Retirar comprovante com justificativa e preservar histórico | AUTO18 | Download retirado retorna 404 |
| PDF exige publicação e médico selecionado | AUTO19; JS demonstrativo; DemonstrativoMedicoControllerTest | Sem declaração falsa de publicação na interface |
| Publicação e republicação, ciência e pedido de revisão | AUTO05; LiberacaoRelatorioTest, ConferenciaMedicoTest | Ciência antiga não é reutilizada |
| Publicação sem confirmação de pendências | LiberacaoRelatorioTest | Checkbox nativo continua teste visual |
| Painel médico, totais e isolamento | AUTO29; FinanceiroMedicoTest, PortalMedicoTest, scripts/painel-medico.test.cjs | Não é captura visual do painel |
| Pedido de revisão e resposta administrativa | AUTO05; ConferenciaMedicoTest | Pedido preservado entre versões |
| Extrato XLSX e demonstrativo PDF administrativo/médico | AUTO08, AUTO29; ExtratoMedicoTest, DemonstrativoMedicoTest | Totais, dados e legibilidade textual; layout visual separado |
| Filtros de visita e movimentos, período vazio/invertido | AUTO19, AUTO29; ExtratoMedicoTest | Período vazio não apaga saldo acumulado |
| Médico bloqueado em API administrativa e relatório de outro médico | AUTO06; SegurancaTest | 401/403/404 registrados |
| Desativação de acesso e sessão existente | AUTO06 | Revogação real da sessão |
| Reativação de acesso e logout | AUTO29 | Exportação após logout bloqueada |
| Duplicado já pago em outro mês e evidência obrigatória | AUTO26; integração | Original permanece pago; ocorrência não cria saldo |
| Categoria e motivo de possível duplicidade | AUTO26; AcompanhamentoRevisaoTest | Categoria própria também coberta em Java |
| Limpar seleção sem requisição/recarregamento | JS selecao.test.cjs | Scripts reais em DOM simulado |
| Selecionar todas limitado ao filtro e até 500 | JS selecao.test.cjs; AcompanhamentoCobrancaTest | Inclui 501 visitas rejeitadas no selecionar todas |
| Selecionar primeira visita filtra o médico | JS selecao.test.cjs | Evita seleção de médicos misturados |
| Trocar médico limpa seleção e cancelar lote não grava | JS selecao.test.cjs | Não clica em navegador real |
| Remover item de distribuição e Usar saldo | JS selecao.test.cjs; PagamentoDistribuidoTest | Último item removido bloqueia confirmação; nenhum recebimento é gravado |
| Reinício preserva dados | AUTO09 | Somente instância QA |
| Backup/restauração do banco | AUTO10 | Compara todas as tabelas; não abre interface restaurada |
| Service Desk: abertura, protocolo, categoria, SLA e busca | AUTO30 | Dados fictícios; fila e prazo conferidos com a categoria |
| Atendimento, aguardando retorno, respostas e encerramento | AUTO31; ChamadoAvisosIntegracaoTest | Inclui reabertura, avaliação e bloqueio de mensagem após fechamento |
| Resposta sem responsável e notificações para equipe | AUTO32; ChamadoAvisosIntegracaoTest | Sem autoaviso; com responsável, somente ele é notificado |
| Leitura de avisos por chamado e por usuário | AUTO33; ChamadoAvisosIntegracaoTest; JS servicedesk.test.cjs | Mais de 50 avisos, idempotência e preservação de avisos posteriores ao limite |
| Anexos do chamado e download | AUTO34 | Compara bytes; rejeita formato falso, arquivo maior que 5 MB e mais de 5 anexos sem gravação parcial |
| Permissões do funcionário, chamado técnico privado e CSRF | AUTO35; ChamadoPermissoesTest | API real; link de acompanhamento não concede ações técnicas |
| Categoria, campos, mensagem e avaliação inválidos | AUTO36 | Verifica ausência de criação, nota de 1 a 5 e avaliação somente pelo solicitante |
| Formulário simplificado, origem automática, anexos e erro de envio | JS servicedesk.test.cjs | Executa widget real em DOM simulado; layout e validação nativa do navegador continuam visuais |

## Pontos que continuam visuais ou fora desta regressão

- Layout, mensagens nativas do navegador, cursor, diálogo de download e abrir arquivos em Excel/leitor de PDF.
- Os cliques de seleção, limpeza, cancelamento, Remover e Usar saldo têm testes em DOM simulado; os prints manuais permanecem como evidência visual anterior.
- Não foi automatizada a reprodução integral das 888 linhas da planilha manual; os mesmos tipos de regra são exercitados com fixtures fictícias menores e testes de integração.
- HTTPS, domínio, infraestrutura de homologação externa, carga e aceite da empresa exigem rodada própria.

O pacote registra falhas sem transformá-las em aprovação. Antes de produzir o guia definitivo, corrigir as falhas identificadas e repetir os cenários afetados.
