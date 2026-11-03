package br.com.faturamed.seguranca;

import br.com.faturamed.conciliacao.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Consolida, para o login da empresa, as publicacoes vigentes dos medicos vinculados a ela. */
@Service
public class PainelEmpresaService {
    public record MedicoResumo(long id,String nome,String crm,String uf) {}
    public record Totais(long visitas,long pagas,long pendentes,long divergentes,BigDecimal faturado,BigDecimal recebido,
            BigDecimal saldo,BigDecimal aguardando,BigDecimal emConferencia) {}
    public record Grupo(String chave,String rotulo,Totais totais) {}
    public record Visita(long relatorioId,String hospital,long medicoId,String medico,String atendimento,LocalDate data,
            String procedimento,String status,String motivo,BigDecimal repasse,BigDecimal recebido,BigDecimal saldo,boolean inconsistente) {}
    public record Publicacao(long relatorioId,String hospital,long medicoId,String medico,long visitas,Instant publicadoEm) {}
    public record Painel(long empresaId,String empresa,List<MedicoResumo> medicos,List<String> hospitais,Totais resumo,
            List<Grupo> porMedico,List<Grupo> porHospital,List<Grupo> porMes,List<Publicacao> publicacoes,List<Visita> visitas) {}
    static final int LIMITE_VISITAS=3000;

