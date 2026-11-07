package br.com.faturamed.producao;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Regras reais do Itaim (tabela recebida do cliente). */
class MotorValorProducaoTest {
    static final long ECO = 1, ESTRESSE = 2, STRAIN = 3, ANGIO = 4, ERGO = 5, MAPA = 6, SEM_REGRA = 99;
    static final LocalDate INICIO = LocalDate.of(2026, 1, 1), DIA = LocalDate.of(2026, 10, 26);

    static NavigableMap<Integer, BigDecimal> tabelaEcoItaim() {
        TreeMap<Integer, BigDecimal> t = new TreeMap<>();
        BigDecimal v = new BigDecimal("1170.21");
        for (int q = 11; q <= 31; q++) { t.put(q, v); v = v.add(new BigDecimal("106.40")); }
        return t;
    }

    static List<MotorValorProducao.Regra> regras() {
        return List.of(
                new MotorValorProducao.Regra(10, "Eco cardio", "TABELA_AGENDA", BigDecimal.ZERO, 0, new BigDecimal("106.40"), null, INICIO, null,
                        Map.of(ECO, 1, ESTRESSE, 2, STRAIN, 2), tabelaEcoItaim()),
                new MotorValorProducao.Regra(11, "AngioTC eletivo", "POR_EXAME", new BigDecimal("400.00"), 1, BigDecimal.ZERO, null, INICIO, null,
                        Map.of(ANGIO, 1), new TreeMap<>()),
                new MotorValorProducao.Regra(12, "Teste ergometrico", "POR_AGENDA", new BigDecimal("1120.00"), 0, BigDecimal.ZERO, null, INICIO, null,
                        Map.of(ERGO, 1), new TreeMap<>()),
                new MotorValorProducao.Regra(13, "MAPA", "POR_EXAME", new BigDecimal("48.00"), 0, BigDecimal.ZERO, null, INICIO, null,
                        Map.of(MAPA, 1), new TreeMap<>()),
                new MotorValorProducao.Regra(14, "Coordenacao teste ergometrico", "MENSAL_MEDICO", new BigDecimal("1500.00"), 0, BigDecimal.ZERO, 77L,
                        INICIO, null, Map.of(), new TreeMap<>()));
    }

    static List<MotorValorProducao.ItemAgenda> itens(long exame, int qtd, long[] seq) {
        List<MotorValorProducao.ItemAgenda> l = new ArrayList<>();
        for (int i = 0; i < qtd; i++) l.add(new MotorValorProducao.ItemAgenda(seq[0]++, exame));
        return l;
    }

    @Test
    void ecoAbaixoDoMinimoPagaPrimeiraFaixaEPesosContam() {
        long[] seq = {1};
        List<MotorValorProducao.ItemAgenda> agenda = new ArrayList<>(itens(ECO, 9, seq));
        agenda.addAll(itens(ESTRESSE, 2, seq)); // 9 + 2x2 = 13 pontos
        var r = MotorValorProducao.calcular(regras(), List.of(new MotorValorProducao.Agenda(1, 5, DIA, "MANHA", agenda)), DIA, DIA);
        var eco = r.calculos().stream().filter(c -> c.regraId() == 10).findFirst().orElseThrow();
        assertEquals(11, eco.exames());
        assertEquals(13, eco.pontos());
        assertEquals(new BigDecimal("1383.01"), eco.valor());

        var pouco = MotorValorProducao.calcular(regras(), List.of(new MotorValorProducao.Agenda(1, 5, DIA, "MANHA", itens(ECO, 4, seq))), DIA, DIA);
        assertEquals(new BigDecimal("1170.21"), pouco.calculos().get(0).valor(), "ate 11 exames paga o minimo");
    }

    @Test
    void ecoAcimaDaTabelaSomaIncremento() {
        assertEquals(new BigDecimal("3298.21"), MotorValorProducao.valorFaixa(tabelaEcoItaim(), new BigDecimal("106.40"), 31));
        assertEquals(new BigDecimal("3404.61"), MotorValorProducao.valorFaixa(tabelaEcoItaim(), new BigDecimal("106.40"), 32));
    }

    @Test
    void porExameFixoPorAgendaMensalESemRegra() {
        long[] seq = {1};
        List<MotorValorProducao.ItemAgenda> a = new ArrayList<>(itens(ANGIO, 3, seq));
        a.addAll(itens(ERGO, 8, seq));
        a.addAll(itens(MAPA, 2, seq));
        a.addAll(itens(SEM_REGRA, 1, seq));
        var r = MotorValorProducao.calcular(regras(), List.of(new MotorValorProducao.Agenda(1, 5, DIA, "MANHA", a)), DIA, DIA);
        Map<Long, BigDecimal> porRegra = new HashMap<>();
        r.calculos().forEach(c -> porRegra.put(c.regraId(), c.valor()));
        assertEquals(new BigDecimal("1200.00"), porRegra.get(11L));
        assertEquals(new BigDecimal("1120.00"), porRegra.get(12L), "teste ergometrico: valor fixo por agenda");
        assertEquals(new BigDecimal("96.00"), porRegra.get(13L));
        assertEquals(new BigDecimal("1500.00"), porRegra.get(14L), "coordenacao entra uma vez");
        assertEquals(1, r.itensSemRegra().size());
    }

    @Test
    void regraForaDaVigenciaNaoVale() {
        var r = MotorValorProducao.calcular(regras(), List.of(new MotorValorProducao.Agenda(1, 5, LocalDate.of(2025, 12, 31), "MANHA",
                List.of(new MotorValorProducao.ItemAgenda(1, MAPA)))), LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31));
        assertEquals(List.of(1L), r.itensSemRegra());
    }
}
