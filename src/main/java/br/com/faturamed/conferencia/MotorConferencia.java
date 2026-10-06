package br.com.faturamed.conferencia;

import br.com.faturamed.domain.TipoDivergencia;
import br.com.faturamed.importacao.RegistroConferencia;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MotorConferencia {

    public List<ResultadoConferencia> conferir(
            Collection<RegistroConferencia> producao,
            Collection<RegistroConferencia> faturamento
    ) {
        List<ResultadoConferencia> resultados = new ArrayList<>();

        Map<String, List<RegistroConferencia>> producaoPorChave = agruparPorChave(producao);
        Map<String, List<RegistroConferencia>> faturamentoPorChave = agruparPorChave(faturamento);

        adicionarDuplicidades(resultados, producaoPorChave, "Duplicidade na producao");
        adicionarDuplicidades(resultados, faturamentoPorChave, "Duplicidade no faturamento");

        for (Map.Entry<String, List<RegistroConferencia>> item : faturamentoPorChave.entrySet()) {
            RegistroConferencia registroFaturamento = item.getValue().getFirst();
            List<RegistroConferencia> correspondentesProducao = producaoPorChave.get(item.getKey());

            if (correspondentesProducao == null || correspondentesProducao.isEmpty()) {
                resultados.add(new ResultadoConferencia(
                        TipoDivergencia.FATURADO_SEM_PRODUCAO,
                        null,
                        registroFaturamento,
                        valorOuZero(registroFaturamento.valorTotal()),
                        "Item faturado sem correspondente na producao"
                ));
                continue;
            }

            RegistroConferencia registroProducao = correspondentesProducao.getFirst();
            compararRegistros(resultados, registroProducao, registroFaturamento);
        }

        for (Map.Entry<String, List<RegistroConferencia>> item : producaoPorChave.entrySet()) {
            if (!faturamentoPorChave.containsKey(item.getKey())) {
                RegistroConferencia registroProducao = item.getValue().getFirst();
                resultados.add(new ResultadoConferencia(
                        TipoDivergencia.PRODUZIDO_SEM_FATURAMENTO,
                        registroProducao,
                        null,
                        valorOuZero(registroProducao.valorTotal()),
                        "Item produzido sem correspondente no faturamento"
                ));
            }
        }

        return resultados;
    }

    private static Map<String, List<RegistroConferencia>> agruparPorChave(Collection<RegistroConferencia> registros) {
        Map<String, List<RegistroConferencia>> agrupados = new LinkedHashMap<>();
        for (RegistroConferencia registro : registros) {
            agrupados.computeIfAbsent(registro.chavePrincipal(), chave -> new ArrayList<>()).add(registro);
        }
        return agrupados;
    }

    private static void adicionarDuplicidades(
            List<ResultadoConferencia> resultados,
            Map<String, List<RegistroConferencia>> registrosPorChave,
            String descricao
    ) {
        for (List<RegistroConferencia> registros : registrosPorChave.values()) {
            if (registros.size() > 1) {
                RegistroConferencia primeiro = registros.getFirst();
                resultados.add(new ResultadoConferencia(
                        TipoDivergencia.DUPLICIDADE,
                        primeiro,
                        primeiro,
                        valorOuZero(primeiro.valorTotal()),
                        descricao
                ));
            }
        }
    }

    private static void compararRegistros(
            List<ResultadoConferencia> resultados,
            RegistroConferencia producao,
            RegistroConferencia faturamento
    ) {
        int totalAntesDaComparacao = resultados.size();

        if (diferente(producao.quantidade(), faturamento.quantidade())) {
            resultados.add(new ResultadoConferencia(
                    TipoDivergencia.QUANTIDADE_DIVERGENTE,
                    producao,
                    faturamento,
                    diferenca(producao.valorTotal(), faturamento.valorTotal()),
                    "Quantidade divergente entre producao e faturamento"
            ));
        }

        if (diferente(producao.valorTotal(), faturamento.valorTotal())) {
            resultados.add(new ResultadoConferencia(
                    TipoDivergencia.VALOR_DIVERGENTE,
                    producao,
                    faturamento,
                    diferenca(producao.valorTotal(), faturamento.valorTotal()),
                    "Valor divergente entre producao e faturamento"
            ));
        }

        if (resultados.size() == totalAntesDaComparacao) {
            resultados.add(new ResultadoConferencia(
                    TipoDivergencia.CONFORME,
                    producao,
                    faturamento,
                    BigDecimal.ZERO,
                    "Registro conforme"
            ));
        }
    }

    private static boolean diferente(BigDecimal primeiro, BigDecimal segundo) {
        if (primeiro == null && segundo == null) {
            return false;
        }
        if (primeiro == null || segundo == null) {
            return true;
        }
        return primeiro.compareTo(segundo) != 0;
    }

    private static BigDecimal diferenca(BigDecimal primeiro, BigDecimal segundo) {
        return valorOuZero(primeiro).subtract(valorOuZero(segundo)).abs();
    }

    private static BigDecimal valorOuZero(BigDecimal valor) {
        if (valor == null) {
            return BigDecimal.ZERO;
        }
        return valor;
    }
}
