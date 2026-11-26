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
    @Autowired RelatorioUnicoService relatorios;
    @Autowired RevisaoVisitasService revisoes;
    @Autowired RecebimentoService recebimentos;
    @Autowired br.com.faturamed.seguranca.LiberacaoRelatorioService liberacoes;
    @Autowired br.com.faturamed.seguranca.ExportadorPreviaLiberacao exportadorPrevia;
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
        // Producao sem coluna de procedimento mostra o do hospital quando ha vinculo.
        assertThat(producao.visitas(hospital,"2026-09").getFirst().get("procedimento")).isEqualTo("Visita");
        assertThat(producao.visitas(hospital,"2026-10").getFirst().get("procedimento")).isNull();
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

    @Test void relatorioDaCompetenciaNasceDaBaseNovaECarregaOMesmoCaminhoDoFluxoAtual() throws Exception {
        producao.importar(hospital,ana,producaoArquivo("123","2026-09-01","124","2026-10-02"),usuario);
        unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("123","2026-09-01","124","2026-10-02"),usuario);
        var grupos=relatorios.grupos(hospital);
        assertThat(grupos).hasSize(2).allSatisfy(g->assertThat(g.get("disponiveis")).isEqualTo(1L));
        var resultado=relatorios.gerar(hospital,ana,"2026-09",null,usuario);
        assertThat(resultado.visitas()).isEqualTo(1);
        var estado=revisoes.carregar(resultado.relatorioId());
        assertThat(estado.atual().visitas()).hasSize(1);
        assertThat(estado.atual().visitas().getFirst().status()).isEqualTo(ConciliadorVisitas.Status.PAGA);
        assertThat(estado.atual().visitas().getFirst().atendimento()).isEqualTo("123");
        // A visita aponta para o relatorio e a competencia de outubro continua disponivel.
        var visita=producao.visitas(hospital,"2026-09").getFirst();
        assertThat(((Number)visita.get("relatorio_id")).longValue()).isEqualTo(resultado.relatorioId());
        assertThat(visita.get("linha_relatorio")).isEqualTo(2);
        // Dentro do relatorio o vinculo vive no relatorio; a tela ainda mostra o procedimento do hospital.
        assertThat(visita.get("procedimento")).isEqualTo("Visita");
        var depois=relatorios.grupos(hospital);
        assertThat(depois).filteredOn(g->"2026-09".equals(g.get("competencia"))).singleElement()
                .satisfies(g->{ assertThat(g.get("disponiveis")).isEqualTo(0L); assertThat(g.get("em_relatorio")).isEqualTo(1L); });
        assertThat(depois).filteredOn(g->"2026-10".equals(g.get("competencia"))).singleElement().satisfies(g->assertThat(g.get("disponiveis")).isEqualTo(1L));
        // Sem visitas novas, nao gera outro relatorio.
        assertThatThrownBy(()->relatorios.gerar(hospital,ana,"2026-09",null,usuario)).isInstanceOf(IllegalArgumentException.class);
        // O registro hospitalar agora pertence ao relatorio e nao volta como "sem producao".
        assertThat(unica.hospitalSemProducao(hospital,"2026-09")).isEmpty();
    }

    @Test void relatorioGeradoSegueParaLiberacaoERecebimento() throws Exception {
        producao.importar(hospital,ana,producaoArquivo("123","2026-09-01"),usuario);
        unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("123","2026-09-01"),usuario);
        long relatorio=relatorios.gerar(hospital,ana,"2026-09",null,usuario).relatorioId();
        liberacoes.liberar(relatorio,new br.com.faturamed.seguranca.LiberacaoRelatorioService.Dados(0L,"Publicacao",true),usuario);
        recebimentos.registrar(relatorio,2,new RecebimentoService.Dados(new java.math.BigDecimal("40"),java.time.LocalDate.of(2026,9,2),"Parcial",0L),usuario);
        var visita=recebimentos.listar(relatorio).visitas().getFirst();
        assertThat(visita.faturado()).isEqualByComparingTo("100");
        assertThat(visita.recebido()).isEqualByComparingTo("40");
        assertThat(visita.saldo()).isEqualByComparingTo("60");
    }

    @Test void arquivoDoHospitalQueChegaDepoisAtualizaORelatorioJaGerado() throws Exception {
        producao.importar(hospital,ana,producaoArquivo("123","2026-09-01","125","2026-09-03"),usuario);
        unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("123","2026-09-01"),usuario);
        long relatorio=relatorios.gerar(hospital,ana,"2026-09",null,usuario).relatorioId();
        assertThat(revisoes.carregar(relatorio).atual().visitas()).extracting(ConciliadorVisitas.Visita::status)
                .containsExactly(ConciliadorVisitas.Status.PAGA,ConciliadorVisitas.Status.PENDENTE);
        var resposta=unica.importarHospital(hospital,"2026-11","NOVO",null,hospital("125","2026-09-03"),usuario);
        @SuppressWarnings("unchecked") var atualizados=(List<ReconciliacaoAutomaticaService.Reconciliada>)resposta.get("relatoriosAtualizados");
        assertThat(atualizados).hasSize(1);
        assertThat(atualizados.getFirst().erro()).isNull();
        long novo=atualizados.getFirst().relatorioNovo();
        assertThat(novo).isNotEqualTo(relatorio);
        assertThat(revisoes.carregar(novo).atual().visitas()).extracting(ConciliadorVisitas.Visita::status)
                .containsOnly(ConciliadorVisitas.Status.PAGA);
        // A visita continua ligada ao mesmo arquivo; o relatorio atual dele avancou de versao.
        assertThat(producao.visitas(hospital,"2026-09")).extracting(v->((Number)v.get("relatorio_id")).longValue()).containsOnly(novo);
        // Nada sobra para a conciliacao unica reprocessar nem aparece como hospital sem producao.
        assertThat(unica.conciliar(hospital).vinculadas()).isZero();
        assertThat(unica.hospitalSemProducao(hospital,null)).isEmpty();
    }

    @Test void totalFaturadoDoArquivoVemAbertoPorMesDoExameComOQueJaCasouComAProducao() throws Exception {
        // Um faturamento paga exames de varios meses; so os de setembro tem producao importada.
        unica.importarHospital(hospital,"2026-09","NOVO",null,hospital("1","2026-07-10","2","2026-08-10","3","2026-09-10"),usuario);
        producao.importar(hospital,ana,producaoArquivo("3","2026-09-10"),usuario);
        var arquivos=unica.faturamento(hospital);
        assertThat(arquivos).hasSize(1);
        var f=arquivos.getFirst();
        assertThat(f.competencia()).isEqualTo("2026-09");
        assertThat(f.registros()).isEqualTo(3);
        assertThat(f.repasse()).isEqualByComparingTo("300.00");
        assertThat(f.conciliadoRegistros()).isEqualTo(1);
        assertThat(f.conciliadoRepasse()).isEqualByComparingTo("100.00");
        assertThat(f.semProducaoRegistros()).isEqualTo(2);
        assertThat(f.semProducaoRepasse()).isEqualByComparingTo("200.00");
        assertThat(f.outrosRegistros()).isZero();
        assertThat(f.meses()).extracting(m->m.mes()).containsExactly("2026-07","2026-08","2026-09");
        assertThat(f.meses().get(0).semProducaoRegistros()).isEqualTo(1);
        assertThat(f.meses().get(2).conciliadoRegistros()).isEqualTo(1);
        // O total do arquivo e a soma das partes.
        assertThat(f.conciliadoRepasse().add(f.semProducaoRepasse()).add(f.outrosRepasse())).isEqualByComparingTo(f.repasse());
        assertThat(f.meses().stream().map(m->m.repasse()).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add)).isEqualByComparingTo(f.repasse());
        // Gerar o relatorio nao muda o que ja estava conciliado; a producao dos outros meses faz o resto casar.
        relatorios.gerar(hospital,ana,"2026-09",null,usuario);
        assertThat(unica.faturamento(hospital).getFirst().conciliadoRegistros()).isEqualTo(1);
        producao.importar(hospital,ana,planilha(List.of(List.of("Atendimento","Dt.","Obs"),List.of("1","2026-07-10","jul"),List.of("2","2026-08-10","ago"))),usuario);
        var depois=unica.faturamento(hospital).getFirst();
        assertThat(depois.conciliadoRegistros()).isEqualTo(3);
        assertThat(depois.semProducaoRegistros()).isZero();
        assertThat(depois.conciliadoRepasse()).isEqualByComparingTo("300.00");
    }

    @Test void relatorioMostraOTotalFaturadoDoArquivoDeOndeSaemSeusRegistrosEQuantoDeleEstaNele() throws Exception {
        unica.importarHospital(hospital,"2026-09","NOVO",null,hospital("1","2026-07-10","2","2026-08-10","3","2026-09-10"),usuario);
        producao.importar(hospital,ana,producaoArquivo("3","2026-09-10"),usuario);
        long deSetembro=relatorios.gerar(hospital,ana,"2026-09",null,usuario).relatorioId();
        var lista=unica.faturamentoDoRelatorio(deSetembro);
        assertThat(lista).hasSize(1);
        var f=lista.getFirst();
        assertThat(f.competencia()).isEqualTo("2026-09");
        assertThat(f.registros()).isEqualTo(3);
        assertThat(f.repasse()).isEqualByComparingTo("300.00");
        assertThat(f.noRelatorioRegistros()).isEqualTo(1);
        assertThat(f.noRelatorioRepasse()).isEqualByComparingTo("100.00");
        assertThat(f.meses()).extracting(m->m.mes()).containsExactly("2026-07","2026-08","2026-09");
        assertThat(f.meses().get(0).noRelatorioRegistros()).isZero();
        assertThat(f.meses().get(2).noRelatorioRegistros()).isEqualTo(1);
        // Outra producao do mesmo faturamento, de outro mes, gera outro relatorio com a parte dela no mesmo total.
        producao.importar(hospital,ana,planilha(List.of(List.of("Atendimento","Dt.","Obs"),List.of("1","2026-07-10","jul"))),usuario);
        long deJulho=relatorios.gerar(hospital,ana,"2026-07",null,usuario).relatorioId();
        var julho=unica.faturamentoDoRelatorio(deJulho).getFirst();
        assertThat(julho.repasse()).isEqualByComparingTo("300.00");
        assertThat(julho.noRelatorioRegistros()).isEqualTo(1);
        assertThat(julho.meses().get(0).noRelatorioRegistros()).isEqualTo(1);
        assertThat(julho.meses().get(2).noRelatorioRegistros()).isZero();
        // Relatorio sem vinculo com o hospital nao tem o que mostrar.
        assertThat(unica.faturamentoDoRelatorio(-1)).isEmpty();
    }

    @Test void previaDaLiberacaoComDadosReaisTrazCompetenciasCodigoENomeDaProducaoEStatusPago() throws Exception {
        // O faturamento de 08/2026 paga um exame de setembro; a visita 2 ainda nao tem registro do hospital.
        unica.importarHospital(hospital,"2026-08","NOVO",null,hospital("1","2026-09-01"),usuario);
        producao.importar(hospital,ana,planilha(List.of(List.of("Atendimento","Dt.","Codigo Procedimento","Procedimento"),
                List.of("1","2026-09-01","101","Visita"),List.of("2","2026-09-02","99887766","Doppler Venoso"))),usuario);
        long relatorio=relatorios.gerar(hospital,ana,"2026-09",null,usuario).relatorioId();
        var previa=liberacoes.previa(relatorio);
        assertThat(previa.competenciasProducao()).containsExactly("2026-09");
        assertThat(previa.extras().values()).extracting(e->e.compFaturada()).containsExactlyInAnyOrder("2026-08","");
        var visitas=previa.medicos().getFirst().visitas();
        var pendente=visitas.stream().filter(v->v.atendimento().equals("2")).findFirst().orElseThrow();
        assertThat(pendente.codigoProcedimento()).isEqualTo("99887766");
        assertThat(pendente.procedimento()).isEqualTo("Doppler Venoso");
        try(var workbook=new XSSFWorkbook(new java.io.ByteArrayInputStream(exportadorPrevia.exportar(previa)))) {
            var aba=workbook.getSheetAt(1);
            var paga=aba.getRow(6);
            assertThat(paga.getCell(0).getStringCellValue()).isEqualTo("1");
            assertThat(paga.getCell(6).getStringCellValue()).isEqualTo("09/2026");
            assertThat(paga.getCell(7).getStringCellValue()).isEqualTo("09/2026");
            assertThat(paga.getCell(8).getStringCellValue()).isEqualTo("08/2026");
            assertThat(paga.getCell(9).getStringCellValue()).isEqualTo("PAGO 08/2026");
            assertThat(paga.getCell(10).getNumericCellValue()).isEqualTo(100.0);
            var pend=aba.getRow(7);
            assertThat(pend.getCell(0).getStringCellValue()).isEqualTo("2");
            assertThat(pend.getCell(4).getStringCellValue()).isEqualTo("99887766");
            assertThat(pend.getCell(5).getStringCellValue()).isEqualTo("Doppler Venoso");
            assertThat(pend.getCell(9).getStringCellValue()).isEqualTo("PENDENTE");
            assertThat(pend.getCell(10).getNumericCellValue()).isZero();
        }
    }

    @Test void repeticoesEmAnaliseEntramNoTotalDoArquivoComoOutros() throws Exception {
        unica.importarHospital(hospital,"2026-09","NOVO",null,hospital("3","2026-09-10"),usuario);
        unica.importarHospital(hospital,"2026-10","NOVO",null,hospital("3","2026-09-10","4","2026-09-11"),usuario);
        var arquivos=unica.faturamento(hospital);
        assertThat(arquivos).hasSize(2);
        var segundo=arquivos.stream().filter(a->"2026-10".equals(a.competencia())).findFirst().orElseThrow();
        assertThat(segundo.registros()).isEqualTo(2);
        assertThat(segundo.repasse()).isEqualByComparingTo("200.00");
        assertThat(segundo.outrosRegistros()).isEqualTo(1);
        assertThat(segundo.outrosRepasse()).isEqualByComparingTo("100.00");
        assertThat(segundo.semProducaoRegistros()).isEqualTo(1);
    }

    @Test void valorDaPlanilhaLeOsFormatosBrasileiroEInternacional() {
        assertThat(ConciliacaoUnicaService.valorDaPlanilha("1234.56")).isEqualByComparingTo("1234.56");
        assertThat(ConciliacaoUnicaService.valorDaPlanilha("1234,56")).isEqualByComparingTo("1234.56");
        assertThat(ConciliacaoUnicaService.valorDaPlanilha("1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(ConciliacaoUnicaService.valorDaPlanilha("R$ 1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(ConciliacaoUnicaService.valorDaPlanilha("157.85")).isEqualByComparingTo("157.85");
        assertThat(ConciliacaoUnicaService.valorDaPlanilha("")).isNull();
        assertThat(ConciliacaoUnicaService.valorDaPlanilha(null)).isNull();
        assertThat(ConciliacaoUnicaService.valorDaPlanilha("abc")).isNull();
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
