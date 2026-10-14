// Theme: explicit choice wins over the OS preference; stored per browser.
function toggleTheme() {
    const root = document.documentElement;
    const dark = root.dataset.theme ? root.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
    root.dataset.theme = dark ? 'light' : 'dark';
    try { localStorage.setItem('theme', root.dataset.theme); } catch (e) { }
    syncCodeTheme();
}

function syncCodeTheme() {
    const t = document.documentElement.dataset.theme;
    const light = document.getElementById('hl-light'), dark = document.getElementById('hl-dark');
    if (!t || !light || !dark) return;
    light.media = t === 'light' ? 'all' : 'not all';
    dark.media = t === 'dark' ? 'all' : 'not all';
}

// Enhance a freshly loaded or HTMX-swapped subtree: diagrams, highlighting, copy buttons, math.
function enhance(root) {
    root.querySelectorAll('pre > code.language-mermaid').forEach(code => {
        const div = document.createElement('div');
        div.className = 'mermaid';
        div.textContent = code.textContent;
        code.parentElement.replaceWith(div);
    });
    if (window.mermaid) {
        const dark = (document.documentElement.dataset.theme || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')) === 'dark';
        mermaid.initialize({ startOnLoad: false, theme: dark ? 'dark' : 'neutral', securityLevel: 'strict' });
        mermaid.run({ nodes: root.querySelectorAll('.mermaid:not([data-processed])') }).catch(() => { });
    }
    root.querySelectorAll('pre > code').forEach(code => {
        if (window.hljs && !code.classList.contains('hljs')) hljs.highlightElement(code);
        const pre = code.parentElement;
        if (pre.querySelector('.copy-btn')) return;
        const btn = document.createElement('button');
        btn.className = 'copy-btn';
        btn.type = 'button';
        btn.textContent = 'Copy';
        btn.onclick = () => navigator.clipboard.writeText(code.textContent).then(() => {
            btn.textContent = 'Copied';
            setTimeout(() => btn.textContent = 'Copy', 1200);
        });
        pre.appendChild(btn);
    });
    if (window.renderMathInElement) {
        renderMathInElement(root, {
            delimiters: [{ left: '\\[', right: '\\]', display: true }, { left: '\\(', right: '\\)', display: false }],
            throwOnError: false
        });
    }
}

function setupTabs() {
    document.querySelectorAll('.tab').forEach(tab => tab.addEventListener('click', () => {
        document.querySelectorAll('.tab').forEach(t => { t.classList.remove('active'); t.setAttribute('aria-selected', 'false'); });
        document.querySelectorAll('.tab-panel').forEach(p => p.hidden = true);
        tab.classList.add('active');
        tab.setAttribute('aria-selected', 'true');
        document.getElementById('tab-' + tab.dataset.tab).hidden = false;
    }));
}

function setupFilter() {
    const input = document.querySelector('input[data-filter]');
    if (!input) return;
    input.addEventListener('input', () => {
        const q = input.value.toLowerCase();
        document.querySelectorAll(input.dataset.filter).forEach(el => el.hidden = !el.textContent.toLowerCase().includes(q));
    });
}

window.addEventListener('load', () => {
    syncCodeTheme();
    enhance(document.body);
    setupTabs();
    setupFilter();
    document.body.addEventListener('htmx:afterSwap', e => enhance(e.detail.target));
});
