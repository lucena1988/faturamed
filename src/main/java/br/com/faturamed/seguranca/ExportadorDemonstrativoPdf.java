package br.com.faturamed.seguranca;

import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

@Component
public class ExportadorDemonstrativoPdf {
    private static final Color TEAL=new Color(29,101,122),INK=new Color(35,59,67),PALE=new Color(239,246,248),LINE=new Color(209,224,230);
    private static final DateTimeFormatter DATA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static String data(LocalDate v){return v==null?"Nao informada":DATA.format(v);}
    private static String moeda(BigDecimal v){return v==null?"Nao confirmado":NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(v);}
    private static String periodo(LocalDate de,LocalDate ate){return de==null&&ate==null?"Todas as datas":(de==null?"Inicio aberto":data(de))+" a "+(ate==null?"fim aberto":data(ate));}
    private static String quantidade(long valor){return valor+(valor==1?" visita":" visitas");}
    private static String subtotal(ExtratoMedicoService.Extrato e,String status,BigDecimal valor){
        var visitas=e.visitas().stream().filter(v->v.status().equals(status)).toList();long ausentes=visitas.stream().filter(v->v.valorInformado()==null).count();
        String conhecido=!visitas.isEmpty()&&ausentes==visitas.size()?"Nao informado":moeda(valor);
        return conhecido+(ausentes>0?" ("+quantidade(ausentes)+" sem valor)":"");
    }

    public byte[] exportar(ExtratoMedicoService.Extrato e) throws IOException {
        try(var document=new PDDocument();var output=new ByteArrayOutputStream();var layout=new Layout(document)){
            document.getDocumentInformation().setTitle("Demonstrativo de recebimentos - FaturaMed");
            document.getDocumentInformation().setAuthor("FaturaMed");
            layout.primeiraPagina();
            layout.paragrafo("Medico: "+e.medico(),11,true);
            layout.paragrafo("Hospital: "+e.hospital(),10,false);
            layout.paragrafo("Relatorio #"+e.relatorio()+" - copia liberada para o medico",9,false);
            layout.paragrafo("Posicao gerada em "+DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(e.geradoEm().atZone(ZoneId.of("America/Sao_Paulo")))+" (Brasilia)",9,false);
            layout.paragrafo("Visitas: "+periodo(e.filtros().visitaDe(),e.filtros().visitaAte()),9,false);
            layout.paragrafo("Recebimentos e estornos: "+periodo(e.filtros().recebimentoDe(),e.filtros().recebimentoAte()),9,false);
            var r=e.resumo();layout.totais(new String[]{"Faturado confirmado","Recebido acumulado","Saldo atual confirmado"},new BigDecimal[]{r.faturado(),r.recebidoAcumulado(),r.saldoAtual()});
            layout.paragrafo("Totais conhecidos no filtro ("+quantidade(r.visitas())+"). "+r.financeiroNaoConfirmado()+" com financeiro nao confirmado; "+r.semValor()+" sem valor informado.",9,false);
            layout.paragrafo("Saldo e recebido acumulado consideram todo o historico das visitas selecionadas. O periodo de recebimentos filtra somente os movimentos abaixo, nao o saldo atual.",9,false);
            layout.secao("Pendencias e divergencias");
            layout.paragrafo("Aguardando faturamento: "+quantidade(r.pendentes())+" - valor informado: "+subtotal(e,"PENDENTE",r.aguardandoFaturamento()),10,false);
            layout.paragrafo("Em conferencia: "+quantidade(r.divergentes())+" - valor informado: "+subtotal(e,"DIVERGENTE",r.emConferencia()),10,false);
            layout.paragrafo("Estes subtotais incluem apenas valores conhecidos e nao compoem o saldo confirmado. Valor ausente nao significa zero. Faturado pelo hospital nao comprova recebimento.",9,false);
            layout.secao("Visitas e posicao financeira");
            float[] larguras={64,57,142,64,64,64,60};String[] titulos={"Atendimento / linha","Data da visita","Procedimento / codigo","Faturado","Recebido acumulado","Saldo atual","Situacao"};
            layout.cabecalhoTabela(larguras,titulos);
            for(var v:e.visitas())layout.linha(larguras,titulos,new String[]{v.atendimento()+"\nLinha "+v.linha(),data(v.data()),Objects.toString(v.procedimento(),"Nao informado")+"\nCodigo: "+Objects.toString(v.codigo(),"Nao informado")+"\nRepasse informado: "+(v.valorInformado()==null?"Nao informado":moeda(v.valorInformado())),moeda(v.faturado()),moeda(v.recebido()),moeda(v.saldo()),ExportadorExtratoMedico.conciliacao(v.status())+"\n"+ExportadorExtratoMedico.situacao(v.situacao())});
            if(e.visitas().isEmpty())layout.paragrafo("Nenhuma visita nos filtros selecionados.",10,false);
            layout.secao("Movimentos no periodo");
            layout.paragrafo("Entradas: "+moeda(r.entradasPeriodo())+" | Estornos: "+moeda(r.estornosPeriodo())+" | Liquido: "+moeda(r.liquidoPeriodo()),10,true);
            float[] movimentoLarguras={38,58,64,32,76,70,177};String[] movimentoTitulos={"Evento","Data","Atendimento","Linha","Tipo","Valor","Pagamento / referencia"};
            layout.cabecalhoTabela(movimentoLarguras,movimentoTitulos);
            for(var m:e.movimentos())layout.linha(movimentoLarguras,movimentoTitulos,new String[]{String.valueOf(m.id()),data(m.data()),m.atendimento(),String.valueOf(m.linha()),m.estornoDe()!=null?"Estorno de #"+m.estornoDe():m.estornado()?"Recebimento (estornado)":"Recebimento",moeda(m.valor()),m.pagamentoId()==null?"Lancamento individual":"#"+m.pagamentoId()+" / "+Objects.toString(m.referencia(),"")});
            if(e.movimentos().isEmpty())layout.paragrafo("Nenhum movimento nos filtros selecionados.",10,false);
            layout.finalizar();document.save(output);return output.toByteArray();
        }
    }

