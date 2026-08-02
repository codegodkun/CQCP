import { Route, Routes } from "react-router-dom";

import { ManagementAccessProvider } from "./access/ManagementAccessContext";
import { AdminDiagnosticPage } from "./adminDiagnostics/AdminDiagnosticPage";
import { ModelProfilesPage } from "./modelProfiles/ModelProfilesPage";
import { PublicResultPage } from "./publicResult/PublicResultPage";
import { ReviewTaskCreationPage } from "./reviewCreation/ReviewTaskCreationPage";
import { ReviewExecutionStatusPage } from "./reviewStatus/ReviewExecutionStatusPage";
import { ReviewTaskListPage } from "./reviewTasks/ReviewTaskListPage";

export function App({
  initialManagementAccessToken = ""
}: {
  initialManagementAccessToken?: string;
}) {
  return (
    <ManagementAccessProvider initialToken={initialManagementAccessToken}>
      <Routes>
        <Route path="/review/new" element={<ReviewTaskCreationPage />} />
        <Route path="/review/tasks" element={<ReviewTaskListPage />} />
        <Route
          path="/review/tasks/:taskId/executions/:executionId"
          element={<ReviewExecutionStatusPage />}
        />
        <Route path="/review/results/:taskId" element={<PublicResultPage />} />
        <Route path="/admin/diagnostics" element={<AdminDiagnosticPage />} />
        <Route path="/admin/model-profiles" element={<ModelProfilesPage />} />
        <Route path="/" element={<PublicResultPage />} />
      </Routes>
    </ManagementAccessProvider>
  );
}
