(() => {
  if (document.getElementById('hd-fab-button') || location.pathname === '/login.html') return;

  const el = (tag, className, text) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  };

  // Vector icon: Customer Support & Service Desk (Headset + Speech Bubble in Faturamed Liquid Glass)
  const SUPPORT_GLASS_SVG = `
    <svg viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <defs>
        <linearGradient id="fm-headset-grad" x1="18" y1="16" x2="82" y2="86" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="#FFFFFF"/>
          <stop offset="35%" stop-color="#E5F3F6"/>
          <stop offset="70%" stop-color="#95D3D5"/>
          <stop offset="100%" stop-color="#66AFC7"/>
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
    FECHADO: 'Fechado'
  };

  const SLA_LABELS = {
    NO_PRAZO: 'SLA no prazo',
    EM_ALERTA: 'SLA em alerta',
    VENCIDO: 'SLA vencido',
    CUMPRIDO: 'SLA cumprido',
    FORA_DO_PRAZO: 'Concluido fora do SLA'
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

  const headerActions = el('div', 'hd-header-actions');
  const kanbanLink = el('a', 'hd-kanban-link', 'Painel de atendimento ↗');
  kanbanLink.href = '/chamados.html';
  kanbanLink.hidden = true;
  const closeBtn = el('button', 'hd-close-btn', '×');
  closeBtn.type = 'button';
  closeBtn.title = 'Fechar';
  closeBtn.setAttribute('aria-label', 'Fechar central de suporte');
  headerActions.append(closeBtn);
  header.append(brandHead, headerActions);

  const tabs = el('div', 'hd-tabs');
  const tabNovo = el('button', 'hd-tab active', 'Novo chamado');
  tabNovo.type = 'button';
  const tabMeus = el('button', 'hd-tab', 'Meus chamados');
  tabMeus.type = 'button';
  const tabDetalhe = el('button', 'hd-tab', 'Protocolo Selecionado');
  tabDetalhe.type = 'button';
  tabDetalhe.hidden = true;
  tabs.append(tabNovo, tabMeus, tabDetalhe);

  const body = el('div', 'hd-dialog-body');
  const feedback = el('div', 'hd-status-banner');
  feedback.hidden = true;

  const viewNovo = el('div');
  const viewMeus = el('div');
  viewMeus.hidden = true;
  const viewDetalhe = el('div');
  viewDetalhe.hidden = true;

  const avisosBox = el('section', 'hd-avisos');
  avisosBox.setAttribute('aria-label','Avisos de chamados');
  avisosBox.hidden = true;
  body.append(feedback, avisosBox, viewNovo, viewMeus, viewDetalhe);
  const footer = el('footer', 'hd-dialog-footer');
  footer.append(kanbanLink);
  shell.append(header, tabs, body, footer);
  dialog.append(shell);
  document.body.append(dialog);

  function showFeedback(text, isError = false) {
    if (!text) {
      feedback.hidden = true;
      return;
    }
    feedback.hidden = false;
    feedback.className = 'hd-status-banner ' + (isError ? 'err' : 'ok');
    feedback.textContent = text;
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

    const origemAtual = location.pathname + (location.hash || '');
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
      preview.replaceChildren();
      preview.hidden = !t;
      if (!t) return;
      preview.append(el('span', '', t.descricao),
        el('span', 'hd-deadline', `Prazo previsto: ${t.slaHoras} horas`));
    };
    selTipo.addEventListener('change', updatePreview);
    updatePreview();
    lblTipo.append(preview);

    const lblTitulo = el('label', '', 'Assunto');
    const inpTitulo = el('input');
    inpTitulo.name = 'titulo';
    inpTitulo.required = true;
    inpTitulo.maxLength = 180;
    inpTitulo.placeholder = 'Ex.: Não consigo importar uma planilha';
    lblTitulo.append(inpTitulo);

    const lblDesc = el('label', '', 'Descrição');
    const txtDesc = el('textarea');
    txtDesc.name = 'descricao';
    txtDesc.required = true;
    txtDesc.rows = 4;
    txtDesc.maxLength = 4000;
    txtDesc.placeholder = 'O que você tentou fazer e o que aconteceu? Se apareceu uma mensagem de erro, inclua aqui.';
    lblDesc.append(txtDesc);

    const anexos = el('div', 'hd-upload');
    const inpAnexos = el('input');
    inpAnexos.type = 'file';
    inpAnexos.hidden = true;
    inpAnexos.multiple = true;
    inpAnexos.accept = '.png,.jpg,.jpeg,.webp,.pdf,image/png,image/jpeg,image/webp,application/pdf';
    const anexarBtn = el('button', 'hd-upload-button', '+ Adicionar arquivos');
    anexarBtn.type = 'button';
    anexarBtn.addEventListener('click', () => inpAnexos.click());
    const ajudaAnexos = el('span', 'hd-upload-help', 'Opcional · PNG, JPG, WEBP ou PDF. Até 5 arquivos de 5 MB cada.');
    const arquivosSelecionados = el('div', 'hd-upload-files');
    arquivosSelecionados.setAttribute('aria-live', 'polite');
    const atualizarAnexos = () => {
      arquivosSelecionados.textContent = Array.from(inpAnexos.files, f => f.name).join(', ');
    };
    inpAnexos.addEventListener('change', atualizarAnexos);
    anexos.append(anexarBtn, ajudaAnexos, arquivosSelecionados, inpAnexos);

    const submitBtn = el('button', 'primary-button', 'Enviar chamado');
    submitBtn.type = 'submit';

    form.append(lblTipo, lblTitulo, lblDesc, anexos, submitBtn);
    form.addEventListener('submit', async e => {
      e.preventDefault();
      submitBtn.disabled = true;
      showFeedback('Registrando chamado...');
      try {
        const fd = new FormData();
        fd.append('tipoCodigo', selTipo.value);
        fd.append('titulo', inpTitulo.value.trim());
        fd.append('descricao', txtDesc.value.trim());
        fd.append('paginaOrigem', location.pathname + (location.hash || ''));
        for (const file of inpAnexos.files) {
          fd.append('arquivos', file);
        }
        const detalhe = await FaturamedAuth.request('/api/chamados', { method: 'POST', body: fd });
        form.reset();
        atualizarAnexos();
        updatePreview();
        await carregarResumoELista();
        abrirDetalheChamado(detalhe.chamado.id, `Chamado registrado com sucesso! Protocolo: ${detalhe.chamado.protocolo}`);
      } catch (err) {
        showFeedback(err.message, true);
      } finally {
        submitBtn.disabled = false;
      }
    });

    viewNovo.append(form);
  }

  function renderListaMeus() {
    viewMeus.replaceChildren();
    if (!meusChamados.length) {
      viewMeus.append(el('p', '', 'Você ainda não possui protocolos registrados.'));
      return;
    }
    const list = el('div', 'hd-ticket-list');
    for (const c of meusChamados) {
      const card = el('article', 'hd-ticket-card');
      const top = el('div', 'hd-ticket-top');
      const prot = el('span', 'hd-protocol', c.protocolo);
      const badges = el('div');
      badges.style.display = 'flex';
      badges.style.gap = '6px';
      const bFila = el('span', `hd-badge fila-${c.filaDestino.toLowerCase()}`, `Fila ${c.filaDestino}`);
      const bStatus = el('span', `hd-badge st-${c.status.toLowerCase()}`, STATUS_LABELS[c.status] || c.status);
      const bSla = el('span', `hd-badge sla-${c.slaStatus.toLowerCase()}`, formatSlaRestante(c));
      badges.append(bFila, bStatus, bSla);
      top.append(prot, badges);

      const h3 = el('h3', 'hd-ticket-title', c.titulo);
      const meta = el('div', 'hd-ticket-meta');
      meta.append(
        el('span', '', c.tipoNome),
        el('span', '', `• Atualizado em ${new Date(c.atualizadoEm).toLocaleString('pt-BR')}`),
        el('span', '', `• ${c.totalMensagens} msg(s)`)
      );
      if (c.notaAvaliacao) {
        meta.append(el('span', '', `• Avaliacao: ${'★'.repeat(c.notaAvaliacao)}`));
      } else if (c.status === 'RESOLVIDO') {
        meta.append(el('strong', '', '• Aguardando sua avaliacao'));
      }
      card.append(top, h3, meta);
      card.addEventListener('click', () => abrirDetalheChamado(c.id));
      list.append(card);
    }
    viewMeus.append(list);
  }

  async function abrirDetalheChamado(id, bannerMsg = '') {
    activeTicketId = id;
    tabDetalhe.hidden = false;
    switchTab('detalhe');
    if (bannerMsg) showFeedback(bannerMsg, false);
    viewDetalhe.replaceChildren(el('p', '', 'Carregando protocolo...'));
    const ateAvisoId = Math.max(0, ...avisosPendentes.map(a => a.id));
    try {
      const det = await FaturamedAuth.request(`/api/chamados/${id}`);
      renderDetalhe(det);
      if (ateAvisoId) await FaturamedAuth.request(`/api/chamados/${id}/notificacoes/leitura?ateId=${ateAvisoId}`, {method:'POST'});
      await carregarAvisos();
    } catch (err) {
      showFeedback(err.message, true);
    }
  }

  function renderDetalhe(det) {
    const c = det.chamado;
    tabDetalhe.textContent = c.protocolo;
    viewDetalhe.replaceChildren();

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

    if (currentUser && c.solicitanteId === currentUser.id && (c.status === 'RESOLVIDO' || c.status === 'FECHADO')) {
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

    if (c.status !== 'FECHADO') {
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
    MENSAGEM_SUPORTE: 'Nova mensagem do suporte'
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
        if (currentUser.perfil === 'OPERADOR') kanbanLink.textContent = 'Acompanhar chamados';
      }
      if (!tiposSla.length) {
        tiposSla = await FaturamedAuth.request('/api/chamados/tipos');
        renderFormNovo();
      }
      const painel = await FaturamedAuth.request('/api/chamados?meus=true');
      meusChamados = painel.chamados || [];
      tabMeus.textContent = `Meus chamados (${meusChamados.length})`;

      await carregarAvisos();

      renderListaMeus();
    } catch (_) {
      // Silencioso caso a sessao ainda nao esteja autenticada
    }
  }

  tabNovo.addEventListener('click', () => switchTab('novo'));
  tabMeus.addEventListener('click', () => {
    carregarResumoELista();
    switchTab('meus');
  });
  tabDetalhe.addEventListener('click', () => {
    if (activeTicketId) switchTab('detalhe');
  });

  fab.addEventListener('click', async () => {
    await carregarResumoELista();
    if (!dialog.open) dialog.showModal();
  });

  closeBtn.addEventListener('click', () => dialog.close());
  setInterval(() => {
    if (!document.hidden) carregarAvisos().catch(() => {});
  }, 30000);

  carregarResumoELista();
})();
