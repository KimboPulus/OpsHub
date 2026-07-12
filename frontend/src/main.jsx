import React, { useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'

const API_BASE = import.meta.env.VITE_API_BASE ?? ''

function apiFetch(path, options = {}) {
  return fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    ...options,
  })
}

const categoryText = {
  MACHINE_FAILURE: 'Awaria maszyny',
  QUALITY_PROBLEM: 'Problem jakościowy',
  MATERIAL_SHORTAGE: 'Brak materiału',
  SAFETY: 'Bezpieczeństwo',
  PLANNING: 'Planowanie',
  OTHER: 'Inne',
}

const severityText = {
  LOW: 'Niski',
  MEDIUM: 'Średni',
  HIGH: 'Wysoki',
  CRITICAL: 'Krytyczny',
}

const statusText = {
  NEW: 'Nowe',
  IN_PROGRESS: 'W toku',
  RESOLVED: 'Rozwiązane',
  VERIFIED: 'Zweryfikowane',
}

const orderStatusText = {
  PLANNED: 'Planowane',
  RELEASED: 'Zwolnione',
  IN_PRODUCTION: 'W produkcji',
  COMPLETED: 'Zamknięte',
  DELAYED: 'Opóźnione',
}

const criticalityText = {
  LOW: 'Niska krytyczność',
  MEDIUM: 'Średnia krytyczność',
  HIGH: 'Wysoka krytyczność',
  BOTTLENECK: 'Wąskie gardło',
}

