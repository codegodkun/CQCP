import { Route, Routes } from "react-router-dom";

import { AdminDiagnosticPage } from "./adminDiagnostics/AdminDiagnosticPage";
import { PublicResultPage } from "./publicResult/PublicResultPage";
import { ReviewTaskCreationPage } from "./reviewCreation/ReviewTaskCreationPage";

export function App() {
  return (
    <Routes>
      <Route path="/review/new" element={<ReviewTaskCreationPage />} />
      <Route path="/admin/diagnostics" element={<AdminDiagnosticPage />} />
      <Route path="/" element={<PublicResultPage />} />
    </Routes>
  );
}
