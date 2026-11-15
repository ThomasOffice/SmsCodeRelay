/* ============================================================
   SmsRelay · Fluent Glass — frontend logic
   ============================================================ */

/* ---------- Lucide icon library (inline SVG, stroke 1.75) ---------- */
const ICON_PATHS = {
  copy: '<rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
  x: '<path d="M18 6 6 18M6 6l12 12"/>',
  'x-circle': '<circle cx="12" cy="12" r="10"/><path d="m15 9-6 6M9 9l6 6"/>',
  minus: '<path d="M5 12h14"/>',
  minimize: '<path d="M8 3v3a2 2 0 0 1-2 2H3m18 0h-3a2 2 0 0 1-2-2V3m0 18v-3a2 2 0 0 0 2-2h3M3 16h3a2 2 0 0 0 2 2v3"/>',
  settings: '<path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/>',
  moon: '<path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/>',
  mail: '<rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>',
  shield: '<path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1 1 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/><path d="m9 12 2 2 4-4"/>',
  folder: '<path d="m6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2"/>',
  history: '<path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5M12 7v5l4 2"/>',
  inbox: '<path d="M22 12h-6l-2 3h-4l-2-3H2"/><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/>',
  refresh: '<path d="M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/>',
  eye: '<path d="M2.06 12.35a1 1 0 0 1 0-.7 10.75 10.75 0 0 1 19.88 0 1 1 0 0 1 0 .7 10.75 10.75 0 0 1-19.88 0"/><circle cx="12" cy="12" r="3"/>',
  'eye-off': '<path d="m2 2 20 20M6.71 6.71a10.75 10.75 0 0 0-4.65 5.64 1 1 0 0 0 0 .7 10.75 10.75 0 0 0 14.29 5.29M10.73 10.73a3 3 0 0 0 4.24 4.24M9.5 5.5A10.75 10.75 0 0 1 21.94 11.65a1 1 0 0 1 0 .7 10.75 10.75 0 0 1-1.44 2.5"/>',
  save: '<path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z"/><path d="M17 21v-8H7v8M7 3v5h8"/>',
  zap: '<path d="M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z"/>',
  bell: '<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0"/>',
  monitor: '<rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8M12 17v4"/>',
};
const APP_MARK = `<svg viewBox="0 0 24 24" fill="none"><defs><linearGradient id="amg" x1="0" y1="0" x2="24" y2="24"><stop offset="0" stop-color="#34d399"/><stop offset="1" stop-color="#14b8a6"/></linearGradient></defs><rect x="1.5" y="1.5" width="21" height="21" rx="6" fill="url(#amg)"/><path d="M6.5 12.5a5.5 5.5 0 0 1 11 0M9.5 12.5a2.5 2.5 0 0 1 5 0" stroke="#04241a" stroke-width="1.9" stroke-linecap="round"/><circle cx="12" cy="16.5" r="1.6" fill="#04241a"/></svg>`;

function svgIcon(name, size = 18) {
  const d = ICON_PATHS[name] || '';
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">${d}</svg>`;
}
function injectIcons(root = document) {
  root.querySelectorAll('[data-icon]').forEach(el => {
    el.innerHTML = svgIcon(el.getAttribute('data-icon'), el.classList.contains('btn-icon') ? 15 : 17);
  });
}

/* ---------- pywebview API bridge ---------- */
function api(method, ...args) {
  if (!window.pywebview || !window.pywebview.api) {
    return Promise.reject(new Error('pywebview not ready'));
  }
  const fn = window.pywebview.api[method];
  if (typeof fn !== 'function') return Promise.reject(new Error('no api ' + method));
  return Promise.resolve(fn(...args));
}

/* ---------- Toast ---------- */
function toast(msg, type = 'success', ms = 2200) {
  const c = document.getElementById('toast-container');
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.innerHTML = svgIcon(type === 'error' ? 'x-circle' : 'check', 15) + `<span>${msg}</span>`;
  c.appendChild(el);
  setTimeout(() => {
    el.classList.add('leaving');
    setTimeout(() => el.remove(), 200);
  }, ms);
}

