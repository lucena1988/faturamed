(() => {
  const api = FaturamedAuth.request;
  const el = (tag, className, text) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  };

  const STATUS_LIST = ['ABERTO', 'EM_ATENDIMENTO', 'AGUARDANDO_USUARIO', 'RESOLVIDO', 'FECHADO'];
  const STATUS_LABELS = {
    ABERTO: 'Aberto (Triagem)',
    EM_ATENDIMENTO: 'Em Atendimento',
    AGUARDANDO_USUARIO: 'Aguardando Retorno',
    RESOLVIDO: 'Resolvido',
    FECHADO: 'Concluido / Avaliado'
  };

  let currentUser = null;
  const tecnico = () => ['ADMIN','DEV'].includes(currentUser?.perfil);
  let tiposSla = [];
  let atendentes = [];
  let chamados = [];

  const banner = document.querySelector('#sd-banner');
  const filtroFila = document.querySelector('#filtro-fila');
  const filtroTipo = document.querySelector('#filtro-tipo');
  const filtroBusca = document.querySelector('#filtro-busca');
  const filtroMeus = document.querySelector('#filtro-meus');
  const modal = document.querySelector('#chamado-modal');
  const modalBody = document.querySelector('#modal-body');
  const slaModal = document.querySelector('#sla-modal');

  function showBanner(msg, isErr = false) {
    if (!msg) {
      banner.hidden = true;
      return;
    }
    banner.hidden = false;
    banner.className = 'sd-banner ' + (isErr ? 'err' : 'ok');
    banner.textContent = msg;
  }

  function formatSlaText(c) {
    if (c.status === 'RESOLVIDO' || c.status === 'FECHADO') {
      return c.slaStatus === 'FORA_DO_PRAZO' ? 'Concluido fora do SLA' : 'SLA cumprido';
    }
    const min = c.minutosRestantes;
    if (min < 0) {
      return `Vencido ha ${Math.ceil(Math.abs(min) / 60)}h`;
    }
    if (min < 60) return `${min} min restantes`;
    const h = Math.floor(min / 60);
    const m = min % 60;
    return `${h}h${m > 0 ? m + 'm' : ''} restantes (SLA ${c.slaHoras}h)`;
  }

  function atualizarKpis(resumo) {
    if (!resumo) return;
    document.querySelector('#kpi-abertos').textContent = resumo.totalAbertos;
    document.querySelector('#kpi-filas-detalhe').textContent = `Fila DEV: ${resumo.filaDevAbertos} | Fila ADMIN: ${resumo.filaAdminAbertos}`;
    document.querySelector('#kpi-no-prazo').textContent = resumo.noPrazo;
    document.querySelector('#kpi-alerta').textContent = resumo.emAlerta;
    document.querySelector('#kpi-vencidos').textContent = resumo.vencidos;
    document.querySelector('#kpi-resolvidos').textContent = resumo.resolvidos;
    document.querySelector('#kpi-avaliacao').textContent = resumo.mediaAvaliacao != null ? `${resumo.mediaAvaliacao} ★` : '-';
    document.querySelector('#kpi-avaliacao-detalhe').textContent = resumo.totalAvaliacoes
      ? `${resumo.totalAvaliacoes} avaliacao(oes) registrada(s)`
      : 'Sem avaliacoes ate o momento';
  }

  function renderKanban() {
    document.querySelector('#kpi-atendimento').textContent = chamados.filter(c => c.status === 'EM_ATENDIMENTO').length;
    document.querySelector('#kpi-retorno').textContent = chamados.filter(c => c.status === 'AGUARDANDO_USUARIO').length;
    for (const st of STATUS_LIST) {
      const col = document.querySelector(`#col-${st}`);
      const cnt = document.querySelector(`#count-${st}`);
      const items = chamados.filter(c => c.status === st);
      cnt.textContent = String(items.length);
      col.replaceChildren();

      if (!items.length) {
        const empty = el('p', '', 'Nenhum chamado nesta etapa.');
        empty.style.fontSize = '12px';
        empty.style.color = 'var(--muted, #64748b)';
        empty.style.textAlign = 'center';
        empty.style.margin = '22px 0';
        col.append(empty);
        continue;
      }

      for (const c of items) {
        const card = el('article', `sd-card sla-${c.slaStatus.toLowerCase()}`);
        card.draggable = tecnico();
        card.dataset.id = c.id;
        card.addEventListener('dragstart', e => {
          e.dataTransfer.setData('text/plain', String(c.id));
        });

        const top = el('div', 'sd-card-top');
        top.append(
          el('span', 'sd-protocol-tag', c.protocolo),
          el('span', `sd-pill sla-${c.slaStatus.toLowerCase()}`, formatSlaText(c))
        );

        const title = el('h3', 'sd-card-title', c.titulo);

        const badges = el('div', 'sd-card-badges');
        badges.append(
          el('span', `sd-pill fila-${c.filaDestino.toLowerCase()}`, `Fila ${c.filaDestino}`),
          el('span', `sd-pill prio-${c.prioridade.toLowerCase()}`, c.prioridade),
          el('span', 'sd-pill', c.tipoNome)
        );

        const footer = el('div', 'sd-card-footer');
        footer.append(
          el('span', '', `Solicitante: ${c.solicitanteNome} (${c.solicitantePerfil})`),
          el('span', '', `Atendente: ${c.responsavelNome || 'Nao atribuido'}`),
          el('span', '', `💬 ${c.totalMensagens} | 📎 ${c.totalAnexos} • ${new Date(c.atualizadoEm).toLocaleString('pt-BR')}`)
        );
        if (c.notaAvaliacao) {
          footer.append(el('strong', '', `Avaliacao: ${'★'.repeat(c.notaAvaliacao)} (${c.notaAvaliacao}/5)`));
        }

        const actions = el('div', 'sd-card-actions');
        if (c.status === 'ABERTO') {
          const bIniciar = el('button', 'sd-mini-btn', 'Assumir / Iniciar');
          bIniciar.type = 'button';
          bIniciar.addEventListener('click', e => {
            e.stopPropagation();
            moverRapido(c.id, 'EM_ATENDIMENTO', true);
          });
          actions.append(bIniciar);
        } else if (c.status === 'EM_ATENDIMENTO') {
          const bAguardar = el('button', 'sd-mini-btn', 'Aguardar Usuario');
          bAguardar.type = 'button';
          bAguardar.addEventListener('click', e => {
            e.stopPropagation();
            moverRapido(c.id, 'AGUARDANDO_USUARIO', false);
          });
          const bResolver = el('button', 'sd-mini-btn', 'Marcar Resolvido');
          bResolver.type = 'button';
          bResolver.addEventListener('click', e => {
            e.stopPropagation();
            moverRapido(c.id, 'RESOLVIDO', false);
          });
          actions.append(bAguardar, bResolver);
        } else if (c.status === 'AGUARDANDO_USUARIO') {
          const bRetomar = el('button', 'sd-mini-btn', 'Retomar');
          bRetomar.type = 'button';
          bRetomar.addEventListener('click', e => {
            e.stopPropagation();
            moverRapido(c.id, 'EM_ATENDIMENTO', false);
          });
          const bResolver = el('button', 'sd-mini-btn', 'Resolver');
          bResolver.type = 'button';
          bResolver.addEventListener('click', e => {
            e.stopPropagation();
            moverRapido(c.id, 'RESOLVIDO', false);
          });
          actions.append(bRetomar, bResolver);
        } else if (c.status === 'RESOLVIDO') {
          const bFechar = el('button', 'sd-mini-btn', 'Fechar Chamado');
          bFechar.type = 'button';
          bFechar.addEventListener('click', e => {
            e.stopPropagation();
            moverRapido(c.id, 'FECHADO', false);
          });
          actions.append(bFechar);
        }

        card.append(top, title, badges, footer);
        if (tecnico()) card.append(actions);
        card.addEventListener('click', () => abrirModalChamado(c.id));
        col.append(card);
      }
    }
  }

  async function moverRapido(id, novoStatus, assumir = false) {
    if (!tecnico()) return;
    try {
      await api(`/api/chamados/${id}/status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: novoStatus, assumir })
      });
      await carregarChamados();
      showBanner(`Chamado atualizado para ${STATUS_LABELS[novoStatus]}.`);
    } catch (err) {
      showBanner(err.message, true);
    }
  }

  async function abrirModalChamado(id) {
    try {
      const det = await api(`/api/chamados/${id}`);
      renderModalDetalhe(det);
      if (!modal.open) modal.showModal();
    } catch (err) {
      showBanner(err.message, true);
    }
  }

  function renderModalDetalhe(det) {
    const c = det.chamado;
    document.querySelector('#modal-protocolo').textContent = `${c.protocolo} • ${c.tipoNome} • ${formatSlaText(c)}`;
    document.querySelector('#modal-titulo').textContent = c.titulo;
    modalBody.replaceChildren();

    const grid = el('div', 'sd-detail-grid');
    const leftCol = el('div');
    leftCol.style.display = 'flex';
    leftCol.style.flexDirection = 'column';
    leftCol.style.gap = '12px';

    const infoCard = el('div', 'sd-control-panel');
    const descTitle = el('strong', '', `Solicitante: ${c.solicitanteNome} (${c.solicitanteEmail} - Perfil ${c.solicitantePerfil})`);
    const origemTxt = el('small', '', `Tela de origem: ${c.paginaOrigem || 'Nao informada'} | Criado em: ${new Date(c.criadoEm).toLocaleString('pt-BR')} | Prazo SLA: ${new Date(c.prazoSlaEm).toLocaleString('pt-BR')}`);
    const descBody = el('p', '', c.descricao);
    descBody.style.whiteSpace = 'pre-wrap';
    descBody.style.margin = '6px 0 0';
    infoCard.append(descTitle, origemTxt, descBody);
    leftCol.append(infoCard);

    const thread = el('div', 'hd-chat-thread');
    thread.style.maxHeight = '360px';
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
          if (a.contentType && a.contentType.startsWith('image/')) {
            const img = el('img', 'sd-img-preview');
            img.src = a.url;
            img.alt = a.nomeArquivo;
            link.append(img);
          }
          attWrap.append(link);
        }
        item.append(attWrap);
      }
      thread.append(item);
    }
    leftCol.append(thread);

    const replyForm = el('form', 'sd-control-panel');
    const lblReply = el('label', '', 'Enviar mensagem / orientacao no protocolo');
    const txtReply = el('textarea');
    txtReply.rows = 3;
    txtReply.maxLength = 4000;
    txtReply.placeholder = 'Responda ao usuario ou registre evidencias da solucao...';
    lblReply.append(txtReply);

    const lblFiles = el('label', '', 'Anexar prints / arquivos (PNG, JPG, WEBP, PDF)');
    const inpFiles = el('input');
    inpFiles.type = 'file';
    inpFiles.multiple = true;
    inpFiles.accept = '.png,.jpg,.jpeg,.webp,.pdf,image/png,image/jpeg,image/webp,application/pdf';
    lblFiles.append(inpFiles);

    const btnSend = el('button', 'primary-button', 'Enviar Mensagem no Protocolo');
    btnSend.type = 'submit';
    replyForm.append(lblReply, lblFiles, btnSend);
    replyForm.addEventListener('submit', async e => {
      e.preventDefault();
      btnSend.disabled = true;
      try {
        const fd = new FormData();
        fd.append('conteudo', txtReply.value.trim());
        for (const f of inpFiles.files) fd.append('arquivos', f);
        const atualizado = await api(`/api/chamados/${c.id}/mensagens`, { method: 'POST', body: fd });
        await carregarChamados();
        renderModalDetalhe(atualizado);
        showBanner('Mensagem registrada no chamado.');
      } catch (err) {
        showBanner(err.message, true);
      } finally {
        btnSend.disabled = false;
      }
    });
    leftCol.append(replyForm);

    if (!tecnico()) {
      const acompanhamento = el('div', 'sd-control-panel');
      acompanhamento.append(el('strong', '', `Etapa: ${STATUS_LABELS[c.status]}`),
        el('p', '', `Responsável: ${c.responsavelNome || 'Não atribuído'} • ${formatSlaText(c)}`),
        el('small', '', 'A equipe técnica gerencia as etapas e o encerramento. Você pode acompanhar e enviar informações.'));
      grid.append(leftCol, acompanhamento);
      modalBody.append(grid);
      return;
    }
    const rightCol = el('form', 'sd-control-panel');
    rightCol.append(el('strong', '', 'Gerenciamento e Distribuicao do Chamado'));

    const lblStatus = el('label', '', 'Etapa do Kanban (Status)');
    const selStatus = el('select');
    for (const st of STATUS_LIST) {
      selStatus.append(new Option(STATUS_LABELS[st], st, st === c.status, st === c.status));
    }
    lblStatus.append(selStatus);

    const lblFila = el('label', '', 'Fila de Destino (Distribuicao)');
    const selFila = el('select');
    selFila.append(
      new Option('Fila DEV (Desenvolvimento / Bugs)', 'DEV', c.filaDestino === 'DEV', c.filaDestino === 'DEV'),
      new Option('Fila ADMIN (Administracao / Faturamento)', 'ADMIN', c.filaDestino === 'ADMIN', c.filaDestino === 'ADMIN')
    );
    lblFila.append(selFila);

    const lblPrio = el('label', '', 'Prioridade');
    const selPrio = el('select');
    for (const p of ['CRITICA', 'ALTA', 'MEDIA', 'BAIXA']) {
      selPrio.append(new Option(p, p, p === c.prioridade, p === c.prioridade));
    }
    lblPrio.append(selPrio);

    const lblResp = el('label', '', 'Atendente Responsavel');
    const selResp = el('select');
    selResp.append(new Option('-- Nao atribuido --', '0'));
    for (const a of atendentes) {
      const selected = c.responsavelId === a.id;
      selResp.append(new Option(`${a.nome} (${a.perfil})`, String(a.id), selected, selected));
    }
    lblResp.append(selResp);

    const lblObs = el('label', '', 'Observacao de transicao (opcional)');
    const inpObs = el('textarea');
    inpObs.rows = 2;
    inpObs.maxLength = 2000;
    inpObs.placeholder = 'Motivo do redirecionamento ou conclusao...';
    lblObs.append(inpObs);

    const btnSaveStatus = el('button', 'primary-button', 'Salvar Alteracoes do Chamado');
    btnSaveStatus.type = 'submit';

    const btnAssumir = el('button', 'secondary-button', 'Assumir para Mim e Iniciar Atendimento');
    btnAssumir.type = 'button';
    btnAssumir.addEventListener('click', async () => {
      btnAssumir.disabled = true;
      try {
        const atualizado = await api(`/api/chamados/${c.id}/status`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ status: 'EM_ATENDIMENTO', assumir: true })
        });
        await carregarChamados();
        renderModalDetalhe(atualizado);
        showBanner('Voce assumiu o chamado.');
      } catch (err) {
        showBanner(err.message, true);
      } finally {
        btnAssumir.disabled = false;
      }
    });

    rightCol.append(lblStatus, lblFila, lblPrio, lblResp, lblObs, btnSaveStatus, btnAssumir);
    if (c.notaAvaliacao) {
      const evalInfo = el('div', 'hd-rating-box');
      evalInfo.append(
        el('strong', '', `Avaliacao do Cliente: ${'★'.repeat(c.notaAvaliacao)} (${c.notaAvaliacao}/5)`),
        c.comentarioAvaliacao ? el('p', '', `"${c.comentarioAvaliacao}"`) : el('span')
      );
      rightCol.append(evalInfo);
    }

    rightCol.addEventListener('submit', async e => {
      e.preventDefault();
      btnSaveStatus.disabled = true;
      try {
        const atualizado = await api(`/api/chamados/${c.id}/status`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            status: selStatus.value,
            filaDestino: selFila.value,
            prioridade: selPrio.value,
            responsavelId: Number(selResp.value),
            observacao: inpObs.value.trim()
          })
        });
        await carregarChamados();
        renderModalDetalhe(atualizado);
        showBanner('Dados do chamado atualizados.');
      } catch (err) {
        showBanner(err.message, true);
      } finally {
        btnSaveStatus.disabled = false;
      }
    });

    grid.append(leftCol, rightCol);
    modalBody.append(grid);
  }

  function renderSlaTable() {
    const container = document.querySelector('#sla-table-container');
    container.replaceChildren();
    const table = el('table', 'sd-sla-table');
    const thead = el('thead');
    const trHead = el('tr');
    ['Categoria', 'Fila Automatica', 'Prioridade Padrao', 'Prazo SLA (Horas)', 'Acao'].forEach(h => trHead.append(el('th', '', h)));
    thead.append(trHead);
    const tbody = el('tbody');

    for (const t of tiposSla) {
      const tr = el('tr');
      const tdNome = el('td');
      tdNome.append(el('strong', '', t.nome), el('div', '', t.descricao));

      const tdFila = el('td');
      const selFila = el('select');
      selFila.append(
        new Option('DEV', 'DEV', t.filaPadrao === 'DEV', t.filaPadrao === 'DEV'),
        new Option('ADMIN', 'ADMIN', t.filaPadrao === 'ADMIN', t.filaPadrao === 'ADMIN')
      );
      tdFila.append(selFila);

      const tdPrio = el('td');
      const selPrio = el('select');
      for (const p of ['CRITICA', 'ALTA', 'MEDIA', 'BAIXA']) {
        selPrio.append(new Option(p, p, t.prioridadePadrao === p, t.prioridadePadrao === p));
      }
      tdPrio.append(selPrio);

      const tdHoras = el('td');
      const inpHoras = el('input');
      inpHoras.type = 'number';
      inpHoras.min = '1';
      inpHoras.max = '720';
      inpHoras.value = String(t.slaHoras);
      inpHoras.style.width = '80px';
      tdHoras.append(inpHoras);

      const tdBtn = el('td');
      const btnSalvar = el('button', 'sd-mini-btn', 'Salvar SLA');
      btnSalvar.type = 'button';
      btnSalvar.addEventListener('click', async () => {
        btnSalvar.disabled = true;
        try {
          await api(`/api/chamados/tipos/${t.codigo}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              slaHoras: Number(inpHoras.value),
              filaPadrao: selFila.value,
              prioridadePadrao: selPrio.value
            })
          });
          tiposSla = await api('/api/chamados/tipos');
          renderSlaTable();
          showBanner(`SLA da categoria "${t.nome}" atualizado para ${inpHoras.value}h.`);
        } catch (err) {
          showBanner(err.message, true);
        } finally {
          btnSalvar.disabled = false;
        }
      });
      tdBtn.append(btnSalvar);

      tr.append(tdNome, tdFila, tdPrio, tdHoras, tdBtn);
      tbody.append(tr);
    }
    table.append(thead, tbody);
    container.append(table);
  }

  async function carregarChamados() {
    const params = new URLSearchParams();
    if (filtroFila.value && filtroFila.value !== 'TODAS') params.set('fila', filtroFila.value);
    if (filtroTipo.value && filtroTipo.value !== 'TODOS') params.set('tipo', filtroTipo.value);
    if (filtroBusca.value.trim()) params.set('busca', filtroBusca.value.trim());
    if (filtroMeus.checked) params.set('meus', 'true');

    const painel = await api('/api/chamados?' + params.toString());
    chamados = painel.chamados || [];
    atualizarKpis(painel.resumo);
    renderKanban();
  }

  document.querySelectorAll('.sd-kanban-col').forEach(col => {
    col.addEventListener('dragover', e => {
      if (!tecnico()) return;
      e.preventDefault();
      col.classList.add('drag-over');
    });
    col.addEventListener('dragleave', () => col.classList.remove('drag-over'));
    col.addEventListener('drop', async e => {
      e.preventDefault();
      col.classList.remove('drag-over');
      const id = Number(e.dataTransfer.getData('text/plain'));
      const destino = col.dataset.status;
      if (!id || !destino) return;
      const atual = chamados.find(x => x.id === id);
      if (!atual || atual.status === destino) return;
      await moverRapido(id, destino, destino === 'EM_ATENDIMENTO' && !atual.responsavelId);
    });
  });

  filtroFila.addEventListener('change', carregarChamados);
  filtroTipo.addEventListener('change', carregarChamados);
  filtroMeus.addEventListener('change', carregarChamados);
  filtroBusca.addEventListener('input', () => carregarChamados());
  document.querySelector('#btn-atualizar').addEventListener('click', carregarChamados);

  document.querySelector('#btn-novo-chamado').addEventListener('click', () => {
    const fab = document.querySelector('#hd-fab-button');
    if (fab) fab.click();
  });

  document.querySelector('#btn-config-sla').addEventListener('click', () => {
    if (!tecnico()) return;
    renderSlaTable();
    slaModal.showModal();
  });

  document.querySelector('#modal-close').addEventListener('click', () => modal.close());
  document.querySelector('#sla-close').addEventListener('click', () => slaModal.close());

  (async () => {
    try {
      currentUser = await api('/api/auth/me');
      document.querySelector('#sd-user-context').textContent =
        `${currentUser.nome} • ${tecnico() ? "Equipe técnica" : "Funcionário FaturaMed"}`;
      document.querySelector("h1").textContent = tecnico() ? "Gestão de chamados e SLA" : "Acompanhar chamados";
      document.title = document.querySelector("h1").textContent + " | FaturaMed";
      if (currentUser.perfil === 'DEV') {
        filtroFila.value = 'DEV';
      }
      tiposSla = await api('/api/chamados/tipos');
      for (const t of tiposSla) {
        filtroTipo.append(new Option(`${t.nome} (${t.slaHoras}h)`, t.codigo));
      }
      document.querySelector('#btn-config-sla').hidden = !tecnico();
      atendentes = tecnico() ? await api('/api/chamados/atendentes') : [];
      await carregarChamados();
    } catch (err) {
      showBanner(err.message, true);
    }
  })();
})();
