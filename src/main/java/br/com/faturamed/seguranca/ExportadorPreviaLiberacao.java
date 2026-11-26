package br.com.faturamed.seguranca;

import br.com.faturamed.conciliacao.ConciliadorVisitas;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Previa em Excel do que sera liberado ao medico: resumo, uma aba por medico com as visitas e uma aba com o que ficaria
 * de fora. Para conferir antes do ok da liberacao. Cada aba do medico tem cabecalho com medico, hospital, competencias e
 * totais, colunas de competencia (exame, producao e faturada pelo hospital), origem de cada linha, status como
 * "PAGO 09/2026", repasse 0,00 para o que nao foi faturado e filtro nos cabecalhos. Alem das visitas do relatorio, traz os
 * exames que o hospital faturou no mesmo arquivo e ficaram fora dele (outros meses de exame ou sem producao), para o total
 * fechar com o painel "Faturamento do hospital" da tela do relatorio.
 */
@Component
public class ExportadorPreviaLiberacao {
    public static final String ORIGEM_RELATORIO="Neste relatorio";
    public static final String ORIGEM_SEM_PRODUCAO="Mesmo faturamento - sem producao";
    public static final String ORIGEM_OUTRO_RELATORIO="Mesmo faturamento - com producao em outro relatorio";
    private static final String[] COLUNAS_MEDICO={"Atendimento","Data","Paciente","Convenio","Codigo do procedimento","Procedimento","Comp. exame","Comp. producao","Comp. faturada (hospital)","Origem","Status","Repasse (R$)","Observacao"};
    private static final int[] LARGURAS_MEDICO={26,13,32,22,24,48,13,16,16,34,18,14,70};
    private static final String[] COLUNAS_FORA={"Atendimento","Data","Paciente","Convenio","Codigo do procedimento","Procedimento","Comp. exame","Situacao","Motivo"};
    private static final int[] LARGURAS_FORA={16,13,32,22,24,48,13,14,70};

