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
@SpringBootTest(properties = {"ADMIN_INITIAL_PASSWORD=TesteIntegracaoSomente123", "faturamed.dados.chave=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
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

        // dados sensiveis criptografados no banco, legiveis pelo sistema
        String bruto = jdbc.queryForObject("select carteirinha from producao_item where lote_id=? and nr_atendimento='12345671'", String.class, lote.id());
        assertTrue(bruto.startsWith("enc:v1:"), "carteirinha gravada criptografada");
        assertEquals("CART12345671", producao.buscar(lote.id()).itens().get(0).carteirinha());
        assertTrue(jdbc.queryForObject("select senha from producao_item where lote_id=? limit 1", String.class, lote.id()).startsWith("enc:v1:"));

        var f = fechamentos.fechar(hospital, dia, dia, "", "operador").fechamento();
        assertEquals(new BigDecimal("144.00"), f.total());
        assertNotNull(f.prazoConferencia(), "prazo de conferencia definido no envio");
        assertTrue(f.diasParaPrazo() > 0);
        var visao = painel.painel(null);
        assertEquals(1, painel.painel(hospital).hospitais().size(), "filtro por hospital");
        assertTrue(painel.painel(outro).prazos().isEmpty());
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

        // envio de lotes escolhidos: so os selecionados entram no fechamento
        LocalDate d2 = LocalDate.of(2026, 10, 20), d3 = LocalDate.of(2026, 10, 21);
        var l2 = producao.salvar(null, new ProducaoService.Salvar(new ProducaoService.Cabecalho(hospital, List.of(medico), List.of(exame), d2,
                "MANHA", "", "", "", 0L), List.of(item("22345671", medico, exame), item("22345672", medico, exame))), "operador");
        var l3 = producao.salvar(null, new ProducaoService.Salvar(new ProducaoService.Cabecalho(hospital, List.of(medico), List.of(exame), d3,
                "TARDE", "", "", "", 0L), List.of(item("32345671", medico, exame))), "operador");
        assertThrows(IllegalArgumentException.class, () -> fechamentos.fecharLotes(List.of(l2.id()), "", "operador"), "rascunho nao pode ser enviado");
        producao.alterarStatus(l2.id(), "CONFERIDO", "operador");
        producao.alterarStatus(l3.id(), "CONFERIDO", "operador");
        var porLotes = fechamentos.fecharLotes(List.of(l2.id()), "", "operador").fechamento();
        assertEquals(2, porLotes.etiquetas());
        assertEquals(new BigDecimal("96.00"), porLotes.total());
        assertEquals(1, fechamentos.previa(hospital, d3, d3).etiquetas(), "lote nao escolhido continua disponivel");
        assertEquals(porLotes.id(), producao.listar(d2, d2, hospital, null, null).getFirst().fechamentoId(), "lote enviado aponta para o fechamento (planilha)");
    }

    private static ProducaoService.Item item(String nr, long medico, long exame) {
        return new ProducaoService.Item(null, nr, "", "Paciente " + nr, "", "", "", "Convenio", "", "", "CART" + nr, "G" + nr, "S" + nr, "", "", "", "MANUAL", false, medico, exame);
    }
}
