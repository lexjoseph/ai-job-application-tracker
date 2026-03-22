export type ApplicationStatus = "APPLIED" | "INTERVIEW" | "OFFER" | "REJECTED";

export type JobApplication = {
  id: string;
  company: string;
  roleTitle: string;
  status: ApplicationStatus;
  notes: string | null;
  sourceUrl: string | null;
  appliedOn: string;
  createdAt: string;
  updatedAt: string;
};

export type ApplicationStats = {
  applied: number;
  interview: number;
  offer: number;
  rejected: number;
  total: number;
};

export type AuthResponse = {
  accessToken: string;
  tokenType: string;
  expiresInMs: number;
};
