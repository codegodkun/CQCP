import { Route, Routes } from "react-router-dom";

import { AdminDiagnosticPage } from "./adminDiagnostics/AdminDiagnosticPage";
import { PublicResultPage } from "./publicResult/PublicResultPage";

export function App() {
  return (
    <Routes>
      <Route path="/admin/diagnostics" element={<AdminDiagnosticPage />} />
      <Route path="/" element={<PublicResultPage />} />
    </Routes>
  );
}
