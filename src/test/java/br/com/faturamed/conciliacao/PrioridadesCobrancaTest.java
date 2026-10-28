package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class PrioridadesCobrancaTest {
    private final LocalDate hoje=LocalDate.of(2026,10,14);
    private final PrioridadesCobranca prioridades=new PrioridadesCobranca();
    private FilaCobranca.Item item(int linha,BigDecimal valor) {
        return new FilaCobranca.Item(linha,"ID"+linha,"A"+linha,LocalDate.of(2026,1,1),286L,"id:1","Ana","123/SP","10102019","Visita",valor);
    }
    private FilaCobranca.Resultado fila() {
        var visitas=List.of(item(2,null),item(3,new BigDecimal("85")),item(4,BigDecimal.ZERO),item(5,new BigDecimal("100")),item(6,null));
        return new FilaCobranca.Resultado("H",hoje,5,List.of(new FilaCobranca.Grupo("id:1","Ana","123/SP",5)),visitas,new BigDecimal("185"),2,0,0);
    }
    private AcompanhamentoCobrancaService.Resumo contato(int linha,ContatoCobrancaRequest.Situacao situacao,LocalDate proximo) {
        return new AcompanhamentoCobrancaService.Resumo(linha,"A"+linha,"Ana","PENDENTE",true,
                new AcompanhamentoCobrancaService.Contato(linha,situacao,situacao==ContatoCobrancaRequest.Situacao.NAO_COBRADA?null:hoje.minusDays(20),"","",proximo,"Usuario",Instant.EPOCH));
    }
    private List<AcompanhamentoCobrancaService.Resumo> contatos() {
        return List.of(contato(3,ContatoCobrancaRequest.Situacao.AGUARDANDO_RETORNO,hoje),contato(4,ContatoCobrancaRequest.Situacao.ENVIADA,hoje.minusDays(10)),
                contato(5,ContatoCobrancaRequest.Situacao.NAO_COBRADA,hoje),contato(6,ContatoCobrancaRequest.Situacao.ENVIADA,hoje.minusDays(3)));
    }
    @Test void contadoresDeDataEEstadoPodemSeSobrepor() {
        var result=prioridades.aplicar(fila(),contatos(),PrioridadesCobranca.Filtro.TODAS);
        assertThat(result.prioridades()).containsEntry(PrioridadesCobranca.Filtro.TODAS,5L).containsEntry(PrioridadesCobranca.Filtro.NAO_COBRADAS,2L)
                .containsEntry(PrioridadesCobranca.Filtro.HOJE,2L).containsEntry(PrioridadesCobranca.Filtro.ATRASADAS,2L).containsEntry(PrioridadesCobranca.Filtro.AGUARDANDO_RETORNO,1L);
        assertThat(result.visitas()).extracting(FilaCobranca.Item::linha).containsExactly(4,6,3,5,2);
    }
    @Test void filtraPorAgendaNaoPelaIdadeDaVisitaEPreservaValorZero() {
        var atrasadas=prioridades.aplicar(fila(),contatos(),PrioridadesCobranca.Filtro.ATRASADAS);
        assertThat(atrasadas.visitas()).extracting(FilaCobranca.Item::linha).containsExactly(4,6);
        assertThat(atrasadas.repasseInformado()).isZero();assertThat(atrasadas.semValor()).isEqualTo(1);
        var nao=prioridades.aplicar(fila(),contatos(),PrioridadesCobranca.Filtro.NAO_COBRADAS);
        assertThat(nao.visitas()).extracting(FilaCobranca.Item::linha).containsExactly(5,2);
        var hoje=prioridades.aplicar(fila(),contatos(),PrioridadesCobranca.Filtro.HOJE);
        assertThat(hoje.repasseInformado()).isEqualByComparingTo("185");assertThat(hoje.semValor()).isZero();
    }
    @Test void contadoresRespeitamEscopoRecebidoENaoIncluemOutrosContatos() {
        var base=fila();var escopo=new FilaCobranca.Resultado(base.hospital(),hoje,5,base.medicos(),List.of(base.visitas().get(1)),new BigDecimal("85"),0,0,0);
        var result=prioridades.aplicar(escopo,contatos(),PrioridadesCobranca.Filtro.ATRASADAS);
        assertThat(result.visitas()).isEmpty();assertThat(result.prioridades()).containsEntry(PrioridadesCobranca.Filtro.HOJE,1L).containsEntry(PrioridadesCobranca.Filtro.ATRASADAS,0L);
    }
    @Test void exportacaoSegueSelecaoEOrdemSemExportarContatoInterno() throws Exception {
        var atrasadas=prioridades.aplicar(fila(),contatos(),PrioridadesCobranca.Filtro.ATRASADAS);
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new ExportadorRelatorioVisitas().exportarCobranca(10,atrasadas)))) {
            var sheet=book.getSheet("Cobranca");assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(4);assertThat(sheet.getRow(2).getCell(0).getNumericCellValue()).isEqualTo(6);
            for(var row:sheet)for(var cell:row)assertThat(cell.toString()).doesNotContain("Usuario");
        }
    }
    @Test void contatoFuturoNaoEHojeNemAtrasadoEDataAusenteNaoCriaPrazo() {
        var result=prioridades.aplicar(fila(),List.of(contato(3,ContatoCobrancaRequest.Situacao.ENVIADA,hoje.plusDays(1))),PrioridadesCobranca.Filtro.ATRASADAS);
        assertThat(result.visitas()).isEmpty();assertThat(result.prioridades()).containsEntry(PrioridadesCobranca.Filtro.HOJE,0L).containsEntry(PrioridadesCobranca.Filtro.ATRASADAS,0L);
    }
}
