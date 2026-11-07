package br.com.faturamed.producao;

import static org.junit.jupiter.api.Assertions.*;

import br.com.faturamed.seguranca.UsuarioService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Fluxo completo: fechamento -> conferencia do hospital -> contestacao -> aprovacao recalculada -> reinclusao. */
@SpringBootTest(properties = "ADMIN_INITIAL_PASSWORD=TesteIntegracaoSomente123")
@EnabledIfEnvironmentVariable(named = "BASE_HOSPITALAR_TEST_DB", matches = "true")
@Transactional
class PortalHospitalIntegracaoTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ProducaoService producao;
    @Autowired FechamentoProducaoService fechamentos;
    @Autowired HospitalPortalService portal;
    @Autowired UsuarioService usuarios;
    @Autowired PainelProducaoService painel;

    @Test
    void hospitalConfereContestaEAprovaSemTravar() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        long hospital = jdbc.queryForObject("insert into hospital_cadastro(nome,nome_normalizado) values(?,?) returning id", Long.class, "Hosp " + sufixo, "hosp " + sufixo);
        long outro = jdbc.queryForObject("insert into hospital_cadastro(nome,nome_normalizado) values(?,?) returning id", Long.class, "Outro " + sufixo, "outro " + sufixo);
        long medico = jdbc.queryForObject("insert into medico_cadastro(nome,crm,uf) values(?,?,'SP') returning id", Long.class, "Dr " + sufixo, sufixo);
        long exame = jdbc.queryForObject("select id from producao_exame_tipo where nome_normalizado='MAPA'", Long.class);
        fechamentos.salvarRegra(null, new FechamentoProducaoService.RegraDados(hospital, "MAPA", "POR_EXAME", new BigDecimal("48.00"), 0,
                BigDecimal.ZERO, null, LocalDate.of(2026, 1, 1), null, "", true, List.of(new FechamentoProducaoService.ExamePeso(exame, 1)), List.of()));

        LocalDate dia = LocalDate.of(2026, 10, 10);
        var itens = List.of(item("12345671", medico, exame), item("12345672", medico, exame), item("12345673", medico, exame));
        var lote = producao.salvar(null, new ProducaoService.Salvar(new ProducaoService.Cabecalho(hospital, List.of(medico), List.of(exame), dia,
                "MANHA", "", "", "", 0L), itens), "operador");
        producao.alterarStatus(lote.id(), "CONFERIDO", "operador");

        var f = fechamentos.fechar(hospital, dia, dia, "", "operador").fechamento();
        assertEquals(new BigDecimal("144.00"), f.total());
        assertNotNull(f.prazoConferencia(), "prazo de conferencia definido no envio");
        assertTrue(f.diasParaPrazo() > 0);
        var visao = painel.painel();
        assertTrue(visao.tarefas().aguardandoHospital() >= 1);
        assertTrue(visao.prazos().stream().anyMatch(x -> x.fechamentoId() == f.id()));

        var conta = usuarios.criar(new UsuarioService.Novo("hosp-" + sufixo + "@teste.local", "Faturamento Hosp", "SenhaTesteSegura12", "HOSPITAL", null, null, hospital));
        assertEquals(hospital, portal.hospitalDoUsuario(conta.id()));

        // outro hospital nao enxerga
        assertThrows(RuntimeException.class, () -> portal.abrir(f.id(), outro, "intruso"));

        var conf = portal.abrir(f.id(), hospital, "Faturamento Hosp");
        assertEquals(1, conf.lotes().size());
        long contestada = conf.detalhe().etiquetas().get(0).itemId();
        portal.contestar(f.id(), contestada, "Exame nao realizado", hospital, "Faturamento Hosp");

        // sem conferir todos os lotes nao aprova
        assertThrows(IllegalArgumentException.class, () -> portal.aprovar(f.id(), hospital, "Faturamento Hosp"));
        portal.conferirLote(f.id(), lote.id(), false, hospital, "Faturamento Hosp");
        assertEquals(1, portal.conferirTodos(f.id(), hospital, "Faturamento Hosp"), "marca de uma vez os lotes que faltam");
        assertEquals(0, portal.conferirTodos(f.id(), hospital, "Faturamento Hosp"));
        var aprovado = portal.aprovar(f.id(), hospital, "Faturamento Hosp").fechamento();
        assertEquals("APROVADO", aprovado.status());
        assertEquals(new BigDecimal("96.00"), aprovado.total(), "valor recalculado sem a etiqueta contestada");
        assertEquals(2, aprovado.etiquetas());

        assertTrue(fechamentos.contestacoes().stream().anyMatch(c -> c.itemId() == contestada));
        fechamentos.resolverContestacao(contestada, "REINCLUIR", "Exame consta no prontuario", "operador");
        var previa = fechamentos.previa(hospital, dia, dia);
        assertEquals(1, previa.etiquetas(), "etiqueta reincluida volta para o proximo fechamento");
        assertEquals(new BigDecimal("48.00"), previa.total());
        var segundo = fechamentos.fechar(hospital, dia, dia, "", "operador").fechamento();
        assertEquals(1, segundo.etiquetas());
        assertEquals(0, fechamentos.previa(hospital, dia, dia).etiquetas());

        // NF emitida com arquivo: o hospital baixa; outro hospital nao
        fechamentos.registrarNf(f.id(), "123", dia, "operador");
        fechamentos.salvarNfArquivo(f.id(), new byte[]{'%', 'P', 'D', 'F'}, "nf-123.pdf", "application/pdf", "operador");
        assertEquals("nf-123.pdf", portal.nfArquivo(f.id(), hospital).get("nf_arquivo_nome"));
        assertEquals("nf-123.pdf", portal.listar(hospital).stream().filter(x -> x.id() == f.id()).findFirst().orElseThrow().nfArquivoNome());
        assertThrows(RuntimeException.class, () -> portal.nfArquivo(f.id(), outro));
    }

    private static ProducaoService.Item item(String nr, long medico, long exame) {
        return new ProducaoService.Item(null, nr, "", "Paciente " + nr, "", "", "", "Convenio", "", "", "", "", "", "", "", "", "MANUAL", false, medico, exame);
    }
}
