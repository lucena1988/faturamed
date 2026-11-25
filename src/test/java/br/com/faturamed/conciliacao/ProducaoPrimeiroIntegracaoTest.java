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
class ProducaoPrimeiroIntegracaoTest {
    @Autowired ProducaoPrimeiroService service;
    @Autowired JdbcTemplate jdbc;
    long hospital, ana, bruno;
    String nomeAna;
    UsuarioAutenticado usuario;

    @BeforeEach void preparar() {
        String sufixo=UUID.randomUUID().toString();
        hospital=id("insert into hospital_cadastro(nome,nome_normalizado) values(?,?) returning id","Hospital "+sufixo,sufixo);
        nomeAna="Ana "+sufixo;
        ana=id("insert into medico_cadastro(nome,crm,uf) values(?,?,'SP') returning id",nomeAna,Long.toString(System.nanoTime()));
        bruno=id("insert into medico_cadastro(nome,crm,uf) values(?,?,'SP') returning id","Bruno "+sufixo,Long.toString(System.nanoTime()+1));
        long usuarioId=id("select id from usuario_acesso where perfil='ADMIN' limit 1");
        usuario=new UsuarioAutenticado(usuarioId,"teste@local","Teste","","ADMIN",null,true,false,0);
    }
    long id(String sql,Object... argumentos) { return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,argumentos)); }
    MockMultipartFile planilha(List<List<String>> linhas) throws Exception {
        try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet("Producao");
            for(int i=0;i<linhas.size();i++) {
                var row=sheet.createRow(i);
                for(int c=0;c<linhas.get(i).size();c++) row.createCell(c).setCellValue(linhas.get(i).get(c));
            }
            workbook.write(output);
            return new MockMultipartFile("arquivo","producao.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
        }
    }

    @Test void competenciaDerivaDaDataSemHospitalImportado() throws Exception {
        var arquivo=planilha(List.of(List.of("Atendimento","Dt."),List.of("1","2026-09-30"),List.of("2","2026-10-01"),List.of("3","2026-10-15")));
        var resultado=service.importar(hospital,ana,arquivo,usuario);
        assertThat(resultado.repetido()).isFalse();
        assertThat(resultado.visitas()).isEqualTo(3);
        assertThat(resultado.competencias()).containsEntry("2026-09",1).containsEntry("2026-10",2).hasSize(2);
        assertThat(service.resumoPorCompetencia(hospital)).containsEntry("2026-09",1L).containsEntry("2026-10",2L);
        assertThat(service.visitas(hospital,"2026-10")).extracting(v->v.get("atendimento")).containsExactly("2","3");
        assertThat(jdbc.queryForObject("select count(*) from visita_base_hospitalar where hospital_id=?",Long.class,hospital)).isZero();
    }

    @Test void reenvioDoMesmoArquivoEIdempotente() throws Exception {
        var arquivo=planilha(List.of(List.of("Atendimento","Dt."),List.of("1","2026-09-30")));
        var primeiro=service.importar(hospital,ana,arquivo,usuario);
        var segundo=service.importar(hospital,ana,arquivo,usuario);
        assertThat(segundo.repetido()).isTrue();
        assertThat(segundo.arquivoId()).isEqualTo(primeiro.arquivoId());
        assertThat(service.visitas(hospital,null)).hasSize(1);
    }

    @Test void repeticaoNoMesmoArquivoSaoVisitasDistintasMasEmOutroArquivoEntraEmAnalise() throws Exception {
        var dentro=planilha(List.of(List.of("Atendimento","Dt."),List.of("7","2026-09-10"),List.of("7","2026-09-10")));
        var primeiro=service.importar(hospital,ana,dentro,usuario);
        assertThat(primeiro.emAnalise()).isZero();
        var fora=planilha(List.of(List.of("Atendimento","Dt.","Obs"),List.of("7","2026-09-10","reapresentada")));
        var segundo=service.importar(hospital,ana,fora,usuario);
        assertThat(segundo.emAnalise()).isEqualTo(1);
        assertThat(service.visitas(hospital,null)).extracting(v->v.get("decisao")).containsExactlyInAnyOrder("ORIGINAL","ORIGINAL","EM_ANALISE");
        // Outro medico com o mesmo atendimento e data e visita legitima.
        var outroMedico=planilha(List.of(List.of("Atendimento","Dt.","Obs"),List.of("7","2026-09-10","outro")));
        assertThat(service.importar(hospital,bruno,outroMedico,usuario).emAnalise()).isZero();
    }

    @Test void producaoColetivaIdentificaMedicoPorLinha() throws Exception {
        var arquivo=planilha(List.of(List.of("Atendimento","Dt.","Medico"),List.of("1","2026-09-10",nomeAna),List.of("2","2026-09-11","Desconhecido")));
        var resultado=service.importar(hospital,null,arquivo,usuario);
        assertThat(resultado.semMedico()).isEqualTo(1);
        var visitas=service.visitas(hospital,null);
        assertThat(visitas.get(0).get("medico_id")).isEqualTo(ana);
        assertThat(visitas.get(1).get("medico_id")).isNull();
    }

    @Test void codigoENomeDoExameVemDasColunasDaPlanilhaDaProducaoEOTipoEClassificadoPorLinha() throws Exception {
        var arquivo=planilha(List.of(List.of("Atendimento","Dt.","Codigo Procedimento","Procedimento"),
                List.of("1","2026-09-01","40901106","Ecodopplercardiograma Transtoracico"),
                List.of("2","2026-09-01","10102019","Visita hospitalar"),
                List.of("3","2026-09-01","","")));
        service.importar(hospital,ana,arquivo,usuario);
        var visitas=service.visitas(hospital,null);
        assertThat(visitas.get(0)).containsEntry("codigo_procedimento","40901106").containsEntry("procedimento","Ecodopplercardiograma Transtoracico").containsEntry("tipo_procedimento","EXAME");
        assertThat(visitas.get(1)).containsEntry("codigo_procedimento","10102019").containsEntry("procedimento","Visita hospitalar").containsEntry("tipo_procedimento","VISITA");
        // Sem codigo nem nome nao ha o que mostrar nem classificar.
        assertThat(visitas.get(2).get("codigo_procedimento")).isNull();
        assertThat(visitas.get(2).get("procedimento")).isNull();
        assertThat(visitas.get(2).get("tipo_procedimento")).isNull();
        // Os dados brutos da planilha nao vazam para a resposta da tela.
        assertThat(visitas.get(0)).doesNotContainKeys("campos","tipo_gravado");
    }

    @Test void tipoInformadoNaImportacaoVaiParaTodasAsLinhasESobrepoeAClassificacao() throws Exception {
        var arquivo=planilha(List.of(List.of("Atendimento","Dt.","Codigo Procedimento","Procedimento"),
                List.of("1","2026-09-01","10102019","Visita hospitalar"),List.of("2","2026-09-02","","")));
        var resultado=service.importar(hospital,ana,TipoProcedimento.CONSULTA,arquivo,usuario);
        assertThat(service.visitas(hospital,null)).extracting(v->v.get("tipo_procedimento")).containsExactly("CONSULTA","CONSULTA");
        assertThat(jdbc.queryForObject("select tipo_procedimento from producao_arquivo where id=?",String.class,resultado.arquivoId())).isEqualTo("CONSULTA");
    }

    @Test void repeticaoEntreArquivosDistingueExamesDiferentesDoMesmoAtendimento() throws Exception {
        service.importar(hospital,ana,planilha(List.of(List.of("Atendimento","Dt.","Codigo Procedimento","Procedimento"),
                List.of("5","2026-09-01","40901106","Ecodopplercardiograma Transtoracico"))),usuario);
        // Outro exame no mesmo atendimento e dia, em outro arquivo, e visita legitima.
        var outroExame=service.importar(hospital,ana,planilha(List.of(List.of("Atendimento","Dt.","Codigo Procedimento","Procedimento"),
                List.of("5","2026-09-01","40901360","Doppler Colorido De Vasos Cervicais"))),usuario);
        assertThat(outroExame.emAnalise()).isZero();
        // O mesmo exame reapresentado entra em analise.
        var reapresentado=service.importar(hospital,ana,planilha(List.of(List.of("Atendimento","Dt.","Codigo Procedimento","Procedimento","Obs"),
                List.of("5","2026-09-01","40901106","Ecodopplercardiograma Transtoracico","de novo"))),usuario);
        assertThat(reapresentado.emAnalise()).isEqualTo(1);
    }

    @Test void linhaSemDataERejeitadaENadaEGravado() throws Exception {
        var arquivo=planilha(List.of(List.of("Atendimento","Dt."),List.of("1","")));
        assertThatThrownBy(()->service.importar(hospital,ana,arquivo,usuario)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select count(*) from producao_arquivo where hospital_id=?",Long.class,hospital)).isZero();
    }
}
