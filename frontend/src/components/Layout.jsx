import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, Link } from 'react-router-dom';
import { FilePlus2, History, LayoutDashboard, Menu, X } from 'lucide-react';
import { BackendStatusPill, useBackendHealth } from './BackendStatus';

const NAV = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/new', label: 'New Analysis', icon: FilePlus2 },
  { to: '/history', label: 'History', icon: History },
];

export default function Layout() {
  const health = useBackendHealth();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  useEffect(() => setOpen(false), [location.pathname]);

  return (
    <div className="app-shell">
      <a href="#main" className="skip-link">Skip to content</a>
      <aside className={`sidebar ${open ? 'open' : ''}`} aria-label="Primary">
        <Link to="/" className="brand">
          <span className="brand-mark"><img src="/favicon.svg" alt="" width="26" height="26" /></span>
          <span>CodeTrace</span>
        </Link>
        <nav className="sidebar-nav">
          <ul>
            {NAV.map(({ to, label, icon: Icon, end }) => (
              <li key={to}>
                <NavLink to={to} end={end} className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
                  <Icon size={18} aria-hidden="true" />
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <div className="sidebar-footer">
          <span className="muted small">UI v{__APP_VERSION__}</span>
        </div>
      </aside>
      {open && <div className="sidebar-scrim" onClick={() => setOpen(false)} aria-hidden="true" />}
      <div className="main-column">
        <header className="topbar">
          <button type="button" className="icon-btn menu-btn" onClick={() => setOpen((o) => !o)}
            aria-label={open ? 'Close navigation' : 'Open navigation'} aria-expanded={open}>
            {open ? <X size={20} /> : <Menu size={20} />}
          </button>
          <span className="topbar-title"><strong>CodeTrace</strong><span>Java Code Similarity Analyzer</span></span>
          <div className="topbar-right">
            <BackendStatusPill status={health.status} />
          </div>
        </header>
        {health.status === 'down' && (
          <div className="banner banner-error" role="alert">
            Cannot reach the CodeTrace API. Start the backend, then
            <button type="button" className="link-btn" onClick={health.recheck}>retry</button>.
          </div>
        )}
        {health.status === 'degraded' && (
          <div className="banner banner-warn" role="alert">
            The API is running but PostgreSQL is unavailable. Saved analyses cannot be loaded until it is back.
            <button type="button" className="link-btn" onClick={health.recheck}>Check again</button>
          </div>
        )}
        <main id="main" className="content" tabIndex={-1}>
          <Outlet context={{ health }} />
        </main>
      </div>
    </div>
  );
}
