(() => {
  if (document.getElementById('hd-fab-button') || location.pathname === '/login.html') return;

  const el = (tag, className, text) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  };

  // Vector reproduction of the interlocked "BR" glass monogram from the reference icon
  const BR_GLASS_SVG = `
    <svg viewBox="0 0 120 100" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <defs>
        <linearGradient id="br-silver-b" x1="12" y1="14" x2="54" y2="86" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="#FFFFFF"/>
          <stop offset="55%" stop-color="#E2E8F0"/>
          <stop offset="82%" stop-color="#A78BFA"/>
          <stop offset="100%" stop-color="#7C3AED"/>
        </linearGradient>
        <linearGradient id="br-slash" x1="30" y1="46" x2="75" y2="86" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="#E879F9"/>
          <stop offset="45%" stop-color="#8B5CF6"/>
          <stop offset="100%" stop-color="#4F46E5"/>
        </linearGradient>
        <linearGradient id="br-r-loop" x1="56" y1="14" x2="108" y2="86" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="#F472B6"/>
          <stop offset="28%" stop-color="#A855F7"/>
          <stop offset="52%" stop-color="#6366F1"/>
          <stop offset="72%" stop-color="#F8FAFC"/>
          <stop offset="100%" stop-color="#FFFFFF"/>
        </linearGradient>
      </defs>
      <!-- Left 'B' outer stem, upper loop, and lower curve -->
      <path d="M12 15H44C55.5 15 62 22 62 32C62 38.8 58.2 44.2 52.5 46.8L46 43.5C48.5 41.2 49.8 38 49.8 34C49.8 28.8 46.2 26 40.5 26H25V74H41.5C46.8 74 50.5 71.5 52.8 67.5L60.5 76.5C56.2 82.2 49.8 85 41 85H12V15Z" fill="url(#br-silver-b)"/>
      <!-- Middle-to-bottom diagonal glowing violet leg of the 'B' -->
      <path d="M31 46H49.5C54.5 46 58.8 48.2 62 52.2L86 85H70.5L51.5 58.5C49.5 55.8 46.8 54.5 43 54.5H37.5L31 46Z" fill="url(#br-slash)"/>
      <!-- Right interlocked 'R' top loop and diagonal leg -->
      <path d="M56 15H86C99.5 15 108 23.2 108 35.5C108 45.8 102.2 53.2 92.8 55.8L110 85H94.5L78.5 57.5H65.5L57.5 46.5H83.5C90.2 46.5 94.5 42.5 94.5 36C94.5 29.5 90.2 26 83.5 26H64L56 15Z" fill="url(#br-r-loop)"/>
    </svg>
  `;

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
  const title = el('h2', '', 'Ambiente de Suporte & Service Desk');
  title.id = 'hd-dialog-title';
  const subtitle = el('p', '', 'Abra um protocolo eletronico, envie evidencias ou acompanhe o SLA em tempo real.');
  headerLeft.append(title, subtitle);
  brandHead.append(brandLogo, headerLeft);

  const headerActions = el('div', 'hd-header-actions');
  const kanbanLink = el('a', 'hd-kanban-link', 'Abrir Painel Kanban');
  kanbanLink.href = '/chamados.html';
  kanbanLink.hidden = true;
  const closeBtn = el('button', 'hd-close-btn', '×');
  closeBtn.type = 'button';
  closeBtn.title = 'Fechar';
  headerActions.append(kanbanLink, closeBtn);
  header.append(brandHead, headerActions);

  const tabs = el('div', 'hd-tabs');
  const tabNovo = el('button', 'hd-tab active', 'Novo Chamado');
  tabNovo.type = 'button';
  const tabMeus = el('button', 'hd-tab', 'Meus Protocolos');
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

  body.append(feedback, viewNovo, viewMeus, viewDetalhe);
  shell.append(header, tabs, body);
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
    const lblTipo = el('label', '', 'Categoria do Problema / Solicitacao');
    const selTipo = el('select');
    selTipo.name = 'tipoCodigo';
    selTipo.required = true;
    for (const t of tiposSla) {
      selTipo.append(new Option(`${t.nome} (SLA ${t.slaHoras}h)`, t.codigo));
    }
    lblTipo.append(selTipo);

    const preview = el('div', 'hd-sla-preview');
    const updatePreview = () => {
      const t = tiposSla.find(x => x.codigo === selTipo.value) || tiposSla[0];
      preview.replaceChildren();
      if (!t) return;
      const desc = el('div', '', t.descricao);
      const meta = el('div');
      const bFila = el('span', `hd-badge fila-${t.filaPadrao.toLowerCase()}`,
        t.filaPadrao === 'DEV' ? 'Fila: Equipe DEV' : 'Fila: Equipe ADMIN');
      const bSla = el('span', 'hd-badge sla-no_prazo', `Prazo SLA: ${t.slaHoras}h (${t.prioridadePadrao})`);
      meta.style.display = 'flex';
      meta.style.gap = '6px';
      meta.append(bFila, bSla);
      preview.append(desc, meta);
    };
    selTipo.addEventListener('change', updatePreview);
    updatePreview();

    const lblTitulo = el('label', '', 'Resumo / Titulo do chamado');
    const inpTitulo = el('input');
    inpTitulo.name = 'titulo';
    inpTitulo.required = true;
    inpTitulo.maxLength = 180;
    inpTitulo.placeholder = 'Ex.: Divergencia ao importar planilha ou erro na tela';
    lblTitulo.append(inpTitulo);

    const lblDesc = el('label', '', 'Descricao detalhada');
    const txtDesc = el('textarea');
    txtDesc.name = 'descricao';
    txtDesc.required = true;
    txtDesc.rows = 4;
    txtDesc.maxLength = 4000;
    txtDesc.placeholder = 'Explique o que aconteceu, passos realizados e como podemos ajudar...';
    lblDesc.append(txtDesc);

    const lblOrigem = el('label', '', 'Tela / Modulo de origem');
    const inpOrigem = el('input');
    inpOrigem.name = 'paginaOrigem';
    inpOrigem.value = origemAtual;
    inpOrigem.maxLength = 250;
    lblOrigem.append(inpOrigem);

    const lblAnexos = el('label', '', 'Capturas de tela / Evidencias (PNG, JPG, WEBP ou PDF - ate 5 MB)');
    const inpAnexos = el('input');
    inpAnexos.type = 'file';
    inpAnexos.multiple = true;
    inpAnexos.accept = '.png,.jpg,.jpeg,.webp,.pdf,image/png,image/jpeg,image/webp,application/pdf';
    lblAnexos.append(inpAnexos);

    const submitBtn = el('button', 'primary-button', 'Registrar Chamado e Gerar Protocolo');
    submitBtn.type = 'submit';

    form.append(lblTipo, preview, lblTitulo, lblDesc, lblOrigem, lblAnexos, submitBtn);
    form.addEventListener('submit', async e => {
      e.preventDefault();
      submitBtn.disabled = true;
      showFeedback('Registrando chamado...');
      try {
        const fd = new FormData();
        fd.append('tipoCodigo', selTipo.value);
        fd.append('titulo', inpTitulo.value.trim());
        fd.append('descricao', txtDesc.value.trim());
        fd.append('paginaOrigem', inpOrigem.value.trim());
        for (const file of inpAnexos.files) {
          fd.append('arquivos', file);
        }
        const detalhe = await FaturamedAuth.request('/api/chamados', { method: 'POST', body: fd });
        form.reset();
        inpOrigem.value = location.pathname + (location.hash || '');
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
      viewMeus.append(el('p', '', 'Voce ainda nao possui protocolos registrados.'));
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
    try {
      const det = await FaturamedAuth.request(`/api/chamados/${id}`);
      renderDetalhe(det);
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
        inpCom.style.border = '1px solid rgba(255,255,255,0.22)';
        inpCom.style.background = 'rgba(0,0,0,0.25)';
        inpCom.style.color = '#fff';

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

  async function carregarResumoELista() {
    try {
      if (!currentUser) {
        currentUser = await FaturamedAuth.request('/api/auth/me');
        const isSuporte = currentUser.perfil === 'ADMIN' || currentUser.perfil === 'DEV';
        kanbanLink.hidden = !isSuporte;
      }
      if (!tiposSla.length) {
        tiposSla = await FaturamedAuth.request('/api/chamados/tipos');
        renderFormNovo();
      }
      const painel = await FaturamedAuth.request('/api/chamados?meus=true');
      meusChamados = painel.chamados || [];
      tabMeus.textContent = `Meus Protocolos (${meusChamados.length})`;

      const isSuporte = currentUser.perfil === 'ADMIN' || currentUser.perfil === 'DEV';
      const badgeCount = isSuporte
        ? (painel.resumo?.totalAbertos || 0)
        : meusChamados.filter(c => c.status !== 'FECHADO').length;
      fabBadge.hidden = badgeCount === 0;
      fabBadge.textContent = badgeCount > 99 ? '99+' : String(badgeCount);

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
    if (!document.hidden && !dialog.open) carregarResumoELista();
  }, 60000);

  carregarResumoELista();
})();
