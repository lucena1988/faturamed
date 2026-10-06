package br.com.faturamed.importacao;

import br.com.faturamed.domain.OrigemRegistro;
import br.com.faturamed.domain.TipoImportacao;
import br.com.faturamed.empresa.Empresa;
import br.com.faturamed.empresa.EmpresaService;
import br.com.faturamed.empresa.Unidade;
import br.com.faturamed.empresa.UnidadeRepository;
import br.com.faturamed.importacao.api.CriarImportacaoRequest;
import br.com.faturamed.importacao.api.CriarRegistroImportadoRequest;
import br.com.faturamed.layout.LayoutImportacao;
import br.com.faturamed.layout.LayoutImportacaoService;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportacaoService {

    private final EmpresaService empresaService;
    private final UnidadeRepository unidadeRepository;
    private final LayoutImportacaoService layoutImportacaoService;
    private final ImportacaoRepository importacaoRepository;
    private final RegistroImportadoRepository registroImportadoRepository;

    public ImportacaoService(
            EmpresaService empresaService,
            UnidadeRepository unidadeRepository,
            LayoutImportacaoService layoutImportacaoService,
            ImportacaoRepository importacaoRepository,
            RegistroImportadoRepository registroImportadoRepository
    ) {
        this.empresaService = empresaService;
        this.unidadeRepository = unidadeRepository;
        this.layoutImportacaoService = layoutImportacaoService;
        this.importacaoRepository = importacaoRepository;
        this.registroImportadoRepository = registroImportadoRepository;
    }

    @Transactional
    public Importacao criar(CriarImportacaoRequest request) {
        Empresa empresa = empresaService.buscar(request.empresaId());
        Unidade unidade = buscarUnidadeOpcional(request.unidadeId());
        LayoutImportacao layout = buscarLayoutOpcional(request.layoutImportacaoId());
        TipoImportacao tipo = TipoImportacao.valueOf(request.tipo());

        return importacaoRepository.save(new Importacao(
                empresa,
                unidade,
                layout,
                null,
                tipo,
                request.nomeArquivo()
        ));
    }

    @Transactional
    public RegistroImportado adicionarRegistro(Long importacaoId, CriarRegistroImportadoRequest request) {
        Importacao importacao = buscar(importacaoId);
        OrigemRegistro origem = OrigemRegistro.valueOf(request.origem());
        String chaveCruzamento = gerarChaveCruzamento(request);

        RegistroImportado registro = new RegistroImportado(
                importacao,
                request.numeroLinha(),
                origem,
                request.pacienteNome(),
                request.pacienteDocumento(),
                request.atendimentoCodigo(),
                request.guiaCodigo(),
                request.dataAtendimento(),
                request.medicoNome(),
                request.medicoDocumento(),
                request.procedimentoCodigo(),
                request.procedimentoNome(),
                request.quantidade(),
                request.valorUnitario(),
                request.valorTotal(),
                chaveCruzamento,
                request.dadosOriginais()
        );

        importacao.registrarLinhaProcessada();
        return registroImportadoRepository.save(registro);
    }

    @Transactional(readOnly = true)
    public List<Importacao> listarPorEmpresa(Long empresaId) {
        empresaService.buscar(empresaId);
        return importacaoRepository.findByEmpresaIdOrderByCriadoEmDesc(empresaId);
    }

    @Transactional(readOnly = true)
    public Importacao buscar(Long id) {
        return importacaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Importacao nao encontrada"));
    }

    @Transactional(readOnly = true)
    public List<RegistroImportado> listarRegistros(Long importacaoId) {
        buscar(importacaoId);
        return registroImportadoRepository.findByImportacaoIdOrderByNumeroLinha(importacaoId);
    }

    private Unidade buscarUnidadeOpcional(Long unidadeId) {
        if (unidadeId == null) {
            return null;
        }
        return unidadeRepository.findById(unidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Unidade nao encontrada"));
    }

    private LayoutImportacao buscarLayoutOpcional(Long layoutImportacaoId) {
        if (layoutImportacaoId == null) {
            return null;
        }
        return layoutImportacaoService.buscar(layoutImportacaoId);
    }

    private static String gerarChaveCruzamento(CriarRegistroImportadoRequest request) {
        return normalizar(request.pacienteDocumento())
                + "|"
                + (request.dataAtendimento() == null ? "" : request.dataAtendimento())
                + "|"
                + normalizar(request.procedimentoCodigo());
    }

    private static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.trim().toUpperCase();
    }
}
