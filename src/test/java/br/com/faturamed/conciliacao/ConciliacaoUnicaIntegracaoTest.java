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
class ConciliacaoUnicaIntegracaoTest {
    @Autowired ProducaoPrimeiroService producao;
    @Autowired ConciliacaoUnicaService unica;
    @Autowired VinculoBaseHospitalar vinculos;
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
    /** Cada item: atendimento, data. Medico fixo: Ana. */
    MockMultipartFile hospital(String... atendimentoEData) throws Exception {
        List<List<String>> linhas=new ArrayList<>();
        linhas.add(List.of("Atendimento","Data Consumo","Medico","Cod Produto","Produto","Valor Tot","Regra","Vl. A Repassar","Setor"));
        for(int i=0;i<atendimentoEData.length;i+=2)
            linhas.add(List.of(atendimentoEData[i],atendimentoEData[i+1],nomeAna,"101","Visita","100","1","100","Faturado"));
        return planilha(linhas);
    }
    MockMultipartFile producaoArquivo(String... atendimentoEData) throws Exception {
        List<List<String>> linhas=new ArrayList<>();
        linhas.add(List.of("Atendimento","Dt."));
        for(int i=0;i<atendimentoEData.length;i+=2) linhas.add(List.of(atendimentoEData[i],atendimentoEData[i+1]));
        return planilha(linhas);
    }
    Map<String,String> situacoes() {
        Map<String,String> mapa=new TreeMap<>();
        producao.visitas(hospital,null).forEach(v->mapa.put(v.get("atendimento").toString(),v.get("situacao").toString()));
        return mapa;
    }

    @Test void producaoPrimeiroDepoisHospital() throws Exception {
        var resultado=producao.importar(hospital,ana,producaoArquivo("123","2026-09-01","124","2026-10-02"),usuario);
        assertThat(resultado.conciliacao().vinculadas()).isZero();
        assertThat(situacoes()).containsEntry("123","PENDENTE").containsEntry("124","PENDENTE");
        var resposta=unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("123","2026-09-01"),usuario);
        assertThat(((ConciliacaoUnicaService.Resumo)resposta.get("conciliacao")).vinculadas()).isEqualTo(1);
        assertThat(situacoes()).containsEntry("123","PAGA").containsEntry("124","PENDENTE");
        // A competencia continua sendo a da data do atendimento, nao a da NF.
        assertThat(producao.visitas(hospital,"2026-09")).extracting(v->v.get("atendimento")).containsExactly("123");
    }

    @Test void hospitalPrimeiroDepoisProducaoDaOMesmoResultado() throws Exception {
        unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("123","2026-09-01"),usuario);
        var resultado=producao.importar(hospital,ana,producaoArquivo("123","2026-09-01","124","2026-10-02"),usuario);
        assertThat(resultado.conciliacao().vinculadas()).isEqualTo(1);
        assertThat(situacoes()).containsEntry("123","PAGA").containsEntry("124","PENDENTE");
    }

    @Test void registroDoHospitalSemProducaoFicaListado() throws Exception {
        producao.importar(hospital,ana,producaoArquivo("123","2026-09-01"),usuario);
        unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("123","2026-09-01","999","2026-09-05"),usuario);
        assertThat(unica.hospitalSemProducao(hospital,null)).extracting(v->v.get("atendimento")).containsExactly("999");
        assertThat(unica.hospitalSemProducao(hospital,"2026-10")).isEmpty();
    }

    @Test void doisRegistrosDaProducaoDisputandoUmDoHospitalNaoVinculamAutomaticamente() throws Exception {
        producao.importar(hospital,ana,producaoArquivo("123","2026-09-01","123","2026-09-01"),usuario);
        unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("123","2026-09-01"),usuario);
        assertThat(jdbc.queryForList("select situacao,hospital_visita_id from producao_visita where hospital_id=?",hospital))
                .allSatisfy(v->{ assertThat(v.get("situacao")).isEqualTo("DIVERGENTE"); assertThat(v.get("hospital_visita_id")).isNull(); });
    }

    @Test void medicoInferidoPeloRegistroUnicoDoHospitalNaProducaoColetiva() throws Exception {
        var coletiva=planilha(List.of(List.of("Atendimento","Dt."),List.of("123","2026-09-01")));
        producao.importar(hospital,null,coletiva,usuario);
        var resposta=unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("123","2026-09-01"),usuario);
        assertThat(((ConciliacaoUnicaService.Resumo)resposta.get("conciliacao")).medicosInferidos()).isEqualTo(1);
        var visita=producao.visitas(hospital,null).getFirst();
        assertThat(visita.get("situacao")).isEqualTo("PAGA");
        assertThat(visita.get("medico_id")).isEqualTo(ana);
        assertThat(visita.get("medico_inferido")).isEqualTo(true);
    }

    @Test void vinculoExistenteNaoEDesfeitoENaoHaDuplaAssociacao() throws Exception {
        producao.importar(hospital,ana,producaoArquivo("123","2026-09-01"),usuario);
        unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("123","2026-09-01"),usuario);
        Integer hospitalVisita=(Integer)producao.visitas(hospital,null).getFirst().get("hospital_visita_id");
        assertThat(hospitalVisita).isNotNull();
        assertThat(vinculos.indisponiveis(hospital)).contains(hospitalVisita);
        // Nova producao com a mesma visita em outro arquivo: entra em analise e nao toma o registro ja vinculado.
        producao.importar(hospital,ana,planilha(List.of(List.of("Atendimento","Dt.","Obs"),List.of("123","2026-09-01","reapresentada"))),usuario);
        assertThat(jdbc.queryForObject("select count(*) from producao_visita where hospital_visita_id=?",Long.class,hospitalVisita)).isEqualTo(1L);
        // Reconciliar de novo nao muda nada.
        assertThat(unica.conciliar(hospital).vinculadas()).isZero();
        assertThat(producao.visitas(hospital,null).getFirst().get("hospital_visita_id")).isEqualTo(hospitalVisita);
    }
}
