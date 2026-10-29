package br.com.faturamed.seguranca;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

class ChamadoPermissoesTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final ChamadoService service=new ChamadoService(jdbc);
    UsuarioAutenticado usuario(String perfil,long id) {
        return new UsuarioAutenticado(id,"teste@example.com","Teste","hash",perfil,null,true,false,0);
    }
    void chamado(String perfil,long solicitante,String status) {
        var item=mock(ChamadoService.ChamadoItem.class);
        when(item.solicitantePerfil()).thenReturn(perfil);
        when(item.solicitanteId()).thenReturn(solicitante);
        when(item.status()).thenReturn(status);
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<ChamadoService.ChamadoItem>>any(),eq(10L))).thenReturn(List.of(item));
    }
    @Test void funcionarioNaoConfiguraSlaNemListaAtendentes() {
        var user=usuario("OPERADOR",3);
        assertThrows(AccessDeniedException.class,()->service.listarAtendentes(user));
        assertThrows(AccessDeniedException.class,()->service.atualizarSla("ERRO",null,user));
        verifyNoInteractions(jdbc);
    }
    @Test void funcionarioNaoAssumeResolveNemRedireciona() {
        chamado("OPERADOR",3,"ABERTO");
        for(var status:List.of("EM_ATENDIMENTO","RESOLVIDO","FECHADO","AGUARDANDO_USUARIO")) {
            assertThrows(AccessDeniedException.class,()->service.atualizarStatus(10,
                new ChamadoService.AlteracaoStatus(status,"DEV","CRITICA",3L,true,"teste"),usuario("OPERADOR",3)));
        }
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void funcionarioNaoEncerraChamadoResolvidoDeOutroSolicitante() {
        chamado("MEDICO",8,"RESOLVIDO");
        assertThrows(AccessDeniedException.class,()->service.atualizarStatus(10,
            new ChamadoService.AlteracaoStatus("FECHADO",null,null,null,false,null),usuario("OPERADOR",3)));
    }
    @Test void funcionarioNaoConsultaAnexosDeChamadoTecnicoPrivado() {
        chamado("DEV",9,"ABERTO");
        assertThrows(AccessDeniedException.class,()->service.baixarAnexo(10,1,usuario("OPERADOR",3)));
        verify(jdbc,never()).queryForList(anyString(),any(Object[].class));
    }
    @Test void medicoNaoConsultaChamadoDeOutroMedico() {
        chamado("MEDICO",8,"ABERTO");
        assertThrows(AccessDeniedException.class,()->service.detalhar(10,usuario("MEDICO",7)));
    }
}
