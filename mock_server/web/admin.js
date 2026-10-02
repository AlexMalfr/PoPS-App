'use strict';
let state, draft, settingsDraft, selected, tab = 'profile', globalView = '', dirty = false;
let semesterIndex = 0, gradeView = 'exams', token = '', jsonDraft = null;
const $ = id => document.getElementById(id);
const csrf = document.querySelector('meta[name="csrf-token"]').content;
const clone = value => JSON.parse(JSON.stringify(value));
const uid = () => crypto.randomUUID();
const labels = {login:'Identifiant de connexion', student_id:'Code étudiant', display_name:'Nom affiché', active:'Compte activé', year:'Année de début', semester:'Semestre (1 ou 2)', status:'Décision / statut', average:'Moyenne de promotion', grade:'Note', name:'Nom de l’épreuve', date:'Date de l’épreuve', rank:'Rang', comment:'Appréciation', coefficient:'Coefficient', subjectId:'Code matière', subject:'Matière', groupCode:'Code UE', groupTitle:'Nom UE', title:'Intitulé', code:'Code', ects:'ECTS', commonCore:'Tronc commun', result:'Résultat', credits:'Crédits', block:'Bloc', field_name:'Identifiant du champ', label:'Libellé', value:'Valeur', fields:'Champs', sections:'Sections', nested_tables:'Tableaux du cursus', rows:'Lignes', cells:'Cellules', row_code:'Code de ligne', nested_table_name:'Nom du tableau', headers:'En-têtes', module_code:'Code UE', module_state:'Inscription à l’UE', courses:'Matières', course_code:'Code matière', state:'Inscription', type:'Type', attrs:'Attributs Oasis', earned_ects:'ECTS acquis', content_base64:'Contenu du fichier', filename:'Nom du fichier', content_type:'Type MIME', page:'Page Oasis', route:'Route Oasis', method:'Méthode HTTP', body:'Corps de la réponse', params:'Conditions sur les paramètres', average_formula:'Formule / détail de moyenne', validation_report:'Motifs de la décision', ranking:'Réponse de classement', years:'Bilans annuels', closed:'Plateforme fermée', ip_blocked:'IP bloquée pour la journée', login_error:'Message en cas d’identifiants rejetés', closed_error:'Message quand Oasis est fermé', session_ttl_seconds:'Durée des sessions (secondes)', latency_ms:'Latence (millisecondes)', failure_status:'Panne HTTP (0 pour désactiver)'};
const tabs = {profile:'Compte & profil', grades:'Notes', cursus:'Cursus', choices:'Choix', documents:'Documents', routes:'Réponses API', advanced:'JSON complet'};
labels.password = 'Mot de passe';