/* ---------- Confirm dialog ---------- */
function confirmDialog(title, html, okText = '确定') {
  return new Promise(resolve => {
    const bd = document.getElementById('confirm-backdrop');
    const box = document.getElementById('confirm-box');
    box.innerHTML = `
      <div class="confirm-title">${title}</div>
      <div class="confirm-text">${html}</div>
      <div class="confirm-actions">
        <button class="btn-ghost sm" id="cf-cancel">取消</button>
        <button class="btn-primary sm" id="cf-ok">${okText}</button>
      </div>`;
    bd.hidden = false;
    const done = v => { bd.hidden = true; box.innerHTML = ''; resolve(v); };
    box.querySelector('#cf-ok').onclick = () => done(true);
    box.querySelector('#cf-cancel').onclick = () => done(false);
    bd.onclick = e => { if (e.target === bd) done(false); };
  });
}

/* ---------- State rendering (live region only) ---------- */
function renderLive(s) {
  // Hero
  const codeEl = document.getElementById('hero-code');
  const metaEl = document.getElementById('hero-meta');
  if (s.latest && s.latest.code) {
    codeEl.textContent = s.latest.code;
    codeEl.classList.remove('placeholder');
    metaEl.textContent = `来自 ${s.latest.sender} · ${s.latest.time}`;
  } else {
    codeEl.textContent = '—';
    codeEl.classList.add('placeholder');
    metaEl.textContent = '尚未收到验证码';
  }

  // BT status
  const orb = document.getElementById('bt-orb');
  const lbl = document.getElementById('bt-label');
  orb.className = 'orb';
  if (s.bt.connected) { orb.classList.add('live'); lbl.textContent = 'Android 已连接'; }
  else if (s.bt.listening) { orb.classList.add('idle'); lbl.textContent = '监听中，等待连接'; }
  else { orb.classList.add('warn'); lbl.textContent = '蓝牙启动中…'; }

  // Email status
  const eorb = document.getElementById('email-orb');
  const elbl = document.getElementById('email-label');
  eorb.className = 'orb';
  if (!s.email.enabled) { eorb.classList.add('orb-muted'); elbl.textContent = '邮箱未启用'; }
  else if (s.email.error) { eorb.classList.add('err'); elbl.textContent = '邮箱异常'; elbl.title = s.email.error; }
  else { eorb.classList.add('live'); elbl.textContent = '邮箱轮询中'; elbl.title = ''; }

  // History
  const list = document.getElementById('history-list');
  document.getElementById('hist-count').textContent = s.history.length;
  if (!s.history.length) {
    list.innerHTML = `<div class="empty-state"><span class="empty-icon" data-icon="inbox"></span><p>收到的验证码会出现在这里</p><p class="empty-hint">点击任意一行即可复制</p></div>`;
    injectIcons(list);
  } else {
    const rows = s.history.slice().reverse().map(r => `
      <div class="history-row" data-code="${escapeAttr(r.code)}" title="点击复制 ${escapeAttr(r.code)}">
        <span class="hr-time">${escapeHtml(r.time)}</span>
        <div class="hr-main">
          <span class="hr-code">${escapeHtml(r.code)}</span>
          <span class="hr-sender">${escapeHtml(r.sender)}</span>
        </div>
        <span class="hr-copy">${svgIcon('copy', 14)}</span>
      </div>`).join('');
    list.innerHTML = rows;
    list.querySelectorAll('.history-row').forEach(row => {
      row.onclick = () => doCopy(row.dataset.code, `已复制 ${row.dataset.code}`);
    });
  }
}

/* ---------- Populate settings form (init + after save) ---------- */
function fillSettings(s) {
  document.getElementById('token-display').textContent = s.token || '';
  const ec = s.email_cfg || {};
  document.getElementById('email-enabled').checked = !!ec.enabled;
  document.getElementById('email-host').value = ec.imap_host || 'imap.qq.com';
  document.getElementById('email-port').value = ec.imap_port || 993;
  document.getElementById('email-interval').value = ec.poll_interval || 30;
  document.getElementById('email-user').value = ec.username || '';
  document.getElementById('email-pass').value = ec.password || '';
  document.getElementById('email-mark').checked = ec.mark_seen !== false;
  document.getElementById('email-strict').checked = ec.strict !== false;
  document.getElementById('notify-enabled').checked = s.notify !== false;
  document.getElementById('close-to-tray').checked = s.close_to_tray !== false;
  document.getElementById('start-to-tray').checked = !!s.start_to_tray;
  document.getElementById('autostart').checked = !!s.autostart;

  const preset = document.getElementById('email-preset');
  preset.innerHTML = Object.keys(s.presets || {}).map(k => `<option value="${escapeAttr(k)}">${escapeHtml(k)}</option>`).join('');
  applyAppearance(s.appearance || 'dark');
}

