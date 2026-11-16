package br.com.faturamed.producao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Extrai as etiquetas de atendimento de uma foto de folha de producao usando a API de visao do Claude.
 * Sem chave configurada (ANTHROPIC_API_KEY) o recurso fica indisponivel e o operador digita manualmente.
 */
@Component
public class ExtratorEtiquetasOcr {
    public record Resultado(List<ProducaoService.Item> itens, Cabecalho cabecalho) {}
    public record Cabecalho(String dataProducao, String medico, String periodo, String tipoAgenda, String auxiliar) {}

    static final String[] CAMPOS = {"nrAtendimento", "dataHoraAtendimento", "paciente", "prontuario", "dataNascimento", "filiacao",
            "convenio", "convenioCodigo", "plano", "carteirinha", "guia", "senha", "nrPrescricao", "procedimento", "medicoSolicitante"};

    private static final String INSTRUCOES = """
            Voce recebe a foto de uma folha de producao medica com etiquetas de atendimento hospitalar coladas.
            Transcreva TODAS as etiquetas, de cima para baixo, uma entrada por etiqueta fisica.
            Etiquetas repetidas do mesmo atendimento sao procedimentos distintos: NAO junte, registre cada uma.
            Copie os valores exatamente como impressos; nao invente. Campo inexistente na etiqueta: string vazia.
            Mapeamento: "Atend." -> nrAtendimento; data/hora ao lado do Atend. ou "Dt.Entr." -> dataHoraAtendimento;
            "Prontuario"/"Pront." -> prontuario; "DN"/"DT Nasc" -> dataNascimento; "Filiacao 1" -> filiacao;
            "Convenio"/"Conv:" -> convenio (nome) e convenioCodigo (codigo numerico, se houver); "Plano" -> plano;
            "Carteirinha"/"Cart:" -> carteirinha; "Guia"/"Nr.Guia" -> guia; "Senha" -> senha; "Nr. Prescr." -> nrPrescricao;
            linha do procedimento/setor (ex.: "SADT Cardiologia (ITM)") -> procedimento; "MD." -> medicoSolicitante.
            Marque ilegivel=true quando algum caractere do numero de atendimento, paciente ou carteirinha estiver incerto.
            Preencha tambem o cabecalho manuscrito da folha (data, medico, periodo, tipo de agenda, auxiliar), se houver.
            """;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final String chave;
    private final String modelo;
    private final String url;
    private final String motor;
    private final LeitorTesseract tesseract;

    public ExtratorEtiquetasOcr(@Value("${faturamed.ocr.api-key:${ANTHROPIC_API_KEY:}}") String chave,
            @Value("${faturamed.ocr.modelo:claude-sonnet-5-5}") String modelo,
            @Value("${faturamed.ocr.url:https://api.anthropic.com/v1/messages}") String url,
            @Value("${faturamed.ocr.motor:tesseract}") String motor, LeitorTesseract tesseract) {
        this.chave = chave == null ? "" : chave.trim();
        this.modelo = modelo;
        this.url = url;
        this.motor = motor == null ? "tesseract" : motor.trim().toLowerCase(java.util.Locale.ROOT);
        this.tesseract = tesseract;
    }

    /** Motor ativo: "tesseract" (local, padrao) ou "claude" (exige chave de API). */
    public String motor() { return usaClaude() ? "claude" : "tesseract"; }
    private boolean usaClaude() { return "claude".equals(motor) && !chave.isEmpty(); }

    public boolean disponivel() { return usaClaude() || tesseract.disponivel(); }

