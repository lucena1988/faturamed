package br.com.faturamed.importacao;

import br.com.faturamed.domain.OrigemRegistro;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "registro_importado")
public class RegistroImportado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "importacao_id", nullable = false)
    private Importacao importacao;

    @Column(name = "numero_linha", nullable = false)
    private int numeroLinha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrigemRegistro origem;

    @Column(name = "paciente_nome", length = 180)
    private String pacienteNome;

    @Column(name = "paciente_documento", length = 40)
    private String pacienteDocumento;

    @Column(name = "atendimento_codigo", length = 80)
    private String atendimentoCodigo;

    @Column(name = "guia_codigo", length = 80)
    private String guiaCodigo;

    @Column(name = "data_atendimento")
    private LocalDate dataAtendimento;

    @Column(name = "medico_nome", length = 180)
    private String medicoNome;

    @Column(name = "medico_documento", length = 40)
    private String medicoDocumento;

    @Column(name = "procedimento_codigo", length = 80)
    private String procedimentoCodigo;

    @Column(name = "procedimento_nome", length = 255)
    private String procedimentoNome;

    @Column(precision = 14, scale = 4)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", precision = 14, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "valor_total", precision = 14, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "chave_cruzamento", length = 500)
    private String chaveCruzamento;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_originais", columnDefinition = "jsonb")
    private JsonNode dadosOriginais;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    protected RegistroImportado() {
    }

    public RegistroImportado(
            Importacao importacao,
            int numeroLinha,
            OrigemRegistro origem,
            String pacienteNome,
            String pacienteDocumento,
            String atendimentoCodigo,
            String guiaCodigo,
            LocalDate dataAtendimento,
            String medicoNome,
            String medicoDocumento,
            String procedimentoCodigo,
            String procedimentoNome,
            BigDecimal quantidade,
            BigDecimal valorUnitario,
            BigDecimal valorTotal,
            String chaveCruzamento,
            JsonNode dadosOriginais
    ) {
        this.importacao = importacao;
        this.numeroLinha = numeroLinha;
        this.origem = origem;
        this.pacienteNome = pacienteNome;
        this.pacienteDocumento = pacienteDocumento;
        this.atendimentoCodigo = atendimentoCodigo;
        this.guiaCodigo = guiaCodigo;
        this.dataAtendimento = dataAtendimento;
        this.medicoNome = medicoNome;
        this.medicoDocumento = medicoDocumento;
        this.procedimentoCodigo = procedimentoCodigo;
        this.procedimentoNome = procedimentoNome;
        this.quantidade = quantidade;
        this.valorUnitario = valorUnitario;
        this.valorTotal = valorTotal;
        this.chaveCruzamento = chaveCruzamento;
        this.dadosOriginais = dadosOriginais;
    }

    @PrePersist
    void prePersist() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Importacao getImportacao() {
        return importacao;
    }

    public int getNumeroLinha() {
        return numeroLinha;
    }

    public OrigemRegistro getOrigem() {
        return origem;
    }

    public String getPacienteNome() {
        return pacienteNome;
    }

    public String getProcedimentoCodigo() {
        return procedimentoCodigo;
    }

    public String getChaveCruzamento() {
        return chaveCruzamento;
    }
}
