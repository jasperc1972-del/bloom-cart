// 朵拉花店前端共用工具

const PLACEHOLDER_IMG = '/images/products/placeholder_flower.webp';

/** 呼叫後端 API；回傳 data，失敗時丟出帶訊息與狀態碼的 Error */
async function api(path, { method = 'GET', body } = {}) {
  const res = await fetch(path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
    credentials: 'same-origin',
  });
  let json = null;
  try { json = await res.json(); } catch (_) { /* 非 JSON 回應 */ }
  if (!res.ok || !json || !json.success) {
    const err = new Error((json && json.message) || `請求失敗（${res.status}）`);
    err.status = res.status;
    throw err;
  }
  return json.data;
}

/** HTML 跳脫，避免使用者輸入（卡片附言等）造成 XSS */
function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/** 金額顯示：NT$1,680 */
function money(n) {
  const v = Number(n || 0);
  return 'NT$' + v.toLocaleString('zh-TW', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}

/** 圖片容錯：載入失敗時改用預設花卉圖（後端已先判斷一次，這裡是第二道防線） */
function imgTag(src, alt, cls = '') {
  return `<img class="${cls}" src="${esc(src || PLACEHOLDER_IMG)}" alt="${esc(alt)}" loading="lazy"
           onerror="this.onerror=null;this.src='${PLACEHOLDER_IMG}'">`;
}

function toast(msg, isError = false) {
  let el = document.getElementById('toast');
  if (!el) {
    el = document.createElement('div');
    el.id = 'toast';
    document.body.appendChild(el);
  }
  el.textContent = msg;
  el.className = 'show' + (isError ? ' error' : '');
  clearTimeout(el._t);
  el._t = setTimeout(() => { el.className = isError ? 'error' : ''; }, 2600);
}

function qs(name) {
  return new URLSearchParams(location.search).get(name);
}

let currentUser = null;

/** 取得登入者（快取） */
async function getMe() {
  if (currentUser !== null) return currentUser || null;
  try {
    currentUser = (await api('/api/auth/me')) || false;
  } catch (_) {
    currentUser = false;
  }
  return currentUser || null;
}

/** 更新頁首購物車數量 */
async function refreshCartBadge(cart) {
  const el = document.getElementById('cart-count');
  if (!el) return;
  try {
    const c = cart || await api('/api/cart');
    el.textContent = c.totalQuantity;
    el.style.display = c.totalQuantity > 0 ? 'inline-block' : 'none';
  } catch (_) { /* 忽略 */ }
}

/** 頁首與頁尾 */
async function renderLayout(active) {
  const me = await getMe();
  const link = (href, text, key) => `<a href="${href}" class="${active === key ? 'active' : ''}">${text}</a>`;
  const header = document.createElement('header');
  header.className = 'site-header';
  header.innerHTML = `
    <div class="container">
      <a class="logo" href="/index.html">🌷 朵拉花店<small>Dora Floral</small></a>
      <nav class="nav">
        ${link('/index.html', '花卉目錄', 'home')}
        ${link('/cart.html', '購物車<span id="cart-count" class="cart-badge" style="display:none">0</span>', 'cart')}
        ${me ? link('/orders.html', '我的訂單', 'orders') : ''}
        ${me && me.role === 'ADMIN' ? link('/admin.html', '管理後台', 'admin') : ''}
        ${me
          ? `<span class="hint">Hi, ${esc(me.username)}</span><button class="link-btn" id="logout-btn">登出</button>`
          : link('/login.html', '登入 / 註冊', 'login')}
      </nav>
    </div>`;
  document.body.prepend(header);

  const footer = document.createElement('footer');
  footer.innerHTML = '© 朵拉花店 Dora Floral · 用花傳遞心意';
  document.body.appendChild(footer);

  const logout = document.getElementById('logout-btn');
  if (logout) {
    logout.onclick = async () => {
      await api('/api/auth/logout', { method: 'POST' });
      location.href = '/index.html';
    };
  }
  refreshCartBadge();
  return me;
}

/** 需要登入的頁面：未登入就導去登入頁，登入後再回來 */
function requireLogin(me) {
  if (!me) {
    location.href = '/login.html?redirect=' + encodeURIComponent(location.pathname + location.search);
    return false;
  }
  return true;
}

/** 卡片附言顯示文字 */
function cardText(msg, sig, anon) {
  if (!msg) return '';
  const who = anon ? '（匿名送花）' : (sig ? `— ${sig}` : '');
  return `💌 ${esc(msg)} ${esc(who)}`;
}
