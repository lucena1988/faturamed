package br.com.faturamed.seguranca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.faturamed.conciliacao.*;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class LiberacaoRelatorioTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);private final RevisaoVisitasService revisoes=mock(RevisaoVisitasService.class);private final MedicoCadastroService medicos=mock(MedicoCadastroService.class);
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();private final LiberacaoRelatorioService service=new LiberacaoRelatorioService(jdbc,revisoes,medicos,json);
    private final UsuarioAutenticado user=new UsuarioAutenticado(1,"admin@test.local","Admin","hash","ADMIN",null,true,false,0);
    private void configurar(ConciliadorVisitas.Status status,boolean identificado) throws Exception {
        var visita=new ConciliadorVisitas.Visita(2,"A",LocalDate.of(2026,1,1),status,"Conferir",Map.of("paciente","SEGREDO"),null,List.of(),new ConciliadorVisitas.CamposRevisados("Ana","Visita","101",BigDecimal.TEN,BigDecimal.TEN,identificado?10L:null));
        var base=new ConciliadorVisitas.Relatorio("H","p","h","",List.of(visita),List.of(),Map.of(),BigDecimal.ZERO);
        when(revisoes.carregar(anyLong())).thenReturn(new RevisaoVisitasService.Estado(base,base,List.of(),3));
        var medico=new MedicoCadastroService.Medico(10,"Ana","123","SP",true,0,List.of());when(medicos.listar()).thenReturn(List.of(medico));when(medicos.identificar(eq(visita),anyList())).thenReturn(identificado?10L:null);
        when(jdbc.queryForObject(contains("with recursive"),eq(Long.class),anyLong())).thenReturn(1L);when(jdbc.queryForList(contains("for update"),eq(1L))).thenReturn(List.of(Map.of("id",1L)));
    }
    @Test void publicaCopiaSanitizadaComIdentidadeFixadaEAtorDaSessao() throws Exception {
        configurar(ConciliadorVisitas.Status.PAGA,true);service.liberar(1,new LiberacaoRelatorioService.Dados(3L,"Conferido",false),user);
        var payload=ArgumentCaptor.forClass(String.class);verify(jdbc).update(contains("insert into liberacao_relatorio"),eq(1L),eq(1L),eq(3L),eq("H"),payload.capture(),eq("Conferido"),eq(1L),eq("Admin (admin@test.local)"));
        assertThat(payload.getValue()).contains("\"10\"","Ana","medicoCadastroId").doesNotContain("SEGREDO","original","candidatos");
    }
    @Test void pendenciasPrecisamDeConfirmacaoExpressa() throws Exception {
        configurar(ConciliadorVisitas.Status.PENDENTE,true);
        assertThatThrownBy(()->service.liberar(1,new LiberacaoRelatorioService.Dados(3L,"",false),user)).isInstanceOf(IllegalArgumentException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));service.liberar(1,new LiberacaoRelatorioService.Dados(3L,"Pendencia conferida",true),user);
    }
    @Test void revisaoDesatualizadaDuplicadaHistoricaOuSemMedicoNaoPublica() throws Exception {
        configurar(ConciliadorVisitas.Status.PAGA,true);
        assertThatThrownBy(()->service.liberar(1,new LiberacaoRelatorioService.Dados(0L,"",true),user)).isInstanceOf(ConflitoRevisaoException.class);
        when(jdbc.queryForList(contains("and versao_revisao=?"),eq(1L),eq(3L))).thenReturn(List.of(Map.of("id",9L)));
        assertThatThrownBy(()->service.liberar(1,new LiberacaoRelatorioService.Dados(3L,"",true),user)).isInstanceOf(ConflitoRevisaoException.class);
        when(jdbc.queryForList(contains("and versao_revisao=?"),eq(1L),eq(3L))).thenReturn(List.of());
        when(jdbc.queryForList(contains("anterior_id=?"),eq(1L))).thenReturn(List.of(Map.of("id",2L)));
        assertThatThrownBy(()->service.liberar(1,new LiberacaoRelatorioService.Dados(3L,"",true),user)).isInstanceOf(ConflitoRevisaoException.class);
        when(jdbc.queryForList(contains("anterior_id=?"),eq(1L))).thenReturn(List.of());configurar(ConciliadorVisitas.Status.PAGA,false);
        assertThatThrownBy(()->service.liberar(1,new LiberacaoRelatorioService.Dados(3L,"",true),user)).isInstanceOf(IllegalArgumentException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void portalLeSomenteSnapshotDoMedicoENaoConsultaRascunho() throws Exception {
        var visita=new PortalMedicoController.Visita(2,"A",null,ConciliadorVisitas.Status.PAGA,"","Ana",10,"Visita","101",BigDecimal.TEN);
        when(jdbc.queryForList(contains("select l.hospital"),eq("10"),eq(1L),eq("10"))).thenReturn(List.of(Map.of("hospital","H","visitas",json.writeValueAsString(List.of(visita)))));
        assertThat(service.buscarDoMedico(1,10).visitas()).containsExactly(visita);verifyNoInteractions(revisoes,medicos);
        assertThatThrownBy(()->service.buscarDoMedico(1,20)).isInstanceOf(RecursoNaoEncontradoException.class);
        verify(jdbc).queryForList(contains("not exists"),eq("10"),eq(1L),eq("10"));
    }
    @Test void novaVersaoEmRevisaoMantemInformacaoDaPublicacaoAnterior() throws Exception {
        configurar(ConciliadorVisitas.Status.PAGA,true);
        when(jdbc.queryForList(contains("from liberacao_relatorio where relatorio_raiz_id=?"),eq(1L))).thenReturn(List.of(Map.of("id",9L,"relatorio_id",1L,"versao_revisao",3L,"responsavel","Admin","observacao","Conferido","criado_em",java.sql.Timestamp.from(Instant.EPOCH))));
        var estado=service.consultar(2);assertThat(estado.situacao()).isEqualTo("EM_REVISAO");assertThat(estado.podeLiberar()).isTrue();assertThat(estado.publicado().relatorio()).isEqualTo(1);
        assertThat(service.consultar(1).situacao()).isEqualTo("LIBERADO");
    }
    @Test void recebimentosAtualizamPosicaoPublicadaSemLerRevisaoEmAndamento(){
        var visita=new PortalMedicoController.Visita(2,"A",LocalDate.of(2026,1,1),ConciliadorVisitas.Status.PAGA,"","Ana",10,"Visita","101",new BigDecimal("100"));
        var publicado=new PortalMedicoController.Relatorio(1,"H",List.of(visita));var movimento=new RecebimentoService.Movimento(1,2,"A",visita.data(),"101",LocalDate.of(2026,3,1),new BigDecimal("40"),null,false);
        var saldo=FinanceiroPublicado.analisar(publicado,List.of(movimento)).getFirst();assertThat(saldo.recebido()).isEqualByComparingTo("40");assertThat(saldo.saldo()).isEqualByComparingTo("60");assertThat(saldo.inconsistente()).isFalse();
        var outro=new RecebimentoService.Movimento(2,99,"OUTRO",visita.data(),"101",movimento.data(),new BigDecimal("999"),null,false);
        assertThat(FinanceiroPublicado.analisar(publicado,List.of(movimento,outro)).getFirst().saldo()).isEqualByComparingTo("60");
    }
    @Test void eventosDeIdentidadeAnteriorIntegralmenteEstornadosNaoBloqueiamNovoVinculoPublicado(){
        var visita=new PortalMedicoController.Visita(2,"A",LocalDate.of(2026,1,1),ConciliadorVisitas.Status.PAGA,"","Ana",10,"Visita","101",new BigDecimal("100"));
        var antigo=new RecebimentoService.Movimento(1,2,"ANTIGO",visita.data(),"101",visita.data(),new BigDecimal("20"),null,true);
        var estorno=new RecebimentoService.Movimento(2,2,"ANTIGO",visita.data(),"101",visita.data(),new BigDecimal("-20"),1L,false);
        var novo=new RecebimentoService.Movimento(3,2,"A",visita.data(),"101",visita.data(),new BigDecimal("40"),null,false);
        var saldo=FinanceiroPublicado.analisar(new PortalMedicoController.Relatorio(1,"H",List.of(visita)),List.of(antigo,estorno,novo)).getFirst();assertThat(saldo.saldo()).isEqualByComparingTo("60");assertThat(saldo.inconsistente()).isFalse();
    }
}
