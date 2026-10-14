package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

class ResumoAtualizacaoTest {
    private LeitorPlanilhaVisitas.Linha p(int linha,String atendimento) {
        return new LeitorPlanilhaVisitas.Linha(linha,Map.of("atendimento",atendimento));
    }
    private LeitorPlanilhaVisitas.Linha h(int linha,String atendimento,String setor) {
        return new LeitorPlanilhaVisitas.Linha(linha,Map.of("atendimento",atendimento,"data consumo","2026-01-01","medico","Ana",
                "cod produto","10102019","produto","Visita","setor",setor,"valor tot","100","regra","0.85","vl. a repassar","85"));
    }
    @Test void contaApenasNovasPagasEDistingueValorAusente() {
        var motor=new ConciliadorVisitas();var producao=List.of(p(2,"A"),p(3,"B"),p(4,"C"));
        var antes=motor.conciliar("H","p","h",producao,List.of(h(2,"A","Faturado"),h(3,"B","Em analise")));
        var depois=motor.conciliar("H","p","h",producao,List.of(h(2,"A","Faturado"),h(3,"B","Faturado")));
        var s=ResumoAtualizacao.comparar(antes,depois);
        assertThat(s.novasPagas()).isEqualTo(1);assertThat(s.valorNovasPagas()).isEqualByComparingTo("85");
        assertThat(s.pendentes()).isEqualTo(1);assertThat(s.pendentesSemValor()).isEqualTo(1);
        assertThat(s.valorPendenteInformado()).isZero();assertThat(s.divergentes()).isZero();
        assertThat(ResumoAtualizacao.comparar(depois,depois).novasPagas()).isZero();
    }
    @Test void reutilizaOriginaisComLinhasFonteEDuplicidadesPreservadas() throws Exception {
        var motor=new ConciliadorVisitas();var r=motor.conciliar("H","p.xlsx","h.xlsx",List.of(p(2,"A"),p(3,"A")),List.of());
        var jdbc=mock(JdbcTemplate.class);var revisoes=mock(RevisaoVisitasService.class);
        when(revisoes.carregar(10L)).thenReturn(new RevisaoVisitasService.Estado(r,r,List.of(),0));
        when(jdbc.queryForMap(anyString(),eq(10L))).thenReturn(Map.of("hash_producao","abc","medico_producao_id",12L,"linhas_producao","{\"2\":7,\"3\":4}"));
        var base=new VersoesConciliacaoService(jdbc,new ObjectMapper(),revisoes,new AtualizadorConciliacao(motor)).producaoBase(10);
        assertThat(base.linhas()).extracting(LeitorPlanilhaVisitas.Linha::numero).containsExactly(7,4);
        assertThat(base.linhas()).extracting(l->l.campo("Atendimento")).containsExactly("A","A");
        assertThat(base.medicoId()).isEqualTo(12L);assertThat(base.hash()).isEqualTo("abc");
    }
}