    private static final class Layout implements AutoCloseable {
        private static final float M=40,W=515,BOTTOM=62;
        private final PDFont REGULAR=new PDType1Font(Standard14Fonts.FontName.HELVETICA),BOLD=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDDocument document;
        private PDPage page;private PDPageContentStream stream;private float y;
        Layout(PDDocument document){this.document=document;}
        void novaPagina() throws IOException {
            if(stream!=null)stream.close();page=new PDPage(PDRectangle.A4);document.addPage(page);stream=new PDPageContentStream(document,page);y=page.getMediaBox().getHeight()-45;
            texto("FaturaMed",M,y,BOLD,12,TEAL);texto("Demonstrativo de recebimentos",M+180,y,REGULAR,10,TEAL);y-=18;regua(y);y-=20;
        }
        void primeiraPagina() throws IOException {
            novaPagina();
            stream.close();stream=new PDPageContentStream(document,page,PDPageContentStream.AppendMode.OVERWRITE,false);
            try(var image=ExportadorDemonstrativoPdf.class.getResourceAsStream("/static/assets/faturamed-logo.jpeg")){
                if(image==null)throw new IOException("Logo FaturaMed nao encontrada");
                var logo=PDImageXObject.createFromByteArray(document,image.readAllBytes(),"faturamed-logo");
                stream.drawImage(logo,M-10,page.getMediaBox().getHeight()-165,200,200);
            }
            texto("DEMONSTRATIVO",M+230,790,BOLD,17,TEAL);texto("Recebimentos do medico",M+230,770,REGULAR,11,INK);
            y=744;regua(y);y-=22;
        }
        void garantir(float altura) throws IOException {if(y-altura<BOTTOM)novaPagina();}
        void secao(String titulo) throws IOException {garantir(65);y-=12;texto(titulo,M,y,BOLD,12,TEAL);y-=20;}
        void paragrafo(String value,float size,boolean bold) throws IOException {
            var font=bold?BOLD:REGULAR;for(String linha:quebrar(value,W,font,size)){garantir(size+6);texto(linha,M,y,font,size,INK);y-=size+4;}y-=5;
        }
        void totais(String[] labels,BigDecimal[] values) throws IOException {
            garantir(80);y-=5;stream.setNonStrokingColor(PALE);stream.addRect(M,y-66,W,70);stream.fill();
            for(int i=0;i<labels.length;i++){float x=M+12+i*(W/3);texto(labels[i],x,y-14,REGULAR,9,INK);String value=moeda(values[i]);float size=17;while(size>9&&BOLD.getStringWidth(value)/1000*size>W/3-24)size--;texto(value,x,y-41,BOLD,size,TEAL);}y-=82;
        }
        void cabecalhoTabela(float[] widths,String[] labels) throws IOException {garantir(65);desenharCabecalho(widths,labels);}
        private void desenharCabecalho(float[] widths,String[] labels) throws IOException {
            float height=36;stream.setNonStrokingColor(PALE);stream.addRect(M,y-height,W,height);stream.fill();float x=M;
            for(int i=0;i<labels.length;i++){var linhas=quebrar(labels[i],widths[i]-10,BOLD,8);for(int k=0;k<linhas.size();k++)texto(linhas.get(k),x+5,y-12-k*10,BOLD,8,TEAL);x+=widths[i];}y-=height;
        }
        void linha(float[] widths,String[] labels,String[] values) throws IOException {
            java.util.List<java.util.List<String>> cells=new ArrayList<>();int lines=1;
            for(int i=0;i<values.length;i++){var cell=quebrar(values[i],widths[i]-10,REGULAR,8);cells.add(cell);lines=Math.max(lines,cell.size());}
            float altura=lines*11+12;if(altura<=620&&y-altura<BOTTOM){novaPagina();desenharCabecalho(widths,labels);}
            int offset=0;
            while(offset<lines){
                if(y-BOTTOM<32){novaPagina();desenharCabecalho(widths,labels);}
                int available=Math.max(1,(int)((y-BOTTOM-12)/11));int take=Math.min(lines-offset,available);float height=take*11+12,x=M;
                for(int i=0;i<cells.size();i++){var cell=cells.get(i);for(int k=0;k<take&&offset+k<cell.size();k++)texto(cell.get(offset+k),x+5,y-13-k*11,REGULAR,8,INK);x+=widths[i];}
                y-=height;regua(y);offset+=take;if(offset<lines){novaPagina();paragrafo("Continuacao: "+values[0].replace('\n',' '),8,true);desenharCabecalho(widths,labels);}
            }
        }
        private void regua(float at) throws IOException {stream.setStrokingColor(LINE);stream.setLineWidth(.5f);stream.moveTo(M,at);stream.lineTo(M+W,at);stream.stroke();}
        private void texto(String value,float x,float at,PDFont font,float size,Color color) throws IOException {
            stream.beginText();stream.setFont(font,size);stream.setNonStrokingColor(color);stream.newLineAtOffset(x,at);stream.showText(value);stream.endText();
        }
        private static java.util.List<String> quebrar(String value,float width,PDFont font,float size) throws IOException {
            StringBuilder seguro=new StringBuilder();
            for(int cp:Objects.toString(value,"").codePoints().toArray()){
                if(cp=='\n'){seguro.append('\n');continue;}if(Character.isISOControl(cp)||Character.isWhitespace(cp)){seguro.append(' ');continue;}
                String c=new String(Character.toChars(cp));try{font.encode(c);seguro.append(c);}catch(IllegalArgumentException ex){seguro.append('?');}
            }
            java.util.List<String> result=new ArrayList<>();
            for(String paragraph:seguro.toString().split("\n",-1)){
                StringBuilder current=new StringBuilder();for(char c:paragraph.toCharArray()){
                    if(current.length()>0&&font.getStringWidth(current.toString()+c)/1000*size>width){int space=current.lastIndexOf(" ");if(space>0){result.add(current.substring(0,space));String rest=current.substring(space+1);current.setLength(0);current.append(rest);}else{result.add(current.toString());current.setLength(0);}}
                    if(current.length()>0&&font.getStringWidth(current.toString()+c)/1000*size>width){result.add(current.toString());current.setLength(0);}current.append(c);
                }result.add(current.toString().strip());
            }return result;
        }
        void finalizar() throws IOException {
            if(stream!=null){stream.close();stream=null;}
            int total=document.getNumberOfPages();for(int i=0;i<total;i++)try(var footer=new PDPageContentStream(document,document.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true)){
                footer.beginText();footer.setFont(REGULAR,8);footer.setNonStrokingColor(INK);footer.newLineAtOffset(M,36);footer.showText("FaturaMed - Demonstrativo de acompanhamento. Nao e comprovante bancario.");footer.endText();
                footer.beginText();footer.setFont(REGULAR,8);footer.newLineAtOffset(M,24);footer.showText("Pagina "+(i+1)+" de "+total);footer.endText();
            }
        }
        @Override public void close() throws IOException {if(stream!=null)stream.close();}
    }
}
