/* ── Inkwell – Main JavaScript ── */

// ── Auth storage ──
const TOKEN_KEY         = 'memoir_token';
const REFRESH_TOKEN_KEY = 'memoir_refresh_token';
const USER_KEY          = 'memoir_user';

const getToken        = ()        => localStorage.getItem(TOKEN_KEY);
const getRefreshToken = ()        => localStorage.getItem(REFRESH_TOKEN_KEY);
const getUser         = ()        => { try { return JSON.parse(localStorage.getItem(USER_KEY)); } catch { return null; } };
const setAuth         = (t, rt, u) => {
  localStorage.setItem(TOKEN_KEY, t);
  if (rt) localStorage.setItem(REFRESH_TOKEN_KEY, rt);
  localStorage.setItem(USER_KEY, JSON.stringify(u));
};
const clearAuth = () => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
};

function requireAuth() {
  if (!getToken()) { window.location.href = '/login'; return false; }
  return true;
}

function logout() {
  clearAuth();
  window.location.href = '/login';
}

// ── API helper ──
async function api(method, path, body, isRetry = false) {
  const headers = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res  = await fetch(`/api${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const data = await res.json();

  // On 401, try a silent token refresh once before forcing re-login
  if (res.status === 401 && !isRetry) {
    const refreshed = await tryRefreshToken();
    if (refreshed) return api(method, path, body, true);
    clearAuth();
    window.location.href = '/login';
    throw new Error('Session expired');
  }

  if (!data.success && data.message) throw new Error(data.message);
  if (!res.ok && !data.success)      throw new Error('Request failed');

  return data.data ?? data;
}

async function tryRefreshToken() {
  const rt = getRefreshToken();
  if (!rt) return false;
  try {
    const res  = await fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${rt}`, 'Content-Type': 'application/json' }
    });
    if (!res.ok) return false;
    const data = await res.json();
    if (data.success && data.data?.token) {
      setAuth(data.data.token, data.data.refreshToken, data.data.user);
      return true;
    }
  } catch (_) { /* fall through */ }
  return false;
}

// ── Utility helpers ──
function formatDate(ts) {
  return new Date(ts).toLocaleDateString('en-US', { weekday: 'short', year: 'numeric', month: 'short', day: 'numeric' });
}

function formatTime(ts) {
  return new Date(ts).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
}

function greet() {
  const h = new Date().getHours();
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  return 'Good evening';
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.appendChild(document.createTextNode(str));
  return div.innerHTML;
}

function moodEmoji(mood) {
  const map = { happy: '😊', sad: '😔', excited: '🤩', calm: '😌', anxious: '😰', angry: '😤', grateful: '🙏' };
  return map[mood] || '';
}

function moodLabel(mood) {
  if (!mood) return '';
  return mood.charAt(0).toUpperCase() + mood.slice(1);
}

// ── Auth pages ──

