import { Route, Routes } from "react-router-dom";

import { AdminDiagnosticPage } from "./adminDiagnostics/AdminDiagnosticPage";
import { PublicResultPage } from "./publicResult/PublicResultPage";
import { ReviewTaskCreationPage } from "./reviewCreation/ReviewTaskCreationPage";
import { ReviewExecutionStatusPage } from "./reviewStatus/ReviewExecutionStatusPage";

export function App() {
  return (
    <Routes>
      <Route path="/review/new" element={<ReviewTaskCreationPage />} />
      <Route
        path="/review/tasks/:taskId/executions/:executionId"
        element={<ReviewExecutionStatusPage />}
      />
      <Route path="/review/results/:taskId" element={<PublicResultPage />} />
      <Route path="/admin/diagnostics" element={<AdminDiagnosticPage />} />
      <Route path="/" element={<PublicResultPage />} />
    </Routes>
  );
}
