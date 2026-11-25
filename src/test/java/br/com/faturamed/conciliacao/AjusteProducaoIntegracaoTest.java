package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="ADMIN_INITIAL_PASSWORD=TesteIntegracaoSomente123")
@EnabledIfEnvironmentVariable(named="BASE_HOSPITALAR_TEST_DB",matches="true")
@Transactional
class AjusteProducaoIntegracaoTest {
    @Autowired ProducaoPrimeiroService producao;
    @Autowired ConciliacaoUnicaService unica;
    @Autowired AjusteProducaoService ajustes;
    @Autowired RelatorioUnicoService relatorios;
    @Autowired JdbcTemplate jdbc;
    long hospital, ana;
    String nomeAna;
    UsuarioAutenticado usuario;

    @BeforeEach void preparar() {
        String sufixo=UUID.randomUUID().toString();
        hospital=id("insert into hospital_cadastro(nome,nome_normalizado) values(?,?) returning id","Hospital "+sufixo,sufixo);
        nomeAna="Ana "+sufixo;
        ana=id("insert into medico_cadastro(nome,crm,uf) values(?,?,'SP') returning id",nomeAna,Long.toString(System.nanoTime()));
        long usuarioId=id("select id from usuario_acesso where perfil='ADMIN' limit 1");
        usuario=new UsuarioAutenticado(usuarioId,"teste@local","Teste","","ADMIN",null,true,false,0);
    }
    long id(String sql,Object... argumentos) { return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,argumentos)); }
    MockMultipartFile planilha(List<List<String>> linhas) throws Exception {
        try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet("Planilha2");
            for(int i=0;i<linhas.size();i++) {
                var row=sheet.createRow(i);
                for(int c=0;c<linhas.get(i).size();c++) row.createCell(c).setCellValue(linhas.get(i).get(c));
            }
            workbook.write(output);
            return new MockMultipartFile("arquivo","arquivo.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
        }
    }
    MockMultipartFile hospital(String atendimento,String data) throws Exception {
        return planilha(List.of(List.of("Atendimento","Data Consumo","Medico","Cod Produto","Produto","Valor Tot","Regra","Vl. A Repassar","Setor"),
                List.of(atendimento,data,nomeAna,"101","Visita","100","1","100","Faturado")));
    }
    MockMultipartFile producaoArquivo(String atendimento,String data,String observacao) throws Exception {
        return planilha(List.of(List.of("Atendimento","Dt.","Obs"),List.of(atendimento,data,observacao)));
    }
    long visitaId(String competencia) { return ((Number)producao.visitas(hospital,competencia).getFirst().get("id")).longValue(); }
    Map<String,Object> visita(long id) { return jdbc.queryForMap("select * from producao_visita where id=?",id); }
    /** Importa a mesma visita em dois arquivos: a segunda entra em analise. Devolve o ID da repeticao. */
    long repeticao() throws Exception {
        producao.importar(hospital,ana,producaoArquivo("7","2026-09-10","original"),usuario);
        producao.importar(hospital,ana,producaoArquivo("7","2026-09-10","reapresentada"),usuario);
        return id("select id from producao_visita where hospital_id=? and decisao='EM_ANALISE'",hospital);
    }

    @Test void competenciaPodeSerCorrigidaERestauradaComHistorico() throws Exception {
        producao.importar(hospital,ana,producaoArquivo("123","2026-09-30","a"),usuario);
        long id=visitaId("2026-09");
        ajustes.corrigirCompetencia(id,"2026/10","Atendimento faturado na competencia seguinte",usuario);
        var corrigida=producao.visitas(hospital,"2026-10").getFirst();
        assertThat(corrigida.get("competencia")).isEqualTo("2026-10");
        assertThat(corrigida.get("competencia_derivada")).isEqualTo("2026-09");
        assertThat(producao.visitas(hospital,"2026-09")).isEmpty();
        assertThat(producao.resumoPorCompetencia(hospital)).containsOnlyKeys("2026-10");
        // Restaurar volta para a derivada da data.
        ajustes.corrigirCompetencia(id,"","Voltando para a data do atendimento",usuario);
        assertThat(visita(id).get("competencia_manual")).isNull();
        assertThat(producao.visitas(hospital,"2026-09")).hasSize(1);
        assertThat(ajustes.historico(id)).extracting(h->h.get("acao")).containsExactly("CORRIGIR_COMPETENCIA","RESTAURAR_COMPETENCIA");
        assertThat(ajustes.historico(id).getFirst()).containsEntry("antes","2026-09").containsEntry("depois","2026-10");
        assertThat(ajustes.historico(id).getFirst().get("responsavel").toString()).contains("Teste");
    }

    @Test void correcaoDeCompetenciaExigeJustificativaMudancaEIgnoraVisitaEmRelatorio() throws Exception {
        producao.importar(hospital,ana,producaoArquivo("123","2026-09-30","a"),usuario);
        long id=visitaId("2026-09");
        assertThatThrownBy(()->ajustes.corrigirCompetencia(id,"2026-10","curt",usuario)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Justificativa");
        assertThatThrownBy(()->ajustes.corrigirCompetencia(id,"2026-09","Mesma competencia da data",usuario)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ja esta");
        assertThatThrownBy(()->ajustes.corrigirCompetencia(id,"setembro","Formato invalido de competencia",usuario)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ajustes.corrigirCompetencia(-1,"2026-10","Visita inexistente no sistema",usuario)).isInstanceOf(br.com.faturamed.shared.RecursoNaoEncontradoException.class);
        unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("123","2026-09-30"),usuario);
        relatorios.gerar(hospital,ana,"2026-09",null,usuario);
        assertThatThrownBy(()->ajustes.corrigirCompetencia(id,"2026-10","Ja esta em um relatorio gerado",usuario)).isInstanceOf(ConflitoRevisaoException.class);
        assertThat(ajustes.historico(id)).isEmpty();
    }

    @Test void repeticaoConfirmadaComoVisitaDistintaVoltaAConciliar() throws Exception {
        unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("7","2026-09-10"),usuario);
        long repetida=repeticao();
        assertThat(visita(repetida)).containsEntry("decisao","EM_ANALISE").containsEntry("situacao","EM_ANALISE");
        ajustes.decidirRepeticao(repetida,"VISITA_DISTINTA","Dois procedimentos no mesmo dia, conferido no prontuario",usuario);
        assertThat(visita(repetida)).containsEntry("decisao","ORIGINAL").containsEntry("situacao","PENDENTE");
        // O unico registro do hospital continua com a visita original; a distinta aguarda outro registro.
        assertThat(visita(repetida).get("hospital_visita_id")).isNull();
        assertThat(ajustes.historico(repetida)).extracting(h->h.get("acao")).containsExactly("VISITA_DISTINTA");
        assertThat(relatorios.grupos(hospital)).singleElement().satisfies(g->assertThat(g.get("disponiveis")).isEqualTo(2L));
        // Nao pode decidir duas vezes.
        assertThatThrownBy(()->ajustes.decidirRepeticao(repetida,"DUPLICADA","Tentativa de decidir de novo",usuario)).isInstanceOf(ConflitoRevisaoException.class);
    }

    @Test void repeticaoConfirmadaComoDuplicadaSaiDaConciliacaoEDosRelatorios() throws Exception {
        unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("7","2026-09-10"),usuario);
        long repetida=repeticao();
        ajustes.decidirRepeticao(repetida,"DUPLICADA","Reapresentacao da mesma visita",usuario);
        assertThat(visita(repetida)).containsEntry("decisao","DUPLICADA").containsEntry("situacao","DUPLICADA");
        assertThat(visita(repetida).get("motivo").toString()).contains("Duplicidade confirmada");
        assertThat(relatorios.grupos(hospital)).singleElement().satisfies(g->{
            assertThat(g.get("disponiveis")).isEqualTo(1L); assertThat(g.get("em_analise")).isEqualTo(0L); });
        assertThat(relatorios.gerar(hospital,ana,"2026-09",null,usuario).visitas()).isEqualTo(1);
        assertThatThrownBy(()->ajustes.corrigirCompetencia(repetida,"2026-10","Duplicada nao tem competencia",usuario)).isInstanceOf(ConflitoRevisaoException.class);
    }

    @Test void decisaoInvalidaOuSemJustificativaERejeitada() throws Exception {
        unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("7","2026-09-10"),usuario);
        long repetida=repeticao();
        assertThatThrownBy(()->ajustes.decidirRepeticao(repetida,"OUTRA","Acao desconhecida para a repeticao",usuario)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ajustes.decidirRepeticao(repetida,"DUPLICADA","x",usuario)).isInstanceOf(IllegalArgumentException.class);
        assertThat(visita(repetida)).containsEntry("decisao","EM_ANALISE");
        assertThat(ajustes.historico(repetida)).isEmpty();
    }
}
