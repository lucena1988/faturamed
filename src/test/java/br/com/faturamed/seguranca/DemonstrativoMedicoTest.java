package br.com.faturamed.seguranca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.faturamed.conciliacao.*;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.*;
import org.junit.jupiter.api.Test;

class DemonstrativoMedicoTest {
    static ExtratoMedicoService.Extrato exemplo(int quantidade,boolean longo){
        var visitas=new ArrayList<ExtratoMedicoService.Visita>();
        for(int i=0;i<quantidade;i++)visitas.add(new ExtratoMedicoService.Visita(i+2,"90000"+i,LocalDate.of(2026,1,10),"10102019",longo&&i==0?"X".repeat(9000)+" FINAL_DO_PROCEDIMENTO":"Visita hospitalar - paciente internado","PAGA",new BigDecimal("100.00"),new BigDecimal("100.00"),new BigDecimal("60.00"),new BigDecimal("40.00"),"PARCIAL"));
        visitas.add(new ExtratoMedicoService.Visita(500,"PENDENTE-01",null,null,"Visita aguardando faturamento","PENDENTE",new BigDecimal("80"),null,BigDecimal.ZERO,null,"SEM_FATURAMENTO"));
        visitas.add(new ExtratoMedicoService.Visita(501,"DIVERGENTE-01",null,null,"Visita sem valor definido","DIVERGENTE",null,null,null,null,"CONFERENCIA"));
        var total=BigDecimal.valueOf(quantidade);
        var resumo=new ExtratoMedicoService.Resumo(visitas.size(),total.multiply(new BigDecimal("100")),total.multiply(new BigDecimal("60")),total.multiply(new BigDecimal("40")),1,new BigDecimal("80"),1,BigDecimal.ZERO,1,1,new BigDecimal("70"),new BigDecimal("10"),new BigDecimal("60"));
        var movimentos=List.of(new RecebimentoService.Movimento(10,2,"900000",LocalDate.of(2026,1,10),"10102019",LocalDate.of(2026,2,1),new BigDecimal("70"),null,true,7L,"REF-TESTE"),new RecebimentoService.Movimento(11,2,"900000",LocalDate.of(2026,1,10),"10102019",LocalDate.of(2026,2,2),new BigDecimal("-10"),10L,false,7L,"REF-TESTE"));
        return new ExtratoMedicoService.Extrato(1,"Hospital Teste","Dra. Ana Goncalves",Instant.parse("2026-02-15T15:30:00Z"),new ExtratoMedicoService.Filtros(LocalDate.of(2026,1,1),LocalDate.of(2026,1,31),null,null),resumo,visitas,movimentos);
    }
    @Test void pdfIncluiValoresFiltrosMarcaEAdvertenciaSemTextoInterno() throws Exception {
        byte[] bytes=new ExportadorDemonstrativoPdf().exportar(exemplo(3,false));assertThat(new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try(var pdf=Loader.loadPDF(bytes)){
            String text=new PDFTextStripper().getText(pdf).replaceAll("\\s+"," ");assertThat(text).contains("DEMONSTRATIVO","Dra. Ana Goncalves","Hospital Teste","01/01/2026 a 31/01/2026","300,00","180,00","120,00","Nao informado","Nao confirmado","REF-TESTE","Estorno de #10","Nao e comprovante bancario").doesNotContain("observacoes","responsavel","paciente secreto");
            assertThat(text).contains("Em conferencia: 1 visita - valor informado: Nao informado (1 visita sem valor)");
            Path dir=Path.of("target","demonstrativo-qa");Files.createDirectories(dir);Files.write(dir.resolve("demonstrativo-exemplo.pdf"),bytes);var renderer=new PDFRenderer(pdf);for(int i=0;i<pdf.getNumberOfPages();i++)ImageIO.write(renderer.renderImageWithDPI(i,120),"PNG",dir.resolve("pagina-"+(i+1)+".png").toFile());
        }
    }
    @Test void muitasVisitasETokensLongosMantemRodapesECabecalhosSemEstourarPagina() throws Exception {
        byte[] bytes=new ExportadorDemonstrativoPdf().exportar(exemplo(100,true));
        try(var pdf=Loader.loadPDF(bytes)){
            assertThat(pdf.getNumberOfPages()).isGreaterThan(5);String text=new PDFTextStripper().getText(pdf);assertThat(text).contains("FINAL_DO_PROCEDIMENTO","9000099","Continuacao:");
            var stripper=new PDFTextStripper(){@Override protected void writeString(String value,List<TextPosition> positions) throws IOException{for(var p:positions){assertThat(p.getXDirAdj()).isBetween(39f,556f);assertThat(p.getXDirAdj()+p.getWidthDirAdj()).isLessThan(557f);assertThat(p.getYDirAdj()).isBetween(20f,820f);}super.writeString(value,positions);}};stripper.getText(pdf);
            for(int i=1;i<=pdf.getNumberOfPages();i++){stripper.setStartPage(i);stripper.setEndPage(i);assertThat(stripper.getText(pdf)).contains("Pagina "+i+" de "+pdf.getNumberOfPages(),"Nao e comprovante bancario");}
        }
    }
    @Test void administradorUsaSomenteCopiaPublicadaEMovimentosDoCadastro() throws Exception {
        var liberacoes=mock(LiberacaoRelatorioService.class);var financeiro=mock(RecebimentoService.class);var service=new DemonstrativoMedicoService(liberacoes,financeiro);
        var visita=new PortalMedicoController.Visita(2,"A",LocalDate.of(2026,1,1),ConciliadorVisitas.Status.PAGA,"","Ana",10,"Visita","101",new BigDecimal("100"));
        when(liberacoes.buscarDoMedico(1,10)).thenReturn(new PortalMedicoController.Relatorio(1,"H",List.of(visita)));when(financeiro.movimentosDoMedico(1,10)).thenReturn(List.of(new RecebimentoService.Movimento(1,2,"A",visita.data(),"101",LocalDate.of(2026,2,1),new BigDecimal("60"),null,false),new RecebimentoService.Movimento(2,99,"OUTRO",visita.data(),"101",LocalDate.of(2026,2,1),new BigDecimal("999"),null,false)));
        var e=service.buscar(1,10,new ExtratoMedicoService.Filtros(null,null,LocalDate.of(2026,1,1),LocalDate.of(2026,1,31)));assertThat(e.resumo().recebidoAcumulado()).isEqualByComparingTo("60");assertThat(e.resumo().saldoAtual()).isEqualByComparingTo("40");assertThat(e.movimentos()).isEmpty();verify(financeiro,never()).listar(anyLong());
        when(liberacoes.buscarDoMedico(1,20)).thenThrow(new RecursoNaoEncontradoException("Nao publicado"));assertThatThrownBy(()->service.buscar(1,20,e.filtros())).isInstanceOf(RecursoNaoEncontradoException.class);verify(financeiro,never()).movimentosDoMedico(1,20);
    }
    @Test void datasInvertidasECadastroAusenteRejeitadosAntesDaConsulta(){
        var liberacoes=mock(LiberacaoRelatorioService.class);var financeiro=mock(RecebimentoService.class);var service=new DemonstrativoMedicoService(liberacoes,financeiro);
        assertThatThrownBy(()->service.buscar(1,0,new ExtratoMedicoService.Filtros(null,null,null,null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.buscar(1,10,new ExtratoMedicoService.Filtros(LocalDate.of(2026,2,1),LocalDate.of(2026,1,1),null,null))).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(liberacoes,financeiro);
    }
}
