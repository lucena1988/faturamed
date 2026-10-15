(function(root){
  function centavos(value){
    const text=String(value??'').trim();if(!/^\d{1,14}(?:\.\d{1,2})?$/.test(text))return null;
    const [whole,fraction='']=text.split('.');return BigInt(whole)*100n+BigInt(fraction.padEnd(2,'0'));
  }
  function moeda(value){if(value==null)return 'Nao informado';const negative=value<0n,absolute=negative?-value:value;
    return `${negative?'-':''}R$ ${new Intl.NumberFormat('pt-BR').format(absolute/100n)},${String(absolute%100n).padStart(2,'0')}`;
  }
  const api={centavos,moeda};if(typeof module!=='undefined')module.exports=api;root.FaturamedPagamentoValores=api;
})(globalThis);
