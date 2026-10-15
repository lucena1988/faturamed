package br.com.faturamed.conciliacao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import br.com.faturamed.seguranca.UsuarioAutenticado;
import br.com.faturamed.shared.RecursoNaoEncontradoException;

@Service
public class ComprovantePagamentoService {
    static final int LIMITE=5*1024*1024;
    public record Metadados(long id,String nome,String tipo,int tamanho,String sha256,String responsavel,Instant criadoEm,Instant retiradoEm,String retiradoPor,String motivoRetirada) {}
    public record Lista(boolean podeAlterar,List<Metadados> comprovantes) {}
    public record Arquivo(String nome,String tipo,byte[] conteudo,String sha256) {}
    public record Retirada(@NotBlank @Size(max=2000) String justificativa) {}
    private final JdbcTemplate jdbc;
    public ComprovantePagamentoService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private void escopo(long relatorio,long pagamento){
        if(jdbc.queryForList("""
            with recursive cadeia as (select id,anterior_id from conciliacao_visitas where id=?
            union all select r.id,r.anterior_id from conciliacao_visitas r join cadeia c on r.id=c.anterior_id)
            select p.id from pagamento_visitas p join cadeia c on p.relatorio_raiz_id=c.id where c.anterior_id is null and p.id=?
            """,relatorio,pagamento).isEmpty())throw new RecursoNaoEncontradoException("Pagamento nao encontrado neste relatorio");
    }
    private boolean historico(long relatorio){return !jdbc.queryForList("select id from conciliacao_visitas where anterior_id=?",relatorio).isEmpty();}
    private void bloquear(long relatorio,long pagamento){
        if(jdbc.queryForList("select id from conciliacao_visitas where id=? for update",relatorio).isEmpty())throw new RecursoNaoEncontradoException("Relatorio nao encontrado");
        escopo(relatorio,pagamento);if(historico(relatorio))throw new ConflitoRevisaoException("Use a versao mais recente para alterar comprovantes");
        jdbc.queryForList("select id from pagamento_visitas where id=? for update",pagamento);
    }
    public Lista listar(long relatorio,long pagamento){escopo(relatorio,pagamento);return new Lista(!historico(relatorio),jdbc.queryForList("select id,nome,tipo,tamanho,sha256,responsavel,criado_em,retirado_em,retirado_por,motivo_retirada from pagamento_comprovante where pagamento_id=? order by id desc",pagamento).stream().map(this::metadados).toList());}
    @Transactional(rollbackFor=Exception.class)
    public Lista anexar(long relatorio,long pagamento,MultipartFile arquivo,UsuarioAutenticado usuario) throws Exception {
        bloquear(relatorio,pagamento);var validado=validar(arquivo);
        if(!jdbc.queryForList("select id from pagamento_comprovante where pagamento_id=? and sha256=? and retirado_em is null",pagamento,validado.sha256()).isEmpty())throw new ConflitoRevisaoException("Este arquivo ja esta anexado ao pagamento");
        var ativos=jdbc.queryForObject("select count(*) from pagamento_comprovante where pagamento_id=? and retirado_em is null",Long.class,pagamento);
        if(ativos!=null&&ativos>=10)throw new IllegalArgumentException("Limite de 10 comprovantes ativos por pagamento");
        jdbc.update("insert into pagamento_comprovante(pagamento_id,nome,tipo,tamanho,sha256,conteudo,usuario_id,responsavel) values(?,?,?,?,?,?,?,?)",pagamento,validado.nome(),validado.tipo(),validado.conteudo().length,validado.sha256(),validado.conteudo(),usuario.id(),autor(usuario));
        return listar(relatorio,pagamento);
    }
    public Arquivo baixar(long relatorio,long pagamento,long comprovante){escopo(relatorio,pagamento);
        var rows=jdbc.queryForList("select nome,tipo,conteudo,sha256 from pagamento_comprovante where id=? and pagamento_id=? and retirado_em is null",comprovante,pagamento);
        if(rows.isEmpty())throw new RecursoNaoEncontradoException("Comprovante nao encontrado ou retirado de uso");var r=rows.getFirst();return new Arquivo(r.get("nome").toString(),r.get("tipo").toString(),(byte[])r.get("conteudo"),r.get("sha256").toString());
    }
    @Transactional
    public Lista retirar(long relatorio,long pagamento,long comprovante,Retirada dados,UsuarioAutenticado usuario){
        if(dados.justificativa()==null||dados.justificativa().isBlank()||dados.justificativa().length()>2000)throw new IllegalArgumentException("Informe justificativa de ate 2000 caracteres");
        bloquear(relatorio,pagamento);
        if(jdbc.update("update pagamento_comprovante set retirado_em=now(),retirado_por=?,motivo_retirada=? where id=? and pagamento_id=? and retirado_em is null",autor(usuario),dados.justificativa().trim(),comprovante,pagamento)==0)throw new ConflitoRevisaoException("Comprovante nao encontrado ou ja retirado de uso");return listar(relatorio,pagamento);
    }
    static Arquivo validar(MultipartFile arquivo) throws Exception {
        if(arquivo==null||arquivo.isEmpty()||arquivo.getSize()>LIMITE)throw new IllegalArgumentException("Envie PDF, PNG ou JPEG de ate 5 MB");
        String nome=Objects.toString(arquivo.getOriginalFilename(),"").replace('\\','/');nome=nome.substring(nome.lastIndexOf('/')+1).trim();
        if(nome.isBlank()||nome.length()>180||nome.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Nome do arquivo invalido");
        byte[] bytes;try(var in=arquivo.getInputStream()){bytes=in.readNBytes(LIMITE+1);}if(bytes.length==0||bytes.length>LIMITE)throw new IllegalArgumentException("Arquivo vazio ou maior que 5 MB");
        String lower=nome.toLowerCase(Locale.ROOT),tipo;
        if(lower.endsWith(".pdf")&&inicia(bytes,"%PDF-".getBytes(StandardCharsets.US_ASCII)))tipo="application/pdf";
        else if(lower.endsWith(".png")&&inicia(bytes,new byte[]{(byte)137,80,78,71,13,10,26,10}))tipo="image/png";
        else if((lower.endsWith(".jpg")||lower.endsWith(".jpeg"))&&inicia(bytes,new byte[]{(byte)255,(byte)216,(byte)255}))tipo="image/jpeg";
        else throw new IllegalArgumentException("Extensao ou assinatura incompativel. Envie PDF, PNG ou JPEG");
        return new Arquivo(nome,tipo,bytes,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
    private static boolean inicia(byte[] bytes,byte[] prefixo){return bytes.length>=prefixo.length&&Arrays.equals(bytes,0,prefixo.length,prefixo,0,prefixo.length);}
    private static String autor(UsuarioAutenticado u){return u.nome()+" ("+u.email()+")";}
    private static Instant instant(Object v){return v==null?null:v instanceof java.sql.Timestamp t?t.toInstant():((OffsetDateTime)v).toInstant();}
    private Metadados metadados(Map<String,Object> r){return new Metadados(((Number)r.get("id")).longValue(),r.get("nome").toString(),r.get("tipo").toString(),((Number)r.get("tamanho")).intValue(),r.get("sha256").toString(),r.get("responsavel").toString(),instant(r.get("criado_em")),instant(r.get("retirado_em")),Objects.toString(r.get("retirado_por"),""),Objects.toString(r.get("motivo_retirada"),""));}
}
