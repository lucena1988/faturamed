package br.com.faturamed.layout;

import br.com.faturamed.domain.TipoImportacao;
import br.com.faturamed.empresa.Empresa;
import br.com.faturamed.empresa.EmpresaService;
import br.com.faturamed.layout.api.CriarLayoutImportacaoRequest;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LayoutImportacaoService {

    private final EmpresaService empresaService;
    private final LayoutImportacaoRepository layoutImportacaoRepository;

    public LayoutImportacaoService(
            EmpresaService empresaService,
            LayoutImportacaoRepository layoutImportacaoRepository
    ) {
        this.empresaService = empresaService;
        this.layoutImportacaoRepository = layoutImportacaoRepository;
    }

    @Transactional
    public LayoutImportacao criar(CriarLayoutImportacaoRequest request) {
        Empresa empresa = empresaService.buscar(request.empresaId());
        TipoImportacao tipo = TipoImportacao.valueOf(request.tipo());

        LayoutImportacao layout = new LayoutImportacao(
                empresa,
                request.nome(),
                tipo,
                request.delimitadorCsv(),
                request.primeiraLinhaCabecalho()
        );

        request.campos().forEach(campo -> layout.adicionarCampo(new LayoutCampo(
                campo.campoPadronizado(),
                campo.colunaOrigem(),
                campo.obrigatorio(),
                campo.formato()
        )));

        return layoutImportacaoRepository.save(layout);
    }

    @Transactional(readOnly = true)
    public List<LayoutImportacao> listarPorEmpresa(Long empresaId) {
        empresaService.buscar(empresaId);
        return layoutImportacaoRepository.findByEmpresaIdOrderByCriadoEmDesc(empresaId);
    }

    @Transactional(readOnly = true)
    public LayoutImportacao buscar(Long id) {
        return layoutImportacaoRepository.findWithCamposById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Layout de importacao nao encontrado"));
    }
}
