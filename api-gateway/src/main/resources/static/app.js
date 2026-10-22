// =========================================================================
// RATE.IO — Frontend SPA
// =========================================================================

// Estado Global
let currentUser = null; // { userId, name, token }
let groups = [];
let currentGroupId = null;
let currentGroup = null;

// Inicialização
document.addEventListener('DOMContentLoaded', () => {
  setupAuthTabs();
  setupAuthForms();
  setupEventListeners();

  // Verifica se já existe sessão salva
  checkStoredSession();
});

// =========================================================================
// Autenticação & Gerenciamento de Sessão
// =========================================================================

function checkStoredSession() {
  const token = localStorage.getItem('rateio_token');
  const userId = localStorage.getItem('rateio_userId');
  const name = localStorage.getItem('rateio_name');

  if (token && userId) {
    currentUser = { token, userId, name: name || userId };
    showMainApp();
  } else {
    showAuthView();
  }
}

function showAuthView() {
  document.getElementById('authView').style.display = 'flex';
  document.getElementById('mainAppView').style.display = 'none';
}

function showMainApp() {
  document.getElementById('authView').style.display = 'none';
  document.getElementById('mainAppView').style.display = 'flex';

  // Atualiza crachá de usuário
  const label = document.getElementById('userBadgeLabel');
  label.textContent = `👤 ${currentUser.name} (${currentUser.userId})`;

  loadGroups();
}

function logout() {
  localStorage.removeItem('rateio_token');
  localStorage.removeItem('rateio_userId');
  localStorage.removeItem('rateio_name');
  currentUser = null;
  groups = [];
  currentGroupId = null;
  currentGroup = null;
  showAuthView();
}

function setupAuthTabs() {
  const tabLogin = document.getElementById('tabLogin');
  const tabRegister = document.getElementById('tabRegister');
  const formLogin = document.getElementById('formLogin');
  const formRegister = document.getElementById('formRegister');

  tabLogin.addEventListener('click', () => {
    tabLogin.classList.add('active');
    tabRegister.classList.remove('active');
    formLogin.style.display = 'flex';
    formRegister.style.display = 'none';
    clearAuthAlerts();
  });

  tabRegister.addEventListener('click', () => {
    tabRegister.classList.add('active');
    tabLogin.classList.remove('active');
    formLogin.style.display = 'none';
    formRegister.style.display = 'flex';
    clearAuthAlerts();
  });

  document.getElementById('btnLogout').addEventListener('click', logout);
}

function clearAuthAlerts() {
  document.getElementById('loginError').style.display = 'none';
  document.getElementById('regError').style.display = 'none';
  document.getElementById('regSuccess').style.display = 'none';
}

function setupAuthForms() {
  // Submit Login
  document.getElementById('formLogin').addEventListener('submit', async (e) => {
    e.preventDefault();
    clearAuthAlerts();

    const userId = document.getElementById('loginUserId').value.trim();
    const password = document.getElementById('loginPassword').value;
    const errBox = document.getElementById('loginError');

    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId, password })
      });

      const data = await res.json();

      if (res.ok && data.token) {
        localStorage.setItem('rateio_token', data.token);
        localStorage.setItem('rateio_userId', data.userId);
        localStorage.setItem('rateio_name', data.name);
        currentUser = { token: data.token, userId: data.userId, name: data.name };
        document.getElementById('formLogin').reset();
        showMainApp();
      } else {
        errBox.textContent = data.message || 'Falha na autenticação (401)';
        errBox.style.display = 'block';
      }
    } catch (err) {
      errBox.textContent = 'Erro ao conectar ao servidor: ' + err.message;
      errBox.style.display = 'block';
    }
  });

  // Submit Registro
  document.getElementById('formRegister').addEventListener('submit', async (e) => {
    e.preventDefault();
    clearAuthAlerts();

    const userId = document.getElementById('regUserId').value.trim();
    const name = document.getElementById('regName').value.trim();
    const password = document.getElementById('regPassword').value;
    const errBox = document.getElementById('regError');
    const succBox = document.getElementById('regSuccess');

    try {
      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId, name, password })
      });

      const data = await res.json();

      if (res.status === 201 && data.success) {
        succBox.textContent = 'Conta criada com sucesso! Fazendo login automático...';
        succBox.style.display = 'block';

        // Login automático imediato
        setTimeout(async () => {
          const loginRes = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ userId, password })
          });
          const loginData = await loginRes.json();
          if (loginRes.ok && loginData.token) {
            localStorage.setItem('rateio_token', loginData.token);
            localStorage.setItem('rateio_userId', loginData.userId);
            localStorage.setItem('rateio_name', loginData.name);
            currentUser = { token: loginData.token, userId: loginData.userId, name: loginData.name };
            document.getElementById('formRegister').reset();
            showMainApp();
          }
        }, 600);
      } else {
        errBox.textContent = data.message || 'Falha ao cadastrar usuário';
        errBox.style.display = 'block';
      }
    } catch (err) {
      errBox.textContent = 'Erro de rede: ' + err.message;
      errBox.style.display = 'block';
    }
  });
}

