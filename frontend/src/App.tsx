import React, { useRef, useState } from 'react';
import { Navigate, NavLink, Route, Routes, useNavigate } from 'react-router-dom';
import { FluxoCaixaChart } from './components/charts/FluxoCaixaChart';
import { ContasView } from './components/contas/ContasView';
import { DashboardView } from './components/DashboardView';
import { GarantiasView } from './components/GarantiasView';
import { PessoasView } from './components/PessoasView';
import { PlanoContasView } from './components/PlanoContasView';
import { RelatoriosView } from './components/RelatoriosView';
import { usePessoas, usePlanosConta, useTitulos } from './hooks/useCprQueries';
import { ApiError } from './services/api';

const ROTAS = {
  dashboard: '/',
  pagar: '/contas-pagar',
  receber: '/contas-receber',
  pessoas: '/pessoas',
  planos: '/plano-contas',
  relatorios: '/relatorios',
  garantias: '/cheques-cartorio',
} as const;

type TabKey = keyof typeof ROTAS;

const navClass = ({ isActive }: { isActive: boolean }) => `nav-item ${isActive ? 'active' : ''}`;

/** Dashboard: junta dados de quatro serviços; cada consulta falha sozinha sem derrubar as outras. */
const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const pagar = useTitulos('PAGAR');
  const receber = useTitulos('RECEBER');
  const pessoas = usePessoas();
  const planos = usePlanosConta();
  const loading = pagar.isLoading || receber.isLoading;

  return (
    <>
      <DashboardView
        contasPagar={pagar.data ?? []}
        contasReceber={receber.data ?? []}
        pessoas={pessoas.data ?? []}
        planosConta={planos.data ?? []}
        loading={loading}
        onNavigate={(tab: TabKey) => navigate(ROTAS[tab])}
      />
      <div style={{ marginTop: 24 }}>
        <FluxoCaixaChart contasPagar={pagar.data ?? []} contasReceber={receber.data ?? []} />
      </div>
    </>
  );
};

/**
 * Indicador no rodapé: o gateway está respondendo? Um 503 de um serviço também conta como
 * "gateway no ar" (é o fallback dele); só falta de conexão (status 0) é offline.
 */
const StatusGateway: React.FC = () => {
  const pessoas = usePessoas();
  const planos = usePlanosConta();
  const respondeu = (e: unknown) => e instanceof ApiError && e.status > 0;
  const online = pessoas.isSuccess || planos.isSuccess || respondeu(pessoas.error) || respondeu(planos.error);
  return (
    <div>
      <span className={`status-dot ${online ? 'online' : 'offline'}`} />
      <strong>API Gateway:</strong> {online ? 'Conectado (8080)' : 'Offline'}
    </div>
  );
};

export const App: React.FC = () => {
  const [isPlayingAudio, setIsPlayingAudio] = useState(false);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  const handleToggleEasterEgg = () => {
    if (!audioRef.current) {
      const audio = new Audio('/breaking-bad-intro.mp3');
      audio.onended = () => setIsPlayingAudio(false);
      audioRef.current = audio;
    }
    if (isPlayingAudio) {
      audioRef.current.pause();
      audioRef.current.currentTime = 0;
      setIsPlayingAudio(false);
    } else {
      audioRef.current.currentTime = 0;
      void audioRef.current.play();
      setIsPlayingAudio(true);
    }
  };

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <img
            src="/logo-cpr.png"
            alt="Logo Contas a Pagar e Receber"
            className={`brand-logo-img ${isPlayingAudio ? 'playing-easter-egg' : ''}`}
            onClick={handleToggleEasterEgg}
            title="Say my name..."
          />
          <div className="brand-text">
            <h1>Contas a Pagar e Receber</h1>
          </div>
        </div>

        <nav className="sidebar-nav">
          <span className="nav-section-label">Menu Principal</span>
          <NavLink to={ROTAS.dashboard} className={navClass} end>
            <span className="nav-icon">📊</span>
            <span>Dashboard Financeiro</span>
          </NavLink>
          <NavLink to={ROTAS.pagar} className={navClass}>
            <span className="nav-icon">💸</span>
            <span>Contas a Pagar</span>
          </NavLink>
          <NavLink to={ROTAS.receber} className={navClass}>
            <span className="nav-icon">💰</span>
            <span>Contas a Receber</span>
          </NavLink>
          <NavLink to={ROTAS.garantias} className={navClass}>
            <span className="nav-icon">🧾</span>
            <span>Cheques e Cartório</span>
          </NavLink>
          <NavLink to={ROTAS.relatorios} className={navClass}>
            <span className="nav-icon">📈</span>
            <span>Relatórios</span>
          </NavLink>

          <span className="nav-section-label">Cadastros Base</span>
          <NavLink to={ROTAS.pessoas} className={navClass}>
            <span className="nav-icon">👥</span>
            <span>Pessoas (Cli / Forn)</span>
          </NavLink>
          <NavLink to={ROTAS.planos} className={navClass}>
            <span className="nav-icon">📂</span>
            <span>Plano de Contas</span>
          </NavLink>
        </nav>

        <div className="sidebar-footer">
          <StatusGateway />
        </div>
      </aside>

      <div className="main-content">
        <header className="topbar">
          <div className="topbar-title">
            <h2>Gestão Financeira Integrada · Microsserviços</h2>
          </div>
        </header>

        <main className="page-container">
          <Routes>
            <Route path={ROTAS.dashboard} element={<Dashboard />} />
            <Route path={ROTAS.pagar} element={<ContasView key="PAGAR" natureza="PAGAR" />} />
            <Route path={ROTAS.receber} element={<ContasView key="RECEBER" natureza="RECEBER" />} />
            <Route path={ROTAS.garantias} element={<GarantiasView />} />
            <Route path={ROTAS.relatorios} element={<RelatoriosView />} />
            <Route path={ROTAS.pessoas} element={<PessoasView />} />
            <Route path={ROTAS.planos} element={<PlanoContasView />} />
            <Route path="*" element={<Navigate to={ROTAS.dashboard} replace />} />
          </Routes>
        </main>
      </div>
    </div>
  );
};
