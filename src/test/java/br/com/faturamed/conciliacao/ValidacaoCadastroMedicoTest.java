package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class ValidacaoCadastroMedicoTest {
    @Test void nomeExigeNomeESobrenome() {
        assertThat(ValidacaoCadastroMedico.nomeCompleto("  Luiz   Carlos  Souza ")).isEqualTo("Luiz Carlos Souza");
        assertThat(ValidacaoCadastroMedico.nomeCompleto("Ana de Souza")).isEqualTo("Ana de Souza");
        assertThatThrownBy(() -> ValidacaoCadastroMedico.nomeCompleto("Luiz")).hasMessageContaining("nome completo");
        assertThatThrownBy(() -> ValidacaoCadastroMedico.nomeCompleto("Luiz A.")).hasMessageContaining("nome completo");
        assertThatThrownBy(() -> ValidacaoCadastroMedico.nomeCompleto(null)).hasMessageContaining("nome completo");
    }

    @Test void emailsAceitaVariosSeparadosPorPontoEVirgula() {
        assertThat(ValidacaoCadastroMedico.emails(" Ana@Clinica.com.br ; ana.pessoal@gmail.com;ana@clinica.com.br; "))
            .containsExactly("ana@clinica.com.br", "ana.pessoal@gmail.com");
        assertThat(ValidacaoCadastroMedico.emails("a@b.com, c@d.com")).containsExactly("a@b.com", "c@d.com");
        assertThatThrownBy(() -> ValidacaoCadastroMedico.emails("ana@clinica")).hasMessageContaining("E-mail invalido");
        assertThatThrownBy(() -> ValidacaoCadastroMedico.emails(" ; ")).hasMessageContaining("pelo menos um e-mail");
    }

    @Test void cpfEValidadoPeloDigitoVerificador() {
        assertThat(ValidacaoCadastroMedico.cpf("529.982.247-25")).isEqualTo("52998224725");
    }

    @ParameterizedTest @ValueSource(strings = {"529.982.247-24", "111.111.111-11", "1234567890", "", "abc"})
    void cpfInvalidoERecusado(String cpf) {
        assertThatThrownBy(() -> ValidacaoCadastroMedico.cpf(cpf)).hasMessage("CPF invalido");
    }

    @Test void cnpjEValidadoPeloDigitoVerificador() {
        assertThat(ValidacaoCadastroMedico.cnpj("11.222.333/0001-81")).isEqualTo("11222333000181");
    }

    @ParameterizedTest @ValueSource(strings = {"11.222.333/0001-80", "00.000.000/0000-00", "1122233300018", ""})
    void cnpjInvalidoERecusado(String cnpj) {
        assertThatThrownBy(() -> ValidacaoCadastroMedico.cnpj(cnpj)).hasMessage("CNPJ invalido");
    }

    @Test void crmSoNumerosSemZerosAEsquerda() {
        assertThat(ValidacaoCadastroMedico.crm(" 012345 ")).isEqualTo("12345");
        assertThatThrownBy(() -> ValidacaoCadastroMedico.crm("")).hasMessageContaining("CRM");
        assertThatThrownBy(() -> ValidacaoCadastroMedico.crm("12A")).hasMessageContaining("CRM");
    }
}