    private final JdbcTemplate jdbc;private final ObjectMapper json;private final EmpresaClienteService empresas;
    private final MedicoCadastroService medicos;private final RecebimentoService recebimentos;
    public PainelEmpresaService(JdbcTemplate jdbc,ObjectMapper json,EmpresaClienteService empresas,MedicoCadastroService medicos,RecebimentoService recebimentos) {
        this.jdbc=jdbc;this.json=json;this.empresas=empresas;this.medicos=medicos;this.recebimentos=recebimentos;
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Painel consultar(long empresaId,Long medicoId,LocalDate de,LocalDate ate,String hospital) throws Exception {
        var empresa=empresas.buscar(empresaId);
        Set<Long> vinculados=new LinkedHashSet<>(empresas.medicosDa(empresaId));
        // O filtro vem do navegador: so aceita medicos vinculados a empresa da sessao.
        if(medicoId!=null&&!vinculados.contains(medicoId))throw new IllegalArgumentException("Medico nao pertence a empresa");
        Map<Long,MedicoCadastroService.Medico> cadastros=new HashMap<>();
        medicos.listar().forEach(m->cadastros.put(m.id(),m));
        var lista=vinculados.stream().map(cadastros::get).filter(Objects::nonNull)
                .map(m->new MedicoResumo(m.id(),m.nome(),m.crm(),m.uf())).sorted(Comparator.comparing(MedicoResumo::nome)).toList();

        var linhas=jdbc.queryForList("""
            select l.relatorio_id,l.hospital,l.dados::text as dados,l.criado_em from liberacao_relatorio l
            where l.empresa_id=? and not exists(select 1 from liberacao_relatorio n where n.relatorio_raiz_id=l.relatorio_raiz_id and n.id>l.id)
            order by l.id desc
            """,empresaId);
        Set<String> hospitais=new TreeSet<>();
        List<Publicacao> publicacoes=new ArrayList<>();List<Visita> visitas=new ArrayList<>();
        for(var linha:linhas) {
            long relatorioId=((Number)linha.get("relatorio_id")).longValue();
            String nomeHospital=linha.get("hospital").toString();
            Map<String,List<PortalMedicoController.Visita>> dados=json.readValue(linha.get("dados").toString(),new TypeReference<>(){});
            for(var entrada:dados.entrySet()) {
                long medico=Long.parseLong(entrada.getKey());
                if(!vinculados.contains(medico))continue;
                hospitais.add(nomeHospital);
                if(medicoId!=null&&medico!=medicoId)continue;
                if(hospital!=null&&!hospital.isBlank()&&!hospital.equals(nomeHospital))continue;
                var publicado=new PortalMedicoController.Relatorio(relatorioId,nomeHospital,entrada.getValue());
                var financeiro=FinanceiroPublicado.analisar(publicado,recebimentos.movimentosDoMedico(relatorioId,medico));
                Map<Integer,RecebimentoService.Visita> porLinha=new HashMap<>();financeiro.forEach(f->porLinha.put(f.linha(),f));
                String nomeMedico=cadastros.containsKey(medico)?cadastros.get(medico).nome():entrada.getValue().stream().map(PortalMedicoController.Visita::medico).findFirst().orElse("");
                long contadas=0;
                for(var v:entrada.getValue()) {
                    if(de!=null&&(v.data()==null||v.data().isBefore(de)))continue;
                    if(ate!=null&&(v.data()==null||v.data().isAfter(ate)))continue;
                    var f=porLinha.get(v.linhaMedico());
                    boolean inconsistente=f!=null&&f.inconsistente();
                    visitas.add(new Visita(relatorioId,nomeHospital,medico,nomeMedico,v.atendimento(),v.data(),v.procedimento(),v.status().name(),v.motivo(),
                            v.repasse(),f==null||inconsistente?null:f.recebido(),f==null||inconsistente?null:f.saldo(),inconsistente));
                    contadas++;
                }
                if(contadas>0)publicacoes.add(new Publicacao(relatorioId,nomeHospital,medico,nomeMedico,contadas,instante(linha.get("criado_em"))));
            }
        }
        visitas.sort(Comparator.comparing(Visita::data,Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Visita::medico));
        var porMedico=agrupar(visitas,v->String.valueOf(v.medicoId()),Visita::medico);
        var porHospital=agrupar(visitas,Visita::hospital,Visita::hospital);
        var porMes=agrupar(visitas,v->v.data()==null?"":YearMonth.from(v.data()).toString(),v->v.data()==null?"Sem data":YearMonth.from(v.data()).toString());
        porMes.sort(Comparator.comparing(Grupo::chave));
        return new Painel(empresa.id(),empresa.nome(),lista,List.copyOf(hospitais),totais(visitas),porMedico,porHospital,porMes,
                publicacoes,visitas.size()>LIMITE_VISITAS?visitas.subList(0,LIMITE_VISITAS):visitas);
    }

    static Totais totais(List<Visita> visitas) {
        long pagas=0,pendentes=0,divergentes=0;
        BigDecimal faturado=BigDecimal.ZERO,recebido=BigDecimal.ZERO,saldo=BigDecimal.ZERO,aguardando=BigDecimal.ZERO,conferencia=BigDecimal.ZERO;
        for(var v:visitas) {
            BigDecimal repasse=v.repasse()==null?BigDecimal.ZERO:v.repasse();
            switch(v.status()) {
                case "PAGA" -> { pagas++; faturado=faturado.add(repasse); }
                case "PENDENTE" -> { pendentes++; aguardando=aguardando.add(repasse); }
                default -> { divergentes++; conferencia=conferencia.add(repasse); }
            }
            if(v.recebido()!=null)recebido=recebido.add(v.recebido());
            if(v.saldo()!=null)saldo=saldo.add(v.saldo());
        }
        return new Totais(visitas.size(),pagas,pendentes,divergentes,faturado,recebido,saldo,aguardando,conferencia);
    }
    private static List<Grupo> agrupar(List<Visita> visitas,java.util.function.Function<Visita,String> chave,java.util.function.Function<Visita,String> rotulo) {
        Map<String,List<Visita>> grupos=new LinkedHashMap<>();Map<String,String> rotulos=new HashMap<>();
        for(var v:visitas){grupos.computeIfAbsent(chave.apply(v),k->new ArrayList<>()).add(v);rotulos.putIfAbsent(chave.apply(v),rotulo.apply(v));}
        List<Grupo> result=new ArrayList<>();
        grupos.forEach((k,lista)->result.add(new Grupo(k,rotulos.get(k),totais(lista))));
        result.sort(Comparator.comparing((Grupo g)->g.totais().faturado()).reversed());
        return result;
    }
    private static Instant instante(Object valor) {
        return valor instanceof java.sql.Timestamp t?t.toInstant():valor instanceof OffsetDateTime o?o.toInstant():null;
    }
}