function App() {
  const [path, setPath] = useState(window.location.pathname)
  const [state, setState] = useState({ issues: [], machines: [], workOrders: [], activities: [] })
  const [user, setUser] = useState(null)
  const [authLoading, setAuthLoading] = useState(true)
  const [loginError, setLoginError] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [eventStatus, setEventStatus] = useState('offline')

  async function loadState() {
    setError('')
    try {
      const response = await apiFetch('/api/state')
      if (response.status === 401) {
        setUser(null)
        return
      }
      if (!response.ok) throw new Error('Nie udało się pobrać danych z API.')
      setState(await response.json())
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function loadSession() {
    setLoginError('')
    try {
      const response = await apiFetch('/api/auth/me')
      if (response.status === 401) {
        setUser(null)
        setLoading(false)
        return
      }
      if (!response.ok) throw new Error('Nie udało się sprawdzić sesji.')
      setUser(await response.json())
      await loadState()
    } catch (err) {
      setLoginError(err.message)
      setLoading(false)
    } finally {
      setAuthLoading(false)
    }
  }

  useEffect(() => {
    loadSession()
    const onPop = () => setPath(window.location.pathname)
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [])

  useEffect(() => {
    if (!user) {
      setEventStatus('offline')
      return undefined
    }

    const source = new EventSource(`${API_BASE}/api/events/operations`, { withCredentials: true })
    source.onopen = () => setEventStatus('live')
    source.onerror = () => setEventStatus('reconnecting')
    source.addEventListener('operations', async event => {
      const payload = JSON.parse(event.data)
      if (payload.type !== 'stream.connected') {
        await loadState()
      }
    })

    return () => source.close()
  }, [user?.username])

  function go(to) {
    window.history.pushState({}, '', to)
    setPath(to)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  async function refreshAndGo(to) {
    await loadState()
    go(to)
  }

  async function login(credentials) {
    setLoginError('')
    const response = await apiFetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams(credentials),
    })

    if (!response.ok) {
      setLoginError('Nieprawidłowy login albo hasło.')
      return
    }

    setLoading(true)
    await loadSession()
  }

  async function logout() {
    await apiFetch('/api/auth/logout', { method: 'POST' })
    setUser(null)
    setState({ issues: [], machines: [], workOrders: [], activities: [] })
    go('/')
  }

  if (authLoading) {
    return <main className="content"><LoadingPage /></main>
  }

  if (!user) {
    return <LoginPage onLogin={login} error={loginError} />
  }

  const issueMatch = path.match(/^\/issues\/(\d+)$/)
  const qrMachineMatch = path.match(/^\/issues\/create\/machine\/([^/]+)$/)

  let page = <Home state={state} go={go} />
  if (path === '/issues') page = <Issues state={state} go={go} />
  if (path === '/issues/create') page = <CreateIssue state={state} go={go} onSaved={refreshAndGo} />
  if (qrMachineMatch) page = <CreateIssue state={state} go={go} onSaved={refreshAndGo} machineCode={decodeURIComponent(qrMachineMatch[1])} />
  if (issueMatch) page = <IssueDetails id={Number(issueMatch[1])} go={go} onChanged={loadState} user={user} />
  if (path === '/machines/qr') page = <MachineQr machines={state.machines} go={go} />
  if (path === '/reports') page = <Reports state={state} />
  if (path === '/audit') page = <AuditTrail go={go} />
  if (path === '/iot') page = <FactoryIoT />
  if (path === '/platform') page = <PowerPlatform state={state} go={go} />

  return (
    <>
      <Shell go={go} path={path} user={user} onLogout={logout} eventStatus={eventStatus} />
      <main className="content">
        {error && <div className="ops-page"><div className="alert danger">{error} Sprawdź, czy backend działa na porcie 8080.</div></div>}
        {loading ? <LoadingPage /> : page}
      </main>
    </>
  )
}

function Shell({ go, path, user, onLogout, eventStatus }) {
  const items = [
    ['/', 'Start'],
    ['/issues', 'Produkcja'],
    ['/reports', 'Raporty'],
    ['/audit', 'Audit'],
    ['/iot', 'IoT'],
    ['/machines/qr', 'QR'],
    ['/platform', 'Power/BI'],
  ]

  return (
    <header className="topbar">
      <button className="brand" onClick={() => go('/')}>
        <span>FOH</span>
        <strong>Fortaco Ops Hub</strong>
      </button>
      <nav>
        {items.map(([to, label]) => (
          <button key={to} className={path === to ? 'active' : ''} onClick={() => go(to)}>{label}</button>
        ))}
      </nav>
      <div className="session-tools">
        <span className="session-pill">{user.displayName}</span>
        <span className="session-pill muted-pill">{roleLabel(user)}</span>
        <span className={`session-pill live-pill ${eventStatus}`}>{liveLabel(eventStatus)}</span>
        <button className="btn btn-outline-dark" onClick={onLogout}>Wyloguj</button>
      </div>
      <button className="btn btn-dark" onClick={() => go('/issues/create')}>Nowe zgłoszenie</button>
    </header>
  )
}

function LoginPage({ onLogin, error }) {
  const [username, setUsername] = useState('lider')
  const [password, setPassword] = useState('opshub')
  const [busy, setBusy] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    await onLogin({ username, password })
    setBusy(false)
  }

  return (
    <main className="login-page">
      <section className="login-card">
        <div className="brand login-brand"><span>FOH</span><strong>Fortaco Ops Hub</strong></div>
        <h1>Logowanie do panelu</h1>
        <p>Demo ma proste konta dla operatora i lidera zmiany, tak jak w małym wewnętrznym narzędziu produkcyjnym.</p>
        {error && <div className="alert danger">{error}</div>}
        <form onSubmit={submit}>
          <Field label="Login"><input value={username} onChange={event => setUsername(event.target.value)} autoComplete="username" /></Field>
          <Field label="Hasło"><input type="password" value={password} onChange={event => setPassword(event.target.value)} autoComplete="current-password" /></Field>
          <button className="btn btn-dark btn-lg" disabled={busy}>{busy ? 'Logowanie...' : 'Zaloguj'}</button>
        </form>
        <div className="login-hint">
          <strong>Konta demo</strong>
          <span>lider / opshub</span>
          <span>operator / opshub</span>
        </div>
      </section>
    </main>
  )
}

function Home({ state, go }) {
  const issues = visibleIssues(state.issues)
  const openIssues = issues.filter(isOpen).length
  const criticalIssues = issues.filter(issue => issue.severity === 'CRITICAL').length
  const totalDowntime = sum(issues, issue => issue.downtimeMinutes)
  const lineCount = new Set(state.machines.map(machine => machine.productionLine?.id)).size
  const bottlenecks = state.machines.filter(machine => machine.criticality === 'BOTTLENECK').length
  const qualitySafety = issues.filter(issue => issue.category === 'QUALITY_PROBLEM' || issue.category === 'SAFETY').length
  const activeOrders = state.workOrders.filter(order => order.status !== 'COMPLETED').length
  const priorities = [...issues]
    .filter(isOpen)
    .sort((a, b) => severityRank(b.severity) - severityRank(a.severity) || new Date(b.createdAt) - new Date(a.createdAt))
    .slice(0, 5)

  return (
    <div className="ops-page">
      <section className="ops-hero">
        <div>
          <div className="eyebrow">Fortaco Ops Hub</div>
          <h1>Centrum operacji produkcyjnych</h1>
          <p>To jest miejsce, w którym produkcja, utrzymanie ruchu, jakość i IT widzą ten sam obraz sytuacji: zgłoszenia z hali, przestoje, zlecenia z ERP, raporty KPI i historię zmian.</p>
          <div className="hero-actions">
            <button className="btn btn-light" onClick={() => go('/issues')}>Otwórz panel produkcji</button>
            <button className="btn btn-outline-light" onClick={() => go('/reports')}>Raporty KPI</button>
            <button className="btn btn-outline-light" onClick={() => go('/platform')}>IT i integracje</button>
          </div>
        </div>
        <div className="ops-command-strip">
          <div><span>{openIssues}</span><small>otwarte tematy</small></div>
          <div><span>{criticalIssues}</span><small>krytyczne ryzyka</small></div>
          <div><span>{totalDowntime} min</span><small>przestoju</small></div>
        </div>
      </section>

      <MetricGrid>
        <Metric label="Linie produkcyjne" value={lineCount} note="Spawalnia, montaż i najważniejsze zasoby" />
        <Metric label="Maszyny monitorowane" value={state.machines.length} note={`${bottlenecks} wąskie gardło w ewidencji`} />
        <Metric danger label="Jakość i bezpieczeństwo" value={qualitySafety} note="Tematy, których lepiej nie zgubić w mailach" />
        <Metric label="Zlecenia aktywne" value={activeOrders} note="Planowane, zwolnione albo w produkcji" />
      </MetricGrid>

      <div className="ops-grid executive-grid mt">
        <Panel title="Priorytety zmiany" subtitle="Najważniejsze tematy posortowane po priorytecie i czasie utworzenia." action={<button className="btn btn-sm btn-outline-dark" onClick={() => go('/issues')}>Wszystkie</button>}>
          {priorities.length === 0 && <div className="empty-state">Brak otwartych zgłoszeń. Produkcja wygląda spokojnie.</div>}
          {priorities.map(issue => (
            <button className="priority-row" key={issue.id} onClick={() => go(`/issues/${issue.id}`)}>
              <div>
                <strong>{issue.title}</strong>
                <span>{issue.machine?.code ?? 'Brak maszyny'} · {issue.workOrder?.sapOrderNumber ?? 'Brak zlecenia'}</span>
              </div>
              <div className="priority-meta">
                <Badge tone={severityTone(issue.severity)}>{severityText[issue.severity]}</Badge>
                <small>{issue.downtimeMinutes} min</small>
              </div>
            </button>
          ))}
        </Panel>

        <Panel title="Moduły systemu" subtitle="Celowo wygląda jak mały system dla zakładu produkcyjnego.">
          <div className="module-list">
            <ListItem title="Produkcja" text="linie, maszyny, zlecenia, przestoje" />
            <ListItem title="Jakość" text="niezgodności, eskalacje, historia awarii" />
            <ListItem title="UR" text="tickety, komentarze i szybka obsługa awarii" />
            <ListItem title="IT" text="role, API, ERP, logi audytu i wdrożenie" />
          </div>
        </Panel>
      </div>

      <div className="ops-grid readiness-grid mt">
        <Feature label="Operator" title="Zgłoszenia przez QR" text="Wejście z kodu maszyny, zdjęcia problemu i duże pola do pracy na tablecie przy stanowisku." button="Otwórz stację QR" onClick={() => go('/machines/qr')} />
        <Feature label="Raportowanie" title="Power BI-style KPI" text="Widok dla lidera pokazuje OEE proxy, MTTR, przestoje, jakość oraz eksport CSV/PDF." button="Zobacz raporty" onClick={() => go('/reports')} />
        <Feature label="Power Platform" title="BI, flowy i Dataverse" text="Widok pokazuje Canvas App, Power Automate, tabele Dataverse i SQL views gotowe pod Power BI." button="Otwórz Power/BI" onClick={() => go('/platform')} />
      </div>
    </div>
  )
}

function Issues({ state, go }) {
  const [filter, setFilter] = useState('ALL')
  const [search, setSearch] = useState('')
  const issues = visibleIssues(state.issues)
  const filtered = issues
    .filter(issue => {
      if (filter === 'OPEN') return isOpen(issue)
      if (filter === 'CRITICAL') return issue.severity === 'CRITICAL'
      if (filter === 'IN_PROGRESS') return issue.status === 'IN_PROGRESS'
      return true
    })
    .filter(issue => {
      const term = search.trim().toLowerCase()
      if (!term) return true
      return [issue.title, issue.description, issue.machine?.code, issue.machine?.name, issue.workOrder?.sapOrderNumber, issue.workOrder?.materialCode]
        .filter(Boolean)
        .some(value => value.toLowerCase().includes(term))
    })

  const downtimeByMachine = groupDowntimeByMachine(issues)
  const worstMachine = downtimeByMachine[0] ? `${downtimeByMachine[0].code} - ${downtimeByMachine[0].downtime} min przestoju` : 'Brak danych o maszynach'
  const worstCategory = worstCategoryLabel(issues)

  return (
    <div className="ops-page">
      <PageHeader eyebrow="Bieżąca sytuacja na produkcji" title="Panel produkcji" text="Zgłoszenia z produkcji, przestoje maszyn, ryzyka dla zleceń i szybki podgląd sytuacji operacyjnej.">
        <span className="muted small">Ostatnie odświeżenie: {new Date().toLocaleTimeString('pl-PL', { hour: '2-digit', minute: '2-digit' })}</span>
        <button className="btn btn-dark" onClick={() => go('/issues/create')}>Nowe zgłoszenie</button>
        <ExportButton href="/exports/production-issues.csv">Eksport CSV</ExportButton>
        <ExportButton href="/exports/weekly-summary.pdf">Raport PDF</ExportButton>
      </PageHeader>

      <MetricGrid>
        <Metric label="Wszystkie zgłoszenia" value={issues.length} note="Cała liczba tematów w systemie" />
        <Metric label="Otwarte tematy" value={issues.filter(isOpen).length} note="Nowe + w toku" />
        <Metric danger label="Krytyczne zgłoszenia" value={issues.filter(issue => issue.severity === 'CRITICAL').length} note="Wymagają reakcji priorytetowej" />
        <Metric label="Łączny przestój" value={`${sum(issues, issue => issue.downtimeMinutes)} min`} note="Suma minut zarejestrowanych problemów" />
      </MetricGrid>

      <div className="ops-grid reporting-grid mt">
        <Panel title="Przestój według maszyn" subtitle="Prosty ranking, który od razu pokazuje gdzie produkcja traci najwięcej czasu.">
          {downtimeByMachine.map(row => <BarRow key={row.code} label={row.code} sublabel={row.name} value={`${row.downtime} min / ${row.count} zgł.`} width={row.width} />)}
        </Panel>
        <Panel title="Ryzyka operacyjne">
          <Risk title="Najgorsza maszyna" value={worstMachine} />
          <Risk title="Najbardziej ryzykowna kategoria" value={worstCategory} />
          <Risk title="Zlecenia dotknięte problemami" value={new Set(issues.map(issue => issue.workOrder?.sapOrderNumber).filter(Boolean)).size} />
        </Panel>
      </div>

      <div className="ops-panel mt">
        <div className="table-toolbar">
          <div>
            <h2>Zgłoszenia produkcyjne</h2>
            <p>Lista tematów z maszynami, zleceniami z ERP, statusem i przestojem. Rozwiązane zgłoszenia znikają po 7 dniach.</p>
          </div>
          <div className="table-tools">
            <input className="form-control search-input" value={search} onChange={event => setSearch(event.target.value)} placeholder="Szukaj po tytule, maszynie, zleceniu..." />
            <div className="btn-group">
              {[
                ['ALL', 'Wszystkie'],
                ['OPEN', 'Otwarte'],
                ['CRITICAL', 'Krytyczne'],
                ['IN_PROGRESS', 'W toku'],
              ].map(([value, label]) => <button key={value} className={filter === value ? 'btn btn-dark btn-sm' : 'btn btn-outline-dark btn-sm'} onClick={() => setFilter(value)}>{label}</button>)}
            </div>
          </div>
        </div>

        <div className="table-wrap">
          <table className="ops-table">
            <thead>
              <tr>
                <th>Zgłoszenie</th><th>Maszyna</th><th>Zlecenie</th><th>Kategoria</th><th>Priorytet</th><th>Status</th><th>Przekazane do</th><th>Przestój</th><th>Akcje</th>
                <th>SLA</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map(issue => (
                <tr key={issue.id}>
                  <td className="issue-title-cell"><strong>{issue.title}</strong><small>{issue.description}</small></td>
                  <td>{issue.machine ? <><strong>{issue.machine.code}</strong><small>{issue.machine.name}</small></> : <span className="muted">Brak maszyny</span>}</td>
                  <td>{issue.workOrder ? <><strong>{issue.workOrder.sapOrderNumber}</strong><small>{issue.workOrder.materialCode}</small></> : <span className="muted">Brak zlecenia</span>}</td>
                  <td>{categoryText[issue.category]}</td>
                  <td><Badge tone={severityTone(issue.severity)}>{severityText[issue.severity]}</Badge></td>
                  <td><Badge tone={statusTone(issue.status)}>{statusText[issue.status]}</Badge></td>
                  <td><strong>{issue.assignedTeam}</strong><small>{issue.notificationChannel}</small></td>
                  <td><strong>{issue.downtimeMinutes} min</strong></td>
                  <td><button className="btn btn-sm btn-outline-dark" onClick={() => go(`/issues/${issue.id}`)}>Szczegóły</button></td>
                  <td><SlaBadge issue={issue} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {filtered.length === 0 && <div className="empty-state">Brak zgłoszeń pasujących do aktualnego filtra.</div>}
      </div>
    </div>
  )
}

function CreateIssue({ state, go, onSaved, machineCode }) {
  const prefilledMachine = machineCode ? state.machines.find(machine => machine.code.toLowerCase() === machineCode.toLowerCase()) : null
  const [form, setForm] = useState({
    title: '',
    description: '',
    category: 'MACHINE_FAILURE',
    severity: 'MEDIUM',
    status: 'NEW',
    downtimeMinutes: 0,
    assignedTeam: 'Mechanicy',
    assignedTo: '',
    notificationChannel: 'Teams: Utrzymanie ruchu',
    machineId: prefilledMachine?.id ?? '',
    workOrderId: '',
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [files, setFiles] = useState([])

  async function submit(event) {
    event.preventDefault()
    setSaving(true)
    setError('')

    const payload = {
      ...form,
      downtimeMinutes: Number(form.downtimeMinutes),
      machineId: form.machineId ? Number(form.machineId) : null,
      workOrderId: form.workOrderId ? Number(form.workOrderId) : null,
    }

    try {
      const response = await apiFetch('/api/issues', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      if (!response.ok) throw new Error('Nie udało się zapisać zgłoszenia.')
      const saved = await response.json()

      for (const file of files) {
        const data = new FormData()
        data.append('file', file)
        const upload = await apiFetch(`/api/issues/${saved.id}/attachments`, {
          method: 'POST',
          body: data,
        })
        if (!upload.ok) throw new Error(`Nie udało się dodać zdjęcia: ${file.name}`)
      }

      await onSaved(`/issues/${saved.id}`)
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  function update(name, value) {
    setForm(current => ({ ...current, [name]: value }))
  }

  return (
    <div className="ops-page narrow-page">
      <PageHeader eyebrow="Zgłoszenie z hali" title="Nowe zgłoszenie produkcyjne" text="Dodaj problem z produkcji: awaria, brak materiału, problem jakościowy albo inny temat blokujący pracę.">
        <button className="btn btn-outline-dark" onClick={() => go('/issues')}>Wróć do panelu</button>
      </PageHeader>

      {prefilledMachine && <div className="operator-banner"><strong>QR maszyny odczytany:</strong> {prefilledMachine.code} · {prefilledMachine.name}</div>}

      <form className="ops-panel" onSubmit={submit}>
        {error && <div className="alert danger">{error}</div>}
        <div className="factory-mode-strip">
          <div><strong>Tryb operatora</strong><span>Duże pola, szybki wybór z ERP i zdjęcia z tabletu albo telefonu.</span></div>
          <button type="button" className="btn btn-outline-dark btn-lg touch-action" onClick={() => go('/machines/qr')}>Skanuj QR</button>
        </div>

        <div className="form-grid">
          <Field className="wide" label="Tytuł zgłoszenia"><input value={form.title} onChange={event => update('title', event.target.value)} maxLength={120} /></Field>
          <Field label="Przestój w minutach"><input type="number" min="0" max="10080" value={form.downtimeMinutes} onChange={event => update('downtimeMinutes', event.target.value)} /></Field>
          <Field className="full" label="Opis"><textarea required rows="4" value={form.description} onChange={event => update('description', event.target.value)} maxLength={1000} /></Field>
          <SelectField label="Kategoria" value={form.category} onChange={value => update('category', value)} options={categoryText} />
          <SelectField label="Priorytet" value={form.severity} onChange={value => update('severity', value)} options={severityText} />
          <SelectField label="Status" value={form.status} onChange={value => update('status', value)} options={statusText} />
          <Field label="Maszyna"><select value={form.machineId} onChange={event => update('machineId', event.target.value)}><option value="">Brak konkretnej maszyny</option>{state.machines.map(machine => <option key={machine.id} value={machine.id}>{machine.code} - {machine.name}</option>)}</select></Field>
          <Field label="Aktywne zlecenie z ERP"><select value={form.workOrderId} onChange={event => update('workOrderId', event.target.value)}><option value="">Brak zlecenia / temat ogólny</option>{state.workOrders.filter(order => order.status !== 'COMPLETED').map(order => <option key={order.id} value={order.id}>{order.sapOrderNumber} - {order.materialCode} - {shortDate(order.dueDate)}</option>)}</select></Field>
          <Field label="Przekaż do"><select value={form.assignedTeam} onChange={event => update('assignedTeam', event.target.value)}>{['Mechanicy', 'Elektrycy', 'Jakość', 'Planowanie', 'BHP'].map(team => <option key={team}>{team}</option>)}</select></Field>
          <Field label="Osoba lub rola"><input value={form.assignedTo} onChange={event => update('assignedTo', event.target.value)} placeholder="np. lider zmiany, dyżurny UR" /></Field>
          <Field label="Kanał alertu"><select value={form.notificationChannel} onChange={event => update('notificationChannel', event.target.value)}>{['Panel produkcji', 'SMS do UR', 'Teams: Utrzymanie ruchu', 'Push tablet lidera'].map(channel => <option key={channel}>{channel}</option>)}</select></Field>
          <Field className="full" label="Zdjęcie problemu"><input type="file" accept="image/jpeg,image/png,image/webp" multiple onChange={event => setFiles([...event.target.files].slice(0, 5))} /><small>{files.length ? files.map(file => `${file.name} (${Math.ceil(file.size / 1024)} KB)`).join(', ') : 'Opcjonalnie: JPG, PNG albo WebP do 8 MB.'}</small></Field>
        </div>

        <div className="form-footer">
          <button className="btn btn-dark btn-lg touch-action" disabled={saving}>{saving ? 'Zapisywanie...' : 'Zapisz zgłoszenie'}</button>
          <button type="button" className="btn btn-outline-dark btn-lg touch-action" onClick={() => go('/issues')}>Anuluj</button>
        </div>
      </form>
    </div>
  )
}

function IssueDetails({ id, go, onChanged, user }) {
  const [issue, setIssue] = useState(null)
  const [similar, setSimilar] = useState({ total: 0, page: 1, pageSize: 3, items: [] })
  const [showSimilar, setShowSimilar] = useState(false)
  const [comment, setComment] = useState('')
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const closed = issue?.status === 'RESOLVED' || issue?.status === 'VERIFIED'

  async function load() {
    setError('')
    try {
      const response = await apiFetch(`/api/issues/${id}`)
      if (!response.ok) throw new Error('Nie znaleziono zgłoszenia.')
      setIssue(await response.json())
    } catch (err) {
      setError(err.message)
    }
  }

  async function loadSimilar(page = 1) {
    const response = await apiFetch(`/api/issues/${id}/similar?page=${page}&pageSize=3`)
    if (response.ok) setSimilar(await response.json())
  }

  useEffect(() => {
    load()
  }, [id])

  async function setStatus(status) {
    setActionError('')
    const response = await apiFetch(`/api/issues/${id}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status }),
    })
    if (!response.ok) {
      setActionError(await responseError(response, 'Status change failed.'))
      return
    }
    await load()
    await onChanged()
  }

  async function addComment(event) {
    event.preventDefault()
    if (!comment.trim()) return
    setActionError('')
    const response = await apiFetch(`/api/issues/${id}/comments`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: comment }),
    })
    if (!response.ok) {
      setActionError(await responseError(response, 'Comment save failed.'))
      return
    }
    setComment('')
    await load()
    await onChanged()
  }

  async function removeIssue() {
    const confirmed = window.confirm(`Na pewno usunąć zgłoszenie "${issue.title}"? Tej akcji nie da się cofnąć.`)
    if (!confirmed) return

    setActionError('')
    const response = await apiFetch(`/api/issues/${id}`, { method: 'DELETE' })
    if (!response.ok) {
      setActionError(await responseError(response, 'Issue delete failed.'))
      return
    }
    await onChanged()
    go('/issues')
  }

  async function syncDowntime() {
    setActionError('')
    const response = await apiFetch(`/api/issues/${id}/downtime-sync`, { method: 'POST' })
    if (!response.ok) {
      setActionError(await responseError(response, 'Downtime sync failed.'))
      return
    }
    await load()
    await onChanged()
  }

  if (error) return <div className="ops-page"><div className="alert danger">{error}</div><button className="btn btn-outline-dark" onClick={() => go('/issues')}>Wróć do panelu</button></div>
  if (!issue) return <LoadingPage />

  return (
    <div className="ops-page">
      <PageHeader eyebrow="Karta zgłoszenia" title={issue.title} text="Szczegóły zgłoszenia, status, powiązane dane i historia działań.">
        <button className="btn btn-outline-dark" onClick={() => go('/issues')}>Wróć do panelu</button>
      </PageHeader>

      <MetricGrid>
        <Metric label="Status" value={<Badge tone={statusTone(issue.status)}>{statusText[issue.status]}</Badge>} note="Aktualny etap obsługi" />
        <Metric label="Priorytet" value={<Badge tone={severityTone(issue.severity)}>{severityText[issue.severity]}</Badge>} note="Waga operacyjna" />
        <Metric label="Przestój" value={`${issue.downtimeMinutes} min`} note="Czas zatrzymania produkcji" />
        <Metric label="Utworzono" value={dateTime(issue.createdAt)} note="Czas lokalny" />
      </MetricGrid>

      <div className="ops-grid lifecycle-grid mt">
        <Metric label="Response SLA" value={<SlaBadge issue={issue} kind="response" />} note={issue.acknowledgedAt ? `Acknowledged ${dateTime(issue.acknowledgedAt)}` : dueNote(issue.responseDueAt)} />
        <Metric label="Resolution SLA" value={<SlaBadge issue={issue} kind="resolution" />} note={dueNote(issue.resolutionDueAt)} />
        <Metric label="Created by" value={issue.createdBy ?? 'System'} note={issue.updatedAt ? `Updated ${dateTime(issue.updatedAt)}` : 'No updates yet'} />
        <Metric danger={Boolean(issue.escalatedAt)} label="Escalation" value={issue.escalatedAt ? 'Escalated' : 'Clear'} note={issue.escalatedAt ? dateTime(issue.escalatedAt) : 'No SLA escalation'} />
      </div>

      <div className="ops-grid handoff-grid mt">
        <Metric label="Przekazane do" value={issue.assignedTeam} note={issue.assignedTo || 'Osoba nieprzypisana'} />
        <Metric label="Źródło" value={issue.source} note="QR / tablet / ręczne zgłoszenie" />
        <Metric label="Kanał alertu" value={issue.notificationChannel} note="Symulowany kanał aktywnego powiadomienia" />
      </div>

      <div className="ops-grid details-grid mt">
        <div>
          <Panel title="Opis problemu">
            <p>{issue.description}</p>
            <dl className="details-list">
              <dt>Kategoria</dt><dd>{categoryText[issue.category]}</dd>
              <dt>Maszyna</dt><dd>{issue.machine ? `${issue.machine.code} - ${issue.machine.name}` : 'Brak przypisanej maszyny'}</dd>
              <dt>Zlecenie</dt><dd>{issue.workOrder ? `${issue.workOrder.sapOrderNumber} - ${issue.workOrder.materialCode}` : 'Brak powiązanego zlecenia'}</dd>
              <dt>Rozwiązano</dt><dd>{issue.resolvedAt ? dateTime(issue.resolvedAt) : 'Jeszcze nie rozwiązano'}</dd>
              <dt>Created by</dt><dd>{issue.createdBy ?? 'System'}</dd>
              <dt>Acknowledged</dt><dd>{issue.acknowledgedAt ? dateTime(issue.acknowledgedAt) : 'Waiting for response'}</dd>
              <dt>Response due</dt><dd>{issue.responseDueAt ? dateTime(issue.responseDueAt) : 'Not set'}</dd>
              <dt>Resolution due</dt><dd>{issue.resolutionDueAt ? dateTime(issue.resolutionDueAt) : 'Not set'}</dd>
            </dl>
          </Panel>

          <Panel className="mt" title="Zdjęcia z hali" subtitle="Załączniki dodane przez operatora przy maszynie.">
            {issue.attachments?.length ? (
              <div className="attachment-gallery">
                {[...issue.attachments].sort((a, b) => new Date(b.uploadedAt) - new Date(a.uploadedAt)).map(attachment => (
                  <a key={attachment.id} href={`${API_BASE}${attachment.relativePath}`} target="_blank" rel="noreferrer">
                    <img src={`${API_BASE}${attachment.relativePath}`} alt={attachment.fileName} />
                    <span>{attachment.fileName}</span>
                  </a>
                ))}
              </div>
            ) : <div className="empty-state">Brak zdjęć dla tego zgłoszenia.</div>}
          </Panel>

          <Panel className="mt" title="Podobne awarie" subtitle="System szuka dawnych zgłoszeń po maszynie, kategorii albo zleceniu." action={<button className="btn btn-sm btn-outline-dark" onClick={async () => { const next = !showSimilar; setShowSimilar(next); if (next) await loadSimilar(1) }}>{showSimilar ? 'Ukryj' : 'Pokaż'}</button>}>
            {!showSimilar && <div className="empty-state">Kliknij Pokaż, żeby zobaczyć podobne awarie i stare komentarze z historii.</div>}
            {showSimilar && similar.items.length === 0 && <div className="empty-state">Brak podobnych zgłoszeń. Historia zacznie działać jak baza wiedzy, gdy temat wróci.</div>}
            {showSimilar && similar.items.map(item => <SimilarIssue key={item.id} current={issue} issue={item} go={go} />)}
            {showSimilar && similar.total > similar.pageSize && <div className="pager"><span>Strona {similar.page} z {Math.max(1, Math.ceil(similar.total / similar.pageSize))}</span><button className="btn btn-sm btn-outline-dark" disabled={similar.page <= 1} onClick={() => loadSimilar(similar.page - 1)}>Poprzednia</button><button className="btn btn-sm btn-outline-dark" disabled={similar.page >= Math.ceil(similar.total / similar.pageSize)} onClick={() => loadSimilar(similar.page + 1)}>Następna</button></div>}
          </Panel>

          <Panel className="mt" title="Komentarze i historia działań">
            <form className="comment-form" onSubmit={addComment}>
              <Field label="Nowy komentarz"><textarea rows="3" value={comment} onChange={event => setComment(event.target.value)} /></Field>
              <button className="btn btn-dark">Dodaj komentarz</button>
            </form>
            {[...(issue.activities ?? [])].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).map(activity => (
              <div className="activity-item" key={activity.id}>
                <strong>{activity.createdBy}</strong><span> - {activityType(activity.type)}</span>
                <div>{activity.message}</div>
                <small>{dateTime(activity.createdAt)}</small>
              </div>
            ))}
          </Panel>
        </div>

        <Panel title="Akcje operacyjne" subtitle="Szybka zmiana statusu. Każda zmiana zapisuje wpis w historii.">
          {actionError && <div className="alert danger mb">{actionError}</div>}
          <div className="action-stack">
            {can(user, 'canStartWork') && issue.status === 'NEW' && <button className="btn btn-outline-dark" onClick={() => setStatus('IN_PROGRESS')}>Start work</button>}
            {can(user, 'canResolveIssue') && !closed ? (
            <button className="btn btn-outline-dark" onClick={() => setStatus('RESOLVED')}>Oznacz jako rozwiązane</button>
            ) : null}
            {can(user, 'canVerifyIssue') && issue.status === 'RESOLVED' ? (
            <button className="btn btn-outline-dark" onClick={() => setStatus('VERIFIED')}>Oznacz jako zweryfikowane</button>
            ) : null}
            {can(user, 'canResolveIssue') && closed ? <button className="btn btn-outline-dark" onClick={() => setStatus('IN_PROGRESS')}>Reopen issue</button> : null}
            {can(user, 'canSyncDowntime') && <button className="btn btn-outline-dark" onClick={syncDowntime}>Sync downtime to ERP</button>}
            {can(user, 'canDeleteIssue') && closed ? <button className="btn btn-danger" onClick={removeIssue}>Delete issue</button> : <div className="muted small">Delete requires leader role and a closed issue.</div>}
          </div>
        </Panel>
      </div>
    </div>
  )
}

function MachineQr({ machines, go }) {
  return (
    <div className="ops-page">
      <PageHeader eyebrow="Wejście przez QR maszyny" title="Skanowanie QR maszyn" text="W realnej hali kod QR na maszynie prowadziłby bezpośrednio do formularza z już wybraną maszyną.">
        <button className="btn btn-outline-dark" onClick={() => go('/issues/create')}>Ręczne zgłoszenie</button>
      </PageHeader>
      <div className="ops-grid qr-grid">
        {machines.map(machine => (
          <button className="qr-card" key={machine.id} onClick={() => go(`/issues/create/machine/${machine.code}`)}>
            <div className="qr-code"><strong>{machine.code}</strong></div>
            <div><h2>{machine.code}</h2><p>{machine.name}</p><small>{machine.productionLine?.code} · {criticalityText[machine.criticality]}</small></div>
          </button>
        ))}
      </div>
    </div>
  )
}

function Reports({ state }) {
  const issues = state.issues
  const totalDowntime = sum(issues, issue => issue.downtimeMinutes)
  const oee = Math.min(99, Math.max(70, 100 - Math.floor(totalDowntime / 8)))
  const mttr = issues.length ? Math.round(sum(issues, issue => Math.max(issue.downtimeMinutes, 1)) / issues.length) : 0
  const atRisk = state.workOrders
    .filter(order => order.status === 'DELAYED' || new Date(order.dueDate) <= daysFromNow(7) || issues.some(issue => issue.workOrder?.id === order.id && isOpen(issue)))
    .sort((a, b) => new Date(a.dueDate) - new Date(b.dueDate))
    .slice(0, 6)
  const downtimeByMachine = groupDowntimeByMachine(issues)
  const categories = groupByCategory(issues)

  return (
    <div className="ops-page">
      <PageHeader eyebrow="Power BI-style reporting" title="Raporty KPI zakładu" text="Ten ekran ma działać jak szybka rozmowa z danymi: gdzie stoimy, co boli najbardziej i które zlecenia mogą się wysypać.">
        <ExportButton href="/exports/production-issues.csv">Eksport CSV</ExportButton>
        <ExportButton dark href="/exports/weekly-summary.pdf">Raport PDF</ExportButton>
      </PageHeader>
      <MetricGrid>
        <Metric label="OEE proxy" value={`${oee}%`} note="Prosty szacunek na podstawie przestojów" />
        <Metric label="MTTR proxy" value={`${mttr} min`} note="Średni czas ogarnięcia zgłoszenia" />
        <Metric danger label="Koszt przestoju" value={`${(totalDowntime * 420).toLocaleString('pl-PL')} zł`} note="W demo liczymy 420 zł za minutę" />
        <Metric label="Zlecenia zagrożone" value={atRisk.length} note="Opóźnione lub z aktywnymi problemami" />
      </MetricGrid>
      <div className="ops-grid reporting-grid mt">
        <Panel title="Przestój według maszyn" subtitle="Od razu widać, która maszyna zabiera najwięcej czasu.">{downtimeByMachine.map(row => <BarRow key={row.code} label={row.code} sublabel={row.name} value={`${row.downtime} min`} width={row.width} />)}</Panel>
        <Panel title="Jakość i kategorie" subtitle="Udział problemów według klasyfikacji operacyjnej.">{categories.map(row => <SplitRow key={row.category} label={categoryText[row.category]} value={`${row.count} zgł. · ${row.downtime} min`} />)}</Panel>
      </div>
      <div className="ops-grid reporting-grid mt">
        <Panel title="Ryzyka dla zleceń ERP" subtitle="Zlecenia z bliskim terminem, opóźnieniem albo aktywnym zgłoszeniem.">{atRisk.map(order => <SplitRow key={order.id} label={`${order.sapOrderNumber} · ${order.materialCode}`} value={`${shortDate(order.dueDate)} · ${orderStatusText[order.status]}`} />)}</Panel>
        <Panel title="Pipeline raportowania" subtitle="Tak można to później spiąć z Power BI, hurtownią danych i cyklicznym raportem.">
          <div className="timeline-list">
            <ListItem title="1. Dane operacyjne" text="H2 demo, docelowo SQL Server lub PostgreSQL" />
            <ListItem title="2. API i eksporty" text="CSV, PDF, endpointy ERP, zdarzenia zgłoszeń" />
            <ListItem title="3. Model BI" text="miary OEE, MTTR, scrap, backlog UR, SLA reakcji" />
            <ListItem title="4. Dashboard" text="Power BI / Fabric / raporty dla liderów zmian" />
          </div>
        </Panel>
      </div>
    </div>
  )
}

function AuditTrail({ go }) {
  const [filters, setFilters] = useState({ type: '', severity: '', actor: '', machine: '', text: '' })
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const query = useMemo(() => {
    const params = new URLSearchParams()
    Object.entries(filters).forEach(([key, value]) => {
      if (value.trim()) params.set(key, value.trim())
    })
    params.set('limit', '200')
    return params.toString()
  }, [filters])

  async function loadAudit() {
    setError('')
    setLoading(true)
    try {
      const response = await apiFetch(`/api/audit/events?${query}`)
      if (!response.ok) throw new Error('Audit search failed.')
      const data = await response.json()
      setRows(data.items ?? [])
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadAudit()
  }, [query])

  function update(name, value) {
    setFilters(current => ({ ...current, [name]: value }))
  }

  return (
    <div className="ops-page">
      <PageHeader eyebrow="Operations audit" title="Audit timeline" text="Searchable workflow history for status changes, comments, evidence and integration actions.">
        <button className="btn btn-outline-dark" onClick={loadAudit}>Refresh</button>
        <ExportButton href={`/exports/audit-events.csv?${query}`}>Audit CSV</ExportButton>
      </PageHeader>

      {error && <div className="alert danger mb">{error}</div>}

      <div className="ops-panel">
        <div className="form-grid audit-filters">
          <SelectField label="Type" value={filters.type} onChange={value => update('type', value)} options={{ '': 'All', SYSTEM: 'System', COMMENT: 'Comment', STATUS_CHANGE: 'Status change' }} />
          <SelectField label="Severity" value={filters.severity} onChange={value => update('severity', value)} options={{ '': 'All', LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', CRITICAL: 'Critical' }} />
          <Field label="Actor"><input value={filters.actor} onChange={event => update('actor', event.target.value)} placeholder="Leader, Operator, SLA engine..." /></Field>
          <Field label="Machine"><input value={filters.machine} onChange={event => update('machine', event.target.value)} placeholder="LASER-01" /></Field>
          <Field className="wide" label="Search text"><input value={filters.text} onChange={event => update('text', event.target.value)} placeholder="message, issue title, description..." /></Field>
        </div>
      </div>

      <div className="ops-panel mt">
        <div className="section-heading">
          <div>
            <h2>Audit events</h2>
            <p>{loading ? 'Loading audit events...' : `${rows.length} events found`}</p>
          </div>
        </div>
        <div className="table-wrap">
          <table className="ops-table">
            <thead>
              <tr>
                <th>Time</th><th>Issue</th><th>Type</th><th>Actor</th><th>Machine</th><th>Severity</th><th>Message</th><th>Action</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(row => (
                <tr key={row.id}>
                  <td>{dateTime(row.createdAt)}</td>
                  <td><strong>{row.issueTitle}</strong><small>#{row.issueId} / {statusText[row.issueStatus] ?? row.issueStatus}</small></td>
                  <td><Badge tone={auditTone(row.type)}>{auditTypeText(row.type)}</Badge></td>
                  <td>{row.actor}</td>
                  <td>{row.machineCode || <span className="muted">No machine</span>}</td>
                  <td><Badge tone={severityTone(row.issueSeverity)}>{severityText[row.issueSeverity] ?? row.issueSeverity}</Badge></td>
                  <td>{row.message}</td>
                  <td><button className="btn btn-sm btn-outline-dark" onClick={() => go(`/issues/${row.issueId}`)}>Open</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!loading && rows.length === 0 && <div className="empty-state">No audit events match current filters.</div>}
      </div>
    </div>
  )
}

function FactoryIoT() {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function loadTelemetry() {
    setError('')
    try {
      const response = await apiFetch('/api/iot/dashboard/summary')
      if (!response.ok) throw new Error('Moduł IoT nie odpowiada. Sprawdź, czy backend Spring Boot działa.')
      setData(await response.json())
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function simulateTick() {
    setBusy(true)
    setError('')
    try {
      const response = await apiFetch('/api/iot/simulator/tick?count=5', { method: 'POST' })
      if (!response.ok) throw new Error('Nie udało się wygenerować próbki telemetryki.')
      await loadTelemetry()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  useEffect(() => {
    loadTelemetry()
  }, [])

  const oee = data?.oee ?? {}
  const latest = data?.latestTelemetry ?? []
  const anomalies = data?.recentAnomalies ?? []
  const alerts = data?.openAlerts ?? []

  return (
    <div className="ops-page">
      <PageHeader eyebrow="Pipeline IoT fabryki" title="Dashboard telemetryki maszyn" text="Moduł Spring symuluje dane z maszyn, wykrywa anomalie, zapisuje alerty i liczy dzienne KPI bez osobnego serwisu.">
        <button className="btn btn-outline-dark" onClick={loadTelemetry}>Odśwież</button>
        <button className="btn btn-dark" onClick={simulateTick} disabled={busy}>{busy ? 'Generuję...' : 'Wygeneruj telemetrykę'}</button>
        <ExportButton href="/api/iot/exports/powerbi.csv">Power BI CSV</ExportButton>
      </PageHeader>

      {error && <div className="alert danger mb">{error}</div>}
      {loading && <LoadingPage />}

      {!loading && !data && !error && <div className="empty-state">Nie ma jeszcze danych telemetrycznych. Wygeneruj kilka odczytów, żeby zapełnić dashboard.</div>}

      {data && (
        <>
          <MetricGrid>
            <Metric label="Dzienne OEE" value={`${oee.oee ?? 0}%`} note={`Dostępność ${oee.availability ?? 0}% / wydajność ${oee.performance ?? 0}%`} />
            <Metric label="Produkcja" value={oee.production_count ?? 0} note="Liczba sztuk z dzisiejszych odczytów" />
            <Metric label="Energia na sztukę" value={`${oee.energy_per_unit ?? 0} kWh`} note="Koszt i efektywność energetyczna procesu" />
            <Metric danger label="Otwarte alerty" value={alerts.length} note="Krytyczne zdarzenia widoczne dla zmiany" />
          </MetricGrid>

          <div className="ops-grid reporting-grid mt">
            <Panel title="Dostępność maszyn" subtitle="Procent odczytów, w których maszyna pracowała normalnie.">
              {(oee.machine_uptime ?? []).map(row => <BarRow key={row.machine} label={row.machine} sublabel="dzisiejsza dostępność" value={`${row.uptime}%`} width={row.uptime} />)}
              {(oee.machine_uptime ?? []).length === 0 && <div className="empty-state">Brak danych o dostępności.</div>}
            </Panel>
            <Panel title="Produkcja według zmiany" subtitle="Prosty podział produkcji dla lidera zmiany.">
              {(oee.production_by_shift ?? []).map(row => <SplitRow key={row.shift} label={`Zmiana ${row.shift}`} value={`${row.produced_units} szt.`} />)}
              {(oee.production_by_shift ?? []).length === 0 && <div className="empty-state">Brak danych produkcyjnych.</div>}
            </Panel>
          </div>

          <div className="ops-grid reporting-grid mt">
            <Panel title="Powody przestojów" subtitle="Szybki widok statusów innych niż praca i kodów błędów maszyn.">
              {(oee.downtime_causes ?? []).map(row => <SplitRow key={row.cause} label={downtimeCauseText(row.cause)} value={`${row.count} odczytów`} />)}
              {(oee.downtime_causes ?? []).length === 0 && <div className="empty-state">Nie zarejestrowano przestojów.</div>}
            </Panel>
            <Panel title="Anomalie temperatury i drgań" subtitle="Ostatnie anomalie wykryte przez reguły działające w backendzie Spring Boot.">
              {anomalies.slice(0, 6).map(item => <SplitRow key={item.id} label={`${anomalyKindText(item.kind)} / ${anomalySeverityText(item.severity)}`} value={dateTime(item.timestamp)} sublabel={item.message} />)}
              {anomalies.length === 0 && <div className="empty-state">Brak anomalii w ostatnich danych.</div>}
            </Panel>
          </div>

          <div className="ops-grid platform-grid mt">
            <Panel title="Ostatnia telemetryka" subtitle="Najnowsze odczyty z pipeline'u sensorów.">
              {latest.slice(0, 8).map(row => <SplitRow key={row.id} label={`${row.machine_code ?? `Maszyna ${row.machine_id}`} - ${machineStatusText(row.status)}`} value={`${row.temperature} C / ${row.vibration} mm/s`} sublabel={`${dateTime(row.timestamp)} - ${row.energy_kwh} kWh - ${row.produced_units} szt.`} />)}
              {latest.length === 0 && <div className="empty-state">Brak odczytów telemetrycznych.</div>}
            </Panel>
            <Panel title="Otwarte alerty" subtitle="Krytyczne zdarzenia, które normalnie trafiłyby do Teams, maila albo tablicy zmiany.">
              {alerts.slice(0, 8).map(alert => <SplitRow key={alert.id} label={alert.title} value={anomalySeverityText(alert.severity)} sublabel={alert.message} />)}
              {alerts.length === 0 && <div className="empty-state">Brak otwartych alertów. Dobra zmiana.</div>}
            </Panel>
          </div>
        </>
      )}
    </div>
  )
}

function anomalyKindText(kind) {
  return {
    temperature: 'temperatura',
    vibration: 'drgania',
    energy: 'energia',
    machine_status: 'status maszyny',
  }[kind] ?? kind
}

function anomalySeverityText(severity) {
  return {
    warning: 'ostrzeżenie',
    critical: 'krytyczny',
  }[severity] ?? severity
}

function machineStatusText(status) {
  return {
    RUNNING: 'praca',
    IDLE: 'postój',
    DOWN: 'awaria',
  }[status] ?? status
}

function downtimeCauseText(cause) {
  return cause === 'no_error' ? 'postój bez kodu błędu' : cause
}

function PowerPlatform({ state, go }) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  async function loadReporting() {
    setError('')
    try {
      const response = await apiFetch('/api/reporting/power-platform')
      if (!response.ok) throw new Error('Nie udało się pobrać warstwy raportowej.')
      setData(await response.json())
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadReporting()
  }, [])

  const summary = data?.summary ?? {}
  const downtimeRows = data?.downtimeByMachine ?? []
  const maxDowntime = Math.max(...downtimeRows.map(row => row.downtimeMinutes), 0)
  const auditPreview = state.activities
    .filter(activity => activity.message && activity.message.toLowerCase() !== 'test' && !activity.message.toLowerCase().includes('testst'))
    .slice(0, 5)

  return (
    <div className="ops-page">
      <PageHeader eyebrow="Power Platform i BI" title="Power Platform readiness" text="Ekran pokazuje, jak OpsHub spina Canvas App, Power Automate, Dataverse i SQL reporting w jeden scenariusz dla produkcji. KPI są czytane z backendowych widoków SQL.">
        <button className="btn btn-outline-dark" onClick={loadReporting}>Odśwież</button>
        <ExportButton href="/api/erp/daily-schedule">ERP API</ExportButton>
      </PageHeader>

      {error && <div className="alert danger mb">{error}</div>}
      {loading && <LoadingPage />}

      {!loading && (
        <>
          <MetricGrid>
            <Metric label="Otwarte zgłoszenia" value={summary.openIssues ?? 0} note="Z widoku rpt_issue_aging" />
            <Metric danger label="Krytyczne otwarte" value={summary.criticalOpenIssues ?? 0} note="Backlog do eskalacji flowem" />
            <Metric label="Średnie OEE proxy" value={`${summary.averageOee ?? 0}%`} note="Z widoku rpt_oee_daily" />
            <Metric label="Energia na sztukę" value={`${summary.energyPerUnit ?? 0} kWh`} note="Z widoku rpt_energy_per_unit" />
          </MetricGrid>

          <div className="ops-grid platform-grid mt">
            <Panel title="Canvas App screens" subtitle="Ekrany, które można przenieść do Power Apps bez zmieniania logiki procesu.">
              <div className="platform-card-grid">
                {(data?.canvasScreens ?? []).map(screen => <PlatformCard key={screen.name} label={screen.scope} title={screen.name} text={screen.detail} />)}
              </div>
            </Panel>
            <Panel title="Power Automate cloud flows" subtitle="Automatyzacje pokazane jako konkretne zdarzenia, a nie luźny pomysł na przyszłość.">
              <div className="flow-list">
                {(data?.cloudFlows ?? []).map(flow => <FlowCard key={flow.name} flow={flow} />)}
              </div>
            </Panel>
          </div>

          <div className="ops-grid platform-grid mt">
            <Panel title="Dataverse model" subtitle="Model tabel pod Canvas App i flowy. Nazwy są bliskie temu, co już istnieje w Javie.">
              <div className="table-chip-grid">
                {(data?.dataverseTables ?? []).map(table => <div className="table-chip" key={table.name}><strong>{table.name}</strong><span>{table.purpose}</span></div>)}
              </div>
            </Panel>
            <Panel title="SQL views dla Power BI" subtitle="Widoki tworzone przez backend przy starcie aplikacji. To jest warstwa pod raporty, nie statyczna tabelka w React.">
              <div className="sql-view-list">
                {(data?.views ?? []).map(view => <SplitRow key={view.name} label={view.name} value={`${view.rows} wierszy`} sublabel={view.purpose} />)}
              </div>
            </Panel>
          </div>

          <div className="ops-grid reporting-grid mt">
            <Panel title="Power BI: przestój według maszyn" subtitle="Dane z rpt_downtime_by_machine, gotowe do wykresu Pareto.">
              {downtimeRows.map(row => <BarRow key={row.machineCode} label={row.machineCode} sublabel={`${row.machineName} / ${row.productionLine}`} value={`${row.downtimeMinutes} min`} width={maxDowntime ? Math.max(12, Math.round((row.downtimeMinutes / maxDowntime) * 100)) : 0} />)}
            </Panel>
            <Panel title="Power BI: OEE i energia" subtitle="Dwie osobne perspektywy: linie produkcyjne oraz zużycie energii na sztukę.">
              {(data?.oeeDaily ?? []).map(row => <SplitRow key={`${row.reportDate}-${row.productionLine}`} label={`${row.productionLine} / ${row.reportDate}`} value={`${row.oeeProxy}% OEE`} sublabel={`${row.downtimeMinutes} min przestoju, dostępność ${row.availabilityProxy}%`} />)}
              {(data?.energyPerUnit ?? []).slice(0, 4).map(row => <SplitRow key={`${row.reportDate}-${row.shiftName}-${row.machineCode}`} label={`${row.machineCode} / zmiana ${row.shiftName}`} value={`${row.energyKwhPerUnit} kWh/szt.`} sublabel={`${row.producedUnits} szt., ${row.totalEnergyKwh} kWh`} />)}
            </Panel>
          </div>

          <div className="ops-grid platform-grid mt">
            <Panel title="Backlog z widoku SQL" subtitle="Najważniejsze otwarte zgłoszenia z rpt_issue_aging.">
              {(data?.issueAging ?? []).map(issue => <div className="report-issue-row" key={issue.issueId}><div><strong>{issue.title}</strong><small>{issue.machineCode} / {issue.productionLine} / {issue.assignedTeam}</small></div><div className="badge-row"><Badge tone={severityTone(issue.severity)}>{severityText[issue.severity]}</Badge><Badge tone={statusTone(issue.status)}>{statusText[issue.status]}</Badge><span>{issue.ageHours} h</span></div></div>)}
            </Panel>
            <Panel title="Audit i integracje" subtitle="Ten sam proces łączy UI, API, SQL reporting i automatyzacje.">
              <div className="connector-chain">
                <span>Canvas App</span>
                <span>Power Automate</span>
                <span>Dataverse / SQL</span>
                <span>Power BI</span>
              </div>
              <div className="integration-list mt">
                <ListItem title="GET /api/reporting/power-platform" text="pakiet danych dla tego ekranu" />
                <ListItem title="GET /exports/production-issues.csv" text="CSV pod Excel / Power BI" />
                <ListItem title="GET /api/iot/dashboard/summary" text="telemetria, anomalie i OEE z modułu Java" />
                <ListItem title="GET /api/erp/daily-schedule" text="mock integracji SAP/ERP" />
              </div>
              {auditPreview.length > 0 && <div className="mt">{auditPreview.map(activity => <SplitRow key={activity.id} label={activity.createdBy} value={dateTime(activity.createdAt)} sublabel={activity.message} />)}</div>}
            </Panel>
          </div>
        </>
      )}
    </div>
  )
}

function PlatformCard({ label, title, text }) {
  return <div className="platform-feature-card"><span>{label}</span><strong>{title}</strong><small>{text}</small></div>
}

function FlowCard({ flow }) {
  return <div className="flow-card"><strong>{flow.name}</strong><span>{flow.trigger}</span><small>{flow.action}</small></div>
}

function Platform({ state, go }) {
  const openEscalations = state.issues.filter(issue => isOpen(issue) && (issue.severity === 'HIGH' || issue.severity === 'CRITICAL')).length
  const auditPreview = state.activities
    .filter(activity => activity.message && activity.message.toLowerCase() !== 'test' && !activity.message.toLowerCase().includes('testst'))
    .slice(0, 6)
  const roles = [
    ['Operator', 'Hala produkcyjna', 'tworzy zgłoszenia, dodaje zdjęcia i widzi swoje stanowisko'],
    ['Lider zmiany', 'Produkcja', 'priorytetyzuje tematy, zamyka problemy, widzi KPI zmiany'],
    ['UR', 'Utrzymanie ruchu', 'obsługuje awarie, komentarze techniczne i historię maszyn'],
    ['Jakość', 'Quality', 'prowadzi niezgodności, weryfikuje rozwiązania i raportuje trendy'],
    ['Business IT', 'Platforma', 'zarządza integracjami, audytem, pipeline’em i dostępami'],
  ]

  return (
    <div className="ops-page">
      <PageHeader eyebrow="Business IT readiness" title="IT, integracje i wdrożenie" text="Tu pokazuję zaplecze projektu: kto z tego korzysta, jakie API już jest, gdzie wejdzie ERP i jak to można potem wdrożyć trochę porządniej niż odpalone na laptopie.">
        <ExportButton href="/api/erp/daily-schedule">Podgląd ERP API</ExportButton>
      </PageHeader>
      <div className="ops-grid platform-grid">
        <Panel title="Role i uprawnienia" subtitle="Docelowo można to podpiąć pod Microsoft Entra ID i rozdzielić dostęp według roli w zakładzie."><div className="role-grid">{roles.map(([name, scope, access]) => <div className="role-card" key={name}><strong>{name}</strong><span>{scope}</span><small>{access}</small></div>)}</div></Panel>
        <Panel title="Integracje API" subtitle="Endpointy są proste, ale pokazują dokładnie gdzie wpiąć ERP, BI i powiadomienia."><div className="integration-list">
          <ListItem title="GET /api/erp/daily-schedule" text="plan zleceń produkcyjnych z warstwy ERP" />
          <ListItem title="POST /api/issues/{id}/downtime-sync" text="potwierdzenie przestoju do ERP" />
          <ListItem title="GET /exports/production-issues.csv" text="dane dla Excela, Power BI lub Fabric" />
          <ListItem title="GET /exports/weekly-summary.pdf" text="raport tygodniowy dla liderów i managementu" />
        </div></Panel>
      </div>
      <div className="ops-grid platform-grid mt">
        <Panel title="Audit logs" subtitle="Historia działań jest po to, żeby po zmianie było wiadomo kto, co i kiedy zrobił.">{auditPreview.map(activity => <SplitRow key={activity.id} label={activity.createdBy} value={dateTime(activity.createdAt)} sublabel={activity.message} />)}</Panel>
        <Panel title="Deployment pipeline" subtitle="Od lokalnego demo do czegoś, co można pokazać jako normalny proces wdrożenia."><div className="deployment-steps">
          <ListItem active title="Local demo" text="H2, dane startowe, frontend w Vite" />
          <ListItem title="CI checks" text="mvnw test, npm run build i szybki przegląd UI" />
          <ListItem title="Staging" text="Azure App Service / Docker, baza SQL, sekrety w Key Vault" />
          <ListItem title="Production" text="Entra ID, monitoring, backupy, alerty i SLA" />
        </div></Panel>
      </div>
      <MetricGrid className="mt">
        <Metric label="Maszyny w API" value={state.machines.length} note="Gotowe do mapowania z CMMS/ERP" />
        <Metric label="Zlecenia ERP" value={state.workOrders.length} note="Aktywne dane planistyczne w demo" />
        <Metric label="Wpisy audytu" value={state.activities.length} note="Komentarze, statusy i akcje systemowe" />
        <Metric danger label="Otwarte eskalacje" value={openEscalations} note="Krytyczne lub wysokie priorytety" />
      </MetricGrid>
    </div>
  )
}

function PageHeader({ eyebrow, title, text, children }) {
  return <div className="page-header"><div><div className="eyebrow">{eyebrow}</div><h1>{title}</h1><p>{text}</p></div><div className="header-actions">{children}</div></div>
}

function Panel({ title, subtitle, action, className = '', children }) {
  return <section className={`ops-panel ${className}`}><div className="section-heading"><div>{title && <h2>{title}</h2>}{subtitle && <p>{subtitle}</p>}</div>{action}</div>{children}</section>
}

function MetricGrid({ className = '', children }) {
  return <div className={`ops-grid metrics-grid ${className}`}>{children}</div>
}

function Metric({ label, value, note, danger }) {
  return <div className={`ops-card metric-card ${danger ? 'danger-card' : ''}`}><div className="ops-label">{label}</div><div className="ops-number compact-number">{value}</div><div className="ops-note">{note}</div></div>
}

function Feature({ label, title, text, button, onClick }) {
  return <div className="ops-panel readiness-panel"><div className="ops-label">{label}</div><h2>{title}</h2><p>{text}</p><button className="btn btn-outline-dark" onClick={onClick}>{button}</button></div>
}

function Field({ label, className = '', children }) {
  return <label className={`field ${className}`}><span>{label}</span>{children}</label>
}

function SelectField({ label, value, onChange, options }) {
  return <Field label={label}><select value={value} onChange={event => onChange(event.target.value)}>{Object.entries(options).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></Field>
}

function Badge({ tone, children }) {
  return <span className={`badge ${tone}`}>{children}</span>
}

function ExportButton({ href, dark, children }) {
  return <a className={dark ? 'btn btn-dark' : 'btn btn-outline-dark'} href={`${API_BASE}${href}`}>{children}</a>
}

function ListItem({ title, text, active }) {
  return <div className={active ? 'active' : ''}><strong>{title}</strong><span>{text}</span></div>
}

function Risk({ title, value }) {
  return <div className="risk-item"><div className="risk-title">{title}</div><div className="risk-value">{value}</div></div>
}

function BarRow({ label, sublabel, value, width }) {
  return <div className="kpi-bar-row"><div><strong>{label}</strong><span>{sublabel}</span></div><div className="kpi-bar-track"><div className="kpi-bar-fill" style={{ width: `${width}%` }} /></div><strong>{value}</strong></div>
}

function SplitRow({ label, sublabel, value }) {
  return <div className="kpi-split-row"><span><strong>{label}</strong>{sublabel && <small>{sublabel}</small>}</span><strong>{value}</strong></div>
}

function SimilarIssue({ current, issue, go }) {
  const reasons = []
  if (current.machine?.id && current.machine.id === issue.machine?.id) reasons.push(`ta sama maszyna: ${issue.machine.code}`)
  if (current.category === issue.category) reasons.push(`ta sama kategoria: ${categoryText[issue.category]}`)
  if (current.workOrder?.id && current.workOrder.id === issue.workOrder?.id) reasons.push(`to samo zlecenie: ${issue.workOrder.sapOrderNumber}`)
  const bestComment = [...(issue.activities ?? [])].filter(activity => activity.type === 'COMMENT').sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))[0]

  return <div className="similar-issue-item"><div className="split"><div><strong>{issue.title}</strong><small>{reasons.join(', ') || 'Podobne zgłoszenie z historii'}</small></div><button className="btn btn-sm btn-outline-dark" onClick={() => go(`/issues/${issue.id}`)}>Otwórz</button></div><div className="badge-row"><Badge tone={statusTone(issue.status)}>{statusText[issue.status]}</Badge><Badge tone={severityTone(issue.severity)}>{severityText[issue.severity]}</Badge><span>{issue.downtimeMinutes} min przestoju</span></div>{bestComment ? <div className="knowledge-hint"><small>Ostatnia wskazówka z historii:</small><div>{bestComment.message}</div><small>{bestComment.createdBy}, {dateTime(bestComment.createdAt)}</small></div> : <small className="muted">Brak komentarzy z rozwiązaniem. Samo zgłoszenie nadal może być przydatne jako kontekst.</small>}</div>
}

function LoadingPage() {
  return <div className="ops-page"><div className="ops-panel"><div className="loading-line" /><div className="loading-line short" /></div></div>
}

function can(user, capability) {
  return Boolean(user?.capabilities?.[capability])
}

function roleLabel(user) {
  if (can(user, 'canResolveIssue')) return 'Leader'
  if (can(user, 'canCreateIssue')) return 'Operator'
  return 'Viewer'
}

function liveLabel(status) {
  if (status === 'live') return 'Live'
  if (status === 'reconnecting') return 'Reconnecting'
  return 'Offline'
}

function SlaBadge({ issue, kind = 'resolution' }) {
  const state = slaState(issue, kind)
  return (
    <span className="sla-stack">
      <Badge tone={state.tone}>{state.label}</Badge>
      <small>{state.detail}</small>
    </span>
  )
}

function slaState(issue, kind) {
  const closed = issue.status === 'RESOLVED' || issue.status === 'VERIFIED'
  const dueAt = kind === 'response' ? issue.responseDueAt : issue.resolutionDueAt
  const doneAt = kind === 'response' ? issue.acknowledgedAt : issue.resolvedAt

  if (doneAt) return { tone: 'success', label: 'Met', detail: dateTime(doneAt) }
  if (closed) return { tone: 'success', label: 'Closed', detail: dueNote(dueAt) }
  if (dueAt && new Date(dueAt) < new Date()) return { tone: 'danger', label: 'Breached', detail: dueNote(dueAt) }
  return { tone: kind === 'response' ? 'info' : 'warning', label: 'On track', detail: dueNote(dueAt) }
}

function dueNote(value) {
  return value ? `Due ${dateTime(value)}` : 'No due date'
}

async function responseError(response, fallback) {
  try {
    const body = await response.json()
    return body.detail || body.message || fallback
  } catch {
    return fallback
  }
}

function visibleIssues(issues) {
  const cutoff = daysFromNow(-7)
  return issues.filter(issue => {
    if (issue.status !== 'RESOLVED' && issue.status !== 'VERIFIED') return true
    if (!issue.resolvedAt) return true
    return new Date(issue.resolvedAt) >= cutoff
  })
}

function isOpen(issue) {
  return issue.status === 'NEW' || issue.status === 'IN_PROGRESS'
}

function severityRank(severity) {
  return { LOW: 1, MEDIUM: 2, HIGH: 3, CRITICAL: 4 }[severity] ?? 0
}

function severityTone(severity) {
  return { LOW: 'neutral', MEDIUM: 'info', HIGH: 'warning', CRITICAL: 'danger' }[severity] ?? 'neutral'
}

function statusTone(status) {
  return { NEW: 'info', IN_PROGRESS: 'warning', RESOLVED: 'success', VERIFIED: 'dark' }[status] ?? 'neutral'
}

function sum(items, select) {
  return items.reduce((total, item) => total + select(item), 0)
}

function groupDowntimeByMachine(issues) {
  const rows = new Map()
  issues.filter(issue => issue.machine).forEach(issue => {
    const key = issue.machine.code
    const current = rows.get(key) ?? { code: key, name: issue.machine.name, downtime: 0, count: 0 }
    current.downtime += issue.downtimeMinutes
    current.count += 1
    rows.set(key, current)
  })
  const sorted = [...rows.values()].sort((a, b) => b.downtime - a.downtime)
  const max = sorted[0]?.downtime ?? 0
  return sorted.map(row => ({ ...row, width: max ? Math.max(12, Math.round((row.downtime / max) * 100)) : 0 }))
}

function groupByCategory(issues) {
  const rows = new Map()
  issues.forEach(issue => {
    const current = rows.get(issue.category) ?? { category: issue.category, count: 0, downtime: 0 }
    current.count += 1
    current.downtime += issue.downtimeMinutes
    rows.set(issue.category, current)
  })
  return [...rows.values()].sort((a, b) => b.downtime - a.downtime)
}

function worstCategoryLabel(issues) {
  const worst = groupByCategory(issues)[0]
  return worst ? `${categoryText[worst.category]} - ${worst.downtime} min` : 'Brak danych'
}

function daysFromNow(days) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return date
}

function shortDate(value) {
  return new Date(value).toLocaleDateString('pl-PL', { day: '2-digit', month: '2-digit' })
}

function dateTime(value) {
  return new Date(value).toLocaleString('pl-PL', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function activityType(type) {
  return { COMMENT: 'Komentarz', STATUS_CHANGE: 'Zmiana statusu', SYSTEM: 'System' }[type] ?? 'Aktywność'
}

function auditTypeText(type) {
  return { COMMENT: 'Comment', STATUS_CHANGE: 'Status change', SYSTEM: 'System' }[type] ?? type
}

function auditTone(type) {
  return { COMMENT: 'info', STATUS_CHANGE: 'warning', SYSTEM: 'dark' }[type] ?? 'neutral'
}

createRoot(document.getElementById('root')).render(<App />)