// =========================================================================
// Cliente HTTP com Interceptador de Autorização
// =========================================================================

async function fetchWithAuth(url, options = {}) {
  const headers = options.headers || {};
  if (currentUser && currentUser.token) {
    headers['Authorization'] = `Bearer ${currentUser.token}`;
  }
  if (!headers['Content-Type'] && options.body) {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(url, { ...options, headers });

  if (response.status === 401) {
    alert('Sessão expirada ou não autorizada (401). Faça login novamente.');
    logout();
    throw new Error('401 Unauthorized');
  }

  return response;
}

// =========================================================================
// Event Listeners da Aplicação Principal
// =========================================================================

function setupEventListeners() {
  const groupSelect = document.getElementById('groupSelect');
  groupSelect.addEventListener('change', (e) => {
    currentGroupId = e.target.value;
    currentGroup = groups.find(g => g.groupId === currentGroupId);
    loadGroupDetails(currentGroupId);
  });

  // Modais
  document.getElementById('btnOpenNewGroup').addEventListener('click', () => {
    document.getElementById('formGroup').reset();
    if (currentUser && currentUser.userId) {
      document.getElementById('grpMembers').value = currentUser.userId;
    }
    document.getElementById('modalGroup').showModal();
  });

  document.getElementById('btnOpenAddExpense').addEventListener('click', () => {
    if (!currentGroup) {
      alert('Você precisa criar ou participar de um grupo antes de adicionar uma despesa.');
      return;
    }
    populateExpenseModal();
    document.getElementById('modalExpense').showModal();
  });

  document.getElementById('btnOpenSettle').addEventListener('click', () => {
    if (!currentGroup) {
      alert('Você precisa criar ou participar de um grupo antes de quitar uma dívida.');
      return;
    }
    populateSettleModal();
    document.getElementById('modalSettle').showModal();
  });

  // Submissões de Formulários
  document.getElementById('formGroup').addEventListener('submit', handleCreateGroup);
  document.getElementById('formExpense').addEventListener('submit', handleAddExpense);
  document.getElementById('formSettle').addEventListener('submit', handleSettleDebt);
}

// =========================================================================
// Carregamento de Dados
// =========================================================================

async function loadGroups() {
  try {
    const res = await fetchWithAuth('/api/groups');
    const allGroups = await res.json();

    // Filtra para exibir apenas os grupos em que o usuário logado participa
    const userLower = (currentUser && currentUser.userId) ? currentUser.userId.toLowerCase().trim() : '';
    groups = (allGroups || []).filter(g =>
      g.members && g.members.some(m => m.toLowerCase().trim() === userLower)
    );

    const select = document.getElementById('groupSelect');
    select.innerHTML = '';

    if (groups.length === 0) {
      currentGroupId = null;
      currentGroup = null;
      select.innerHTML = '<option value="">Nenhum grupo cadastrado</option>';
      document.getElementById('currentGroupName').textContent = 'Nenhum grupo encontrado';
      document.getElementById('currentGroupMembers').textContent = 'Você não participa de nenhum grupo ainda. Clique em "+ Novo Grupo" para começar!';
      renderEmptyState();
      return;
    }

    groups.forEach(g => {
      const opt = document.createElement('option');
      opt.value = g.groupId;
      opt.textContent = `${g.groupName} (${g.members.length} membros)`;
      select.appendChild(opt);
    });

    if (!currentGroupId || !groups.some(g => g.groupId === currentGroupId)) {
      currentGroupId = groups[0].groupId;
    }

    select.value = currentGroupId;
    currentGroup = groups.find(g => g.groupId === currentGroupId);
    loadGroupDetails(currentGroupId);
  } catch (err) {
    console.error('Erro ao carregar grupos:', err);
  }
}

async function loadGroupDetails(groupId) {
  if (!groupId) return;

  try {
    const res = await fetchWithAuth(`/api/group-details?groupId=${encodeURIComponent(groupId)}`);
    const data = await res.json();

    renderGroupHero();
    renderMetrics(data);
    renderDebts(data.debts || []);
    renderExpenses(data.expenses || []);
  } catch (err) {
    console.error('Erro ao carregar detalhes do grupo:', err);
  }
}

// =========================================================================
// Renderização do Dashboard
// =========================================================================

function renderGroupHero() {
  if (!currentGroup) return;
  document.getElementById('currentGroupName').textContent = currentGroup.groupName;
  document.getElementById('currentGroupMembers').textContent =
    'Membros: ' + currentGroup.members.join(', ');
}

function renderMetrics(data) {
  const total = data.totalExpensesAmount || 0;
  const debtsCount = (data.debts || []).length;
  const membersCount = currentGroup ? currentGroup.members.length : 0;

  document.getElementById('metricTotalExpenses').textContent =
    'R$ ' + total.toFixed(2).replace('.', ',');
  document.getElementById('metricDebtsCount').textContent = debtsCount;
  document.getElementById('metricMembersCount').textContent = membersCount;
}

function renderDebts(debts) {
  const container = document.getElementById('debtsList');
  container.innerHTML = '';

  if (debts.length === 0) {
    container.innerHTML = '<div class="empty-state">🎉 Nenhuma dívida pendente. O grupo está quitado!</div>';
    return;
  }

  debts.forEach(d => {
    const card = document.createElement('div');
    card.className = 'debt-card';
    card.innerHTML = `
      <div class="debt-actors">
        <span class="debt-from">${escapeHtml(d.fromUser)}</span>
        <span class="debt-arrow">deve pagar</span>
        <span class="debt-to">${escapeHtml(d.toUser)}</span>
      </div>
      <div class="debt-amount">
        R$ ${d.amount.toFixed(2).replace('.', ',')}
      </div>
    `;
    container.appendChild(card);
  });
}

function renderExpenses(expenses) {
  const container = document.getElementById('expensesList');
  container.innerHTML = '';

  if (expenses.length === 0) {
    container.innerHTML = '<div class="empty-state">Nenhuma despesa registrada neste grupo.</div>';
    return;
  }

  expenses.forEach(e => {
    const item = document.createElement('div');
    item.className = 'expense-item';

    const participantsStr = e.participants && e.participants.length > 0
      ? e.participants.join(', ')
      : 'todos';

    item.innerHTML = `
      <div class="expense-left">
        <span class="expense-desc">${escapeHtml(e.description || 'Despesa')}</span>
        <span class="expense-meta">Paga por <strong>${escapeHtml(e.paidBy)}</strong> &bull; Dividida entre: ${escapeHtml(participantsStr)}</span>
      </div>
      <div class="expense-right">
        <div class="expense-amount">R$ ${e.amount.toFixed(2).replace('.', ',')}</div>
        <div class="expense-share">R$ ${e.sharePerPerson.toFixed(2).replace('.', ',')} / pessoa</div>
      </div>
    `;
    container.appendChild(item);
  });
}

function renderEmptyState() {
  document.getElementById('metricTotalExpenses').textContent = 'R$ 0,00';
  document.getElementById('metricDebtsCount').textContent = '0';
  document.getElementById('metricMembersCount').textContent = '0';
  document.getElementById('debtsList').innerHTML = '<div class="empty-state">Crie um grupo para começar.</div>';
  document.getElementById('expensesList').innerHTML = '<div class="empty-state">Nenhum dado disponível.</div>';
}

// =========================================================================
// População dos Modais
// =========================================================================

function populateExpenseModal() {
  if (!currentGroup) return;

  const paidBySelect = document.getElementById('expPaidBy');
  paidBySelect.innerHTML = '';
  currentGroup.members.forEach(m => {
    const opt = document.createElement('option');
    opt.value = m;
    opt.textContent = m;
    if (currentUser && m.toLowerCase() === currentUser.userId.toLowerCase()) {
      opt.selected = true;
    }
    paidBySelect.appendChild(opt);
  });

  const partContainer = document.getElementById('expParticipantsList');
  partContainer.innerHTML = '';
  currentGroup.members.forEach(m => {
    const label = document.createElement('label');
    label.className = 'checkbox-label';
    label.innerHTML = `
      <input type="checkbox" name="expenseParticipant" value="${escapeHtml(m)}" checked>
      ${escapeHtml(m)}
    `;
    partContainer.appendChild(label);
  });
}

function populateSettleModal() {
  if (!currentGroup) return;

  const fromSelect = document.getElementById('settleFrom');
  const toSelect = document.getElementById('settleTo');

  fromSelect.innerHTML = '';
  toSelect.innerHTML = '';

  currentGroup.members.forEach((m, idx) => {
    const optFrom = document.createElement('option');
    optFrom.value = m;
    optFrom.textContent = m;
    fromSelect.appendChild(optFrom);

    const optTo = document.createElement('option');
    optTo.value = m;
    optTo.textContent = m;
    toSelect.appendChild(optTo);
  });

  if (currentGroup.members.length > 1) {
    toSelect.selectedIndex = 1;
  }
}

// =========================================================================
// Handlers de Envio dos Formulários (POST)
// =========================================================================

async function handleCreateGroup(e) {
  e.preventDefault();
  const groupId = document.getElementById('grpId').value.trim().toLowerCase();
  const groupName = document.getElementById('grpName').value.trim();
  const membersRaw = document.getElementById('grpMembers').value;

  const members = membersRaw
    .split(',')
    .map(s => s.trim().toLowerCase())
    .filter(s => s.length > 0);

  // Garante que o criador logado seja sempre incluído como membro
  if (currentUser && currentUser.userId) {
    const userLower = currentUser.userId.toLowerCase().trim();
    if (!members.includes(userLower)) {
      members.unshift(userLower);
    }
  }

  if (!groupId || !groupName || members.length === 0) {
    alert('Preencha todos os campos obrigatórios.');
    return;
  }

  try {
    const res = await fetchWithAuth('/api/groups', {
      method: 'POST',
      body: JSON.stringify({ groupId, groupName, members })
    });

    const data = await res.json();
    if (res.status === 201 && data.success) {
      document.getElementById('modalGroup').close();
      currentGroupId = groupId;
      await loadGroups();
    } else {
      alert(data.message || 'Erro ao criar grupo');
    }
  } catch (err) {
    console.error('Erro na requisição:', err);
  }
}

async function handleAddExpense(e) {
  e.preventDefault();
  const description = document.getElementById('expDesc').value.trim();
  const amount = parseFloat(document.getElementById('expAmount').value);
  const paidBy = document.getElementById('expPaidBy').value;

  const checkboxes = document.querySelectorAll('input[name="expenseParticipant"]:checked');
  const participants = Array.from(checkboxes).map(cb => cb.value);

  if (!description || isNaN(amount) || amount <= 0 || !paidBy || participants.length === 0) {
    alert('Preencha os campos obrigatórios e selecione ao menos 1 participante.');
    return;
  }

  try {
    const res = await fetchWithAuth('/api/expenses', {
      method: 'POST',
      body: JSON.stringify({
        groupId: currentGroupId,
        description,
        amount,
        paidBy,
        participants
      })
    });

    const data = await res.json();
    if (res.status === 201 && data.success) {
      document.getElementById('modalExpense').close();
      document.getElementById('formExpense').reset();
      loadGroupDetails(currentGroupId);
    } else {
      alert(data.message || 'Erro ao registrar despesa');
    }
  } catch (err) {
    console.error('Erro ao adicionar despesa:', err);
  }
}

async function handleSettleDebt(e) {
  e.preventDefault();
  const fromUser = document.getElementById('settleFrom').value;
  const toUser = document.getElementById('settleTo').value;
  const amount = parseFloat(document.getElementById('settleAmount').value);

  if (fromUser === toUser) {
    alert('O devedor e o credor não podem ser a mesma pessoa.');
    return;
  }

  if (isNaN(amount) || amount <= 0) {
    alert('Informe um valor de liquidação válido maior que zero.');
    return;
  }

  try {
    const res = await fetchWithAuth('/api/settle', {
      method: 'POST',
      body: JSON.stringify({
        groupId: currentGroupId,
        fromUser,
        toUser,
        amount
      })
    });

    const data = await res.json();
    if (res.ok && data.success) {
      document.getElementById('modalSettle').close();
      document.getElementById('formSettle').reset();
      loadGroupDetails(currentGroupId);
    } else {
      alert(data.message || 'Erro ao liquidar dívida');
    }
  } catch (err) {
    console.error('Erro ao liquidar dívida:', err);
  }
}

function escapeHtml(str) {
  if (!str) return '';
  return str.toString()
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

