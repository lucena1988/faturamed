const priorityItems = [
  {
    paciente: "A. Martins",
    procedimento: "Consulta especializada",
    tipo: "Faturado sem producao",
    medico: "Dra. Helena Costa",
    valor: "R$ 4.820,00",
    status: "Pendente"
  },
  {
    paciente: "R. Almeida",
    procedimento: "Ultrassonografia",
    tipo: "Valor divergente",
    medico: "Dr. Caio Nunes",
    valor: "R$ 3.160,00",
    status: "Em analise"
  },
  {
    paciente: "M. Rocha",
    procedimento: "Exame laboratorial",
    tipo: "Quantidade divergente",
    medico: "Dra. Livia Prado",
    valor: "R$ 1.940,00",
    status: "Contestado"
  },
  {
    paciente: "C. Ferreira",
    procedimento: "Consulta retorno",
    tipo: "Duplicidade",
    medico: "Dr. Victor Maia",
    valor: "R$ 690,00",
    status: "Pendente"
  }
];

const imports = [
  ["faturamento-setembro-2026.xlsx", "FATURAMENTO", "8.214", "Concluida", "07/10/2026 08:11"],
  ["producao-setembro-2026.xlsx", "PRODUCAO", "7.628", "Concluida", "07/10/2026 08:06"],
  ["faturamento-agosto-2026.csv", "FATURAMENTO", "8.042", "Concluida", "02/09/2026 17:42"],
  ["producao-agosto-2026.csv", "PRODUCAO", "7.984", "Concluida", "02/09/2026 17:31"]
];

const layoutFields = [
  ["A", "data_atendimento", "dd/MM/yyyy"],
  ["B", "paciente_nome", "texto"],
  ["C", "paciente_documento", "CPF/carteirinha"],
  ["D", "procedimento_codigo", "codigo TUSS"],
  ["E", "procedimento_nome", "texto"],
  ["F", "medico_nome", "texto"],
  ["G", "quantidade", "numero"],
  ["H", "valor_total", "moeda"]
];

const viewTitles = {
  dashboard: "Dashboard",
  importacoes: "Importacoes",
  divergencias: "Divergencias",
  layouts: "Layouts"
};

const viewTitle = document.querySelector("#view-title");
const toast = document.querySelector("#toast");

function pillClass(status) {
  if (status === "Pendente") return "danger";
  if (status === "Em analise") return "warning";
  return "success";
}

function renderPriorityTable() {
  const rows = priorityItems.map((item) => `
    <tr>
      <td>${item.paciente}</td>
      <td>${item.procedimento}</td>
      <td>${item.tipo}</td>
      <td>${item.medico}</td>
      <td><strong>${item.valor}</strong></td>
      <td><span class="pill ${pillClass(item.status)}">${item.status}</span></td>
    </tr>
  `).join("");

  document.querySelector("#priority-table").innerHTML = rows;
}

function renderImportsTable() {
  const rows = imports.map((item) => `
    <tr>
      <td>${item[0]}</td>
      <td>${item[1]}</td>
      <td>${item[2]}</td>
      <td><span class="pill success">${item[3]}</span></td>
      <td>${item[4]}</td>
    </tr>
  `).join("");

  document.querySelector("#imports-table").innerHTML = rows;
}

function renderDivergenceTable() {
  const typeFilter = document.querySelector("#type-filter").value;
  const statusFilter = document.querySelector("#status-filter").value;

  const rows = priorityItems
    .filter((item) => typeFilter === "TODOS" || item.tipo === typeFilter)
    .filter((item) => statusFilter === "TODOS" || item.status === statusFilter)
    .map((item, index) => `
      <tr>
        <td>${item.paciente}</td>
        <td>01/09/2026</td>
        <td>${item.procedimento}</td>
        <td>${item.tipo}</td>
        <td><strong>${item.valor}</strong></td>
        <td><span class="pill ${pillClass(item.status)}">${item.status}</span></td>
        <td>
          <button class="icon-button row-action" type="button" title="Abrir divergencia" aria-label="Abrir divergencia ${index + 1}">
            <svg viewBox="0 0 24 24"><path d="M9 18l6-6-6-6"/></svg>
          </button>
        </td>
      </tr>
    `).join("");

  document.querySelector("#divergence-table").innerHTML = rows;
}

function renderLayoutBoard() {
  const tiles = layoutFields.map((field) => `
    <article class="field-tile">
      <header>
        <span class="column-letter">${field[0]}</span>
        <span class="badge">obrigatorio</span>
      </header>
      <strong>${field[1]}</strong>
      <small>${field[2]}</small>
    </article>
  `).join("");

  document.querySelector("#layout-board").innerHTML = tiles;
}

function showView(view) {
  document.querySelectorAll(".view").forEach((section) => {
    section.classList.toggle("active", section.dataset.view === view);
  });

  document.querySelectorAll("[data-view-link]").forEach((button) => {
    button.classList.toggle("active", button.dataset.viewLink === view);
  });

  viewTitle.textContent = viewTitles[view] || "Dashboard";
}

function showToast(message) {
  toast.textContent = message;
  toast.classList.add("visible");
  window.setTimeout(() => toast.classList.remove("visible"), 2200);
}

document.querySelectorAll("[data-view-link]").forEach((button) => {
  button.addEventListener("click", () => showView(button.dataset.viewLink));
});

document.querySelector("#refresh-button").addEventListener("click", () => {
  showToast("Indicadores atualizados");
});

document.querySelector("#import-form").addEventListener("submit", (event) => {
  event.preventDefault();
  showToast("Importacao registrada para processamento");
});

document.querySelector("#type-filter").addEventListener("change", renderDivergenceTable);
document.querySelector("#status-filter").addEventListener("change", renderDivergenceTable);

document.querySelector("#add-column").addEventListener("click", () => {
  const nextLetter = String.fromCharCode(65 + layoutFields.length);
  layoutFields.push([nextLetter, "novo_campo", "texto"]);
  renderLayoutBoard();
  showToast("Campo adicionado ao layout");
});

renderPriorityTable();
renderImportsTable();
renderDivergenceTable();
renderLayoutBoard();