    public byte[] exportar(LiberacaoRelatorioService.Previa previa) throws Exception {
        try(var workbook=new XSSFWorkbook();var saida=new ByteArrayOutputStream()) {
            CellStyle moeda=workbook.createCellStyle();moeda.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            CellStyle data=workbook.createCellStyle();data.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));
            Font negrito=workbook.createFont();negrito.setBold(true);
            CellStyle titulo=workbook.createCellStyle();titulo.setFont(negrito);titulo.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
            titulo.setFillPattern(FillPatternType.SOLID_FOREGROUND);titulo.setWrapText(true);
            CellStyle destaque=workbook.createCellStyle();destaque.setFont(negrito);destaque.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());destaque.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle rotulo=workbook.createCellStyle();rotulo.setFont(negrito);
            CellStyle totalMoeda=workbook.createCellStyle();totalMoeda.cloneStyleFrom(moeda);totalMoeda.setFont(negrito);
            var estilos=new Estilos(moeda,data,titulo,rotulo,totalMoeda);

            String competencia=previa.competenciasProducao().stream().map(ExportadorPreviaLiberacao::mesAno).collect(Collectors.joining(", "));
            Set<String> faturadas=new TreeSet<>();
            previa.extras().values().forEach(e->{ if(e.compFaturada()!=null && !e.compFaturada().isBlank()) faturadas.add(e.compFaturada()); });
            previa.outrosExames().values().forEach(lista->lista.forEach(o->{ if(!o.compFaturada().isBlank()) faturadas.add(o.compFaturada()); }));
            String faturamento=faturadas.isEmpty()?"-":faturadas.stream().map(ExportadorPreviaLiberacao::mesAno).collect(Collectors.joining(", "));

            Sheet resumo=workbook.createSheet("Resumo");
            var geradaEm=LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            int linha=0;
            Row aviso=resumo.createRow(linha++);aviso.createCell(0).setCellValue("PREVIA - conferencia interna. Nada foi enviado ao medico.");aviso.getCell(0).setCellStyle(destaque);
            String situacao="LIBERADO".equals(previa.situacao())?"Liberado ao medico (esta revisao ja foi publicada)":"Em revisao (ainda nao liberado)";
            long noPortal=previa.medicos().stream().mapToLong(m->m.visitas().size()).sum();
            long outrosQtd=previa.outrosExames().values().stream().mapToLong(List::size).sum();
            String medicosTexto=previa.medicos().isEmpty()?"-":previa.medicos().stream().map(LiberacaoRelatorioService.PreviaMedico::nome).collect(Collectors.joining(", "));
            Object[][] itens={{"Hospital",previa.hospital()},{"Medico(s)",medicosTexto},{"Competencia da producao",competencia.isEmpty()?"-":competencia},
                    {"Competencia faturada pelo hospital",faturamento},{"Relatorio",previa.relatorio()},{"Revisao",previa.versao()},{"Situacao atual",situacao},{"Gerada em",geradaEm},
                    {"Visitas no relatorio (sem excluidas)",previa.visitas()},{"Visitas que os medicos passam a ver no portal",noPortal},
                    {"Exames do mesmo faturamento fora deste relatorio (incluidos nas abas dos medicos)",outrosQtd},
                    {"Exames do mesmo faturamento de outros medicos ou sem medico cadastrado (nao incluidos)",previa.deOutrosMedicos()},
                    {"Repasse desses exames de outros medicos (R$)",previa.repasseDeOutrosMedicos()},
                    {"Visitas sem medico cadastrado (nao aparecem no portal)",previa.semMedico().size()},{"Linhas excluidas por duplicidade (nao vao ao medico)",previa.excluidas()}};
            for(Object[] item:itens){Row row=resumo.createRow(linha++);Cell nome=row.createCell(0);nome.setCellValue(item[0].toString());nome.setCellStyle(rotulo);preencher(row.createCell(1),item[1],moeda);}
            linha++;
            Row cab=resumo.createRow(linha++);int c=0;
            for(String texto:new String[]{"Medico","Visitas","Pagas","Pendentes","Divergentes","Repasse das pagas neste relatorio (R$)","Outros exames do faturamento","Repasse dos outros exames (R$)","Total faturado pelo hospital (R$)"}){Cell cell=cab.createCell(c++);cell.setCellValue(texto);cell.setCellStyle(titulo);}
            BigDecimal repasseGeral=BigDecimal.ZERO,outrosGeral=BigDecimal.ZERO;
            for(var medico:previa.medicos()) {
                var outros=previa.outrosExames().getOrDefault(medico.id(),List.of());
                BigDecimal repasse=repasseDasPagas(medico.visitas()),repasseOutros=somaOutros(outros);
                repasseGeral=repasseGeral.add(repasse);outrosGeral=outrosGeral.add(repasseOutros);
                Row row=resumo.createRow(linha++);
                row.createCell(0).setCellValue(medico.nome());row.createCell(1).setCellValue(medico.visitas().size());
                row.createCell(2).setCellValue(contar(medico.visitas(),ConciliadorVisitas.Status.PAGA));row.createCell(3).setCellValue(contar(medico.visitas(),ConciliadorVisitas.Status.PENDENTE));
                row.createCell(4).setCellValue(contar(medico.visitas(),ConciliadorVisitas.Status.DIVERGENTE));
                dinheiro(row.createCell(5),repasse,moeda);row.createCell(6).setCellValue(outros.size());dinheiro(row.createCell(7),repasseOutros,moeda);dinheiro(row.createCell(8),repasse.add(repasseOutros),moeda);
            }
            Row soma=resumo.createRow(linha);Cell somaNome=soma.createCell(0);somaNome.setCellValue("Total");somaNome.setCellStyle(rotulo);
            Cell somaVisitas=soma.createCell(1);somaVisitas.setCellValue(noPortal);somaVisitas.setCellStyle(rotulo);
            dinheiro(soma.createCell(5),repasseGeral,totalMoeda);Cell somaOutrosQtd=soma.createCell(6);somaOutrosQtd.setCellValue(outrosQtd);somaOutrosQtd.setCellStyle(rotulo);
            dinheiro(soma.createCell(7),outrosGeral,totalMoeda);dinheiro(soma.createCell(8),repasseGeral.add(outrosGeral),totalMoeda);
            resumo.setColumnWidth(0,80*256);resumo.setColumnWidth(1,40*256);for(int i=2;i<=4;i++)resumo.setColumnWidth(i,14*256);for(int i=5;i<=8;i++)resumo.setColumnWidth(i,26*256);

            Set<String> nomes=new HashSet<>(List.of("resumo","fora da liberacao"));
            for(var medico:previa.medicos()) abaDoMedico(workbook,nomeUnico(medico.nome(),nomes),medico,previa,competencia,estilos);
            if(!previa.semMedico().isEmpty()) abaForaDaLiberacao(workbook,previa,estilos);
            workbook.write(saida);return saida.toByteArray();
        }
    }

    private record Estilos(CellStyle moeda,CellStyle data,CellStyle titulo,CellStyle rotulo,CellStyle totalMoeda) {}

    /** Linha de uma aba de medico: visita do relatorio ou exame do mesmo faturamento que ficou fora dele. */
    record LinhaMedico(int grupo,long ordem,String atendimento,LocalDate data,String paciente,String convenio,String codigo,String procedimento,
            String compProducao,String compFaturada,String origem,String status,BigDecimal repasse,String observacao) {}

    private void abaDoMedico(Workbook workbook,String nomeAba,LiberacaoRelatorioService.PreviaMedico medico,LiberacaoRelatorioService.Previa previa,String competencia,Estilos e) {
        Sheet aba=workbook.createSheet(nomeAba);
        var linhas=linhasDoMedico(medico,previa,competencia);
        var outros=previa.outrosExames().getOrDefault(medico.id(),List.of());
        BigDecimal noRelatorio=repasseDasPagas(medico.visitas()),foraDoRelatorio=somaOutros(outros);
        Set<String> faturadas=new TreeSet<>();
        linhas.forEach(l->{ if(l.compFaturada()!=null && !l.compFaturada().isBlank()) faturadas.add(l.compFaturada()); });
        String faturamento=faturadas.isEmpty()?"-":faturadas.stream().map(ExportadorPreviaLiberacao::mesAno).collect(Collectors.joining(", "));
        // Cabecalho do relatorio (fora do filtro): medico, hospital, competencias e totais que fecham com a tela do relatorio.
        Object[][] topo={{"Medico",medico.nome()},{"Hospital",previa.hospital()},{"Competencia da producao",competencia.isEmpty()?"-":competencia},{"Competencia faturada pelo hospital",faturamento},
                {"Repasse das pagas neste relatorio (R$)",noRelatorio},{"Repasse de exames do mesmo faturamento fora deste relatorio (R$)",foraDoRelatorio},{"Total faturado pelo hospital para este medico (R$)",noRelatorio.add(foraDoRelatorio)}};
        for(int i=0;i<topo.length;i++) {
            Row row=aba.createRow(i);Cell nome=row.createCell(0);nome.setCellValue(topo[i][0].toString());nome.setCellStyle(e.rotulo());
            Cell valor=row.createCell(1);
            if(topo[i][1] instanceof BigDecimal b){valor.setCellValue(b.doubleValue());valor.setCellStyle(e.totalMoeda());} else valor.setCellValue(String.valueOf(topo[i][1]));
        }
        int cabecalhoLinha=topo.length+1;
        Row cab=aba.createRow(cabecalhoLinha);
        for(int i=0;i<COLUNAS_MEDICO.length;i++){Cell cell=cab.createCell(i);cell.setCellValue(COLUNAS_MEDICO[i]);cell.setCellStyle(e.titulo());}
        int r=cabecalhoLinha+1;
        for(var l:linhas) {
            Row row=aba.createRow(r++);
            row.createCell(0).setCellValue(texto(l.atendimento()));
            Cell d=row.createCell(1);if(l.data()!=null){d.setCellValue(l.data());d.setCellStyle(e.data());}
            row.createCell(2).setCellValue(texto(l.paciente()));row.createCell(3).setCellValue(texto(l.convenio()));
            row.createCell(4).setCellValue(texto(l.codigo()));row.createCell(5).setCellValue(texto(l.procedimento()));
            row.createCell(6).setCellValue(l.data()==null?"":mesAnoDaData(l.data()));
            row.createCell(7).setCellValue(texto(l.compProducao()));
            row.createCell(8).setCellValue(l.compFaturada()==null||l.compFaturada().isBlank()?"":mesAno(l.compFaturada()));
            row.createCell(9).setCellValue(l.origem());row.createCell(10).setCellValue(l.status());
            Cell valor=row.createCell(11);valor.setCellValue(l.repasse().doubleValue());valor.setCellStyle(e.moeda());
            row.createCell(12).setCellValue(texto(l.observacao()));
        }
        aba.createFreezePane(0,cabecalhoLinha+1);
        aba.setAutoFilter(new CellRangeAddress(cabecalhoLinha,Math.max(cabecalhoLinha,r-1),0,COLUNAS_MEDICO.length-1));
        for(int i=0;i<LARGURAS_MEDICO.length;i++)aba.setColumnWidth(i,LARGURAS_MEDICO[i]*256);
        aba.setColumnWidth(0,Math.max(LARGURAS_MEDICO[0],26)*256);
    }

    /**
     * Linhas da aba: visitas do relatorio e exames do mesmo faturamento fora dele. Faturadas primeiro (as do relatorio, depois as
     * de outros meses ou sem producao), depois divergentes e pendentes, que ficam com 0,00. Dentro de cada grupo, a ordem natural.
     */
    static List<LinhaMedico> linhasDoMedico(LiberacaoRelatorioService.PreviaMedico medico,LiberacaoRelatorioService.Previa previa,String competencia) {
        List<LinhaMedico> linhas=new ArrayList<>();
        for(var v:medico.visitas()) {
            var extra=previa.extras().get(v.linhaMedico());
            String faturada=extra==null?"":extra.compFaturada();
            int grupo=v.status()==ConciliadorVisitas.Status.PAGA && valorDoRepasse(v).signum()>0?0:v.status()==ConciliadorVisitas.Status.DIVERGENTE?2:v.status()==ConciliadorVisitas.Status.PENDENTE?3:4;
            linhas.add(new LinhaMedico(grupo,v.linhaMedico(),texto(v.atendimento()),v.data(),extra==null?"":texto(extra.paciente()),extra==null?"":texto(extra.convenio()),
                    texto(v.codigoProcedimento()),texto(v.procedimento()),competencia,faturada,ORIGEM_RELATORIO,statusTexto(v.status(),faturada),valorDoRepasse(v),texto(v.motivo())));
        }
        for(var o:previa.outrosExames().getOrDefault(medico.id(),List.of())) {
            String mes=o.data()==null?"":mesAnoDaData(o.data());
            String obs="Faturado pelo hospital em "+mesAno(o.compFaturada())+(mes.isEmpty()?"":" (exame de "+mes+")")+"; "
                    +(o.comProducao()?"a producao dele esta em outro relatorio.":"ainda sem producao importada neste relatorio.");
            linhas.add(new LinhaMedico(1,o.visitaId(),o.atendimento(),o.data(),o.paciente(),o.convenio(),o.codigo(),o.procedimento(),
                    o.comProducao()?"Outro relatorio":"Sem producao",o.compFaturada(),o.comProducao()?ORIGEM_OUTRO_RELATORIO:ORIGEM_SEM_PRODUCAO,
                    statusTexto(ConciliadorVisitas.Status.PAGA,o.compFaturada()),o.repasse(),obs));
        }
        linhas.sort(Comparator.comparingInt(LinhaMedico::grupo).thenComparing(l->l.data()==null?LocalDate.MAX:l.data()).thenComparingLong(LinhaMedico::ordem));
        return linhas;
    }

    private void abaForaDaLiberacao(Workbook workbook,LiberacaoRelatorioService.Previa previa,Estilos e) {
        Sheet fora=workbook.createSheet("Fora da liberacao");
        Row cab=fora.createRow(0);
        for(int i=0;i<COLUNAS_FORA.length;i++){Cell cell=cab.createCell(i);cell.setCellValue(COLUNAS_FORA[i]);cell.setCellStyle(e.titulo());}
        int r=1;
        for(var v:previa.semMedico()) {
            Row row=fora.createRow(r++);row.createCell(0).setCellValue(texto(v.atendimento()));
            Cell d=row.createCell(1);if(v.data()!=null){d.setCellValue(v.data());d.setCellStyle(e.data());}
            row.createCell(2).setCellValue(texto(v.paciente()));row.createCell(3).setCellValue(texto(v.convenio()));
            row.createCell(4).setCellValue(texto(v.codigo()));row.createCell(5).setCellValue(texto(v.procedimento()));
            row.createCell(6).setCellValue(v.data()==null?"":mesAnoDaData(v.data()));
            row.createCell(7).setCellValue(statusTexto(v.status(),v.compFaturada()==null?"":v.compFaturada()));row.createCell(8).setCellValue(texto(v.motivo()));
        }
        fora.createFreezePane(0,1);fora.setAutoFilter(new CellRangeAddress(0,r-1,0,COLUNAS_FORA.length-1));
        for(int i=0;i<LARGURAS_FORA.length;i++)fora.setColumnWidth(i,LARGURAS_FORA[i]*256);
    }

    static BigDecimal valorDoRepasse(PortalMedicoController.Visita v) {
        return v.status()==ConciliadorVisitas.Status.PAGA && v.repasse()!=null ? v.repasse() : BigDecimal.ZERO;
    }
    /** "PAGO 09/2026" (com a competencia faturada pelo hospital), "PENDENTE" ou "DIVERGENTE". */
    static String statusTexto(ConciliadorVisitas.Status status,String faturada) {
        return switch(status) {
            case PAGA -> faturada==null || faturada.isBlank() ? "PAGO" : "PAGO "+mesAno(faturada);
            case PENDENTE -> "PENDENTE";
            case DIVERGENTE -> "DIVERGENTE";
            default -> "EXCLUIDA";
        };
    }
    /** 2026-09 vira 09/2026. */
    static String mesAno(String competencia) {
        if(competencia==null || competencia.length()!=7 || competencia.charAt(4)!='-') return competencia==null?"":competencia;
        return competencia.substring(5)+"/"+competencia.substring(0,4);
    }
    static String mesAnoDaData(LocalDate data) { return String.format("%02d/%04d",data.getMonthValue(),data.getYear()); }
    private static String texto(String valor) { return valor==null?"":valor; }
    private static void preencher(Cell cell,Object valor,CellStyle moeda) {
        if(valor instanceof BigDecimal b){dinheiro(cell,b,moeda);}
        else if(valor instanceof Number n)cell.setCellValue(n.doubleValue());else cell.setCellValue(String.valueOf(valor));
    }
    private static void dinheiro(Cell cell,BigDecimal valor,CellStyle estilo) { cell.setCellValue(valor.doubleValue());cell.setCellStyle(estilo); }
    private static long contar(List<PortalMedicoController.Visita> visitas,ConciliadorVisitas.Status status) { return visitas.stream().filter(v->v.status()==status).count(); }
    private static BigDecimal somaOutros(List<LiberacaoRelatorioService.PreviaOutroExame> outros) { return outros.stream().map(LiberacaoRelatorioService.PreviaOutroExame::repasse).reduce(BigDecimal.ZERO,BigDecimal::add); }
    /** Repasse so das visitas pagas, como o resumo do relatorio; sem repasse conhecido nao entra na soma. */
    static BigDecimal repasseDasPagas(List<PortalMedicoController.Visita> visitas) {
        return visitas.stream().map(ExportadorPreviaLiberacao::valorDoRepasse).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    /** Nome de aba valido (31 caracteres, sem caracteres proibidos) e unico no arquivo. */
    private static String nomeUnico(String desejado,Set<String> usados) {
        String base=WorkbookUtil.createSafeSheetName(desejado==null||desejado.isBlank()?"Medico":desejado);
        String nome=base;int n=2;
        while(!usados.add(nome.toLowerCase(Locale.ROOT))) {
            String sufixo=" ("+n+++")";nome=(base.length()+sufixo.length()>31?base.substring(0,31-sufixo.length()):base)+sufixo;
        }
        return nome;
    }
}