function readEmailForm() {
  return {
    enabled: document.getElementById('email-enabled').checked,
    imap_host: document.getElementById('email-host').value.trim() || 'imap.qq.com',
    imap_port: parseInt(document.getElementById('email-port').value, 10) || 993,
    poll_interval: parseInt(document.getElementById('email-interval').value, 10) || 30,
    username: document.getElementById('email-user').value.trim(),
    password: document.getElementById('email-pass').value.trim(),
    mark_seen: document.getElementById('email-mark').checked,
    strict: document.getElementById('email-strict').checked,
  };
}

/* ---------- Actions ---------- */
async function doCopy(text, msg) {
  if (!text) return;
  try { await api('copy', text); toast(msg || '已复制'); }
  catch (e) { toast('复制失败', 'error'); }
}

async function copyLatest() {
  const code = document.getElementById('hero-code').textContent;
  if (!code || code === '—') { toast('暂无验证码', 'error'); return; }
  const btn = document.getElementById('btn-copy-latest');
  btn.classList.add('copied');
  const old = btn.innerHTML;
  btn.innerHTML = svgIcon('check', 15) + '<span>已复制</span>';
  await doCopy(code, `已复制 ${code}`);
  setTimeout(() => { btn.classList.remove('copied'); btn.innerHTML = old; injectIcons(btn); }, 1400);
}

async function genToken() {
  const ok = await confirmDialog('生成新 Token',
    '将生成新的随机 Token 并保存。\n<span class="mono">需同步更新手机 App 中的 Token，否则转发将失败！</span>\n\n确定生成吗？', '生成并复制');
  if (!ok) return;
  try {
    const token = await api('gen_token');
    document.getElementById('token-display').textContent = token;
    await api('copy', token);
    toast('新 Token 已生成并复制');
  } catch (e) { toast('生成失败', 'error'); }
}

async function saveEmail() {
  const cfg = readEmailForm();
  if (cfg.enabled && (!cfg.username || !cfg.password)) {
    toast('请填写邮箱账号和授权码', 'error'); return;
  }
  try {
    const res = await api('save_email', cfg);
    if (res && res.ok) toast('邮箱配置已保存');
    else toast('保存失败: ' + (res && res.error || ''), 'error');
  } catch (e) { toast('保存失败', 'error'); }
}

async function testEmail() {
  const cfg = readEmailForm();
  if (!cfg.username || !cfg.password) { toast('请先填写账号和授权码', 'error'); return; }
  const btn = document.getElementById('btn-test-email');
  const old = btn.innerHTML;
  btn.disabled = true; btn.innerHTML = '测试中…';
  try {
    const res = await api('test_email', cfg);
    if (res && res.ok) toast('IMAP 连接并登录成功');
    else toast('连接失败: ' + (res && res.error || ''), 'error', 4000);
  } catch (e) { toast('连接失败', 'error'); }
  finally { btn.disabled = false; btn.innerHTML = old; injectIcons(btn); }
}

/* ---------- Appearance ---------- */
let _appearance = 'dark';
let _mediaQuery = null;
function _onSystemTheme() {
  if (_appearance === 'system') applyAppearance('system');
}
function applyAppearance(mode) {
  _appearance = mode;
  const effective = mode === 'system'
    ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
    : mode;
  document.documentElement.setAttribute('data-theme', effective);
  document.querySelectorAll('#appearance-seg .seg').forEach(b => {
    b.classList.toggle('active', b.dataset.mode === mode);
  });
  const icon = mode === 'dark' ? 'sun' : mode === 'light' ? 'moon' : 'monitor';
  document.getElementById('btn-appearance').innerHTML = svgIcon(icon, 17);
  if (_mediaQuery) {
    _mediaQuery.removeEventListener('change', _onSystemTheme);
    _mediaQuery = null;
  }
  if (mode === 'system') {
    _mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    _mediaQuery.addEventListener('change', _onSystemTheme);
  }
}
async function setAppearance(mode) {
  applyAppearance(mode);
  try { await api('set_appearance', mode); } catch (e) {}
}

/* ---------- Drawer ---------- */
function openSettings() {
  document.getElementById('settings-backdrop').hidden = false;
  document.getElementById('settings-drawer').hidden = false;
}
function closeSettings() {
  document.getElementById('settings-backdrop').hidden = true;
  document.getElementById('settings-drawer').hidden = true;
}

/* ---------- Utils ---------- */
function escapeHtml(s) { return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }
function escapeAttr(s) { return escapeHtml(s); }