    public Resultado extrair(byte[] imagem, String tipo) {
        if (!usaClaude()) return new Resultado(InterpretadorEtiquetas.interpretar(tesseract.ler(imagem)), null);
        try {
            HttpResponse<String> resposta = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(120))
                    .header("x-api-key", chave).header("anthropic-version", "2023-06-01").header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(requisicao(imagem, tipo)))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resposta.statusCode() / 100 != 2)
                throw new IllegalArgumentException("Falha na leitura automatica (HTTP " + resposta.statusCode() + "). Lance os itens manualmente");
            return interpretar(json.readTree(resposta.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Leitura automatica interrompida");
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Nao foi possivel contatar o servico de leitura automatica. Lance os itens manualmente");
        }
    }

    private ObjectNode requisicao(byte[] imagem, String tipo) {
        ObjectNode corpo = json.createObjectNode();
        corpo.put("model", modelo);
        corpo.put("max_tokens", 16000);
        corpo.put("system", INSTRUCOES);
        ObjectNode ferramenta = corpo.putArray("tools").addObject();
        ferramenta.put("name", "registrar_etiquetas");
        ferramenta.put("description", "Registra o cabecalho da folha e cada etiqueta de atendimento encontrada na foto.");
        ferramenta.set("input_schema", esquema());
        corpo.putObject("tool_choice").put("type", "tool").put("name", "registrar_etiquetas");
        ArrayNode conteudo = corpo.putArray("messages").addObject().put("role", "user").putArray("content");
        ObjectNode img = conteudo.addObject().put("type", "image");
        img.putObject("source").put("type", "base64").put("media_type", tipo).put("data", Base64.getEncoder().encodeToString(imagem));
        conteudo.addObject().put("type", "text").put("text", "Transcreva todas as etiquetas desta folha.");
        return corpo;
    }

    private ObjectNode esquema() {
        ObjectNode s = json.createObjectNode().put("type", "object");
        ObjectNode props = s.putObject("properties");
        ObjectNode cab = props.putObject("cabecalho").put("type", "object");
        ObjectNode cabProps = cab.putObject("properties");
        for (String c : List.of("dataProducao", "medico", "periodo", "tipoAgenda", "auxiliar")) cabProps.putObject(c).put("type", "string");
        ObjectNode item = props.putObject("etiquetas").put("type", "array").putObject("items").put("type", "object");
        ObjectNode itemProps = item.putObject("properties");
        for (String c : CAMPOS) itemProps.putObject(c).put("type", "string");
        itemProps.putObject("ilegivel").put("type", "boolean");
        item.putArray("required").add("nrAtendimento").add("paciente");
        s.putArray("required").add("etiquetas");
        return s;
    }

    Resultado interpretar(JsonNode resposta) {
        JsonNode entrada = null;
        for (JsonNode bloco : resposta.path("content"))
            if ("tool_use".equals(bloco.path("type").asText())) entrada = bloco.path("input");
        if (entrada == null) throw new IllegalArgumentException("A leitura automatica nao retornou etiquetas. Lance os itens manualmente");
        List<ProducaoService.Item> itens = new ArrayList<>();
        for (JsonNode e : entrada.path("etiquetas")) {
            String atendimento = e.path("nrAtendimento").asText("").replaceAll("\\s", "");
            boolean pendente = e.path("ilegivel").asBoolean(false) || !atendimento.matches("\\d{4,}") || e.path("paciente").asText("").isBlank();
            itens.add(new ProducaoService.Item(null, atendimento, t(e, "dataHoraAtendimento"), t(e, "paciente"), t(e, "prontuario"),
                    t(e, "dataNascimento"), t(e, "filiacao"), t(e, "convenio"), t(e, "convenioCodigo"), t(e, "plano"), t(e, "carteirinha"),
                    t(e, "guia"), t(e, "senha"), t(e, "nrPrescricao"), t(e, "procedimento"), t(e, "medicoSolicitante"), "OCR", pendente, null, null));
        }
        JsonNode c = entrada.path("cabecalho");
        return new Resultado(itens, new Cabecalho(t(c, "dataProducao"), t(c, "medico"), t(c, "periodo"), t(c, "tipoAgenda"), t(c, "auxiliar")));
    }

    private static String t(JsonNode n, String campo) { return n.path(campo).asText("").trim(); }
}
