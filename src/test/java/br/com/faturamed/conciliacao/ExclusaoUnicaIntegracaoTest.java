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
class ExclusaoUnicaIntegracaoTest {
    @Autowired ProducaoPrimeiroService producao;
    @Autowired ConciliacaoUnicaService unica;
    @Autowired RelatorioUnicoService relatorios;
    @Autowired ExclusaoProducaoService exclusaoProducao;
    @Autowired ExclusaoRelatorioService exclusaoRelatorio;
    @Autowired ExclusaoArquivoHospitalService exclusaoHospital;
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
    MockMultipartFile producaoArquivo(String observacao,String... atendimentoEData) throws Exception {
        List<List<String>> linhas=new ArrayList<>();
        linhas.add(List.of("Atendimento","Dt.","Obs"));
        for(int i=0;i<atendimentoEData.length;i+=2) linhas.add(List.of(atendimentoEData[i],atendimentoEData[i+1],observacao));
        return planilha(linhas);
    }
    Map<String,Object> visita(String atendimento) {
        return jdbc.queryForList("select * from producao_visita where hospital_id=? and atendimento=? order by id desc",hospital,atendimento).getFirst();
    }

    @Test void importacaoDaProducaoPodeSerExcluidaComAuditoria() throws Exception {
        long arquivo=producao.importar(hospital,ana,producaoArquivo("a","1","2026-09-01","2","2026-09-02"),usuario).arquivoId();
        assertThat(exclusaoProducao.listar(hospital)).singleElement().satisfies(a->{
            assertThat(a.get("visitas")).isEqualTo(2L); assertThat(a.get("em_relatorio")).isEqualTo(0L); assertThat(a.get("competencias")).isEqualTo("2026-09");
            // Sem codigo nem nome de procedimento e sem tipo informado, o tipo fica indefinido.
            assertThat(a.get("tipos")).isEqualTo("SEM:2"); });
        // Com tipo informado na importacao, a lista informa o tipo do arquivo.
        long exames=producao.importar(hospital,ana,TipoProcedimento.EXAME,producaoArquivo("b","3","2026-09-03"),usuario).arquivoId();
        assertThat(exclusaoProducao.listar(hospital)).filteredOn(a->((Number)a.get("id")).longValue()==exames).singleElement()
                .satisfies(a->assertThat(a.get("tipos")).isEqualTo("EXAME:1"));
        exclusaoProducao.excluir(exames,usuario);
        var resultado=exclusaoProducao.excluir(arquivo,usuario);
        assertThat(resultado.visitas()).isEqualTo(2);
        assertThat(exclusaoProducao.listar(hospital)).isEmpty();
        assertThat(producao.visitas(hospital,null)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from producao_arquivo_exclusao_auditoria where arquivo_id=?",Long.class,arquivo)).isEqualTo(1L);
        assertThatThrownBy(()->exclusaoProducao.excluir(arquivo,usuario)).isInstanceOf(br.com.faturamed.shared.RecursoNaoEncontradoException.class);
        // A mesma planilha pode ser importada de novo.
        assertThat(producao.importar(hospital,ana,producaoArquivo("a","1","2026-09-01","2","2026-09-02"),usuario).repetido()).isFalse();
    }

    @Test void importacaoComVisitaEmRelatorioNaoPodeSerExcluida() throws Exception {
        long arquivo=producao.importar(hospital,ana,producaoArquivo("a","1","2026-09-01"),usuario).arquivoId();
        unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("1","2026-09-01"),usuario);
        relatorios.gerar(hospital,ana,"2026-09",null,usuario);
        assertThat(exclusaoProducao.listar(hospital).getFirst().get("em_relatorio")).isEqualTo(1L);
        assertThatThrownBy(()->exclusaoProducao.excluir(arquivo,usuario)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("em relatorios");
        assertThat(producao.visitas(hospital,null)).hasSize(1);
    }

    @Test void repeticaoPassaASerOriginalQuandoAOriginalEExcluidaERegistroDoHospitalVoltaAConciliar() throws Exception {
        unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("7","2026-09-10"),usuario);
        long primeiro=producao.importar(hospital,ana,producaoArquivo("original","7","2026-09-10"),usuario).arquivoId();
        producao.importar(hospital,ana,producaoArquivo("reapresentada","7","2026-09-10"),usuario);
        var repetida=visita("7");
        assertThat(repetida).containsEntry("decisao","EM_ANALISE");
        var resultado=exclusaoProducao.excluir(primeiro,usuario);
        assertThat(resultado.repeticoesPromovidas()).isEqualTo(1);
        // A repeticao virou a original e ficou com o registro do hospital que a original liberou.
        var promovida=visita("7");
        assertThat(promovida).containsEntry("decisao","ORIGINAL").containsEntry("situacao","PAGA");
        assertThat(promovida.get("anterior_id")).isNull();
        assertThat(promovida.get("hospital_visita_id")).isNotNull();
    }

    @Test void excluirRelatorioDevolveAsVisitasABaseEReconcilia() throws Exception {
        producao.importar(hospital,ana,producaoArquivo("a","1","2026-09-01"),usuario);
        unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("1","2026-09-01"),usuario);
        var gerado=relatorios.gerar(hospital,ana,"2026-09",null,usuario);
        assertThat(visita("1").get("arquivo_relatorio_id")).isNotNull();
        exclusaoRelatorio.excluir(gerado.relatorioId(),usuario);
        // As visitas voltam a estar disponiveis e ja conciliadas de novo com o registro do hospital.
        var depois=visita("1");
        assertThat(depois.get("arquivo_relatorio_id")).isNull();
        assertThat(depois).containsEntry("situacao","PAGA");
        assertThat(depois.get("hospital_visita_id")).isNotNull();
        assertThat(relatorios.grupos(hospital)).singleElement().satisfies(g->assertThat(g.get("disponiveis")).isEqualTo(1L));
        // E o relatorio pode ser gerado de novo.
        assertThat(relatorios.gerar(hospital,ana,"2026-09",null,usuario).visitas()).isEqualTo(1);
    }

    @Test void arquivoDoHospitalConciliadoNaTelaUnicaSoPodeSerExcluidoDepoisDaProducao() throws Exception {
        long arquivoProducao=producao.importar(hospital,ana,producaoArquivo("a","1","2026-09-01"),usuario).arquivoId();
        long arquivoHospital=((Number)unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("1","2026-09-01"),usuario).get("arquivoId")).longValue();
        assertThat(visita("1").get("hospital_visita_id")).isNotNull();
        assertThatThrownBy(()->exclusaoHospital.excluir(arquivoHospital,usuario)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Importar e conciliar");
        exclusaoProducao.excluir(arquivoProducao,usuario);
        assertThat(exclusaoHospital.excluir(arquivoHospital,usuario).procedimentos()).isEqualTo(1);
    }
}
