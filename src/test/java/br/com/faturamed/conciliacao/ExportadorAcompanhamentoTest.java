package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExportadorAcompanhamentoTest {
    private static final AcompanhamentoBaseHospitalarService.Etapa FATURADO=AcompanhamentoBaseHospitalarService.Etapa.FATURADO_SEM_RECEBIMENTO;
    private static final AcompanhamentoBaseHospitalarService.Etapa AGUARDANDO=AcompanhamentoBaseHospitalarService.Etapa.AGUARDANDO_PRODUCAO;
    private AcompanhamentoBaseHospitalarService.Item item(int id,String medico,Long empresaId,String empresa,AcompanhamentoBaseHospitalarService.Etapa etapa,String valor) {
        BigDecimal v=valor==null?null:new BigDecimal(valor);
        return new AcompanhamentoBaseHospitalarService.Item(id,"A"+id,LocalDate.of(2026,8,1),medico,etapa,empresaId==null?null:10L,empresaId==null?null:2,
                v,v==null?null:BigDecimal.ZERO,v,"2026-08",empresaId==null?null:"2026-07",empresaId,empresa);
    }
    private final List<AcompanhamentoBaseHospitalarService.Item> itens=List.of(
            item(1,"Ana",5L,"Clinica X",FATURADO,"100"),item(2,"Bruno",6L,"Clinica Y",FATURADO,"50"),item(3,"Ana",null,null,AGUARDANDO,null));

    @Test void filtraPorMedicoEmpresaEtapa() {
        assertThat(ExportadorAcompanhamento.filtrar(itens,new ExportadorAcompanhamento.Filtros(null,null,"Ana",null))).extracting(AcompanhamentoBaseHospitalarService.Item::visitaId).containsExactly(1,3);
        assertThat(ExportadorAcompanhamento.filtrar(itens,new ExportadorAcompanhamento.Filtros(null,null,null,"6"))).extracting(AcompanhamentoBaseHospitalarService.Item::visitaId).containsExactly(2);
        assertThat(ExportadorAcompanhamento.filtrar(itens,new ExportadorAcompanhamento.Filtros(null,null,null,"SEM"))).extracting(AcompanhamentoBaseHospitalarService.Item::visitaId).containsExactly(3);
        assertThat(ExportadorAcompanhamento.filtrar(itens,new ExportadorAcompanhamento.Filtros(null,FATURADO.name(),"Ana","5"))).extracting(AcompanhamentoBaseHospitalarService.Item::visitaId).containsExactly(1);
    }
    @Test void geraPlanilhaComMesmasColunasEStatusPago() throws Exception {
        byte[] xlsx=new ExportadorAcompanhamento().exportar("Hospital H",new ExportadorAcompanhamento.Filtros("2026-07",null,"Ana",null),
                ExportadorAcompanhamento.filtrar(itens,new ExportadorAcompanhamento.Filtros(null,null,"Ana",null)));
        try(var wb=new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            var sheet=wb.getSheet("Acompanhamento");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("Empresa");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("Clinica X");
            assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEqualTo("PAGO 08/2026");
            assertThat(sheet.getRow(1).getCell(9).getNumericCellValue()).isEqualTo(100.0);
            assertThat(sheet.getRow(2).getCell(9).getStringCellValue()).isEqualTo("Nao confirmado");
            assertThat(wb.getSheet("Resumo").getRow(3).getCell(1).getStringCellValue()).isEqualTo("Ana");
        }
    }
}
