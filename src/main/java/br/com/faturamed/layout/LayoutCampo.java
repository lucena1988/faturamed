package br.com.faturamed.layout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "layout_campo")
public class LayoutCampo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "layout_importacao_id", nullable = false)
    private LayoutImportacao layoutImportacao;

    @Column(name = "campo_padronizado", nullable = false, length = 80)
    private String campoPadronizado;

    @Column(name = "coluna_origem", nullable = false, length = 20)
    private String colunaOrigem;

    @Column(nullable = false)
    private boolean obrigatorio;

    @Column(length = 80)
    private String formato;

    protected LayoutCampo() {
    }

    public LayoutCampo(String campoPadronizado, String colunaOrigem, boolean obrigatorio, String formato) {
        this.campoPadronizado = campoPadronizado;
        this.colunaOrigem = colunaOrigem;
        this.obrigatorio = obrigatorio;
        this.formato = formato;
    }

    void vincularLayout(LayoutImportacao layoutImportacao) {
        this.layoutImportacao = layoutImportacao;
    }

    public Long getId() {
        return id;
    }

    public String getCampoPadronizado() {
        return campoPadronizado;
    }

    public String getColunaOrigem() {
        return colunaOrigem;
    }

    public boolean isObrigatorio() {
        return obrigatorio;
    }

    public String getFormato() {
        return formato;
    }
}
