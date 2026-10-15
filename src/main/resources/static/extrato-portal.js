(() => {
  const $=id=>document.getElementById(id),money=new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'});
  const format=value=>value==null?'Nao confirmado':money.format(value),date=value=>value?value.split('-').reverse().join('/'):'Nao informada';
  const node=(tag,text)=>{const e=document.createElement(tag);e.textContent=text;return e;};
  const fields=['visitaDe','visitaAte','recebimentoDe','recebimentoAte'];let reportId=null,generation=0,controller=null;
  const financialLabels={CONFERENCIA:'Financeiro em conferencia',SEM_FATURAMENTO:'Sem faturamento confirmado',RECEBIDO:'Recebido',PARCIAL:'Recebido parcialmente',A_RECEBER:'A receber'};
  const statusLabels={PAGA:'Faturado',PENDENTE:'Aguardando faturamento',DIVERGENTE:'Divergente'};
  function clear(){for(const id of ['extrato-visits','extrato-movements','extrato-balances','extrato-movement-stats'])$(id).replaceChildren();$('extrato-scope').textContent='';$('extrato-attention').textContent='';$('extrato-export').hidden=true;}
  function stats(id,entries){for(const [label,value] of entries){const e=node('div',label);e.append(node('strong',format(value)));$(id).append(e);}}
  function row(id,values){const tr=node('tr','');values.forEach((value,i)=>{const td=node('td',value);td.dataset.label=$(id).closest('table').querySelectorAll('th')[i].textContent;tr.append(td);});$(id).append(tr);return tr;}
  async function load(){controller?.abort();controller=new AbortController();const token=++generation;clear();$('extrato-apply').disabled=!reportId;$('extrato-clear').disabled=!reportId;
    if(!reportId){$('extrato-message').textContent='Selecione um relatorio.';return;}
    const params=new URLSearchParams();const form=$('extrato-filters');for(const field of fields)if(form.elements[field].value)params.set(field,form.elements[field].value);
    $('extrato-message').textContent='Carregando extrato...';
    try{const response=await fetch(`/api/medico/relatorios/${reportId}/extrato?${params}`,{signal:controller.signal});const data=await response.json();if(!response.ok)throw new Error(data.erro||`Falha ao carregar extrato (${response.status})`);if(token!==generation)return;
      const r=data.resumo;$('extrato-message').textContent=r.visitas?`${r.visitas} visitas e ${data.movimentos.length} movimentos nos filtros.`:'Nenhuma visita nos filtros.';
      $('extrato-scope').textContent=`${data.hospital} - Relatorio #${data.relatorio} - Posicao em ${new Date(data.geradoEm).toLocaleString('pt-BR',{timeZone:'America/Sao_Paulo'})}`;
      stats('extrato-balances',[['Faturado confirmado',r.faturado],['Recebido acumulado',r.recebidoAcumulado],['Saldo atual confirmado',r.saldoAtual],['Aguardando faturamento (valor informado)',r.aguardandoFaturamento],['Em conferencia (valor informado)',r.emConferencia]]);
      $('extrato-attention').textContent=`${r.pendentes} pendentes; ${r.divergentes} divergentes; ${r.semValor} sem valor informado; ${r.financeiroNaoConfirmado} com financeiro nao confirmado.`;
      for(const v of data.visitas)row('extrato-visits',[v.linha,v.atendimento,date(v.data),`${v.procedimento||'Nao informado'}${v.codigo?' / '+v.codigo:''}`,v.valorInformado==null?'Nao informado':format(v.valorInformado),v.faturado==null?'Nao confirmado':format(v.faturado),format(v.recebido),format(v.saldo),v.situacao==='SEM_FATURAMENTO'?statusLabels[v.status]:financialLabels[v.situacao]]);
      stats('extrato-movement-stats',[['Entradas no periodo',r.entradasPeriodo],['Estornos no periodo',r.estornosPeriodo],['Liquido no periodo',r.liquidoPeriodo]]);
      for(const m of data.movimentos){const tr=row('extrato-movements',[m.id,date(m.data),m.linha,m.atendimento,date(m.dataVisita),m.estornoDe?'Estorno':m.estornado?'Recebimento (estornado)':'Recebimento',format(m.valor),m.estornoDe||'-',m.pagamentoId?`#${m.pagamentoId} - ${m.referencia}`:'-']);if(Number(m.valor)<0)tr.classList.add('extrato-negative');}
      if(!data.visitas.length)row('extrato-visits',['Nenhuma visita.','','','','','','','','']);
      if(!data.movimentos.length)row('extrato-movements',['Nenhum movimento neste periodo.','','','','','','','','']);
      $('extrato-export').href=`/api/medico/relatorios/${reportId}/extrato.xlsx?${params}`;$('extrato-export').hidden=false;
    }catch(e){if(token===generation&&e.name!=='AbortError')$('extrato-message').textContent=e.message;}
  }
  $('extrato-filters').onsubmit=e=>{e.preventDefault();load();};$('extrato-clear').onclick=()=>{$('extrato-filters').reset();load();};
  for(const field of fields)$('extrato-filters').elements[field].oninput=()=>{controller?.abort();generation++;clear();$('extrato-message').textContent='Filtros alterados.';};
  window.FaturamedExtrato={carregar(id){if(id!==reportId)$('extrato-filters').reset();reportId=id;return load();}};
})();
