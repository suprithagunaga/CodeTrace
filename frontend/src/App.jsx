import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import NewAnalysis from './pages/NewAnalysis';
import AnalysisResults from './pages/AnalysisResults';
import ComparisonView from './pages/ComparisonView';
import History from './pages/History';

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Dashboard />} />
        <Route path="new" element={<NewAnalysis />} />
        <Route path="history" element={<History />} />
        <Route path="analyses/:id" element={<AnalysisResults />} />
        <Route path="analyses/:id/comparisons/:comparisonId" element={<ComparisonView />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
