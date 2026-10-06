package br.com.faturamed.importacao;

import br.com.faturamed.domain.StatusImportacao;
import br.com.faturamed.domain.TipoImportacao;
import br.com.faturamed.empresa.Empresa;
import br.com.faturamed.empresa.Unidade;
import br.com.faturamed.empresa.Usuario;
import br.com.faturamed.layout.LayoutImportacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "importacao")
public class Importacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_id")
    private Unidade unidade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "layout_importacao_id")
    private LayoutImportacao layoutImportacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoImportacao tipo;

    @Column(name = "nome_arquivo", nullable = false, length = 255)
    private String nomeArquivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusImportacao status = StatusImportacao.RECEBIDA;

    @Column(name = "total_linhas", nullable = false)
    private int totalLinhas;

    @Column(name = "total_processadas", nullable = false)
    private int totalProcessadas;

    @Column(name = "total_erros", nullable = false)
    private int totalErros;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "finalizado_em")
    private LocalDateTime finalizadoEm;

    protected Importacao() {
    }

    public Importacao(
            Empresa empresa,
            Unidade unidade,
            LayoutImportacao layoutImportacao,
            Usuario usuario,
            TipoImportacao tipo,
            String nomeArquivo
    ) {
        this.empresa = empresa;
        this.unidade = unidade;
        this.layoutImportacao = layoutImportacao;
        this.usuario = usuario;
        this.tipo = tipo;
        this.nomeArquivo = nomeArquivo;
    }

    @PrePersist
    void prePersist() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public void registrarLinhaProcessada() {
        totalLinhas++;
        totalProcessadas++;
    }

    public Long getId() {
        return id;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public TipoImportacao getTipo() {
        return tipo;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public StatusImportacao getStatus() {
        return status;
    }

    public int getTotalLinhas() {
        return totalLinhas;
    }

    public int getTotalProcessadas() {
        return totalProcessadas;
    }

    public int getTotalErros() {
        return totalErros;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