async function handleLogin(e) {
  e.preventDefault();
  const btn   = document.getElementById('loginBtn');
  const errEl = document.getElementById('loginError');
  errEl.classList.add('hidden');
  btn.disabled = true;
  btn.querySelector('.btn-text').textContent = 'Signing in…';

  try {
    const data = await api('POST', '/auth/login', {
      email:    document.getElementById('email').value,
      password: document.getElementById('password').value,
    });
    setAuth(data.token, data.refreshToken, data.user);
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
  const btn   = document.getElementById('registerBtn');
  const errEl = document.getElementById('registerError');
  errEl.classList.add('hidden');
  btn.disabled = true;
  btn.querySelector('.btn-text').textContent = 'Creating account…';

  try {
    const data = await api('POST', '/auth/register', {
      name:     document.getElementById('name').value,
      email:    document.getElementById('email').value,
      password: document.getElementById('password').value,
    });
    setAuth(data.token, data.refreshToken, data.user);
    window.location.href = '/dashboard';
  } catch (err) {
    errEl.textContent = err.message || 'Registration failed. Please try again.';
    errEl.classList.remove('hidden');
    btn.disabled = false;
    btn.querySelector('.btn-text').textContent = 'Create Account';
  }
}

// ── Dashboard ──

const PAGE_SIZE = 10;

let allEntries     = [];
let filteredEntries = [];
let currentPage    = 1;

async function initDashboard() {
  if (!requireAuth()) return;

  const user     = getUser();
  const greeting = document.getElementById('headerGreeting');
  const title    = document.getElementById('dashboardTitle');
  const dateEl   = document.getElementById('dashboardDate');

  if (greeting) greeting.textContent = user?.name || '';
  if (title)    title.textContent    = `${greet()}, ${user?.name?.split(' ')[0] || 'there'}`;
  if (dateEl)   dateEl.textContent   = new Date().toLocaleDateString('en-US', {
    weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
  });

  await Promise.all([loadEntries(), loadDashboardStats()]);
}

async function loadDashboardStats() {
  try {
    const [streak, analytics] = await Promise.all([
      api('GET', '/diary/analytics/streak'),
      api('GET', '/diary/analytics/mood'),
    ]);
    const streakEl = document.getElementById('statStreak');
    const totalEl  = document.getElementById('statTotal');
    const moodEl   = document.getElementById('statTopMood');
    if (streakEl) streakEl.textContent = `🔥 ${streak.currentStreak} day streak`;
    if (totalEl)  totalEl.textContent  = `📖 ${streak.totalDaysJournaled} days journaled`;
    if (moodEl) {
      const top = analytics.moodCounts?.[0];
      moodEl.textContent = top ? `✨ Top mood: ${top.mood} (${top.count}×)` : '✨ No mood data yet';
    }
  } catch (_) { /* stats are non-critical */ }
}

async function loadEntries() {
  const container = document.getElementById('entriesContainer');
  if (!container) return;

  try {
    const paged     = await api('GET', '/diary?limit=100');
    allEntries      = paged.items ?? paged ?? [];
    filteredEntries = allEntries;
    currentPage     = 1;
    renderPage();
  } catch (err) {
    container.innerHTML = `<div class="state-center">
      <div class="state-icon">⚠️</div>
      <h3>Couldn't load entries</h3>
      <p>${escapeHtml(err.message)}</p>
    </div>`;
  }
}

function applyFilters(query) {
  const q    = (query ?? document.getElementById('searchInput')?.value ?? '').toLowerCase().trim();
  const mood = document.getElementById('moodFilter')?.value ?? '';

  filteredEntries = allEntries.filter(e => {
    const matchQ    = !q    || (e.title   || '').toLowerCase().includes(q) || (e.preview || '').toLowerCase().includes(q);
    const matchMood = !mood || e.mood === mood;
    return matchQ && matchMood;
  });
  currentPage = 1;
  renderPage();
}

function renderPage() {
  const container  = document.getElementById('entriesContainer');
  const pagBar     = document.getElementById('paginationBar');
  const countEl    = document.getElementById('entryCount');
  if (!container) return;

  const total      = filteredEntries.length;
  const totalPages = Math.ceil(total / PAGE_SIZE);
  const start      = (currentPage - 1) * PAGE_SIZE;
  const pageItems  = filteredEntries.slice(start, start + PAGE_SIZE);

  // Entry count label
  if (countEl) {
    countEl.textContent = total === 0 ? '' :
      total === 1 ? '1 entry' : `${total} entries`;
  }

  if (total === 0) {
    container.innerHTML = `<div class="state-center">
      <div class="state-icon">📖</div>
      <h3>Your diary awaits</h3>
      <p>Start capturing your thoughts and memories.</p>
      <a href="/entry/new" class="btn-new-entry" style="margin-top:0.5rem">Write your first entry</a>
    </div>`;
    if (pagBar) pagBar.classList.add('hidden');
    return;
  }

  // Render list rows
  container.innerHTML = pageItems.map((entry, i) => `
    <a href="/entry/${entry.id}" class="entry-row" style="animation-delay:${i * 0.04}s">
      <div class="entry-row-left">
        ${entry.mood ? `<div class="row-mood" title="${moodLabel(entry.mood)}">${moodEmoji(entry.mood)}</div>` : '<div class="row-mood-placeholder"></div>'}
      </div>
        <div class="entry-row-body">
        <div class="row-title">${escapeHtml(entry.title || 'Untitled')}</div>
        <div class="row-preview">${escapeHtml(entry.preview || '')}</div>
      </div>
      <div class="entry-row-meta">
        <span class="row-date">${formatDate(entry.createdAt)}</span>
        <span class="row-time">${formatTime(entry.createdAt)}</span>
        ${entry.mood ? `<span class="row-mood-badge">${moodLabel(entry.mood)}</span>` : ''}
      </div>
      <div class="entry-row-actions">
        <button class="row-del-btn" onclick="deleteEntry(event,'${entry.id}')" title="Delete entry">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/>
            <path d="M9 6V4h6v2"/>
          </svg>
        </button>
      </div>
    </a>
  `).join('');

  // Pagination bar
  if (!pagBar) return;
  if (totalPages <= 1) {
    pagBar.classList.add('hidden');
    return;
  }

  pagBar.classList.remove('hidden');
  const rangeStart = start + 1;
  const rangeEnd   = Math.min(start + PAGE_SIZE, total);

  pagBar.innerHTML = `
    <span class="pag-info">${rangeStart}–${rangeEnd} of ${total}</span>
    <div class="pag-btns">
      <button class="pag-btn" onclick="goPage(${currentPage - 1})" ${currentPage === 1 ? 'disabled' : ''} title="Previous page">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="15 18 9 12 15 6"/>
        </svg>
      </button>
      ${buildPageNumbers(currentPage, totalPages)}
      <button class="pag-btn" onclick="goPage(${currentPage + 1})" ${currentPage === totalPages ? 'disabled' : ''} title="Next page">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="9 18 15 12 9 6"/>
        </svg>
      </button>
    </div>
  `;
}

function buildPageNumbers(current, total) {
  const pages = [];
  // Always show first, last, current ±1, with ellipsis
  const show = new Set([1, total, current, current - 1, current + 1].filter(p => p >= 1 && p <= total));
  const sorted = [...show].sort((a, b) => a - b);

  let prev = 0;
  for (const p of sorted) {
    if (prev && p - prev > 1) pages.push('<span class="pag-ellipsis">…</span>');
    pages.push(`<button class="pag-btn pag-num ${p === current ? 'active' : ''}" onclick="goPage(${p})">${p}</button>`);
    prev = p;
  }
  return pages.join('');
}

function goPage(page) {
  currentPage = page;
  renderPage();
  document.querySelector('.dashboard-body')?.scrollTo({ top: 0, behavior: 'smooth' });
  window.scrollTo({ top: 0, behavior: 'smooth' });
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

// ── Entry page ──


async function initEntryPage() {
  if (!requireAuth()) return;

  const entryIdEl  = document.getElementById('entryId');
  const isNewEl    = document.getElementById('isNewEntry');
  const entryId    = entryIdEl?.value?.trim() || '';
  const isNew      = (isNewEl?.value ?? 'true') === 'true';
  const dateEl     = document.getElementById('entryDate');

  if (dateEl) dateEl.textContent = new Date().toLocaleDateString('en-US', {
    weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
  });

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
  const titleEl   = document.getElementById('entryTitle');
  const contentEl = document.getElementById('entryContent');

  // Show loading state
  if (titleEl)   titleEl.placeholder   = 'Loading…';
  if (contentEl) contentEl.placeholder = 'Loading entry…';

  try {
    const entry = await api('GET', `/diary/${id}`);

    if (titleEl)   titleEl.value   = entry.title   || '';
    if (contentEl) contentEl.value = entry.content || '';

    if (entry.mood) {
      const moodInput = document.getElementById('selectedMood');
      if (moodInput) moodInput.value = entry.mood;
      document.querySelectorAll('.mood-btn').forEach(b => {
        if (b.dataset.mood === entry.mood) b.classList.add('selected');
      });
    }
  } catch (err) {
    const msg = 'Failed to load entry: ' + (err.message || 'Unknown error');
    if (titleEl)   titleEl.placeholder   = 'Give your entry a title…';
    if (contentEl) contentEl.placeholder = 'What\'s on your mind today?\n\nWrite freely — your thoughts, feelings, experiences…';
    showSaveStatus(msg, 'error');
  }
}

async function rewriteWithAI() {
  const content = document.getElementById('entryContent').value.trim();
  if (!content) { showAiError('Please write something before using AI enhancement.'); return; }

  const btn         = document.getElementById('aiBtn');
  const instruction = document.getElementById('aiInstruction').value;
  document.getElementById('aiError').classList.add('hidden');

  btn.disabled = true;
  btn.innerHTML = `<span class="spinner" style="width:13px;height:13px;border-width:2px;display:inline-block;vertical-align:middle;margin-right:5px"></span>Enhancing…`;

  try {
    const result = await api('POST', '/diary/ai-rewrite', { content, instruction });
    document.getElementById('aiGeneratedContent').value = result.rewritten;
    showAiPanel(result.rewritten);
  } catch (err) {
    showAiError(err.message || 'AI enhancement failed. Please try again.');
  } finally {
    btn.disabled = false;
    btn.innerHTML = `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg> Enhance`;
  }
}

function showAiPanel(content) {
  const aiPanel = document.getElementById('aiPanel');
  const panels  = document.getElementById('editorPanels');
  document.getElementById('aiContent').textContent = content;
  aiPanel.classList.remove('hidden');
  if (window.innerWidth >= 900) panels.classList.add('split');
}

function hideAiPanel() {
  document.getElementById('aiPanel').classList.add('hidden');
  document.getElementById('editorPanels').classList.remove('split');
}

function useAiContent() {
  document.getElementById('entryContent').value = document.getElementById('aiGeneratedContent').value;
  hideAiPanel();
  showSaveStatus('AI content applied! Remember to save.', 'success');
}

function keepOriginal() {
  hideAiPanel();
}

function showAiError(msg) {
  const el = document.getElementById('aiError');
  el.textContent = msg;
  el.classList.remove('hidden');
  setTimeout(() => el.classList.add('hidden'), 5000);
}

async function saveEntry() {
  const entryId = document.getElementById('entryId').value;
  const isNew   = document.getElementById('isNewEntry').value === 'true';
  const title   = document.getElementById('entryTitle').value.trim();
  const content = document.getElementById('entryContent').value.trim();
  const mood    = document.getElementById('selectedMood').value || null;
  const btn     = document.getElementById('saveBtn');

  if (!content) { showSaveStatus('Please write something before saving.', 'error'); return; }

  btn.disabled    = true;
  btn.innerHTML   = `<span class="spinner" style="width:13px;height:13px;border-width:2px;display:inline-block;vertical-align:middle;margin-right:5px"></span>Saving…`;

  try {
    if (isNew) {
      await api('POST', '/diary', { title: title || 'Untitled', content, mood });
      showSaveStatus('Entry saved!', 'success');
      setTimeout(() => { window.location.href = '/dashboard'; }, 800);
    } else {
      await api('PUT', `/diary/${entryId}`, { title: title || 'Untitled', content, mood });
      showSaveStatus('Entry updated!', 'success');
    }
  } catch (err) {
    showSaveStatus('Save failed: ' + err.message, 'error');
  } finally {
    btn.disabled = false;
    btn.innerHTML = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg> Save`;
  }
}

function showSaveStatus(msg, type) {
  const el = document.getElementById('saveStatus');
  if (!el) return;
  el.textContent = msg;
  el.className   = `save-toast ${type === 'success' ? 'ok' : 'err'}`;
  el.classList.remove('hidden');
  setTimeout(() => el.classList.add('hidden'), 3000);
}

// ── Profile page ──

async function initProfilePage() {
  if (!requireAuth()) return;

  // Load latest profile from server
  try {
    const profile = await api('GET', '/auth/profile');
    // Update local storage with fresh data
    const stored = getUser();
    if (stored) setAuth(getToken(), getRefreshToken(), { ...stored, name: profile.name, email: profile.email });

    const nameEl   = document.getElementById('profileName');
    const emailEl  = document.getElementById('profileEmail');
    const avatarEl = document.getElementById('profileAvatar');
    const nameInput = document.getElementById('profileNameInput');

    if (nameEl)   nameEl.textContent  = profile.name  || '–';
    if (emailEl)  emailEl.textContent = profile.email || '–';
    if (avatarEl) avatarEl.textContent = (profile.name || '?').charAt(0).toUpperCase();
    if (nameInput) nameInput.value    = profile.name  || '';
  } catch (_) {
    // Fallback to cached
    const user = getUser();
    if (!user) return;
    const nameEl   = document.getElementById('profileName');
    const emailEl  = document.getElementById('profileEmail');
    const avatarEl = document.getElementById('profileAvatar');
    const nameInput = document.getElementById('profileNameInput');
    if (nameEl)   nameEl.textContent  = user.name  || '–';
    if (emailEl)  emailEl.textContent = user.email || '–';
    if (avatarEl) avatarEl.textContent = (user.name || '?').charAt(0).toUpperCase();
    if (nameInput) nameInput.value    = user.name || '';
  }

  // Load stats
  try {
    const [streak, analytics] = await Promise.all([
      api('GET', '/diary/analytics/streak'),
      api('GET', '/diary/analytics/mood'),
    ]);
    const streakEl = document.getElementById('statStreak');
    const totalEl  = document.getElementById('statTotal');
    const moodEl   = document.getElementById('statTopMood');
    if (streakEl) streakEl.textContent = `🔥 ${streak.currentStreak} day streak`;
    if (totalEl)  totalEl.textContent  = `📖 ${streak.totalDaysJournaled} days journaled`;
    if (moodEl) {
      const top = analytics.moodCounts?.[0];
      moodEl.textContent = top ? `✨ Top mood: ${top.mood} (${top.count}×)` : '✨ No mood data yet';
    }
  } catch (_) { /* non-critical */ }
}

async function handleUpdateProfile(e) {
  e.preventDefault();
  const btn       = document.getElementById('updateProfileBtn');
  const errEl     = document.getElementById('profileUpdateError');
  const successEl = document.getElementById('profileUpdateSuccess');
  errEl.classList.add('hidden');
  successEl.classList.add('hidden');

  const name = document.getElementById('profileNameInput').value.trim();
  if (!name) { errEl.textContent = 'Name cannot be blank.'; errEl.classList.remove('hidden'); return; }

  btn.disabled = true;
  btn.querySelector('.btn-text').textContent = 'Saving…';
  try {
    const updated = await api('PUT', '/auth/profile', { name });
    // Update cached user
    const stored = getUser();
    if (stored) setAuth(getToken(), getRefreshToken(), { ...stored, name: updated.name });

    document.getElementById('profileName').textContent   = updated.name;
    document.getElementById('profileAvatar').textContent = (updated.name || '?').charAt(0).toUpperCase();
    successEl.textContent = 'Profile updated!';
    successEl.classList.remove('hidden');
  } catch (err) {
    errEl.textContent = err.message || 'Failed to update profile.';
    errEl.classList.remove('hidden');
  } finally {
    btn.disabled = false;
    btn.querySelector('.btn-text').textContent = 'Save Changes';
  }
}

async function handleChangePassword(e) {
  e.preventDefault();

  const btn       = document.getElementById('changePasswordBtn');
  const errEl     = document.getElementById('passwordError');
  const successEl = document.getElementById('passwordSuccess');

  errEl.classList.add('hidden');
  successEl.classList.add('hidden');

  const currentPassword = document.getElementById('currentPassword').value;
  const newPassword     = document.getElementById('newPassword').value;
  const confirmPassword = document.getElementById('confirmPassword').value;

  if (newPassword !== confirmPassword) {
    errEl.textContent = 'New passwords do not match.';
    errEl.classList.remove('hidden');
    return;
  }

  btn.disabled = true;
  btn.querySelector('.btn-text').textContent = 'Updating…';

  try {
    await api('POST', '/auth/change-password', { currentPassword, newPassword });
    successEl.textContent = 'Password updated successfully!';
    successEl.classList.remove('hidden');
    document.getElementById('changePasswordForm').reset();
  } catch (err) {
    errEl.textContent = err.message || 'Failed to change password. Please try again.';
    errEl.classList.remove('hidden');
  } finally {
    btn.disabled = false;
    btn.querySelector('.btn-text').textContent = 'Update Password';
  }
}

// ── Auto-redirect logged-in users from auth pages ──
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
} else if (path === '/profile') {
  document.addEventListener('DOMContentLoaded', initProfilePage);
}