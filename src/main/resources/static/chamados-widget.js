(() => {
  if (document.getElementById('hd-fab-button') || location.pathname === '/login.html') return;
  const el = (tag, className, text) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  };

  const SUPPORT_GLASS_SVG = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="100%" height="100%" fill="none">
      <defs>
        <radialGradient id="fm-disc-grad" cx="36%" cy="30%" r="70%">
          <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.95"/>
          <stop offset="35%" stop-color="#E2F2F5" stop-opacity="0.85"/>
          <stop offset="70%" stop-color="#A5D7DC" stop-opacity="0.45"/>
          <stop offset="100%" stop-color="#4B96A8" stop-opacity="0.75"/>
        </radialGradient>
        <linearGradient id="fm-ring-grad" x1="0" y1="0" x2="100" y2="100" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.9"/>
          <stop offset="45%" stop-color="#B2E0E4" stop-opacity="0.5"/>
          <stop offset="80%" stop-color="#24677D" stop-opacity="0.3"/>
          <stop offset="100%" stop-color="#0E3D4C" stop-opacity="0.75"/>
        </linearGradient>
        <linearGradient id="fm-headset-grad" x1="20" y1="20" x2="80" y2="85" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="#0F4C5C"/>
          <stop offset="50%" stop-color="#1A6174"/>
          <stop offset="100%" stop-color="#2C7A8E"/>
        </linearGradient>
        <linearGradient id="fm-bubble-fill" x1="34" y1="34" x2="66" y2="64" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="#95D3D5" stop-opacity="0.95"/>
          <stop offset="100%" stop-color="#24677D" stop-opacity="0.95"/>
        </linearGradient>
      </defs>
      <!-- Headset arch -->
      <path d="M 22 52 C 22 30 34 16 50 16 C 66 16 78 30 78 52" stroke="url(#fm-headset-grad)" stroke-width="6.5" stroke-linecap="round"/>
      <!-- Left earpad -->
      <rect x="15" y="44" width="12" height="22" rx="6" fill="url(#fm-headset-grad)"/>
      <rect x="18" y="47" width="6" height="16" rx="3" fill="#194D60" opacity="0.6"/>
      <!-- Right earpad -->
      <rect x="73" y="44" width="12" height="22" rx="6" fill="url(#fm-headset-grad)"/>
      <rect x="76" y="47" width="6" height="16" rx="3" fill="#194D60" opacity="0.6"/>
      <!-- Mic boom -->
      <path d="M 78 60 C 78 78 68 84 56 84 H 52" stroke="url(#fm-headset-grad)" stroke-width="4.5" stroke-linecap="round"/>
      <rect x="44" y="81" width="9" height="6" rx="3" fill="#FFFFFF"/>
      <!-- Center speech bubble -->
      <path d="M 33 36 C 33 32.7 35.7 30 39 30 H 61 C 64.3 30 67 32.7 67 36 V 54 C 67 57.3 64.3 60 61 60 H 45 L 37 66 V 60 H 39 C 35.7 60 33 57.3 33 54 Z" fill="url(#fm-bubble-fill)" stroke="#FFFFFF" stroke-width="1.8"/>
      <!-- Chat lines -->
      <rect x="40" y="38" width="18" height="3" rx="1.5" fill="#FFFFFF"/>
      <rect x="40" y="45" width="12" height="3" rx="1.5" fill="#E5F3F6"/>
    </svg>
  `;
  const BR_GLASS_SVG = SUPPORT_GLASS_SVG;

  const STATUS_LABELS = {
    ABERTO: 'Aberto (Triagem)',
    EM_ATENDIMENTO: 'Em Atendimento',
    AGUARDANDO_USUARIO: 'Aguardando Retorno',
    RESOLVIDO: 'Resolvido',
    FECHADO: 'Fechado',
    CANCELADO: 'Cancelado'
  };

  const SLA_LABELS = {
    NO_PRAZO: 'SLA no prazo',
    EM_ALERTA: 'SLA em alerta',
    VENCIDO: 'SLA vencido',
    CUMPRIDO: 'SLA cumprido',
    FORA_DO_PRAZO: 'Concluido fora do SLA',
    CANCELADO: 'SLA cancelado'
  };

  let currentUser = null;
  let tiposSla = [];
  let meusChamados = [];
  let activeTicketId = null;
  let avisosPendentes = [];

  const fab = el('button', 'hd-fab');
  fab.id = 'hd-fab-button';
  fab.type = 'button';
  fab.title = 'Central de Suporte e Chamados (Service Desk)';
  fab.setAttribute('aria-label', 'Abrir ambiente de Suporte e Chamados');

  const fabIcon = el('span', 'hd-fab-icon');
  fabIcon.innerHTML = BR_GLASS_SVG;

  const fabLabel = el('span', 'hd-fab-label');
  fabLabel.append(
    el('strong', '', 'Suporte & Chamados'),
    el('small', '', 'Service Desk • Abrir / Ver')
  );

  const fabBadge = el('span', 'hd-fab-badge');
  fabBadge.hidden = true;
  fab.append(fabIcon, fabLabel, fabBadge);
  document.body.append(fab);

  const dialog = el('dialog');
  dialog.id = 'hd-widget-dialog';
  dialog.setAttribute('aria-labelledby', 'hd-dialog-title');

  const shell = el('div', 'hd-dialog-shell');
  const header = el('header', 'hd-dialog-header');
  const brandHead = el('div', 'hd-brand-head');
  const brandLogo = el('span', 'hd-brand-logo');
  brandLogo.innerHTML = BR_GLASS_SVG;
  const headerLeft = el('div');
  const title = el('h2', '', 'Central de suporte');
  title.id = 'hd-dialog-title';
  const subtitle = el('p', '', 'Conte o que aconteceu. Estamos aqui para ajudar.');
  headerLeft.append(title, subtitle);
  brandHead.append(brandLogo, headerLeft);

  const headerRight = el('div');
  headerRight.style.display = 'flex';
  headerRight.style.alignItems = 'center';
  headerRight.style.gap = '8px';

  const kanbanLink = el('a', 'hd-kanban-link', 'Abrir Kanban ↗');
  kanbanLink.href = '/chamados.html';
  kanbanLink.title = 'Ir para o painel de atendimento e gestao de SLA';

  const btnClose = el('button', 'hd-close-btn', '×');
  btnClose.type = 'button';
  btnClose.title = 'Fechar central de suporte';
  headerRight.append(kanbanLink, btnClose);
  header.append(brandHead, headerRight);

  const nav = el('nav', 'hd-tabs');
  nav.setAttribute('aria-label', 'Navegacao do suporte');
  const tabNovo = el('button', 'hd-tab-btn active', 'Novo chamado');
  tabNovo.type = 'button';
  const tabMeus = el('button', 'hd-tab-btn', 'Meus chamados (0)');
  tabMeus.type = 'button';
  const tabDetalhe = el('button', 'hd-tab-btn', 'Acompanhamento');
  tabDetalhe.type = 'button';
  tabDetalhe.hidden = true;
  nav.append(tabNovo, tabMeus, tabDetalhe);

  const body = el('div', 'hd-dialog-body');
  const feedback = el('p', 'hd-feedback');
  feedback.hidden = true;

  const avisosBox = el('div', 'hd-avisos-box');
  avisosBox.hidden = true;

  const viewNovo = el('section', 'hd-tab-view');
  const viewMeus = el('section', 'hd-tab-view');
  viewMeus.hidden = true;
  const viewDetalhe = el('section', 'hd-tab-view');
  viewDetalhe.hidden = true;

  body.append(feedback, avisosBox, viewNovo, viewMeus, viewDetalhe);
  shell.append(header, nav, body);
  dialog.append(shell);
  document.body.append(dialog);

  function showFeedback(msg, isErr = false) {
    if (!msg) {
      feedback.hidden = true;
      return;
    }
    feedback.hidden = false;
    feedback.className = 'hd-feedback ' + (isErr ? 'err' : 'ok');
    feedback.textContent = msg;
  }

  function switchTab(which) {
    showFeedback('');
    tabNovo.classList.toggle('active', which === 'novo');
    tabMeus.classList.toggle('active', which === 'meus');
    tabDetalhe.classList.toggle('active', which === 'detalhe');
    viewNovo.hidden = which !== 'novo';
    viewMeus.hidden = which !== 'meus';
    viewDetalhe.hidden = which !== 'detalhe';
  }

  function formatSlaRestante(c) {
    if (c.status === 'CANCELADO') {
      return 'SLA cancelado';
    }
    if (c.status === 'RESOLVIDO' || c.status === 'FECHADO') {
      return SLA_LABELS[c.slaStatus] || c.slaStatus;
    }
    const min = c.minutosRestantes;
    if (min < 0) {
      const atrasoH = Math.ceil(Math.abs(min) / 60);
      return `SLA vencido ha ${atrasoH}h`;
    }
    if (min < 60) return `SLA: ${min} min restantes`;
    const h = Math.floor(min / 60);
    const m = min % 60;
    return `SLA: ${h}h${m > 0 ? m + 'm' : ''} restantes`;
  }

  function renderFormNovo() {
    viewNovo.replaceChildren();
    const form = el('form', 'hd-form-grid');

    const lblTipo = el('label', '', 'Qual é o problema?');
    const selTipo = el('select');
    selTipo.name = 'tipoCodigo';
    selTipo.required = true;
    selTipo.append(new Option('Selecione uma categoria', ''));
    selTipo.setAttribute('aria-describedby', 'hd-category-help');
    for (const t of tiposSla) {
      selTipo.append(new Option(t.nome, t.codigo));
    }
    lblTipo.append(selTipo);

    const preview = el('div', 'hd-category-help');
    preview.id = 'hd-category-help';
    preview.setAttribute('aria-live', 'polite');
    const updatePreview = () => {
      const t = tiposSla.find(x => x.codigo === selTipo.value);
      preview.hidden = !t;
      if (!t) {
        preview.textContent = 'Dica: escolha a categoria para direcionarmos sua solicitacao com o prazo adequado.';
        return;
      }
      preview.replaceChildren(el('span', '', t.descricao), el('span', '', `Prazo previsto: ${t.slaHoras} horas`));
    };
    selTipo.addEventListener('change', updatePreview);
    updatePreview();

    const lblTitulo = el('label', '', 'Resumo em uma frase');
    const inpTitulo = el('input');
    inpTitulo.type = 'text';
    inpTitulo.name = 'titulo';
    inpTitulo.required = true;
    inpTitulo.maxLength = 180;
    inpTitulo.placeholder = 'Ex.: Divergencia no valor da competencia 08/2026';
    lblTitulo.append(inpTitulo);

    const lblDesc = el('label', '', 'Detalhes do que aconteceu');
    const txtDesc = el('textarea');
    txtDesc.name = 'descricao';
    txtDesc.required = true;
    txtDesc.rows = 4;
    txtDesc.maxLength = 4000;
    txtDesc.placeholder = 'Conte os passos para reproduzir, informando se possivel competencia, medico ou hospital envolvido.';
    lblDesc.append(txtDesc);

    const lblFiles = el('label', '', 'Anexar prints ou planilhas (opcional)');
    const inpFiles = el('input');
    inpFiles.type = 'file';
    inpFiles.name = 'arquivos';
    inpFiles.multiple = true;
    inpFiles.accept = '.png,.jpg,.jpeg,.webp,.pdf,image/png,image/jpeg,image/webp,application/pdf';
    const helpFiles = el('small', '', 'Envie ate 5 arquivos (PNG, JPG, WEBP, PDF) de ate 5 MB cada.');
    const selectedFiles = el('small', 'hd-upload-files', '');
    inpFiles.addEventListener('change', () => {
      selectedFiles.textContent = Array.from(inpFiles.files || []).map(file => file.name).join(', ');
    });
    lblFiles.append(inpFiles, helpFiles, selectedFiles);

    const actions = el('div', 'hd-form-actions');
    const btnSubmit = el('button', 'primary-button', 'Enviar chamado');
    btnSubmit.type = 'submit';
    actions.append(btnSubmit);

    form.append(lblTipo, preview, lblTitulo, lblDesc, lblFiles, actions);

    form.addEventListener('submit', async e => {
      e.preventDefault();
      btnSubmit.disabled = true;
      showFeedback('');
      try {
        const fd = new FormData();
        fd.append('tipoCodigo', selTipo.value);
        fd.append('titulo', inpTitulo.value.trim());
        fd.append('descricao', txtDesc.value.trim());
        for (const file of inpFiles.files || []) fd.append('arquivos', file);
        fd.append('paginaOrigem', location.pathname + location.hash);
        const resp = await FaturamedAuth.request('/api/chamados', {
          method: 'POST',
          body: fd
        });
        showFeedback(`Chamado registrado com sucesso! Protocolo: ${resp.chamado.protocolo}`);
        form.reset();
        selectedFiles.textContent = '';
        updatePreview();
        await carregarResumoELista();
        abrirDetalheChamado(resp.chamado.id);
      } catch (err) {
        showFeedback(err.message, true);
        btnSubmit.disabled = false;
      }
    });

    viewNovo.append(form);
  }

  function renderListaMeus() {
    viewMeus.replaceChildren();
    if (!meusChamados.length) {
      const empty = el('div', 'hd-empty-state');
      empty.append(
        el('p', '', 'Você ainda não possui chamados abertos.'),
        el('small', '', 'Clique na aba "Novo chamado" para registrar uma duvida ou incidente.')
      );
      viewMeus.append(empty);
      return;
    }
    const list = el('div', 'hd-ticket-list');
    for (const c of meusChamados) {
      const card = el('article', 'hd-ticket-card');
      card.dataset.id = c.id;
      const top = el('div', 'hd-ticket-top');
      top.append(
        el('span', 'hd-protocol', c.protocolo),
        el('span', `hd-badge st-${c.status.toLowerCase()}`, STATUS_LABELS[c.status] || c.status)
      );
      const title = el('h3', 'hd-ticket-title', c.titulo);
      const meta = el('div', 'hd-ticket-meta');
      meta.append(
        el('span', `hd-badge fila-${c.filaDestino.toLowerCase()}`, `Fila ${c.filaDestino}`),
        el('span', `hd-badge sla-${c.slaStatus.toLowerCase()}`, formatSlaRestante(c)),
        el('span', '', `${c.tipoNome} • ${new Date(c.atualizadoEm).toLocaleDateString('pt-BR')}`)
      );
      card.append(top, title, meta);
      card.addEventListener('click', () => abrirDetalheChamado(c.id));
      list.append(card);
    }
    viewMeus.append(list);
  }

  function renderDetalhe(det) {
    activeTicketId = det.chamado.id;
    tabDetalhe.hidden = false;
    tabDetalhe.textContent = `Protocolo ${det.chamado.protocolo}`;
    viewDetalhe.replaceChildren();

    const c = det.chamado;
    const isCancelado = c.status === 'CANCELADO';
    const headCard = el('div', 'hd-ticket-card');
    headCard.style.cursor = 'default';
    const top = el('div', 'hd-ticket-top');
    top.append(
      el('span', 'hd-protocol', `${c.protocolo} — ${c.tipoNome}`),
      el('span', `hd-badge st-${c.status.toLowerCase()}`, STATUS_LABELS[c.status] || c.status)
    );
    const h3 = el('h3', 'hd-ticket-title', c.titulo);
    const desc = el('p', '', c.descricao);
    desc.style.whiteSpace = 'pre-wrap';
    desc.style.fontSize = '13px';
    desc.style.margin = '6px 0';

    const meta = el('div', 'hd-ticket-meta');
    meta.append(
      el('span', `hd-badge fila-${c.filaDestino.toLowerCase()}`, `Fila ${c.filaDestino}`),
      el('span', `hd-badge sla-${c.slaStatus.toLowerCase()}`, formatSlaRestante(c)),
      el('span', '', `Responsavel: ${c.responsavelNome || 'Aguardando atribuicao'}`),
      el('span', '', c.paginaOrigem ? `Tela: ${c.paginaOrigem}` : '')
    );
    headCard.append(top, h3, desc, meta);
    viewDetalhe.append(headCard);

    if (isCancelado) {
      const cancelBox = el('div', 'hd-rating-box');
      cancelBox.append(
        el('strong', '', `🚫 Chamado cancelado em ${new Date(c.canceladoEm || c.atualizadoEm).toLocaleString('pt-BR')}`),
        el('p', '', `Motivo: ${c.motivoCancelamento || 'Não informado'}`),
        c.justificativaCancelamento ? el('p', '', `Justificativa: "${c.justificativaCancelamento}"`) : el('span')
      );
      viewDetalhe.append(cancelBox);
    }

    // Cancelamento disponível para o próprio solicitante antes do atendimento
    if (currentUser && c.solicitanteId === currentUser.id && ['ABERTO', 'AGUARDANDO_USUARIO'].includes(c.status)) {
      const cancelWrap = el('div');
      cancelWrap.style.margin = '8px 0';
      const btnCancel = el('button', 'text-button', 'Cancelar este chamado');
      btnCancel.type = 'button';
      btnCancel.style.color = 'var(--rose, #e11d48)';
      btnCancel.style.fontSize = '12px';

      const cancelForm = el('div');
      cancelForm.hidden = true;
      cancelForm.style.padding = '12px';
      cancelForm.style.borderRadius = '8px';
      cancelForm.style.background = 'var(--surface)';
      cancelForm.style.border = '1px solid var(--line)';
      cancelForm.style.display = 'flex';
      cancelForm.style.flexDirection = 'column';
      cancelForm.style.gap = '8px';

      const lblMotivo = el('label', '', 'Motivo:');
      lblMotivo.style.fontSize = '12px';
      const selMotivo = el('select');
      selMotivo.style.padding = '8px';
      selMotivo.style.borderRadius = '6px';
      selMotivo.style.border = '1px solid var(--line)';
      selMotivo.style.background = 'var(--surface)';
      selMotivo.style.color = 'var(--text)';
      const MOTIVOS = {
        ABERTURA_INCORRETA: 'Abertura incorreta / Sem problema real',
        DUPLICADO: 'Chamado duplicado',
        CANCELADO_SOLICITANTE: 'Cancelado pelo solicitante',
        NAO_REPRODUZIVEL: 'Não reproduzível / Sem retorno',
        OUTRO: 'Outro motivo'
      };
      for (const [k, v] of Object.entries(MOTIVOS)) selMotivo.append(new Option(v, k));
      lblMotivo.append(selMotivo);

      const lblJust = el('label', '', 'Justificativa (obrigatória):');
      lblJust.style.fontSize = '12px';
      const txtJust = el('textarea');
      txtJust.rows = 2;
      txtJust.maxLength = 2000;
      txtJust.style.padding = '8px';
      txtJust.style.borderRadius = '6px';
      txtJust.style.border = '1px solid var(--line)';
      txtJust.style.background = 'var(--surface)';
      txtJust.style.color = 'var(--text)';
      txtJust.placeholder = 'Explique o motivo do cancelamento...';
      lblJust.append(txtJust);

      const rowBtn = el('div');
      rowBtn.style.display = 'flex';
      rowBtn.style.gap = '8px';
      const btnConf = el('button', 'primary-button', 'Confirmar cancelamento');
      btnConf.type = 'button';
      btnConf.style.background = 'var(--rose, #e11d48)';
      btnConf.style.borderColor = 'var(--rose, #e11d48)';
      const btnVoltar = el('button', 'secondary-button', 'Voltar');
      btnVoltar.type = 'button';
      rowBtn.append(btnConf, btnVoltar);
      cancelForm.append(lblMotivo, lblJust, rowBtn);

      btnCancel.addEventListener('click', () => { btnCancel.hidden = true; cancelForm.hidden = false; });
      btnVoltar.addEventListener('click', () => { cancelForm.hidden = true; btnCancel.hidden = false; });
      btnConf.addEventListener('click', async () => {
        const just = txtJust.value.trim();
        if (!just) { showFeedback('Informe a justificativa do cancelamento.', true); return; }
        btnConf.disabled = true;
        try {
          const atualizado = await FaturamedAuth.request(`/api/chamados/${c.id}/cancelar`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ motivo: selMotivo.value, justificativa: just })
          });
          await carregarResumoELista();
          renderDetalhe(atualizado);
          showFeedback('Chamado cancelado com sucesso.');
        } catch (err) {
          showFeedback(err.message, true);
          btnConf.disabled = false;
        }
      });

      cancelWrap.append(btnCancel, cancelForm);
      viewDetalhe.append(cancelWrap);
    }

    if (!isCancelado && currentUser && c.solicitanteId === currentUser.id && (c.status === 'RESOLVIDO' || c.status === 'FECHADO')) {
      const ratingBox = el('div', 'hd-rating-box');
      if (c.notaAvaliacao) {
        ratingBox.append(
          el('strong', '', `Sua avaliacao do atendimento: ${'★'.repeat(c.notaAvaliacao)}${'☆'.repeat(5 - c.notaAvaliacao)} (${c.notaAvaliacao}/5)`),
          c.comentarioAvaliacao ? el('p', '', `"${c.comentarioAvaliacao}"`) : el('span')
        );
      } else {
        ratingBox.append(el('strong', '', 'O suporte marcou este chamado como Resolvido. Avalie o atendimento para concluir:'));
        let notaEscolhida = 5;
        const starsWrap = el('div', 'hd-stars');
        const starBtns = [];
        for (let n = 1; n <= 5; n++) {
          const b = el('button', 'hd-star-btn' + (n === 5 ? ' selected' : ''), `${n} ★`);
          b.type = 'button';
          b.addEventListener('click', () => {
            notaEscolhida = n;
            starBtns.forEach((btn, idx) => btn.classList.toggle('selected', idx + 1 === n));
          });
          starBtns.push(b);
          starsWrap.append(b);
        }
        const inpCom = el('input');
        inpCom.placeholder = 'Comentario opcional sobre o atendimento...';
        inpCom.maxLength = 1500;
        inpCom.style.width = '100%';
        inpCom.style.padding = '9px 12px';
        inpCom.style.marginBottom = '10px';
        inpCom.style.borderRadius = '10px';
        inpCom.style.border = '1px solid var(--line)';
        inpCom.style.background = 'var(--surface)';
        inpCom.style.color = 'var(--text)';

        const btnAvaliar = el('button', 'primary-button', 'Enviar Avaliacao e Encerrar Chamado');
        btnAvaliar.type = 'button';
        btnAvaliar.addEventListener('click', async () => {
          btnAvaliar.disabled = true;
          try {
            const atualizado = await FaturamedAuth.request(`/api/chamados/${c.id}/avaliacao`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ nota: notaEscolhida, comentario: inpCom.value.trim() })
            });
            await carregarResumoELista();
            renderDetalhe(atualizado);
            showFeedback('Obrigado! Sua avaliacao foi registrada.');
          } catch (err) {
            showFeedback(err.message, true);
            btnAvaliar.disabled = false;
          }
        });
        ratingBox.append(starsWrap, inpCom, btnAvaliar);
      }
      viewDetalhe.append(ratingBox);
    }

    const thread = el('div', 'hd-chat-thread');
    for (const m of det.mensagens) {
      const item = el('div', 'hd-msg' + (m.tipo === 'EVENTO_SISTEMA' ? ' evento' : ''));
      const mHead = el('div', 'hd-msg-head');
      mHead.append(
        el('strong', '', `${m.autorNome} (${m.autorPerfil})`),
        el('time', '', new Date(m.criadoEm).toLocaleString('pt-BR'))
      );
      const mBody = el('div', '', m.conteudo);
      mBody.style.whiteSpace = 'pre-wrap';
      item.append(mHead, mBody);

      if (m.anexos && m.anexos.length) {
        const attWrap = el('div', 'hd-msg-attachments');
        for (const a of m.anexos) {
          const link = el('a', 'hd-attachment-thumb', `📎 ${a.nomeArquivo}`);
          link.href = a.url;
          link.target = '_blank';
          link.rel = 'noopener';
          attWrap.append(link);
        }
        item.append(attWrap);
      }
      thread.append(item);
    }
    viewDetalhe.append(thread);

    if (!isCancelado && c.status !== 'FECHADO') {
      const replyForm = el('form', 'hd-form-grid');
      replyForm.style.marginTop = '10px';
      const lblMsg = el('label', '', 'Responder no protocolo');
      const txtMsg = el('textarea');
      txtMsg.rows = 3;
      txtMsg.maxLength = 4000;
      txtMsg.placeholder = 'Escreva uma mensagem para o atendimento...';
      lblMsg.append(txtMsg);

      const lblFile = el('label', '', 'Anexar imagem / arquivo (opcional)');
      const inpFile = el('input');
      inpFile.type = 'file';
      inpFile.multiple = true;
      inpFile.accept = '.png,.jpg,.jpeg,.webp,.pdf,image/png,image/jpeg,image/webp,application/pdf';
      lblFile.append(inpFile);

      const btnSend = el('button', 'primary-button', 'Enviar Mensagem');
      btnSend.type = 'submit';
      replyForm.append(lblMsg, lblFile, btnSend);

      replyForm.addEventListener('submit', async e => {
        e.preventDefault();
        btnSend.disabled = true;
        try {
          const fd = new FormData();
          fd.append('conteudo', txtMsg.value.trim());
          for (const f of inpFile.files) fd.append('arquivos', f);
          const atualizado = await FaturamedAuth.request(`/api/chamados/${c.id}/mensagens`, {
            method: 'POST',
            body: fd
          });
          await carregarResumoELista();
          renderDetalhe(atualizado);
          showFeedback('Mensagem enviada.');
        } catch (err) {
          showFeedback(err.message, true);
        } finally {
          btnSend.disabled = false;
        }
      });
      viewDetalhe.append(replyForm);
    }
  }

  const rotulosAviso = {
    RETORNO_SOLICITADO: 'Suporte aguarda sua resposta',
    RESPOSTA_SOLICITANTE: 'Nova resposta do solicitante',
    MENSAGEM_SUPORTE: 'Nova mensagem do suporte',
    CHAMADO_CANCELADO: 'Chamado cancelado / descartado'
  };
  async function carregarAvisos() {
    const caixa = await FaturamedAuth.request('/api/chamados/notificacoes');
    avisosPendentes = caixa.itens || [];
    const total = caixa.naoLidas || 0;
    fabBadge.hidden = total === 0;
    fabBadge.textContent = total > 99 ? '99+' : String(total);
    fab.title = total ? `Service Desk: ${total} aviso(s) não lido(s)` : 'Service Desk: nenhum aviso novo';
    fab.setAttribute('aria-label',fab.title);
    avisosBox.replaceChildren();
    avisosBox.hidden = total === 0;
    if (total) avisosBox.append(el('strong','','Avisos não lidos ('+total+')'));
    for (const aviso of avisosPendentes) {
      const abrir = el('button','hd-aviso',`${rotulosAviso[aviso.tipo] || 'Atualização no chamado'} • ${aviso.protocolo} — ${aviso.titulo}`);
      abrir.type = 'button';
      abrir.addEventListener('click',()=>abrirDetalheChamado(aviso.chamadoId));
      avisosBox.append(abrir);
    }
  }

  async function carregarResumoELista() {
    try {
      if (!currentUser) {
        currentUser = await FaturamedAuth.request('/api/auth/me');
        const isSuporte = currentUser.perfil === 'ADMIN' || currentUser.perfil === 'DEV';
        kanbanLink.hidden = !isSuporte && currentUser.perfil !== 'OPERADOR';
        kanbanLink.textContent = isSuporte ? 'Painel de atendimento ↗' : 'Acompanhar chamados';
      }
      if (!tiposSla.length) {
        tiposSla = await FaturamedAuth.request('/api/chamados/tipos');
        renderFormNovo();
      }
      const painel = await FaturamedAuth.request('/api/chamados?meus=true');
      meusChamados = painel.chamados || [];
      tabMeus.textContent = `Meus chamados (${meusChamados.length})`;
      renderListaMeus();
      await carregarAvisos();
    } catch (err) {
      showFeedback(err.message, true);
    }
  }

  async function abrirDetalheChamado(id) {
    showFeedback('');
    try {
      const det = await FaturamedAuth.request(`/api/chamados/${id}`);
      renderDetalhe(det);
      switchTab('detalhe');
      const avisosDeste = avisosPendentes.filter(a => a.chamadoId === id);
      if (avisosDeste.length) {
        const maior = Math.max(...avisosDeste.map(a => a.id));
        await FaturamedAuth.request(`/api/chamados/${id}/notificacoes/leitura?ateId=${maior}`, { method: 'POST' });
        await carregarAvisos();
      }
    } catch (err) {
      showFeedback(err.message, true);
    }
  }

  fab.addEventListener('click', async () => {
    dialog.showModal();
    await carregarResumoELista();
  });

  btnClose.addEventListener('click', () => {
    dialog.close();
  });

  tabNovo.addEventListener('click', () => switchTab('novo'));
  tabMeus.addEventListener('click', () => switchTab('meus'));
  tabDetalhe.addEventListener('click', () => switchTab('detalhe'));

  // Carrega contadores em background se logado
  setTimeout(async () => {
    try {
      await carregarResumoELista();
    } catch (_) {}
  }, 1200);
})();