function node(tag, text, className) {
  const element = document.createElement(tag);
  if (text !== undefined) element.textContent = text;
  if (className) element.className = className;
  return element;
}
function notice(message, error = false) { $('notice').textContent = message; $('notice').className = error ? 'error' : ''; }
function markDirty() { dirty = true; $('save').textContent = 'Enregistrer •'; notice('Modifications en attente d’enregistrement.'); }
function action(text, callback, className = '') {
  const button = node('button', text, className); button.type = 'button';
  button.addEventListener('click', async () => {
    button.disabled = true;
    try { await callback(); } catch (error) { notice(error.message, true); }
    finally { button.disabled = false; }
  }); return button;
}
async function api(path, method = 'GET', body) {
  const headers = {'X-CSRF-Token':csrf};
  if (token) headers.Authorization = 'Bearer ' + token;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch('/api/admin/' + path, {method, headers, body:body === undefined ? undefined : JSON.stringify(body)});
  const result = await response.json();
  if (!response.ok) {
    const error = new Error(result.error || 'Erreur HTTP ' + response.status); error.status = response.status; throw error;
  }
  return result;
}
async function load() {
  try { state = await api('state'); }
  catch (error) {
    if (error.status === 403) {
      $('title').textContent = 'Administration'; $('editor').replaceChildren($('token-template').content.cloneNode(true));
      $('save').disabled = true;
      $('token-form').addEventListener('submit', async event => {
        event.preventDefault(); token = new FormData(event.target).get('token'); await load();
      }); notice(error.message, true); return;
    } throw error;
  }
  selected = state.accounts.some(a => a.id === selected) ? selected : state.accounts[0]?.id;
  draft = clone(state.accounts.find(a => a.id === selected) || null);
  settingsDraft = clone(state.settings); dirty = false; jsonDraft = null;
  $('save').textContent = 'Enregistrer'; $('revision').textContent = 'Données persistantes · Révision ' + state.revision;
  renderSidebar(); render();
}
function canLeave() { return !dirty || confirm('Abandonner les modifications non enregistrées ?'); }
function renderSidebar() {
  $('accounts').replaceChildren();
  for (const account of state.accounts) {
    const button = action('', () => {
      if (!canLeave()) return;
      selected = account.id; draft = clone(account); globalView = ''; dirty = false; jsonDraft = null; semesterIndex = 0;
      $('save').textContent = 'Enregistrer'; renderSidebar(); render(); notice('');
    }, !globalView && selected === account.id ? 'active' : '');
    button.append(node('span', account.display_name), node('small', account.login + ' · ' + (account.active ? 'Actif' : 'Désactivé')));
    $('accounts').append(button);
  }
  document.querySelectorAll('[data-global]').forEach(b => b.classList.toggle('active', b.dataset.global === globalView));
}
labels.max_login_attempts = 'Tentatives avant blocage IP';
function panel(title, help) {
  const box = node('section', undefined, 'panel'); box.append(node('h2', title));
  if (help) box.append(node('p', help)); $('editor').append(box); return box;
}
function scalar(key, value, update, path) {
  const label = node('label', labels[key] || key, 'field');
  let input;
  if (typeof value === 'boolean') {
    input = document.createElement('input'); input.type = 'checkbox'; input.checked = value;
    input.addEventListener('change', () => { update(input.checked); markDirty(); });
  } else {
    let options;
    const sem = draft?.semesters[semesterIndex];
    if (key === 'groupCode' && path.includes('semesters')) options = sem?.units.map(u => [u.code, u.code + ' · ' + u.title]);
    if (key === 'subjectId' && path.includes('semesters')) options = sem?.modules.map(m => [m.code, m.code + ' · ' + m.title]);
    if (options?.length) {
      input = document.createElement('select');
      if (!options.some(([v]) => v === value)) options.unshift([value, value || 'Choisir…']);
      for (const [v, text] of options) { const option = node('option', text); option.value = v; input.append(option); }
      input.value = value;
    } else {
      const multiline = ['comment','value','body','validation_report','average_formula','closed_error','login_error'].includes(key) && (key !== 'value' || String(value).length > 90);
      input = document.createElement(multiline ? 'textarea' : 'input');
      if (typeof value === 'number' || value === null) { input.type = 'number'; input.step = 'any'; }
      input.value = value ?? '';
      if (value === null) input.placeholder = 'Non publiée';
    }
    input.addEventListener('input', () => {
      const next = typeof value === 'number' || value === null ? (input.value === '' ? null : Number(input.value)) : input.value;
      update(next); markDirty();
    });
  }
  input.dataset.field = key; label.append(input); return label;
}
function defaults(key, path) {
  const sem = draft?.semesters[semesterIndex];
  const unit = sem?.units[0], module = sem?.modules[0];
  const templates = {
    sections:{title:'Nouvelle section',fields:[]}, fields:{field_name:'FIELD_' + uid().slice(0,8),label:'Nouveau champ',value:''},
    nested_tables:{title:'Nouveau tableau',nested_table_name:'TABLE_' + uid().slice(0,8),headers:[],rows:[]},
    rows:{row_code:uid(),cells:[]}, cells:{field_name:'FIELD_' + uid().slice(0,8),column:'Colonne',label:'Colonne',value:''},
    headers:'Nouvelle colonne', years:{year:2026,status:'ATT',average:'—',average_formula:'',validation_report:''},
    units:{code:'ue-' + uid().slice(0,4),title:'Nouvelle UE',ects:'4',commonCore:true,grade:'—',average:'—',rank:'',result:'ATT'},
    modules:path.includes('choices') ? {module_code:'ue-' + uid().slice(0,4),title:'Nouvelle UE',module_state:'INSCRIT',attrs:{ects:'4',coeff:'1',choice_type:'i'},courses:[]} : {groupCode:unit?.code || '',groupTitle:unit?.title || '',code:'matiere-' + uid().slice(0,4),title:'Nouvelle matière',coefficient:'1',block:'—',grade:'—',average:'—',rank:'',credits:'2'},
    exams:{id:uid(),subjectId:module?.code || '',subject:module?.title || '',name:'Nouvelle épreuve',date:'',grade:null,average:null,rank:'',comment:'',coefficient:1},
    courses:{course_code:'cours-' + uid().slice(0,4),title:'Nouvelle matière',type:'Optionnelle',coefficient:'1',state:'Non inscrit',attrs:{optional:'1',choice_type:'i',data_type:'OPTIONAL'}},
    semesters:{year:2026,semester:1,status:'ATT',average:'—',average_formula:'',ranking:{text:'',success:true,reload:'no'},validation_report:'',exams:[],modules:[],units:[]},
    choices:{year:2026,semester:1,earned_ects:'0',modules:[]},
    route_overrides:{route:'Oasis\\Example::load',method:'GET',status:200,content_type:'application/json; charset=utf-8',body:'{"success":true}',params:{}},
  };
  return clone(templates[key] ?? {});
}
function structured(value, container, path = []) {
  if (Array.isArray(value)) {
    const list = node('div', undefined, 'row-list'); container.append(list);
    value.forEach((item, index) => {
      const record = node('details', undefined, 'record');
      const title = typeof item === 'object' && item ? (item.title || item.label || item.name || item.filename || item.route || item.field_name || item.row_code || `${labels[path.at(-1)] || 'Ligne'} ${index+1}`) : String(item);
      record.append(node('summary', (item?.code || item?.course_code || '') + (item?.code || item?.course_code ? ' · ' : '') + title));
      const body = node('div', undefined, 'record-body'); record.append(body); list.append(record);
      if (typeof item === 'object' && item !== null) structured(item, body, [...path, index]);
      else body.append(scalar(String(index + 1), item, next => { value[index] = next; }, path));
      const controls = node('div', undefined, 'record-actions');
      controls.append(action('Dupliquer', () => {
        const copied = clone(value[index]);
        if (copied && typeof copied === 'object') {
          for (const key of ['id','code','module_code','course_code','field_name','row_code','nested_table_name']) if (key in copied) copied[key] += '-' + uid().slice(0,4);
        }
        value.splice(index + 1, 0, copied); markDirty(); render();
      }), action('Supprimer', () => { if (confirm('Supprimer cet élément ?')) { value.splice(index, 1); markDirty(); render(); } }, 'danger'));
      body.append(controls);
    });
    if (!value.length) list.append(node('p', 'Aucun élément. Ajoutez des données pour ce compte.', 'empty'));
    container.append(action('+ Ajouter', () => { value.push(defaults(path.at(-1), path)); markDirty(); render(); }, 'add'));
  } else if (value && typeof value === 'object') {
    const grid = node('div', undefined, 'grid'); container.append(grid);
    for (const [key, item] of Object.entries(value)) {
      if (['id','selector','heading_selector','title_selector','content_base64'].includes(key)) continue;
      if (typeof item !== 'object' || item === null) {
        grid.append(scalar(key, item, next => {
          value[key] = next;
          const sem = draft?.semesters[semesterIndex];
          if (key === 'groupCode' && path.includes('semesters')) value.groupTitle = sem.units.find(u => u.code === next)?.title || '';
          if (key === 'subjectId' && path.includes('semesters')) value.subject = sem.modules.find(m => m.code === next)?.title || '';
        }, path));
      }
    }
    for (const [key, item] of Object.entries(value)) if (typeof item === 'object' && item !== null) {
      container.append(node('h3', labels[key] || key, 'object-title')); structured(item, container, [...path, key]);
    }
    if (path.at(-1) === 'attrs' || path.at(-1) === 'params') container.append(action('+ Ajouter un attribut', () => {
      const key = prompt('Nom du nouvel attribut');
      if (key && !['__proto__','constructor','prototype'].includes(key)) { value[key] = ''; markDirty(); render(); }
    }, 'add'));
  }
}
function render() {
  $('editor').replaceChildren(); $('tabs').replaceChildren();
  $('save').disabled = ['events','coverage'].includes(globalView) || (!globalView && !draft);
  if (globalView) { renderGlobal(); return; }
  if (!draft) { $('title').textContent = 'Votre atelier Oasis'; panel('Créez votre premier compte', 'Les données sont séparées par étudiant.'); return; }
  $('title').textContent = draft.display_name; $('subtitle').textContent = 'Compte ' + draft.login + ' · Étudiant ' + draft.student_id;
  for (const [key, title] of Object.entries(tabs)) $('tabs').append(action(title, () => {
    if (tab === 'advanced' && jsonDraft !== null) { draft = JSON.parse(jsonDraft); jsonDraft = null; }
    tab = key; render();
  }, key === tab ? 'active' : ''));
  if (tab === 'profile') {
    const box = panel('Accès au compte', 'Seuls les identifiants de comptes enregistrés permettent de se connecter.');
    const grid = node('div', undefined, 'grid'); box.append(grid);
    for (const key of ['login','student_id','display_name','active','password']) grid.append(scalar(key, draft[key], value => { draft[key] = value; }, []));
    box.append(action('Supprimer le compte', async () => {
      if (!confirm('Supprimer le compte et toutes ses données ?')) return;
      await api('accounts/' + selected, 'DELETE', {revision:state.revision}); selected = null; await load(); notice('Compte supprimé.');
    }, 'danger'));
    structured(draft.pages.STUDENT, panel('Informations personnelles', 'Tous les champs des anciens rapports sont disponibles. Les valeurs sont fictives au premier lancement.'), ['pages','STUDENT']);
    structured(draft.pages.HOME, panel('Accueil'), ['pages','HOME']);
  } else if (tab === 'grades') renderGrades();
  else if (tab === 'cursus') {
    structured(draft.pages.MYCURSUS, panel('Parcours, quitus & décisions', 'Modifiez les champs et les tableaux de synthèse du cursus.'), ['pages','MYCURSUS']);
    structured(draft.years, panel('Bilans annuels'), ['years']);
  } else if (tab === 'choices') {
    structured(draft.choices, panel('Inscriptions & choix de matières', 'Les inscriptions, options et attributs Oasis sont propres à chaque année et semestre.'), ['choices']);
    structured(draft.pages.MYCHOICES, panel('Champs complémentaires'), ['pages','MYCHOICES']);
  } else if (tab === 'documents') renderDocuments();
  else if (tab === 'routes') {
    structured(draft.route_overrides, panel('Réponses API personnalisées', 'Une réponse peut remplacer une route pour ce compte, avec des conditions sur les paramètres. Les routes de connexion et les fichiers restent gérés par le serveur. Les mutations dont le protocole n’a pas été capturé renvoient 501 par défaut.'), ['route_overrides']);
    structured(draft.pages.MYMARKS, panel('Champs complémentaires des notes'), ['pages','MYMARKS']);
  } else if (tab === 'advanced') {
    const box = panel('Toutes les données du compte', 'Ce JSON conserve les champs supplémentaires des artifacts, ainsi que le mot de passe du compte mock en clair. Enregistrer valide les relations entre UE, matière et épreuve.');
    const area = node('textarea', undefined, 'json-editor'); area.value = jsonDraft ?? JSON.stringify(draft, null, 2);
    area.setAttribute('aria-label', 'JSON complet du compte'); area.addEventListener('input', () => { jsonDraft = area.value; markDirty(); }); box.append(area);
  }
}
function renderGrades() {
  const box = panel('Résultats académiques', 'Les notes, moyennes, rangs et décisions restent explicitement éditables : les règles de calcul de l’école ne sont pas encore toutes connues.');
  const toolbar = node('div', undefined, 'toolbar'); box.append(toolbar);
  const select = document.createElement('select'); select.setAttribute('aria-label','Année et semestre');
  semesterIndex = Math.min(semesterIndex, Math.max(0, draft.semesters.length - 1));
  draft.semesters.forEach((s,i) => { const opt = node('option', `${s.year} / ${s.year+1} · Semestre ${s.semester}`); opt.value=i; select.append(opt); });
  select.value = semesterIndex; select.addEventListener('change', () => { semesterIndex = Number(select.value); render(); }); toolbar.append(select);
  toolbar.append(action('+ Semestre', () => { draft.semesters.push(defaults('semesters', [])); semesterIndex=draft.semesters.length-1; markDirty(); render(); }, 'add'));
  const sem = draft.semesters[semesterIndex];
  if (!sem) { box.append(node('p','Ajoutez un semestre pour commencer.', 'empty')); return; }
  const metrics = node('div', undefined, 'overview');
  for (const [text,count] of [['Épreuves',sem.exams.length],['Matières',sem.modules.length],['UEs',sem.units.length]]) { const m=node('div',undefined,'metric'); m.append(node('strong',count),node('span',text)); metrics.append(m); } box.append(metrics);
  const navigation = node('div',undefined,'toolbar');
  for (const [key,text] of [['exams','Épreuves & notes'],['modules','Matières'],['units','UEs'],['summary','Semestre & jury']]) navigation.append(action(text, () => { gradeView=key; render(); },gradeView===key?'active':'')); box.append(navigation);
  if (gradeView==='summary') {
    const fields = node('div',undefined,'grid'); box.append(fields);
    for (const key of ['year','semester','status','average','average_formula','validation_report']) fields.append(scalar(key,sem[key] ?? '',v=>{sem[key]=v;},['semesters',semesterIndex]));
    structured(sem.ranking || (sem.ranking={text:'',success:true,reload:'no'}),box,['semesters',semesterIndex,'ranking']);
    box.append(action('Supprimer ce semestre',()=>{ if(confirm('Supprimer ce semestre et ses résultats ?')){draft.semesters.splice(semesterIndex,1); markDirty(); render();}},'danger'));
  } else structured(sem[gradeView],box,['semesters',semesterIndex,gradeView]);
  const scenarios = panel('Scénarios de notification', 'Appliquer un scénario remplace les résultats de 2025/2026 du compte sélectionné. Les autres années et les autres données sont conservées.');
  for (const [name,text] of [['initial','État initial'],['new_note','Nouvelle note'],['updated_note','Note corrigée']]) scenarios.append(action(text,async()=>{
    if (!canLeave() || !confirm('Remplacer les résultats 2025/2026 de ce compte par le scénario « '+text+' » ?'))return;
    await api('scenario','POST',{revision:state.revision,account_id:selected,scenario:name}); await load(); notice('Scénario appliqué à ce compte.');
  }));
}
function download(value, filename, type='application/json') {
  const blob = value instanceof Uint8Array ? new Blob([value],{type}) : new Blob([JSON.stringify(value,null,2)],{type});
  const url=URL.createObjectURL(blob), a=document.createElement('a'); a.href=url; a.download=filename; a.click(); setTimeout(()=>URL.revokeObjectURL(url),1000);
}
function renderDocuments() {
  const box = panel('Fichiers & photo de profil', 'Joignez un fichier de 5 Mo maximum. Les téléchargements Oasis nécessitent la session de son propriétaire. Une photo PNG avec le champ PHOTO alimente aussi la photo de l’application.');
  box.append(action('+ Joindre un fichier',()=>openDialog('Joindre un document',[
    ['page','Page','select',['MYDOCUMENTS','STUDENT','MYCURSUS']],['field_name','Identifiant Oasis du champ','text'],['file','Fichier','file']
  ],async values=>{
    const file=values.file; if(!file || !file.size) throw new Error('Choisissez un fichier non vide.'); if(file.size>5*1024*1024) throw new Error('Fichier limité à 5 Mo.');
    const bytes=new Uint8Array(await file.arrayBuffer()); let binary=''; for(let i=0;i<bytes.length;i+=8192) binary+=String.fromCharCode(...bytes.subarray(i,i+8192));
    draft.documents.push({id:uid(),page:values.page,field_name:values.field_name,filename:file.name,content_type:file.type||'application/octet-stream',year:2026,content_base64:btoa(binary)}); markDirty();render();
  },'Exemples : PHOTO, CV, GRADEBOOK_IC_3. Enregistrez le compte pour publier le fichier.'),'add'));
  for(const doc of draft.documents){
    const card=node('section',undefined,'panel'), head=node('div',undefined,'document-head'); box.append(card);card.append(head);head.append(node('strong',doc.filename));
    if(doc.field_name==='PHOTO' && doc.content_type==='image/png'){const img=document.createElement('img');img.src='data:image/png;base64,'+doc.content_base64;img.alt='Photo du compte';head.append(img);}
    head.append(action('Télécharger',()=>download(Uint8Array.from(atob(doc.content_base64),c=>c.charCodeAt(0)),doc.filename,doc.content_type)));
    structured(doc,card,['documents']);
    card.append(action('Retirer le fichier',()=>{if(confirm('Retirer ce fichier ?')){draft.documents.splice(draft.documents.indexOf(doc),1);markDirty();render();}},'danger'));
  }
  structured(draft.pages.MYDOCUMENTS,panel('Rubriques des documents'),['pages','MYDOCUMENTS']);
}
async function renderGlobal(){
  const titles={settings:'Réglages du serveur',events:'Sessions & activité',coverage:'Couverture des routes'};
  $('title').textContent=titles[globalView];$('subtitle').textContent='Contrôlez le comportement du simulateur pour vos tests.';
  if(globalView==='settings') {
    structured(settingsDraft,panel('Connexion & réseau','Le mode fermé renvoie une erreur d’identification configurable. Le mode IP bloquée reproduit le message observé le 2 octobre 2026. Latence et panne s’appliquent à la connexion et aux requêtes authentifiées ; l’administration reste disponible.'),['settings']);
    return;
  }
  try {
    if(globalView==='events'){
      const data=await api('events');if(globalView!=='events')return;
      const box=panel(data.sessions+' session(s) en mémoire',data.failed_attempts+' échec(s) de connexion aujourd’hui · '+data.blocked_clients+' client(s) bloqué(s). Les compteurs sont en mémoire, jusqu’à minuit (heure du serveur) ou redémarrage. Les 200 derniers événements gardent l’heure, la méthode, la route connue et le statut HTTP.');
      box.append(action('Expirer toutes les sessions',async()=>{await api('sessions/expire','POST',{});render();notice('Sessions expirées.');},'danger'));
      box.append(action('Réinitialiser les tentatives',async()=>{await api('auth/reset','POST',{});render();notice('Compteurs et blocages automatiques réinitialisés.');}));
      for(const e of data.events.reverse()){const row=node('div',undefined,'event');for(const val of [e.time,e.method||e.kind,e.route||e.mail_type||'Demande simulée',e.status||''])row.append(node('span',val));box.append(row);}
    } else {
      const data=await api('coverage');if(globalView!=='coverage')return;
      const box=panel('Contrats retrouvés dans les artifacts','« Simulée » signifie que le mock produit une réponse. Les autres routes ont été découvertes sans requête complète capturée ; configurez une réponse dans Réponses API pour les tester.');
      for(const r of data.routes){const row=node('div',undefined,'coverage-row'),description=node('div');description.append(node('code',r.route),node('small',r.pages.join(' · ')));row.append(description,node('span',r.support==='simulated'?'Simulée':'À configurer','badge'));box.append(row);}
    }
  }catch(error){notice(error.message,true);}
}
function openDialog(title, fields, submit, help=''){
  $('dialog-title').textContent=title;$('dialog-help').textContent=help;$('dialog-fields').replaceChildren();
  for(const [name,title,type,options] of fields){const label=node('label',title),input=document.createElement(type==='select'?'select':'input');input.name=name;
    if(type==='select')for(const text of options){const opt=node('option',text);opt.value=text;input.append(opt);}else input.type=type==='password'?'text':type;
    input.required=true;if(type==='password')input.autocomplete='new-password';if(type==='file')input.accept=name==='file'?'':'.json';label.append(input);$('dialog-fields').append(label);}
  $('dialog-form').onsubmit=async event=>{event.preventDefault();const values={};for(const input of $('dialog-fields').querySelectorAll('input,select'))values[input.name]=input.type==='file'?input.files[0]:input.value;
    $('dialog-submit').disabled=true;try{await submit(values);$('dialog').close();$('dialog-fields').replaceChildren();}catch(error){$('dialog-help').textContent=error.message;}finally{$('dialog-submit').disabled=false;}};
  $('dialog').showModal();
}
async function save(){
  if(globalView==='settings') await api('settings','PUT',{revision:state.revision,settings:settingsDraft});
  else {if(jsonDraft!==null)draft=JSON.parse(jsonDraft);await api('accounts/'+selected,'PUT',{revision:state.revision,account:draft});}
  await load();notice('Données enregistrées. Les prochaines requêtes Oasis utiliseront ces valeurs.');
}
$('save').addEventListener('click',async()=>{ $('save').disabled=true;try{await save();}catch(e){notice(e.message,true);}finally{$('save').disabled=false;} });
$('reload').addEventListener('click',async()=>{if(canLeave())try{await load();notice('Données rechargées.');}catch(e){notice(e.message,true);}});
$('dialog-cancel').addEventListener('click',()=>{$('dialog').close();$('dialog-fields').replaceChildren();});
$('create-account').addEventListener('click',()=>{if(!canLeave())return;openDialog('Créer un compte',[
  ['display_name','Nom affiché','text'],['student_id','Code étudiant','text'],['login','Identifiant de connexion','text'],['password','Mot de passe','password']
],async values=>{const data=await api('accounts','POST',{revision:state.revision,...values,empty:true});selected=data.accounts.at(-1).id;globalView='';tab='profile';await load();notice('Compte créé. Ajoutez son cursus et ses notes.');});});
document.querySelectorAll('[data-global]').forEach(button=>button.addEventListener('click',()=>{if(!canLeave())return;globalView=button.dataset.global;dirty=false;jsonDraft=null;settingsDraft=clone(state.settings);draft=clone(state.accounts.find(a=>a.id===selected)||null);$('save').textContent='Enregistrer';renderSidebar();render();notice('');}));
$('export').addEventListener('click',async()=>{try{download(await api('export'),'oasis-mock-backup.json');notice('Sauvegarde exportée, mots de passe du mock en clair inclus.');}catch(e){notice(e.message,true);}});
$('import-backup').addEventListener('click',()=>{if(!canLeave())return;openDialog('Restaurer une sauvegarde',[['backup','Sauvegarde JSON','file']],async values=>{
  const data=JSON.parse(await values.backup.text());if(!confirm('Remplacer toutes les données enregistrées et expirer les sessions ?'))return;
  await api('import','POST',{revision:state.revision,state:data});await load();notice('Sauvegarde restaurée.');
},'Cette action remplace les comptes et réglages. Exportez une sauvegarde au préalable si nécessaire.');});
$('import-report').addEventListener('click',()=>{if(!canLeave())return;openDialog('Importer un ancien rapport Oasis',[
  ['report','Rapport de reverse JSON','file'],['login','Login du nouveau compte mock','text'],['password','Mot de passe du compte mock','password']
],async values=>{const report=JSON.parse(await values.report.text());const data=await api('import-report','POST',{revision:state.revision,report,login:values.login,password:values.password});selected=data.accounts.at(-1).id;globalView='';tab='profile';await load();notice('Rapport importé. Joignez les fichiers locaux depuis Documents.');},'Importez all_pages.json ou un rapport de page. Les valeurs personnelles du rapport sont importées ; les fichiers distants ne sont pas téléchargés. Choisissez un mot de passe de test.');});
window.addEventListener('beforeunload',event=>{if(dirty){event.preventDefault();event.returnValue='';}});
load().catch(e=>notice(e.message,true));