/* ---------- Init ---------- */
let _lastLiveKey = '';
window.__setState = function (s) {
  // 仅当 live 区域数据变化时重绘，避免闪烁
  const key = JSON.stringify([s.bt, s.email, s.latest, s.history]);
  if (key !== _lastLiveKey) { _lastLiveKey = key; renderLive(s); }
};

window.addEventListener('pywebviewready', async () => {
  try {
    const s = await api('get_state');
    _appearance = s.appearance || 'dark';
    renderLive(s);
    fillSettings(s);
  } catch (e) { console.error('init failed', e); }
});

document.addEventListener('DOMContentLoaded', () => {
  injectIcons();
  document.getElementById('app-mark').innerHTML = APP_MARK;
  document.getElementById('btn-settings').innerHTML = svgIcon('settings', 17);
  document.getElementById('btn-minimize').innerHTML = svgIcon('minus', 17);
  document.getElementById('btn-close').innerHTML = svgIcon('x', 17);
  document.getElementById('btn-appearance').innerHTML = svgIcon('sun', 17);

  // Title bar
  document.getElementById('btn-minimize').onclick = () => api('minimize_window');
  document.getElementById('btn-close').onclick = () => api('hide_window');
  document.getElementById('btn-settings').onclick = openSettings;
  document.getElementById('btn-settings-close').onclick = closeSettings;
  document.getElementById('settings-backdrop').onclick = closeSettings;
  document.getElementById('btn-appearance').onclick = () => {
    const next = _appearance === 'dark' ? 'light' : _appearance === 'light' ? 'system' : 'dark';
    setAppearance(next);
  };

  // Dashboard actions
  document.getElementById('btn-copy-latest').onclick = copyLatest;
  document.getElementById('btn-reconnect').onclick = async () => { await api('force_reconnect'); toast('正在重置蓝牙…'); };
  document.getElementById('btn-open-dir').onclick = () => api('open_config_dir');
  document.getElementById('btn-hide').onclick = () => api('hide_window');

  // Settings actions
  document.getElementById('token-display').onclick = () => doCopy(document.getElementById('token-display').textContent, '已复制 Token');
  document.getElementById('btn-copy-token').onclick = () => doCopy(document.getElementById('token-display').textContent, '已复制 Token');
  document.getElementById('btn-gen-token').onclick = genToken;
  document.getElementById('btn-save-email').onclick = saveEmail;
  document.getElementById('btn-test-email').onclick = testEmail;
  document.getElementById('notify-enabled').onchange = e => api('set_notify', e.target.checked);
  document.getElementById('close-to-tray').onchange = e => api('set_close_to_tray', e.target.checked);
  document.getElementById('start-to-tray').onchange = e => api('set_start_to_tray', e.target.checked);
  document.getElementById('autostart').onchange = async e => {
    const ok = await api('set_autostart', e.target.checked);
    if (ok) toast(e.target.checked ? '已开启开机自启动' : '已关闭开机自启动');
    else { toast('设置失败', 'error'); e.target.checked = !e.target.checked; }
  };
  document.getElementById('email-preset').onchange = e => {
    // 预设选择由前端直接填 host/port（presets 在 state 中）
    // 从当前 state 缓存读取
    const presets = window.__presets || {};
    const p = presets[e.target.value];
    if (p) { document.getElementById('email-host').value = p[0]; document.getElementById('email-port').value = p[1]; }
  };
  document.querySelectorAll('#appearance-seg .seg').forEach(b => {
    b.onclick = () => setAppearance(b.dataset.mode);
  });
  document.getElementById('btn-toggle-pass').onclick = () => {
    const inp = document.getElementById('email-pass');
    const btn = document.getElementById('btn-toggle-pass');
    const show = inp.type === 'password';
    inp.type = show ? 'text' : 'password';
    btn.innerHTML = svgIcon(show ? 'eye-off' : 'eye', 15);
  };

  // 键盘：Esc 关闭抽屉/对话框
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape') { closeSettings(); document.getElementById('confirm-backdrop').hidden = true; }
  });
});

// 供 preset 使用：在 get_state 时缓存 presets
const _origSetState = window.__setState;
window.__setState = function (s) { window.__presets = s.presets; _origSetState(s); };
// get_state 也填一次 presets
window.addEventListener('pywebviewready', async () => {
  try { const s = await api('get_state'); window.__presets = s.presets; } catch (e) {}
});