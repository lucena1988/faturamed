package br.com.faturamed.seguranca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class NotificacaoTest {
    private final NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
    private final NotificacaoService service=new NotificacaoService(jdbc);
    private final UsuarioAutenticado medico=new UsuarioAutenticado(7,"m@test.local","Ana","hash","MEDICO",10L,true,false,0);
    private final UsuarioAutenticado admin=new UsuarioAutenticado(8,"a@test.local","Admin","hash","ADMIN",null,true,false,0);
    @Test void leituraUsaUsuarioPerfilECadastroDaSessao(){
        when(jdbc.update(anyString(),anyMap())).thenReturn(1);
        service.ler(medico,"LIBERACAO",9);
        var sql=ArgumentCaptor.forClass(String.class);var args=ArgumentCaptor.forClass(Map.class);
        verify(jdbc).update(sql.capture(),args.capture());
        assertThat(args.getValue()).containsEntry("usuario",7L).containsEntry("perfil","MEDICO").containsEntry("medico",10L).containsEntry("escopo","MEDICO:10").containsEntry("evento",9L);
        assertThat(sql.getValue()).contains("s.medico_id=:medico","a.dados -> cast(:medico as text)","on conflict","lido_em=leitura_notificacao.lido_em");
    }
    @Test void eventoNaoAutorizadoRetorna404(){
        when(jdbc.update(anyString(),anyMap())).thenReturn(0);
        assertThatThrownBy(()->service.ler(medico,"RESPOSTA",99)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
    @Test void invalidoNaoConsultaNemGrava(){
        assertThatThrownBy(()->service.ler(medico,"OUTRO",1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.ler(medico,"PEDIDO",0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.listar(medico,false,-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.listar(medico,false,100001)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }
    @Test void administradorTemEscopoIndividualSemMedico(){
        when(jdbc.update(anyString(),anyMap())).thenReturn(1);service.ler(admin,"PEDIDO",3);
        verify(jdbc).update(contains(":perfil='ADMIN'"),argThat((Map<String,?> p)->p.get("escopo").equals("ADMIN:0")&&p.get("usuario").equals(8L)&&p.get("medico").equals(0L)));
    }
    @Test void paginacaoContagemEFiltroUsamMesmoEscopo(){
        when(jdbc.queryForObject(anyString(),anyMap(),eq(Long.class))).thenReturn(22L);
        var caixa=service.listar(medico,true,2);assertThat(caixa.naoLidas()).isEqualTo(22);assertThat(caixa.total()).isEqualTo(22);assertThat(caixa.tamanho()).isEqualTo(20);assertThat(caixa.pagina()).isEqualTo(2);
        verify(jdbc).query(contains("where lido_em is null order by criado_em desc,tipo,evento desc limit 20 offset :offset"),argThat((Map<String,?> p)->p.get("offset").equals(40)&&p.get("usuario").equals(7L)),any(org.springframework.jdbc.core.RowMapper.class));
    }
}
