package br.com.faturamed.seguranca;

import br.com.faturamed.conciliacao.RecebimentoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class DemonstrativoMedicoService {
    private final LiberacaoRelatorioService liberacoes;
    private final RecebimentoService recebimentos;
    public DemonstrativoMedicoService(LiberacaoRelatorioService liberacoes,RecebimentoService recebimentos){this.liberacoes=liberacoes;this.recebimentos=recebimentos;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ExtratoMedicoService.Extrato buscar(long relatorio,long medico,ExtratoMedicoService.Filtros filtros) throws Exception {
        filtros.validar();if(medico<=0)throw new IllegalArgumentException("Selecione um medico cadastrado");
        var publicado=liberacoes.buscarDoMedico(relatorio,medico);
        return ExtratoMedicoService.montarPublicado(publicado,recebimentos.movimentosDoMedico(relatorio,medico),filtros);
    }
}
