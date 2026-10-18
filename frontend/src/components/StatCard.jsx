export function StatCard({ icon: Icon, label, value, hint }) {
  return (
    <div className="card stat-card">
      <div className="stat-icon" aria-hidden="true">{Icon && <Icon size={20} />}</div>
      <div>
        <div className="stat-label">{label}</div>
        <div className="stat-value">{value}</div>
        {hint && <div className="stat-hint">{hint}</div>}
      </div>
    </div>
  );
}
