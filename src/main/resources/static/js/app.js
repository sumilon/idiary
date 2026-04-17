/* ── Memoiré – Main JavaScript ── */

// ── Auth helpers ──
const TOKEN_KEY = 'memoir_token';
const USER_KEY  = 'memoir_user';

function getToken()   { return localStorage.getItem(TOKEN_KEY); }
function getUser()    { try { return JSON.parse(localStorage.getItem(USER_KEY)); } catch { return null; } }
function setAuth(token, user) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}
function clearAuth()  { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY); }

function requireAuth() {
  if (!getToken()) { window.location.href = '/login'; return false; }
  return true;
}

function logout() {
  clearAuth();
  window.location.href = '/login';
}

// ── API helper ──
async function api(method, path, body) {
  const headers = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(`/api${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined
  });

  const data = await res.json();

  if (res.status === 401) {
    clearAuth();
    window.location.href = '/login';
    throw new Error('Session expired');
  }

  if (!data.success && data.message) throw new Error(data.message);
  if (!res.ok && !data.success) throw new Error('Request failed');

  return data.data ?? data;
}

// ── Date helpers ──
function formatDate(ts) {
  const d = new Date(ts);
  return d.toLocaleDateString('en-US', { weekday: 'short', year: 'numeric', month: 'short', day: 'numeric' });
}

function greet() {
  const h = new Date().getHours();
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  return 'Good evening';
}

// ── Auth pages ──

async function handleLogin(e) {
  e.preventDefault();
  const btn = document.getElementById('loginBtn');
  const errEl = document.getElementById('loginError');
  errEl.classList.add('hidden');
  btn.disabled = true;
  btn.querySelector('.btn-text').textContent = 'Signing in...';

  try {
    const data = await api('POST', '/auth/login', {
      email: document.getElementById('email').value,
      password: document.getElementById('password').value
    });
    setAuth(data.token, data.user);
    window.location.href = '/dashboard';
  } catch (err) {
    errEl.textContent = err.message || 'Login failed. Please try again.';
    errEl.classList.remove('hidden');
    btn.disabled = false;
    btn.querySelector('.btn-text').textContent = 'Sign In';
  }
}

async function handleRegister(e) {
  e.preventDefault();
  const btn = document.getElementById('registerBtn');
  const errEl = document.getElementById('registerError');
  errEl.classList.add('hidden');
  btn.disabled = true;
  btn.querySelector('.btn-text').textContent = 'Creating account...';

  try {
    const data = await api('POST', '/auth/register', {
      name: document.getElementById('name').value,
      email: document.getElementById('email').value,
      password: document.getElementById('password').value
    });
    setAuth(data.token, data.user);
    window.location.href = '/dashboard';
  } catch (err) {
    errEl.textContent = err.message || 'Registration failed. Please try again.';
    errEl.classList.remove('hidden');
    btn.disabled = false;
    btn.querySelector('.btn-text').textContent = 'Create Account';
  }
}

// ── Dashboard ──

async function initDashboard() {
  if (!requireAuth()) return;

  const user = getUser();
  const greeting = document.getElementById('headerGreeting');
  const title    = document.getElementById('dashboardTitle');
  const dateEl   = document.getElementById('dashboardDate');

  if (greeting) greeting.textContent = user?.name || '';
  if (title)    title.textContent = `${greet()}, ${user?.name?.split(' ')[0] || 'there'}`;
  if (dateEl)   dateEl.textContent = new Date().toLocaleDateString('en-US', { weekday:'long', year:'numeric', month:'long', day:'numeric' });

  await loadEntries();
}

async function loadEntries() {
  const container = document.getElementById('entriesContainer');
  if (!container) return;

  try {
    const entries = await api('GET', '/diary');
    renderEntries(container, entries);
  } catch (err) {
    container.innerHTML = `<div class="empty-state">
      <div class="empty-state-icon">⚠️</div>
      <h3>Couldn't load entries</h3>
      <p>${err.message}</p>
    </div>`;
  }
}

function renderEntries(container, entries) {
  if (!entries || entries.length === 0) {
    container.innerHTML = `<div class="empty-state">
      <div class="empty-state-icon">📖</div>
      <h3>Your diary awaits</h3>
      <p>Start capturing your thoughts and memories.</p>
      <a href="/entry/new" class="btn-new">Write your first entry</a>
    </div>`;
    return;
  }

  container.innerHTML = entries.map((entry, i) => `
    <a href="/entry/${entry.id}" class="entry-card" style="animation-delay:${i * 0.05}s">
      ${entry.mood ? `<div class="card-mood">${moodEmoji(entry.mood)}</div>` : ''}
      <div class="card-title">${escapeHtml(entry.title || 'Untitled')}</div>
      <div class="card-preview">${escapeHtml(entry.preview || '')}</div>
      <div class="card-footer">
        <span class="card-date">${formatDate(entry.createdAt)}</span>
        <div class="card-actions">
          <button class="card-delete-btn" onclick="deleteEntry(event, '${entry.id}')" title="Delete">🗑</button>
        </div>
      </div>
    </a>
  `).join('');
}

async function deleteEntry(e, id) {
  e.preventDefault();
  e.stopPropagation();
  if (!confirm('Delete this diary entry? This cannot be undone.')) return;
  try {
    await api('DELETE', `/diary/${id}`);
    await loadEntries();
  } catch (err) {
    alert('Failed to delete entry: ' + err.message);
  }
}

function moodEmoji(mood) {
  const map = { happy:'😊', sad:'😔', excited:'🤩', calm:'😌', anxious:'😰', angry:'😤', grateful:'🙏' };
  return map[mood] || '';
}

// ── Entry page ──

let currentEntry = null;

async function initEntryPage() {
  if (!requireAuth()) return;

  const entryId  = document.getElementById('entryId')?.value;
  const isNew    = document.getElementById('isNewEntry')?.value === 'true';
  const dateEl   = document.getElementById('entryDate');

  if (dateEl) dateEl.textContent = new Date().toLocaleDateString('en-US', { weekday:'long', year:'numeric', month:'long', day:'numeric' });

  // Mood picker
  document.querySelectorAll('.mood-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.mood-btn').forEach(b => b.classList.remove('selected'));
      btn.classList.add('selected');
      document.getElementById('selectedMood').value = btn.dataset.mood;
    });
  });

  if (!isNew && entryId) {
    await loadEntry(entryId);
  }
}

async function loadEntry(id) {
  try {
    const entry = await api('GET', `/diary/${id}`);
    currentEntry = entry;

    document.getElementById('entryTitle').value   = entry.title || '';
    document.getElementById('entryContent').value = entry.content || '';

    if (entry.mood) {
      document.getElementById('selectedMood').value = entry.mood;
      document.querySelectorAll('.mood-btn').forEach(b => {
        if (b.dataset.mood === entry.mood) b.classList.add('selected');
      });
    }

    if (entry.aiContent) {
      document.getElementById('aiGeneratedContent').value = entry.aiContent;
      document.getElementById('usingAiContent').value = String(entry.usedAiContent);
      if (entry.usedAiContent) showAiPanel(entry.aiContent);
    }
  } catch (err) {
    showSaveStatus('Failed to load entry: ' + err.message, 'error');
  }
}

async function rewriteWithAI() {
  const content = document.getElementById('entryContent').value.trim();
  if (!content) {
    showAiError('Please write something before using AI enhancement.');
    return;
  }

  const btn = document.getElementById('aiBtn');
  const instruction = document.getElementById('aiInstruction').value;
  const errEl = document.getElementById('aiError');
  errEl.classList.add('hidden');

  btn.disabled = true;
  btn.innerHTML = `<span class="loading-spinner" style="width:14px;height:14px;border-width:2px;display:inline-block;vertical-align:middle;margin-right:6px"></span>Enhancing...`;

  try {
    const result = await api('POST', '/diary/ai-rewrite', { content, instruction });
    document.getElementById('aiGeneratedContent').value = result.rewritten;
    document.getElementById('usingAiContent').value = 'false';
    showAiPanel(result.rewritten);
  } catch (err) {
    showAiError(err.message || 'AI enhancement failed. Please try again.');
  } finally {
    btn.disabled = false;
    btn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg> Enhance with AI`;
  }
}

