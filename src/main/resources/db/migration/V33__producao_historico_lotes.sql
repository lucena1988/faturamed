-- "Folhas de producao" passaram a se chamar "Lotes de producao": ajusta os textos ja gravados no historico.
update producao_historico set descricao = 'Lote criado' where entidade = 'LOTE' and descricao = 'Folha criada';
