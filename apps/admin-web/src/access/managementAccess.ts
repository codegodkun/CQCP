import { createContext, useContext } from "react";

export type ManagementAccess = {
  token: string;
  epoch: number;
  authenticate: (token: string) => void;
  clear: () => void;
};

export const ManagementAccessContext =
  createContext<ManagementAccess | null>(null);

export function useManagementAccess(): ManagementAccess {
  const value = useContext(ManagementAccessContext);
  if (!value) {
    throw new Error("ManagementAccessProvider is required");
  }
  return value;
}