function showAiPanel(content) {
  const aiPanel = document.getElementById('aiPanel');
  const panels  = document.getElementById('editorPanels');
  const display = document.getElementById('aiContent');

  display.textContent = content;
  aiPanel.classList.remove('hidden');

  if (window.innerWidth >= 900) {
    panels.classList.add('split');
  }
}

function hideAiPanel() {
  document.getElementById('aiPanel').classList.add('hidden');
  document.getElementById('editorPanels').classList.remove('split');
}

function useAiContent() {
  const aiContent = document.getElementById('aiGeneratedContent').value;
  document.getElementById('entryContent').value = aiContent;
  document.getElementById('usingAiContent').value = 'true';
  hideAiPanel();
  showSaveStatus('AI content applied! Remember to save.', 'success');
}

function keepOriginal() {
  document.getElementById('usingAiContent').value = 'false';
  hideAiPanel();
}

function showAiError(msg) {
  const el = document.getElementById('aiError');
  el.textContent = msg;
  el.classList.remove('hidden');
  setTimeout(() => el.classList.add('hidden'), 5000);
}

async function saveEntry() {
  const entryId  = document.getElementById('entryId').value;
  const isNew    = document.getElementById('isNewEntry').value === 'true';
  const title    = document.getElementById('entryTitle').value.trim();
  const content  = document.getElementById('entryContent').value.trim();
  const mood     = document.getElementById('selectedMood').value || null;
  const aiContent = document.getElementById('aiGeneratedContent').value || null;
  const usingAi  = document.getElementById('usingAiContent').value === 'true';
  const btn      = document.getElementById('saveBtn');

  if (!content) {
    showSaveStatus('Please write something before saving.', 'error');
    return;
  }

  btn.disabled = true;
  btn.textContent = 'Saving...';

  try {
    if (isNew) {
      const created = await api('POST', '/diary', {
        title: title || 'Untitled',
        content,
        mood
      });
      // If AI content exists, update with it
      if (aiContent) {
        await api('PUT', `/diary/${created.id}`, {
          title: title || 'Untitled',
          content,
          aiContent,
          usedAiContent: usingAi,
          mood
        });
      }
      showSaveStatus('Entry saved!', 'success');
      setTimeout(() => { window.location.href = '/dashboard'; }, 800);
    } else {
      await api('PUT', `/diary/${entryId}`, {
        title: title || 'Untitled',
        content,
        aiContent,
        usedAiContent: usingAi,
        mood
      });
      showSaveStatus('Entry updated!', 'success');
    }
  } catch (err) {
    showSaveStatus('Save failed: ' + err.message, 'error');
  } finally {
    btn.disabled = false;
    btn.innerHTML = `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg> Save`;
  }
}

function showSaveStatus(msg, type) {
  const el = document.getElementById('saveStatus');
  if (!el) return;
  el.textContent = msg;
  el.className = `save-status ${type}`;
  el.classList.remove('hidden');
  setTimeout(() => el.classList.add('hidden'), 3000);
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.appendChild(document.createTextNode(str));
  return div.innerHTML;
}

// ── Auto-redirect logged in users from auth pages ──
function redirectIfLoggedIn() {
  if (getToken()) window.location.href = '/dashboard';
}

// ── Page router ──
const path = window.location.pathname;

if (path === '/login' || path === '/register') {
  redirectIfLoggedIn();
} else if (path === '/dashboard') {
  document.addEventListener('DOMContentLoaded', initDashboard);
} else if (path.startsWith('/entry')) {
  document.addEventListener('DOMContentLoaded', initEntryPage);
}
