package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import br.com.faturamed.seguranca.UsuarioAutenticado;
import br.com.faturamed.seguranca.LiberacaoRelatorioService;
import br.com.faturamed.seguranca.PortalMedicoController;
import br.com.faturamed.seguranca.ExtratoMedicoService;
import br.com.faturamed.seguranca.ExportadorDemonstrativoPdf;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class BaseHospitalarIntegracaoTest {
    @Autowired BaseHospitalarService service;
    @Autowired RevisaoVisitasService revisoes;
    @Autowired RecebimentoService recebimentos;
    @Autowired JdbcTemplate jdbc;
    @Autowired AcompanhamentoBaseHospitalarService acompanhamento;
    @Autowired LiberacaoRelatorioService liberacoes;
    @Autowired PortalMedicoController portal;
    @Autowired ExtratoMedicoService extratos;
    @Autowired ExportadorDemonstrativoPdf pdf;
    long hospital, ana, bruno;
    UsuarioAutenticado usuario;

    @BeforeEach void preparar() {
        String sufixo=UUID.randomUUID().toString();
        hospital=jdbc.queryForObject("insert into hospital_cadastro(nome,nome_normalizado) values(?,?) returning id",Long.class,"Hospital "+sufixo,sufixo);
        ana=jdbc.queryForObject("insert into medico_cadastro(nome,crm,uf) values(?,?,'SP') returning id",Long.class,"Ana "+sufixo,Long.toString(System.nanoTime()));
        bruno=jdbc.queryForObject("insert into medico_cadastro(nome,crm,uf) values(?,?,'SP') returning id",Long.class,"Bruno "+sufixo,Long.toString(System.nanoTime()));
        long usuarioId=jdbc.queryForObject("select id from usuario_acesso where perfil='ADMIN' limit 1",Long.class);
        usuario=new UsuarioAutenticado(usuarioId,"teste@local","Teste","","ADMIN",null,true,false,0);
    }
    String nome(long medico) { return jdbc.queryForObject("select nome from medico_cadastro where id=?",String.class,medico); }
    MockMultipartFile hospital(String... medicos) throws Exception {
        List<List<String>> linhas=new ArrayList<>();
        linhas.add(List.of("Atendimento","Data Consumo","Medico","Cod Produto","Produto","Valor Tot","Regra","Vl. A Repassar","Setor"));
        for(String medico:medicos) linhas.add(List.of("123","2026-09-01",medico,"101","Visita","100","1","100","Faturado"));
        return planilha(linhas);
    }
    MockMultipartFile producao() throws Exception { return planilha(List.of(List.of("Atendimento","Dt."),List.of("123","2026-09-01"))); }
    MockMultipartFile hospitalSituacao(String setor,String valor,String repasse) throws Exception {
        return planilha(List.of(List.of("Atendimento","Data Consumo","Medico","Cod Produto","Produto","Valor Tot","Regra","Vl. A Repassar","Setor"),
                List.of("123","2026-09-01",nome(ana),"101","Visita",valor,"1",repasse,setor)));
    }
    MockMultipartFile planilha(List<List<String>> linhas) throws Exception {
        try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet("Planilha2");
            for(int indice=0;indice<linhas.size();indice++) {
                var row=sheet.createRow(indice);
                for(int coluna=0;coluna<linhas.get(indice).size();coluna++) row.createCell(coluna).setCellValue(linhas.get(indice).get(coluna));
            }
            workbook.write(output);
            return new MockMultipartFile("arquivo","teste.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
        }
    }
    int id(Map<String,Object> visita) { return ((Number)visita.get("id")).intValue(); }

    @Test void guardaHospitalSemProducaoEPreservaOriginalNoReenvio() throws Exception {
        var arquivo=hospital(nome(ana));
        var resultado=service.importarHospital(hospital,"2026-09",arquivo,usuario);
        assertThat(service.visitas(hospital)).singleElement().satisfies(visita->assertThat(visita.get("situacao")).isEqualTo("AGUARDANDO_PRODUCAO"));
        assertThat((byte[])service.arquivo(resultado.arquivoId()).get("conteudo")).isEqualTo(arquivo.getBytes());
        assertThat(service.importarHospital(hospital,"2026-09",arquivo,usuario).repetido()).isTrue();
        assertThat(service.visitas(hospital)).hasSize(1);
    }

    @Test void mesmoAtendimentoDataComDoisMedicosGeraVinculosDiferentes() throws Exception {
        var arquivo=hospital(nome(ana),nome(bruno));
        assertThat(service.importarHospital(hospital,"2026-09",arquivo,usuario).divergencias()).isZero();
        var producao=producao();
        var primeira=service.importarProducao(hospital,ana,"2026-09",producao,usuario);
        var segunda=service.importarProducao(hospital,bruno,"2026-09",producao,usuario);
        var visitaAna=revisoes.carregar(primeira.relatorioId()).atual().visitas().getFirst();
        var visitaBruno=revisoes.carregar(segunda.relatorioId()).atual().visitas().getFirst();
        assertThat(visitaAna.hospital().linha()).isNotEqualTo(visitaBruno.hospital().linha());
        assertThat(visitaAna.medicoCadastroId()).isEqualTo(ana);
        assertThat(service.visitas(hospital)).allSatisfy(visita->assertThat(visita.get("situacao")).isEqualTo("CONCILIADA"));
        assertThat(service.importarProducao(hospital,ana,"2026-09",producao,usuario).relatorioId()).isEqualTo(primeira.relatorioId());
    }

    @Test void repeticaoEmOutroMesExigeDecisaoENaoDuplicaRecebimentos() throws Exception {
        var arquivo=hospital(nome(ana));
        service.importarHospital(hospital,"2026-09",arquivo,usuario);
        var primeira=service.importarProducao(hospital,ana,"2026-09",producao(),usuario);
        int original=revisoes.carregar(primeira.relatorioId()).atual().visitas().getFirst().hospital().linha();
        recebimentos.registrar(primeira.relatorioId(),2,new RecebimentoService.Dados(new BigDecimal("100"),LocalDate.of(2026,9,2),"Confirmado",0L),usuario);
        assertThat(service.importarHospital(hospital,"2026-10",arquivo,usuario).divergencias()).isEqualTo(1);
        var repetida=service.visitas(hospital).stream().filter(visita->visita.get("decisao").equals("EM_ANALISE")).findFirst().orElseThrow();
        service.decidir(id(repetida),new BaseHospitalarService.Decisao("DUPLICADO_PAGO_OUTRO_MES",original,"Mesma visita reapresentada","Pagamento confirmado em setembro"),usuario);
        assertThat(service.historico(id(repetida))).hasSize(1);
        var segunda=service.importarProducao(hospital,ana,"2026-10",producao(),usuario);
        assertThat(revisoes.carregar(segunda.relatorioId()).atual().visitas().getFirst().status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);
        assertThat(jdbc.queryForObject("select count(*) from vinculo_base_hospitalar where visita_id=?",Long.class,original)).isEqualTo(1);
        assertThat(recebimentos.listar(primeira.relatorioId()).visitas().getFirst().recebido()).isEqualByComparingTo("100");
    }

    @Test void bloqueiaRevisaoQueTentariaConsumirVisitaDeOutraProducao() throws Exception {
        service.importarHospital(hospital,"2026-09",hospital(nome(ana)),usuario);
        var primeira=service.importarProducao(hospital,ana,"2026-09",producao(),usuario);
        int original=revisoes.carregar(primeira.relatorioId()).atual().visitas().getFirst().hospital().linha();
        var segunda=service.importarProducao(hospital,ana,"2026-10",producao(),usuario);
        var pedido=new RevisaoVisitaRequest(RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA,original,"Teste","Confirmar mesmo atendimento",0L,null);
        assertThatThrownBy(()->revisoes.revisar(segunda.relatorioId(),2,pedido)).isInstanceOf(ConflitoRevisaoException.class).hasMessageContaining("ja vinculada");
    }

    @Test void novaVersaoMantemIdRecebimentoEReavaliaPendencias() throws Exception {
        service.importarHospital(hospital,"2026-09",hospital(nome(ana)),usuario);
        var primeira=service.importarProducao(hospital,ana,"2026-09",producao(),usuario);
        int original=revisoes.carregar(primeira.relatorioId()).atual().visitas().getFirst().hospital().linha();
        recebimentos.registrar(primeira.relatorioId(),2,new RecebimentoService.Dados(new BigDecimal("40"),LocalDate.of(2026,9,2),"Parcial",0L),usuario);
        long nova=service.atualizar(primeira.relatorioId(),0);
        assertThat(revisoes.carregar(nova).atual().visitas().getFirst().hospital().linha()).isEqualTo(original);
        assertThat(recebimentos.listar(nova).visitas().getFirst().recebido()).isEqualByComparingTo("40");
        assertThat(jdbc.queryForObject("select relatorio_id from vinculo_base_hospitalar where visita_id=?",Long.class,original)).isEqualTo(nova);
    }

    @Test void repeticaoAntesDaProducaoBloqueiaVinculoAteAnalise() throws Exception {
        var arquivo=hospital(nome(ana));
        service.importarHospital(hospital,"2026-09",arquivo,usuario);
        service.importarHospital(hospital,"2026-10",arquivo,usuario);
        var producao=service.importarProducao(hospital,ana,"2026-09",producao(),usuario);
        assertThat(revisoes.carregar(producao.relatorioId()).atual().visitas().getFirst().hospital()).isNull();
        var repetida=service.visitas(hospital).stream().filter(visita->visita.get("decisao").equals("EM_ANALISE")).findFirst().orElseThrow();
        service.decidir(id(repetida),new BaseHospitalarService.Decisao("VISITA_DISTINTA",null,"Duas visitas distintas conferidas",null),usuario);
        long nova=service.atualizar(producao.relatorioId(),0);
        assertThat(revisoes.carregar(nova).atual().visitas().getFirst().status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);
        assertThat(service.visitas(hospital)).hasSize(2);
    }

    @Test void novaVisitaDoHospitalResolveProducaoSemCorrespondencia() throws Exception {
        service.importarHospital(hospital,"2026-09",hospital(nome(bruno)),usuario);
        var primeira=service.importarProducao(hospital,ana,"2026-09",producao(),usuario);
        assertThat(revisoes.carregar(primeira.relatorioId()).atual().visitas().getFirst().hospital()).isNull();
        service.importarHospital(hospital,"2026-09",hospital(nome(ana)),usuario);
        long nova=service.atualizar(primeira.relatorioId(),0);
        assertThat(revisoes.carregar(nova).atual().visitas().getFirst().hospital()).isNotNull();
        assertThat(revisoes.carregar(nova).atual().visitas().getFirst().medicoCadastroId()).isEqualTo(ana);
    }

    @Test void revisaoQueRemoveVinculoNaoLiberaVisitaParaOutraProducao() throws Exception {
        service.importarHospital(hospital,"2026-09",hospital(nome(ana)),usuario);
        var primeira=service.importarProducao(hospital,ana,"2026-09",producao(),usuario);
        revisoes.revisar(primeira.relatorioId(),2,new RevisaoVisitaRequest(RevisaoVisitaRequest.Acao.MANTER_DIVERGENTE,null,"Teste","Conferir informacoes da visita",0L,null));
        var segunda=service.importarProducao(hospital,ana,"2026-10",producao(),usuario);
        assertThat(revisoes.carregar(segunda.relatorioId()).atual().visitas().getFirst().status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);
        assertThat(jdbc.queryForObject("select count(*) from reserva_base_hospitalar",Long.class)).isGreaterThan(0);
    }

    @Test void coletivaVinculaDoisMedicosNoMesmoDiaEPreservaIdsNaAtualizacao() throws Exception {
        service.importarHospital(hospital,"2026-09",hospital(nome(ana),nome(bruno)),usuario);
        var arquivo=planilha(List.of(List.of("Atendimento","Dt.","Medico"),
                List.of("123","2026-09-01",nome(ana)),List.of("123","2026-09-01",nome(bruno))));
        var resultado=service.importarProducao(hospital,null,"VARIOS","2026-09",arquivo,usuario);
        var visitas=revisoes.carregar(resultado.relatorioId()).atual().visitas();
        assertThat(visitas).extracting(ConciliadorVisitas.Visita::medicoCadastroId).containsExactly(ana,bruno);
        assertThat(visitas).allSatisfy(visita->assertThat(visita.status()).isEqualTo(ConciliadorVisitas.Status.PAGA));
        assertThat(visitas).extracting(visita->visita.hospital().linha()).doesNotHaveDuplicates();
        assertThat(service.importarProducao(hospital,null,"VARIOS","2026-09",arquivo,usuario).relatorioId()).isEqualTo(resultado.relatorioId());
        recebimentos.registrar(resultado.relatorioId(),2,new RecebimentoService.Dados(new BigDecimal("30"),LocalDate.of(2026,9,2),"Recebimento Ana",0L),usuario);
        long nova=service.atualizar(resultado.relatorioId(),0);
        assertThat(revisoes.carregar(nova).atual().visitas()).extracting(visita->visita.hospital().linha())
                .containsExactlyElementsOf(visitas.stream().map(visita->visita.hospital().linha()).toList());
        assertThat(recebimentos.listar(nova).visitas().getFirst().recebido()).isEqualByComparingTo("30");
        assertThat(service.arquivos(hospital)).anySatisfy(registro->{
            assertThat(registro.get("modo_producao")).isEqualTo("VARIOS");
            assertThat(registro.get("relatorio_id")).isEqualTo(nova);
        });
    }

    @Test void coletivaSemMedicoOuComHomonimoNaoEscolheHospitalAutomaticamente() throws Exception {
        service.importarHospital(hospital,"2026-09",hospital(nome(ana),nome(bruno)),usuario);
        jdbc.update("update medico_cadastro set nome=? where id=?",nome(ana),bruno);
        var arquivo=planilha(List.of(List.of("Atendimento","Dt.","Medico"),
                List.of("123","2026-09-01",""),List.of("123","2026-09-01",nome(ana)),List.of("123","2026-09-01","Nao cadastrado")));
        var resultado=service.importarProducao(hospital,null,"VARIOS","2026-09",arquivo,usuario);
        assertThat(revisoes.carregar(resultado.relatorioId()).atual().visitas()).hasSize(3).allSatisfy(visita->{
            assertThat(visita.hospital()).isNull();
            assertThat(visita.status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);
            assertThat(visita.candidatos()).hasSize(2);
        });
    }

    @Test void coletivaAgrupaAliasesEImpedeDuasLinhasUsaremMesmaVisita() throws Exception {
        String alias="Alias "+UUID.randomUUID();
        jdbc.update("insert into medico_alias(medico_id,nome,normalizado) values(?,?,?)",ana,alias,LeitorPlanilhaVisitas.normalizar(alias));
        service.importarHospital(hospital,"2026-09",hospital(nome(ana)),usuario);
        var arquivo=planilha(List.of(List.of("Atendimento","Dt.","Medico"),
                List.of("123","2026-09-01",alias),List.of("123","2026-09-01",nome(ana))));
        var resultado=service.importarProducao(hospital,null,"VARIOS","2026-09",arquivo,usuario);
        assertThat(revisoes.carregar(resultado.relatorioId()).atual().visitas()).allSatisfy(visita->{
            assertThat(visita.hospital()).isNull();
            assertThat(visita.medicoCadastroId()).isEqualTo(ana);
            assertThat(visita.status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);
        });
    }

    @Test void coletivaNaoReutilizaVisitaConsumidaPorProducaoIndividual() throws Exception {
        service.importarHospital(hospital,"2026-09",hospital(nome(ana),nome(bruno)),usuario);
        service.importarProducao(hospital,ana,"2026-09",producao(),usuario);
        var arquivo=planilha(List.of(List.of("Atendimento","Dt.","Medico"),
                List.of("123","2026-09-01",nome(ana)),List.of("123","2026-09-01",nome(bruno))));
        var resultado=service.importarProducao(hospital,null,"VARIOS","2026-09",arquivo,usuario);
        var visitas=revisoes.carregar(resultado.relatorioId()).atual().visitas();
        assertThat(visitas.getFirst().status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);
        assertThat(visitas.getFirst().hospital()).isNull();
        assertThat(visitas.get(1).hospital()).isNotNull();
        assertThat(visitas.get(1).medicoCadastroId()).isEqualTo(bruno);
    }

    @Test void validaModoDeProducaoESelecaoDoMedico() throws Exception {
        var arquivo=producao();
        assertThatThrownBy(()->service.importarProducao(hospital,null,"UNICO","2026-09",arquivo,usuario)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.importarProducao(hospital,ana,"VARIOS","2026-09",arquivo,usuario)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.importarProducao(hospital,null,"INVALIDO","2026-09",arquivo,usuario)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void revisaoColetivaNaoPodeAssociarMedicoHospitalarDiferenteDaLinha() throws Exception {
        service.importarHospital(hospital,"2026-09",hospital(nome(ana),nome(bruno)),usuario);
        var arquivo=planilha(List.of(List.of("Atendimento","Dt.","Medico"),List.of("123","2026-09-01",nome(ana))));
        var resultado=service.importarProducao(hospital,null,"VARIOS","2026-09",arquivo,usuario);
        int idBruno=service.visitas(hospital).stream().filter(visita->visita.get("medico_nome").equals(nome(bruno))).mapToInt(this::id).findFirst().orElseThrow();
        var pedido=new RevisaoVisitaRequest(RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA,idBruno,"Teste","Tentativa de associar outro medico",0L,null);
        assertThatThrownBy(()->revisoes.revisar(resultado.relatorioId(),2,pedido)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("medico hospitalar");
    }

    @Test void coletivaSemMedicoInfereCorrespondenciaUnicaEPreservaFonte() throws Exception {
        service.importarHospital(hospital,"2026-09",hospital(nome(ana)),usuario);
        var resultado=service.importarProducao(hospital,null,"VARIOS","2026-09",producao(),usuario);
        var visita=revisoes.carregar(resultado.relatorioId()).atual().visitas().getFirst();
        assertThat(visita.status()).isEqualTo(ConciliadorVisitas.Status.PAGA);
        assertThat(visita.medicoCadastroId()).isEqualTo(ana);
        assertThat(visita.hospital()).isNotNull();
        assertThat(visita.original().getOrDefault("medico","")).isEmpty();
        assertThat(visita.medicoProducao().preenchido()).isTrue();
        assertThat(visita.motivo()).contains("correspondencia hospitalar unica");
        assertThat(service.visitas(hospital)).singleElement().satisfies(registro->assertThat(registro.get("situacao")).isEqualTo("CONCILIADA"));
    }

    @Test void coletivaSemMedicoESemHospitalFicaPendenteENaoPresumeIdentidade() throws Exception {
        service.importarHospital(hospital,"2026-09",hospital(nome(ana)),usuario);
        var arquivo=planilha(List.of(List.of("Atendimento","Dt."),List.of("999","2026-09-01")));
        var resultado=service.importarProducao(hospital,null,"VARIOS","2026-09",arquivo,usuario);
        var visita=revisoes.carregar(resultado.relatorioId()).atual().visitas().getFirst();
        assertThat(visita.status()).isEqualTo(ConciliadorVisitas.Status.PENDENTE);
        assertThat(visita.medicoCadastroId()).isNull();
        assertThat(visita.hospital()).isNull();
    }

    @Test void inferenciaNaoSobrescreveMedicoInformadoDesconhecido() throws Exception {
        service.importarHospital(hospital,"2026-09",hospital(nome(ana)),usuario);
        var arquivo=planilha(List.of(List.of("Atendimento","Dt.","Medico"),List.of("123","2026-09-01","Outro profissional")));
        var resultado=service.importarProducao(hospital,null,"VARIOS","2026-09",arquivo,usuario);
        var visita=revisoes.carregar(resultado.relatorioId()).atual().visitas().getFirst();
        assertThat(visita.status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);
        assertThat(visita.medicoCadastroId()).isNull();
        assertThat(visita.hospital()).isNull();
    }

    @Test void linhasSemMedicoDisputandoUnicoRegistroNaoSaoVinculadas() throws Exception {
        service.importarHospital(hospital,"2026-09",hospital(nome(ana)),usuario);
        var arquivo=planilha(List.of(List.of("Atendimento","Dt.","Medico"),List.of("123","2026-09-01",""),List.of("123","2026-09-01",nome(ana))));
        var resultado=service.importarProducao(hospital,null,"VARIOS","2026-09",arquivo,usuario);
        assertThat(revisoes.carregar(resultado.relatorioId()).atual().visitas()).allSatisfy(visita->{
            assertThat(visita.status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);
            assertThat(visita.hospital()).isNull();
        });
    }

    @Test void inferenciaMantemAmbiguidadeMesmoComDoisRegistrosDoMesmoMedico() throws Exception {
        service.importarHospital(hospital,"2026-09",hospital(nome(ana),nome(ana)),usuario);
        var repetida=service.visitas(hospital).stream().filter(visita->visita.get("decisao").equals("EM_ANALISE")).findFirst().orElseThrow();
        service.decidir(id(repetida),new BaseHospitalarService.Decisao("VISITA_DISTINTA",null,"Procedimentos distintos conferidos",null),usuario);
        var resultado=service.importarProducao(hospital,null,"VARIOS","2026-09",producao(),usuario);
        var visita=revisoes.carregar(resultado.relatorioId()).atual().visitas().getFirst();
        assertThat(visita.status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);
        assertThat(visita.medicoCadastroId()).isNull();
        assertThat(visita.hospital()).isNull();
    }

    @Test void atualizacaoResolveInferenciaAposCadastroSemAlterarFonteOriginal() throws Exception {
        String nomeHospital="Sem cadastro "+UUID.randomUUID();
        service.importarHospital(hospital,"2026-09",hospital(nomeHospital),usuario);
        var resultado=service.importarProducao(hospital,null,"VARIOS","2026-09",producao(),usuario);
        assertThat(revisoes.carregar(resultado.relatorioId()).atual().visitas().getFirst().status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);
        jdbc.update("insert into medico_alias(medico_id,nome,normalizado) values(?,?,?)",ana,nomeHospital,LeitorPlanilhaVisitas.normalizar(nomeHospital));
        long nova=service.atualizar(resultado.relatorioId(),0);
        var visita=revisoes.carregar(nova).atual().visitas().getFirst();
        assertThat(visita.medicoCadastroId()).isEqualTo(ana);
        assertThat(visita.hospital()).isNotNull();
        assertThat(visita.original().getOrDefault("medico","")).isEmpty();
    }

    @Test void atualizacaoHospitalarMantemIdETransformaPendenteEmFaturada() throws Exception {
        var original=hospitalSituacao("Em analise","100","100");
        var arquivoOriginal=service.importarHospital(hospital,"2026-09",original,usuario);
        var producao=service.importarProducao(hospital,null,"VARIOS","2026-09",producao(),usuario);
        var primeira=revisoes.carregar(producao.relatorioId()).atual().visitas().getFirst();
        assertThat(primeira.status()).isEqualTo(ConciliadorVisitas.Status.PENDENTE);
        var resultado=service.importarHospital(hospital,"2026-09","ATUALIZAR",hospitalSituacao("Faturado","100","100"),usuario);
        assertThat(resultado.atualizadas()).isEqualTo(1);
        assertThat(resultado.divergencias()).isZero();
        assertThat(service.visitas(hospital)).hasSize(1);
        long nova=service.atualizar(producao.relatorioId(),0);
        var visita=revisoes.carregar(nova).atual().visitas().getFirst();
        assertThat(visita.hospital().linha()).isEqualTo(primeira.hospital().linha());
        assertThat(visita.status()).isEqualTo(ConciliadorVisitas.Status.PAGA);
        assertThat((byte[])service.arquivo(arquivoOriginal.arquivoId()).get("conteudo")).isEqualTo(original.getBytes());
        assertThat(service.historico(visita.hospital().linha())).singleElement().satisfies(evento->assertThat(evento.get("decisao")).isEqualTo("ATUALIZACAO_HOSPITALAR"));
    }

    @Test void atualizacaoNaoEscolheEntreDuasVisitasEquivalentes() throws Exception {
        service.importarHospital(hospital,"2026-09",hospital(nome(ana),nome(ana)),usuario);
        var repetida=service.visitas(hospital).stream().filter(visita->visita.get("decisao").equals("EM_ANALISE")).findFirst().orElseThrow();
        service.decidir(id(repetida),new BaseHospitalarService.Decisao("VISITA_DISTINTA",null,"Duas visitas legitimas distintas",null),usuario);
        var resultado=service.importarHospital(hospital,"2026-10","ATUALIZAR",hospitalSituacao("Em analise","100","100"),usuario);
        assertThat(resultado.atualizadas()).isZero();
        assertThat(resultado.divergencias()).isEqualTo(1);
    }

    @Test void repeticaoNoArquivoDeAtualizacaoNaoAtualizaOriginal() throws Exception {
        service.importarHospital(hospital,"2026-09",hospitalSituacao("Em analise","100","100"),usuario);
        var resultado=service.importarHospital(hospital,"2026-09","ATUALIZAR",hospital(nome(ana),nome(ana)),usuario);
        assertThat(resultado.atualizadas()).isZero();
        assertThat(resultado.divergencias()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select campos->>'setor' from visita_base_hospitalar where hospital_id=? and decisao='ORIGINAL'",String.class,hospital)).isEqualTo("Em analise");
    }

    @Test void atualizacaoMantemRevisaoManualESeusValores() throws Exception {
        service.importarHospital(hospital,"2026-09",hospitalSituacao("Em analise","100","100"),usuario);
        var producao=service.importarProducao(hospital,ana,"2026-09",producao(),usuario);
        var decisao=revisoes.revisar(producao.relatorioId(),2,new RevisaoVisitaRequest(RevisaoVisitaRequest.Acao.MANTER_PENDENTE,null,"Teste","Aguardando apuracao administrativa",0L,null));
        service.importarHospital(hospital,"2026-09","ATUALIZAR",hospitalSituacao("Faturado","100","100"),usuario);
        long nova=service.atualizar(producao.relatorioId(),decisao.versao());
        assertThat(revisoes.carregar(nova).atual().visitas().getFirst().status()).isEqualTo(ConciliadorVisitas.Status.PENDENTE);
        assertThat(revisoes.carregar(nova).historico()).hasSize(1);
    }

    @Test void diminuicaoDoRepasseNaoApagaRecebimentosESinalizaConferencia() throws Exception {
        service.importarHospital(hospital,"2026-09",hospital(nome(ana)),usuario);
        var producao=service.importarProducao(hospital,ana,"2026-09",producao(),usuario);
        recebimentos.registrar(producao.relatorioId(),2,new RecebimentoService.Dados(new BigDecimal("80"),LocalDate.of(2026,9,2),"Confirmado",0L),usuario);
        service.importarHospital(hospital,"2026-09","ATUALIZAR",hospitalSituacao("Faturado","50","50"),usuario);
        long nova=service.atualizar(producao.relatorioId(),0);
        assertThat(recebimentos.listar(nova).visitas().getFirst().inconsistente()).isTrue();
        var resumo=acompanhamento.consultar(hospital);
        assertThat(resumo.etapas().get(AcompanhamentoBaseHospitalarService.Etapa.EM_CONFERENCIA)).isEqualTo(1);
        assertThat(resumo.faturado()).isEqualByComparingTo("0");
    }

    @Test void cicloCompletoPublicaRecebeExportaEEstornaSemDuplicarVersoes() throws Exception {
        service.importarHospital(hospital,"2026-09",hospitalSituacao("Em analise","100","100"),usuario);
        assertThat(acompanhamento.consultar(hospital).etapas().get(AcompanhamentoBaseHospitalarService.Etapa.AGUARDANDO_PRODUCAO)).isEqualTo(1);
        var producao=service.importarProducao(hospital,null,"VARIOS","2026-09",producao(),usuario);
        assertThat(acompanhamento.consultar(hospital).etapas().get(AcompanhamentoBaseHospitalarService.Etapa.AGUARDANDO_FATURAMENTO)).isEqualTo(1);
        var medicoUser=new UsuarioAutenticado(999,"medico@teste.local","Medico","","MEDICO",ana,true,false,0);
        liberacoes.liberar(producao.relatorioId(),new LiberacaoRelatorioService.Dados(0L,"Publicacao inicial",true),usuario);
        service.importarHospital(hospital,"2026-09","ATUALIZAR",hospitalSituacao("Faturado","100","100"),usuario);
        long nova=service.atualizar(producao.relatorioId(),0);
        assertThat(portal.buscar(producao.relatorioId(),medicoUser).visitas().getFirst().status()).isEqualTo(ConciliadorVisitas.Status.PENDENTE);
        var visita=revisoes.carregar(nova).atual().visitas().getFirst();
        var revisao=revisoes.revisar(nova,2,new RevisaoVisitaRequest(RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA,visita.hospital().linha(),"Teste","Correspondencia conferida pelo administrador",0L,null));
        liberacoes.liberar(nova,new LiberacaoRelatorioService.Dados(revisao.versao(),"Faturamento confirmado",false),usuario);
        assertThat(portal.buscar(nova,medicoUser).visitas().getFirst().status()).isEqualTo(ConciliadorVisitas.Status.PAGA);
        assertThatThrownBy(()->portal.buscar(producao.relatorioId(),medicoUser)).isInstanceOf(br.com.faturamed.shared.RecursoNaoEncontradoException.class);
        recebimentos.registrar(nova,2,new RecebimentoService.Dados(new BigDecimal("40"),LocalDate.of(2026,9,2),"Recebimento parcial",0L),usuario);
        var resumo=acompanhamento.consultar(hospital);
        assertThat(resumo.visitasDistintas()).isEqualTo(1);
        assertThat(resumo.faturado()).isEqualByComparingTo("100");
        assertThat(resumo.recebido()).isEqualByComparingTo("40");
        assertThat(resumo.saldo()).isEqualByComparingTo("60");
        assertThat(resumo.etapas().get(AcompanhamentoBaseHospitalarService.Etapa.RECEBIDO_PARCIALMENTE)).isEqualTo(1);
        var extrato=extratos.buscar(nova,new ExtratoMedicoService.Filtros(null,null,null,null),medicoUser);
        assertThat(extrato.resumo().recebidoAcumulado()).isEqualByComparingTo("40");
        assertThat(pdf.exportar(extrato)).startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        var financeiro=recebimentos.listar(nova).visitas().getFirst();
        recebimentos.estornar(nova,2,financeiro.historico().getFirst().id(),new RecebimentoService.Estorno("Estorno para conferir documentacao",financeiro.versao()),usuario);
        assertThat(extratos.buscar(nova,new ExtratoMedicoService.Filtros(null,null,null,null),medicoUser).resumo().recebidoAcumulado()).isEqualByComparingTo("0");
        assertThat(acompanhamento.consultar(hospital).etapas().get(AcompanhamentoBaseHospitalarService.Etapa.FATURADO_SEM_RECEBIMENTO)).isEqualTo(1);
    }
}
