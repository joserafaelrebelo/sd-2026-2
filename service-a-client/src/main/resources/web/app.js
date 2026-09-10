// Estado Global da Aplicação
let groups = [];
let currentGroupId = null;
let currentGroup = null;

// Inicialização
document.addEventListener('DOMContentLoaded', () => {
  setupEventListeners();
  loadGroups();
});

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
    document.getElementById('modalGroup').showModal();
  });

  document.getElementById('btnOpenAddExpense').addEventListener('click', () => {
    if (!currentGroup) return;
    populateExpenseModal();
    document.getElementById('modalExpense').showModal();
  });

  document.getElementById('btnOpenSettle').addEventListener('click', () => {
    if (!currentGroup) return;
    populateSettleModal();
    document.getElementById('modalSettle').showModal();
  });

  // Submissões de Formulários
  document.getElementById('formGroup').addEventListener('submit', handleCreateGroup);
  document.getElementById('formExpense').addEventListener('submit', handleAddExpense);
  document.getElementById('formSettle').addEventListener('submit', handleSettleDebt);
}

// =========================================================================
// Carregamento de Dados (API Gateway)
// =========================================================================

async function loadGroups() {
  try {
    const res = await fetch('/api/groups');
    groups = await res.json();

    const select = document.getElementById('groupSelect');
    select.innerHTML = '';

    if (groups.length === 0) {
      select.innerHTML = '<option value="">Nenhum grupo cadastrado</option>';
      document.getElementById('currentGroupName').textContent = 'Nenhum Grupo';
      document.getElementById('currentGroupMembers').textContent = 'Crie um grupo para começar';
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
    const res = await fetch(`/api/group-details?groupId=${encodeURIComponent(groupId)}`);
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
// Renderização
// =========================================================================

function renderGroupHero() {
  if (!currentGroup) return;
  document.getElementById('currentGroupName').textContent = currentGroup.groupName;
  document.getElementById('currentGroupMembers').textContent =
    `Participantes: ${currentGroup.members.join(', ')}`;
}

function renderMetrics(data) {
  const total = data.totalExpensesAmount || 0;
  document.getElementById('metricTotalExpenses').textContent = formatBRL(total);
  document.getElementById('metricDebtsCount').textContent = (data.debts || []).length;
  document.getElementById('metricMembersCount').textContent = currentGroup ? currentGroup.members.length : 0;
}

function renderDebts(debts) {
  const container = document.getElementById('debtsList');
  container.innerHTML = '';

  if (debts.length === 0) {
    container.innerHTML = '<div class="empty-state">🎉 Todas as contas estão quitadas! Nenhuma dívida pendente.</div>';
    return;
  }

  debts.forEach(d => {
    const item = document.createElement('div');
    item.className = 'debt-item';

    item.innerHTML = `
      <div class="debt-flow">
        <span class="user-tag debt-debtor">${escapeHtml(d.fromUser)}</span>
        <span style="color: var(--text-muted); font-size: 0.85rem;">deve pagar</span>
        <span class="user-tag debt-creditor">${escapeHtml(d.toUser)}</span>
      </div>
      <div style="display: flex; align-items: center; gap: 1rem;">
        <span class="debt-amount">${formatBRL(d.amount)}</span>
        <button class="btn btn-sm btn-outline" onclick="openDirectSettle('${escapeHtml(d.fromUser)}', '${escapeHtml(d.toUser)}', ${d.amount})">
          Quitar
        </button>
      </div>
    `;
    container.appendChild(item);
  });
}

function renderExpenses(expenses) {
  const container = document.getElementById('expensesList');
  container.innerHTML = '';

  if (expenses.length === 0) {
    container.innerHTML = '<div class="empty-state">Nenhuma despesa registrada neste grupo ainda.</div>';
    return;
  }

  // Ordenar decrescente por mais recente
  expenses.slice().reverse().forEach(exp => {
    const item = document.createElement('div');
    item.className = 'expense-item';

    const dateStr = exp.timestamp ? new Date(exp.timestamp).toLocaleString('pt-BR') : '';

    item.innerHTML = `
      <div class="expense-top">
        <span class="expense-title">${escapeHtml(exp.description || 'Despesa sem descrição')}</span>
        <span class="expense-val">${formatBRL(exp.amount)}</span>
      </div>
      <div class="expense-details">
        <span>Pago por <strong>${escapeHtml(exp.paidBy)}</strong> &bull; Divisão: ${formatBRL(exp.sharePerPerson)} cada</span>
        <span>${dateStr}</span>
      </div>
    `;
    container.appendChild(item);
  });
}

// =========================================================================
// Ações de Modais e Submissões
// =========================================================================

function populateExpenseModal() {
  const paidBySelect = document.getElementById('expPaidBy');
  const partContainer = document.getElementById('expParticipantsList');
  paidBySelect.innerHTML = '';
  partContainer.innerHTML = '';

  currentGroup.members.forEach(m => {
    const opt = document.createElement('option');
    opt.value = m;
    opt.textContent = m;
    paidBySelect.appendChild(opt);

    const label = document.createElement('label');
    label.className = 'checkbox-label';
    label.innerHTML = `
      <input type="checkbox" name="expenseParticipant" value="${escapeHtml(m)}" checked>
      ${escapeHtml(m)}
    `;
    partContainer.appendChild(label);
  });
}

function populateSettleModal(defaultFrom = '', defaultTo = '', defaultAmount = '') {
  const fromSelect = document.getElementById('settleFrom');
  const toSelect = document.getElementById('settleTo');
  fromSelect.innerHTML = '';
  toSelect.innerHTML = '';

  currentGroup.members.forEach(m => {
    const opt1 = document.createElement('option');
    opt1.value = m;
    opt1.textContent = m;
    fromSelect.appendChild(opt1);

    const opt2 = document.createElement('option');
    opt2.value = m;
    opt2.textContent = m;
    toSelect.appendChild(opt2);
  });

  if (defaultFrom) fromSelect.value = defaultFrom;
  if (defaultTo) toSelect.value = defaultTo;
  if (defaultAmount) document.getElementById('settleAmount').value = defaultAmount;
}

window.openDirectSettle = function(fromUser, toUser, amount) {
  populateSettleModal(fromUser, toUser, amount);
  document.getElementById('modalSettle').showModal();
};

async function handleCreateGroup(e) {
  e.preventDefault();
  const id = document.getElementById('grpId').value.trim();
  const name = document.getElementById('grpName').value.trim();
  const rawMembers = document.getElementById('grpMembers').value.trim();

  const members = rawMembers.split(',').map(m => m.trim()).filter(m => m.length > 0);

  if (!id || !name || members.length === 0) {
    alert('Preencha todos os campos do grupo.');
    return;
  }

  try {
    const res = await fetch('/api/groups', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ groupId: id, groupName: name, members })
    });
    const data = await res.json();
    if (data.success) {
      document.getElementById('modalGroup').close();
      currentGroupId = id;
      await loadGroups();
    } else {
      alert('Erro: ' + (data.message || 'Falha ao criar grupo'));
    }
  } catch (err) {
    alert('Erro de conexão com o servidor.');
  }
}

