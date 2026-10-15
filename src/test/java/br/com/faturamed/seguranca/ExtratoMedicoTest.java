package br.com.faturamed.seguranca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.faturamed.conciliacao.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;

class ExtratoMedicoTest {
    private final LocalDate janeiro=LocalDate.of(2026,1,15),marco=LocalDate.of(2026,3,10),abril=LocalDate.of(2026,4,10);
    private PortalMedicoController.Visita visita(int linha,LocalDate data,ConciliadorVisitas.Status status,BigDecimal valor) {
        return new PortalMedicoController.Visita(linha,"A"+linha,data,status,"","Ana",10,"Visita","101",valor);
    }
    private RecebimentoService.Visita financeiro(int linha,boolean inconsistente) {
        return new RecebimentoService.Visita(linha,"A"+linha,janeiro,10L,"Ana","101","PAGA",new BigDecimal("100"),new BigDecimal("20"),new BigDecimal("80"),3,true,inconsistente,List.of());
    }
    private List<RecebimentoService.Movimento> movimentos() {
        return List.of(new RecebimentoService.Movimento(1,2,"A2",janeiro,"101",marco,new BigDecimal("40"),null,true),
                new RecebimentoService.Movimento(2,2,"A2",janeiro,"101",abril,new BigDecimal("20"),null,false),
                new RecebimentoService.Movimento(3,2,"A2",janeiro,"101",abril,new BigDecimal("-40"),1L,false));
    }
    private ExtratoMedicoService.Extrato extrato(ExtratoMedicoService.Filtros filtros) {
        var base=new PortalMedicoController.Relatorio(1,"H",List.of(visita(2,janeiro,ConciliadorVisitas.Status.PAGA,new BigDecimal("100")),visita(3,null,ConciliadorVisitas.Status.PENDENTE,null)));
        return ExtratoMedicoService.montar(base,List.of(financeiro(2,false)),movimentos(),filtros,"Ana",Instant.EPOCH);
    }
    @Test void periodoDoMovimentoNaoAlteraSaldoAtualNemPeriodoDaVisita() {
        var e=extrato(new ExtratoMedicoService.Filtros(janeiro,janeiro,LocalDate.of(2026,3,1),LocalDate.of(2026,3,31)));
        assertThat(e.visitas()).hasSize(1);assertThat(e.movimentos()).hasSize(1);
        assertThat(e.resumo().entradasPeriodo()).isEqualByComparingTo("40");assertThat(e.resumo().estornosPeriodo()).isZero();
        assertThat(e.resumo().recebidoAcumulado()).isEqualByComparingTo("20");assertThat(e.resumo().saldoAtual()).isEqualByComparingTo("80");
    }
    @Test void estornoPosteriorPermaneceNegativoNoPeriodoCorreto() {
        var e=extrato(new ExtratoMedicoService.Filtros(null,null,abril,abril));
        assertThat(e.resumo().entradasPeriodo()).isEqualByComparingTo("20");assertThat(e.resumo().estornosPeriodo()).isEqualByComparingTo("40");assertThat(e.resumo().liquidoPeriodo()).isEqualByComparingTo("-20");
        assertThat(e.movimentos()).extracting(RecebimentoService.Movimento::id).containsExactly(2L,3L);
    }
    @Test void filtroDeVisitasSemCorrespondenciaNaoPuxaMovimentosDeOutraCompetencia() {
        var e=extrato(new ExtratoMedicoService.Filtros(abril,abril,null,null));assertThat(e.visitas()).isEmpty();assertThat(e.movimentos()).isEmpty();assertThat(e.resumo().faturado()).isZero();
        assertThat(extrato(new ExtratoMedicoService.Filtros(null,null,null,null)).visitas()).hasSize(2);
    }
    @Test void financeiroInconsistenteENaoAutorizadoNaoVazamMovimentos() {
        var base=new PortalMedicoController.Relatorio(1,"H",List.of(visita(2,janeiro,ConciliadorVisitas.Status.PAGA,new BigDecimal("100"))));
        var events=new ArrayList<>(movimentos());events.add(new RecebimentoService.Movimento(9,99,"SEGREDO",janeiro,"101",marco,new BigDecimal("999"),null,false));
        var e=ExtratoMedicoService.montar(base,List.of(financeiro(2,true),financeiro(99,false)),events,new ExtratoMedicoService.Filtros(null,null,null,null),"Ana",Instant.EPOCH);
        assertThat(e.movimentos()).isEmpty();assertThat(e.visitas().getFirst().recebido()).isNull();assertThat(e.visitas().getFirst().saldo()).isNull();assertThat(e.resumo().financeiroNaoConfirmado()).isEqualTo(1);
    }
    @Test void pendenciasEDivergenciasFicamSeparadasDoSaldoConfirmado() {
        var base=new PortalMedicoController.Relatorio(1,"H",List.of(visita(3,janeiro,ConciliadorVisitas.Status.PENDENTE,null),visita(4,janeiro,ConciliadorVisitas.Status.DIVERGENTE,new BigDecimal("22.005"))));
        var e=ExtratoMedicoService.montar(base,List.of(),List.of(),new ExtratoMedicoService.Filtros(null,null,null,null),"Ana",Instant.EPOCH);
        assertThat(e.resumo().faturado()).isZero();assertThat(e.resumo().saldoAtual()).isZero();assertThat(e.resumo().pendentes()).isEqualTo(1);assertThat(e.resumo().divergentes()).isEqualTo(1);assertThat(e.resumo().semValor()).isEqualTo(1);assertThat(e.resumo().emConferencia()).isEqualByComparingTo("22.01");
    }
    @Test void validaIntervalosInclusiveComApenasUmLimite() {
        assertThatThrownBy(()->new ExtratoMedicoService.Filtros(abril,janeiro,null,null).validar()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ExtratoMedicoService.Filtros(null,null,abril,janeiro).validar()).isInstanceOf(IllegalArgumentException.class);
        var f=new ExtratoMedicoService.Filtros(null,janeiro,marco,null);f.validar();assertThat(f.visita(janeiro)).isTrue();assertThat(f.visita(null)).isFalse();assertThat(f.movimento(abril)).isTrue();
    }
    @Test void usaUsuarioAutenticadoParaBuscarMovimentos() throws Exception {
        var portal=mock(PortalMedicoController.class);var recebimentos=mock(RecebimentoService.class);var user=new UsuarioAutenticado(1,"a@test.local","Ana","hash","MEDICO",10L,true,false,0);
        when(portal.buscar(1,user)).thenReturn(new PortalMedicoController.Relatorio(1,"H",List.of(visita(2,janeiro,ConciliadorVisitas.Status.PAGA,new BigDecimal("100")))));
        when(recebimentos.listar(1)).thenReturn(new RecebimentoService.Resultado("H",false,List.of(financeiro(2,false))));when(recebimentos.movimentosDoMedico(1,10)).thenReturn(movimentos());
        assertThat(new ExtratoMedicoService(portal,recebimentos).buscar(1,new ExtratoMedicoService.Filtros(null,null,null,null),user).movimentos()).hasSize(3);
        verify(recebimentos).movimentosDoMedico(1,10);
    }
    @Test void excelTemMesmosFiltrosDatasNumericasEstornosEValoresDesconhecidosEmBranco() throws Exception {
        var e=extrato(new ExtratoMedicoService.Filtros(null,null,abril,abril));byte[] bytes=new ExportadorExtratoMedico().exportar(e);
        try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);var resumo=workbook.getSheet("Resumo");var visitas=workbook.getSheet("Visitas");var movements=workbook.getSheet("Movimentos");
            assertThat(resumo.getRow(11).getCell(1).getNumericCellValue()).isEqualTo(80);
            assertThat(movements.getLastRowNum()).isEqualTo(2);assertThat(movements.getRow(2).getCell(7).getNumericCellValue()).isEqualTo(-40);assertThat(movements.getRow(2).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);assertThat(DateUtil.isCellDateFormatted(movements.getRow(2).getCell(1))).isTrue();
            assertThat(visitas.getRow(2).getCell(6).getCellType()).isEqualTo(CellType.BLANK);assertThat(visitas.getRow(2).getCell(8).getCellType()).isEqualTo(CellType.BLANK);assertThat(visitas.getPaneInformation()).isNotNull();assertThat(movements.getPaneInformation()).isNotNull();
        }
    }
    @Test void excelIdentificaPagamentoSemTransformarReferenciaEmFormula() throws Exception {
        var original=extrato(new ExtratoMedicoService.Filtros(null,null,null,null));
        var movimento=new RecebimentoService.Movimento(1,2,"A2",janeiro,"101",marco,new BigDecimal("40"),null,false,55L,"=REF-HOSPITAL");
        var e=new ExtratoMedicoService.Extrato(original.relatorio(),original.hospital(),original.medico(),original.geradoEm(),original.filtros(),original.resumo(),original.visitas(),List.of(movimento));
        try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(new ExportadorExtratoMedico().exportar(e)))) {
            var row=workbook.getSheet("Movimentos").getRow(1);assertThat(row.getCell(9).getNumericCellValue()).isEqualTo(55);assertThat(row.getCell(10).getCellType()).isEqualTo(CellType.STRING);assertThat(row.getCell(10).getStringCellValue()).isEqualTo("=REF-HOSPITAL");
        }
    }
}
