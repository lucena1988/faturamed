package br.com.faturamed.conciliacao;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Conciliacao unica, etapa 4: gera o relatorio do medico a partir da base nova.
 * Para cada medico e competencia, as visitas ainda sem relatorio viram uma producao no formato atual
 * (planilha gerada a partir dos dados originais) e passam pelo mesmo caminho de importacao. Dai em diante
 * revisao, liberacao, portal do medico e recebimentos funcionam como sempre, e a chegada de um novo arquivo do
 * hospital atualiza o relatorio pelo mecanismo de versoes existente.
 */
@Service
public class RelatorioUnicoService {
    public record Resultado(long relatorioId, long arquivoId, int visitas) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final BaseHospitalarService base;
    private final VinculoBaseHospitalar vinculos;

    public RelatorioUnicoService(JdbcTemplate jdbc, ObjectMapper json, BaseHospitalarService base, VinculoBaseHospitalar vinculos) {
        this.jdbc=jdbc; this.json=json; this.base=base; this.vinculos=vinculos;
    }

    /** Medico x competencia com o que ainda pode virar relatorio e o que ja virou. */
    public List<Map<String,Object>> grupos(long hospitalId) {
        return jdbc.queryForList("""
                select v.medico_id,m.nome as medico,coalesce(v.competencia_manual,v.competencia) as competencia,
                  count(*) filter (where v.arquivo_relatorio_id is null and v.decisao='ORIGINAL') as disponiveis,
                  count(*) filter (where v.arquivo_relatorio_id is null and v.decisao='ORIGINAL' and v.situacao='PAGA') as pagas,
                  count(*) filter (where v.arquivo_relatorio_id is null and v.decisao='ORIGINAL' and v.situacao='PENDENTE') as pendentes,
                  count(*) filter (where v.arquivo_relatorio_id is null and v.decisao='ORIGINAL' and v.situacao='DIVERGENTE') as divergentes,
                  count(*) filter (where v.decisao='EM_ANALISE') as em_analise,
                  count(*) filter (where v.arquivo_relatorio_id is not null) as em_relatorio,
                  string_agg(distinct a.relatorio_id::text,',') as relatorios
                from producao_visita v
                left join medico_cadastro m on m.id=v.medico_id
                left join arquivo_base_hospitalar a on a.id=v.arquivo_relatorio_id
                where v.hospital_id=?
                group by v.medico_id,m.nome,coalesce(v.competencia_manual,v.competencia)
                order by 3 desc,2 nulls last
                """,hospitalId);
    }

    @Transactional(rollbackFor=Exception.class)
    public Resultado gerar(long hospitalId,long medicoId,String competencia,Long empresaId,UsuarioAutenticado usuario) throws Exception {
        competencia=BaseHospitalarService.normalizarCompetencia(competencia);
        vinculos.bloquear(hospitalId);
        var visitas=jdbc.queryForList("""
                select id,campos::text as campos from producao_visita
                where hospital_id=? and medico_id=? and coalesce(competencia_manual,competencia)=?
                  and decisao='ORIGINAL' and arquivo_relatorio_id is null order by id
                """,hospitalId,medicoId,competencia);
        if(visitas.isEmpty()) throw new IllegalArgumentException("Nao ha visitas desse medico nessa competencia sem relatorio");
        // O relatorio refaz o vinculo pelo caminho atual; o vinculo da conciliacao unica e liberado antes e volta ao fim.
        for(var visita:visitas) jdbc.update("update producao_visita set hospital_visita_id=null where id=?",visita.get("id"));
        List<Map<String,String>> linhas=new ArrayList<>();
        for(var visita:visitas) linhas.add(json.readValue(visita.get("campos").toString(),new TypeReference<LinkedHashMap<String,String>>(){}));
        String nome="conciliacao-unica-"+competencia+"-medico-"+medicoId+".xlsx";
        var arquivo=new ArquivoEmMemoria(nome,planilha(linhas));
        var resultado=base.importarProducao(hospitalId,medicoId,empresaId,"UNICO",competencia,arquivo,usuario);
        if(resultado.repetido()) throw new IllegalArgumentException("Ja existe um relatorio identico para essas visitas");
        int linha=2;
        for(var visita:visitas) {
            jdbc.update("update producao_visita set arquivo_relatorio_id=?,linha_relatorio=? where id=?",resultado.arquivoId(),linha++,visita.get("id"));
        }
        return new Resultado(resultado.relatorioId(),resultado.arquivoId(),visitas.size());
    }

    /** Planilha no layout da producao: cabecalho com os campos originais e uma linha por visita, na ordem do ID. */
    static byte[] planilha(List<Map<String,String>> linhas) throws IOException {
        LinkedHashSet<String> colunas=new LinkedHashSet<>();
        linhas.forEach(linha->colunas.addAll(linha.keySet()));
        try(var workbook=new XSSFWorkbook();var saida=new ByteArrayOutputStream()) {
            var aba=workbook.createSheet("Producao");
            var cabecalho=aba.createRow(0);
            int c=0;
            for(String coluna:colunas) cabecalho.createCell(c++).setCellValue(coluna);
            int r=1;
            for(var linha:linhas) {
                var row=aba.createRow(r++);
                c=0;
                for(String coluna:colunas) row.createCell(c++).setCellValue(linha.getOrDefault(coluna,""));
            }
            workbook.write(saida);
            return saida.toByteArray();
        }
    }

    private record ArquivoEmMemoria(String nome,byte[] bytes) implements MultipartFile {
        public String getName() { return "arquivo"; }
        public String getOriginalFilename() { return nome; }
        public String getContentType() { return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; }
        public boolean isEmpty() { return bytes.length==0; }
        public long getSize() { return bytes.length; }
        public byte[] getBytes() { return bytes; }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        public void transferTo(File destino) throws IOException { java.nio.file.Files.write(destino.toPath(),bytes); }
    }
}
