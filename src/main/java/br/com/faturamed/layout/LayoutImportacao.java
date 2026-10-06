package br.com.faturamed.layout;

import br.com.faturamed.domain.TipoImportacao;
import br.com.faturamed.empresa.Empresa;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "layout_importacao")
public class LayoutImportacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false, length = 180)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoImportacao tipo;

    @Column(name = "delimitador_csv", length = 5)
    private String delimitadorCsv;

    @Column(name = "primeira_linha_cabecalho", nullable = false)
    private boolean primeiraLinhaCabecalho = true;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @OneToMany(mappedBy = "layoutImportacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LayoutCampo> campos = new ArrayList<>();

    protected LayoutImportacao() {
    }

    public LayoutImportacao(
            Empresa empresa,
            String nome,
            TipoImportacao tipo,
            String delimitadorCsv,
            boolean primeiraLinhaCabecalho
    ) {
        this.empresa = empresa;
        this.nome = nome;
        this.tipo = tipo;
        this.delimitadorCsv = delimitadorCsv;
        this.primeiraLinhaCabecalho = primeiraLinhaCabecalho;
    }

    @PrePersist
    void prePersist() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public void adicionarCampo(LayoutCampo campo) {
        campo.vincularLayout(this);
        campos.add(campo);
    }

    public Long getId() {
        return id;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public String getNome() {
        return nome;
    }

    public TipoImportacao getTipo() {
        return tipo;
    }

    public String getDelimitadorCsv() {
        return delimitadorCsv;
    }

    public boolean isPrimeiraLinhaCabecalho() {
        return primeiraLinhaCabecalho;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public List<LayoutCampo> getCampos() {
        return Collections.unmodifiableList(campos);
    }
}
