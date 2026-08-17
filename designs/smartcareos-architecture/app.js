const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
let current='business', focusChain=false, filters={fact:true,inference:true};
const chainIds=new Set(['mqtt','mqtt-ingress','telemetry','risk-event','device','alarm','care','notification','outbox','rabbit']);
const viewLabels={business:'业务全景',application:'应用与领域',flow:'数据与消息',deployment:'部署与安全'};

function render(){
  const data=ARCH_DATA[current];
  $('#page-title').textContent=data.title; $('#page-subtitle').textContent=data.subtitle; $('#breadcrumb').textContent=viewLabels[current];
  let count=0;
  $('#architecture-canvas').innerHTML=data.layers.map((layer,li)=>{
    count+=layer.nodes.length;
    const nodes=layer.nodes.map(n=>{const [id,title,sub,icon,origin,status]=n;return `<button class="arch-node ${focusChain&&!chainIds.has(id)?'dim':''} ${focusChain&&chainIds.has(id)?'chain':''} ${!filters[origin]?'hidden-origin':''}" data-id="${id}" data-origin="${origin}" data-status="${status}"><span class="node-icon">${icon}</span><strong>${title}</strong><small>${sub}</small><i class="origin"></i></button>`}).join('');
    return `<div class="layer"><span class="layer-label">${layer.label}</span><div class="layer-content" style="--cols:${layer.cols}">${nodes}</div></div>${li<data.layers.length-1?'<div class="flow-line">↓</div>':''}`;
  }).join('');
  $('#node-count').textContent=`${count} 个能力节点`;
  $$('.arch-node').forEach(n=>n.addEventListener('click',()=>selectNode(n)));
  const first=$('.arch-node:not(.hidden-origin)'); if(first) selectNode(first,true);
}

function selectNode(node,silent=false){
  $('.inspector').classList.remove('collapsed');
  $$('.arch-node').forEach(n=>n.classList.remove('selected'));node.classList.add('selected');
  const id=node.dataset.id, title=node.querySelector('strong').textContent, icon=node.querySelector('.node-icon').textContent;
  const base=NODE_DETAILS[id]||DEFAULT_DETAIL; const origin=node.dataset.origin; const status=node.dataset.status;
  $('#detail-icon').textContent=icon; $('#detail-index').textContent=`${viewLabels[current].toUpperCase()} / ${id.toUpperCase()}`;
  $('#detail-kicker').innerHTML=`<i class="${origin}"></i>${base.k}`; $('#detail-title').textContent=base.title||title; $('#detail-desc').textContent=base.desc;
  $('#detail-bullets').innerHTML=base.bullets.map(x=>`<li>${x}</li>`).join(''); $('#detail-interfaces').innerHTML=base.interfaces.map(x=>`<span>${x}</span>`).join('');
  $('#detail-status-dot').className=`status-dot ${status==='external'?'sandbox':'complete'}`; $('#detail-status').textContent=status==='external'?'待外部验收':'已实现'; $('#detail-owner').textContent=base.owner;
  $('#source-note').innerHTML=`<b>${origin==='fact'?'事实依据':'设计依据'}</b><span>${base.source}</span>`;
  if(!silent) $('.inspector').animate([{opacity:.7,transform:'translateX(4px)'},{opacity:1,transform:'translateX(0)'}],{duration:180});
}

function toast(msg){const t=$('#toast');t.textContent=msg;t.classList.add('show');clearTimeout(window._tt);window._tt=setTimeout(()=>t.classList.remove('show'),2200)}

$('#views').addEventListener('click',e=>{const b=e.target.closest('[data-view]');if(!b)return;current=b.dataset.view;focusChain=false;$$('.nav-item').forEach(x=>x.classList.toggle('active',x===b));$('#chain-btn').classList.remove('active');render()});
$$('.utility').forEach(b=>b.addEventListener('click',()=>{const k=b.dataset.toggle;filters[k]=!filters[k];b.classList.toggle('active',filters[k]);render()}));
$('#chain-btn').addEventListener('click',()=>{focusChain=!focusChain;$('#chain-btn').classList.toggle('active',focusChain);if(focusChain&&current!=='flow'){current='flow';$$('.nav-item').forEach(x=>x.classList.toggle('active',x.dataset.view==='flow'))}render();toast(focusChain?'已聚焦：MQTT → 风险 → 告警 → 护理/通知':'已恢复完整架构视图')});
$('#reset-btn').addEventListener('click',()=>{focusChain=false;filters={fact:true,inference:true};$$('.utility').forEach(x=>x.classList.add('active'));render();toast('视图已重置')});
$('#close-detail').addEventListener('click',()=>{$('.inspector').classList.toggle('collapsed');toast('点击任意节点可重新查看详情')});
$('#export-btn').addEventListener('click',()=>{const text=`SmartCareOS Architecture Atlas\n版本 1.0.0 · 2026-08-17\n\n8 个限界上下文 · Schema V10 · 39/39 测试通过\n核心链路：MQTT → Device → Risk Event → Alarm → Care / Notification → Outbox → RabbitMQ\n\n来源事实与架构推演已在交互全景中分别标注。`;const blob=new Blob([text],{type:'text/plain;charset=utf-8'});const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='SmartCareOS-architecture-summary.txt';a.click();URL.revokeObjectURL(a.href);toast('架构摘要已导出')});

const tips=['架构资产与治理基线','模块化单体骨架','告警纵向切片','机构与空间域','老人与入住域','设备绑定域','护理业务闭环','安全与生产基线','MySQL/MQTT/AMQP 实链路','DLQ 与消息恢复','MQTT TLS 1.3 mTLS','OIDC/API Key 与租户隔离','性能基线 P95 156ms','恢复演练 RTO 4s','外部通道沙箱完成','机构运营工作台','1.0.0 内部验收'];
$('#timeline').innerHTML=tips.map((t,i)=>`<div class="milestone ${i===14?'sandbox':''}" data-tip="${t}">M${i}</div>`).join('');
render();
