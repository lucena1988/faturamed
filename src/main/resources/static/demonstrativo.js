(() => {
  const form=document.getElementById('demonstrativo-form'),fields=document.getElementById('demonstrativo-fields'),message=document.getElementById('demonstrativo-message');
  let report=null,doctor=null,generation=0,controller=null,busy=false;
  const controls=()=>{fields.disabled=busy||!report||!doctor;};
  form.onsubmit=async e=>{e.preventDefault();if(busy||!report||!doctor)return;const token=generation,id=report;
    const params=new URLSearchParams({medicoId:doctor});for(const name of ['visitaDe','visitaAte','recebimentoDe','recebimentoAte'])if(form.elements[name].value)params.set(name,form.elements[name].value);
    controller=new AbortController();busy=true;controls();message.textContent='Gerando demonstrativo...';
    try{const response=await fetch(`/api/conciliacoes/visitas/${id}/demonstrativo.pdf?${params}`,{signal:controller.signal});if(!response.ok){const error=await response.json().catch(()=>({}));throw new Error(error.erro||'Nao foi possivel gerar o PDF');}
      const blob=await response.blob();if(token!==generation)return;const url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download=`demonstrativo-medico-${id}.pdf`;document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),60000);message.textContent='Demonstrativo gerado.';
    }catch(error){if(token===generation&&error.name!=='AbortError')message.textContent=error.message;}
    finally{if(token===generation){busy=false;controls();}}
  };
  window.FaturamedDemonstrativo={selecionar(id,medico,hospital,nome){const nextDoctor=/^[1-9]\d*$/.test(medico||'')?medico:null;if(id!==report||nextDoctor!==doctor){generation++;controller?.abort();busy=false;form.reset();message.textContent='';}report=id;doctor=nextDoctor;controls();document.getElementById('demonstrativo-scope').textContent=report&&doctor?`${nome} | ${hospital} | Relatorio #${report} | O PDF exige uma publicacao liberada para este medico. A disponibilidade sera verificada ao baixar.`:'Selecione um relatorio e um medico cadastrado.';}};
})();