async function handleAddExpense(e) {
  e.preventDefault();
  const desc = document.getElementById('expDesc').value.trim();
  const amount = parseFloat(document.getElementById('expAmount').value);
  const paidBy = document.getElementById('expPaidBy').value;

  const checkedCheckboxes = document.querySelectorAll('input[name="expenseParticipant"]:checked');
  const participants = Array.from(checkedCheckboxes).map(cb => cb.value);

  if (participants.length === 0) {
    alert('Selecione pelo menos um participante para a divisão.');
    return;
  }

  try {
    const res = await fetch('/api/expenses', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        groupId: currentGroupId,
        description: desc,
        amount,
        paidBy,
        participants
      })
    });
    const data = await res.json();
    if (data.success) {
      document.getElementById('modalExpense').close();
      document.getElementById('formExpense').reset();
      loadGroupDetails(currentGroupId);
    } else {
      alert('Erro: ' + (data.message || 'Falha ao adicionar despesa'));
    }
  } catch (err) {
    alert('Erro de conexão ao salvar despesa.');
  }
}

async function handleSettleDebt(e) {
  e.preventDefault();
  const fromUser = document.getElementById('settleFrom').value;
  const toUser = document.getElementById('settleTo').value;
  const amount = parseFloat(document.getElementById('settleAmount').value);

  if (fromUser === toUser) {
    alert('O devedor e credor não podem ser a mesma pessoa.');
    return;
  }

  try {
    const res = await fetch('/api/settle', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        groupId: currentGroupId,
        fromUser,
        toUser,
        amount
      })
    });
    const data = await res.json();
    if (data.success) {
      document.getElementById('modalSettle').close();
      document.getElementById('formSettle').reset();
      loadGroupDetails(currentGroupId);
    } else {
      alert('Erro: ' + (data.message || 'Falha ao quitar'));
    }
  } catch (err) {
    alert('Erro ao processar quitação.');
  }
}

// =========================================================================
// Utilitários
// =========================================================================

function formatBRL(value) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value || 0);
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

