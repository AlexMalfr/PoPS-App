'use strict';
const ajax = '/prod/bo/core/Router/Ajax/ajax.php?targetProject=oasis_polytech_paris&';
const routeUrl = (route, params = {}) => ajax + new URLSearchParams({route, ...params});
for (const [id, route] of [['LoginForm', 'BO\\Connection\\User::login'], ['PassWordForgottenForm', 'Oasis\\PolytechParis\\Override\\Connection\\LoginPage::password_forgot']]) {
  const form = document.getElementById(id);
  if (form) form.addEventListener('submit', async event => {
    event.preventDefault();
    try {
      const response = await fetch(routeUrl(route), {method:'POST', body:new URLSearchParams(new FormData(form))});
      const result = await response.json();
      document.getElementById('login-error').textContent = result.text || result.error || '';
      if (result.success && id === 'LoginForm') location.href = '/';
    } catch { document.getElementById('login-error').textContent = 'Le serveur est inaccessible.'; }
  });
}
async function loadPage(code) {
  const response = await fetch(routeUrl('BO\\Layout\\MainContent::load', {codepage:code}));
  if (response.status === 403) { location.href = '/'; return; }
  document.getElementById('MainContent').innerHTML = await response.text();
}
for (const link of document.querySelectorAll('[data-page]')) link.addEventListener('click', event => {
  event.preventDefault(); location.hash = 'codepage=' + link.dataset.page;
});
window.addEventListener('hashchange', () => {
  const code = new URLSearchParams(location.hash.slice(1)).get('codepage');
  if (code) loadPage(code).catch(() => { document.getElementById('MainContent').textContent = 'Le serveur est inaccessible.'; });
});
if (location.hash && document.getElementById('MainContent')) window.dispatchEvent(new HashChangeEvent('hashchange'));
const logout = document.getElementById('logout');
if (logout) logout.addEventListener('click', async () => { await fetch(routeUrl('BO\\Connection\\Logout::logout'), {method:'POST'}); location.href='/'; });
