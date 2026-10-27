package br.com.faturamed.seguranca;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="ADMIN_INITIAL_PASSWORD=TesteIntegracaoSomente123")
@EnabledIfEnvironmentVariable(named="BASE_HOSPITALAR_TEST_DB",matches="true")
@Transactional
class PerfilIntegracaoTest {
 @Autowired UsuarioService users;
 @Autowired ChamadoService chamados;
 @Autowired JdbcTemplate jdbc;
 @Test void funcionarioAbreEAcompanhaESomenteTecnicoResolve() throws Exception {
  var id=jdbc.queryForObject("select id from usuario_acesso where perfil='ADMIN' limit 1",Long.class);
  var tecnico=new UsuarioAutenticado(id,"admin@teste.local","Tecnico","hash","ADMIN",null,true,false,0);
  var conta=users.criar(new UsuarioService.Novo(UUID.randomUUID()+"@teste.local","Funcionario","SenhaTesteSegura12","OPERADOR",null),id);
  var operador=new UsuarioAutenticado(conta.id(),conta.email(),conta.nome(),"hash","OPERADOR",null,true,false,0);
  assertEquals(1L,jdbc.queryForObject("select count(*) from usuario_acesso_auditoria where usuario_id=? and operador_id=? and acao='CRIACAO'",Long.class,conta.id(),id));
  var chamado=chamados.criar("ERRO_BLOQUEANTE","Teste de acessos","Cenario ficticio de permissoes","/teste",List.of(),operador).chamado();
  assertTrue(chamados.listar(null,null,null,null,false,operador).chamados().stream().anyMatch(c->c.id()==chamado.id()));
  assertThrows(AccessDeniedException.class,()->chamados.atualizarStatus(chamado.id(),new ChamadoService.AlteracaoStatus("RESOLVIDO",null,null,null,null,null),operador));
  chamados.atualizarStatus(chamado.id(),new ChamadoService.AlteracaoStatus("EM_ATENDIMENTO",null,null,null,true,null),tecnico);
  chamados.adicionarMensagem(chamado.id(),"Informacoes adicionais",List.of(),operador);
  chamados.atualizarStatus(chamado.id(),new ChamadoService.AlteracaoStatus("RESOLVIDO",null,null,null,false,"Solucao de teste"),tecnico);
  assertEquals("RESOLVIDO",chamados.detalhar(chamado.id(),operador).chamado().status());
  assertEquals("FECHADO",chamados.avaliar(chamado.id(),new ChamadoService.Avaliacao(5,"Validado"),operador).chamado().status());
  var privado=chamados.criar("ERRO_BLOQUEANTE","Chamado tecnico","Somente equipe tecnica","/teste",List.of(),tecnico).chamado();
  assertThrows(AccessDeniedException.class,()->chamados.detalhar(privado.id(),operador));
  assertFalse(chamados.listar(null,null,null,null,false,operador).chamados().stream().anyMatch(c->c.id()==privado.id()));
 }
 @Test void medicoSemVinculoNaoPodeSerCriado() {
  assertThrows(IllegalArgumentException.class,()->users.criar(new UsuarioService.Novo("semvinculo@teste.local","Medico","SenhaTesteSegura12","MEDICO",null)));
 }
}
