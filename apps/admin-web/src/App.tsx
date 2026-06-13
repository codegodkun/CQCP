import { Route, Routes } from "react-router-dom";

import { PublicResultPage } from "./publicResult/PublicResultPage";

export function App() {
  return (
    <Routes>
      <Route path="*" element={<PublicResultPage />} />
    </Routes>
  );
}
