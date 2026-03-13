"use client";

import { useState } from "react";
import type { JobApplication } from "../types/jobs";

interface JobFormProps {
  addApplication: (application: JobApplication) => void;
}

const JobForm = ({ addApplication }: JobFormProps) => {
  const [company, setCompany] = useState<string>("");
  const [role, setRole] = useState<string>("");

  const handleSubmit = (e: React.SubmitEvent<HTMLFormElement>): void => {
    const newApplication: JobApplication = {
      id: Date.now(),
      company,
      role,
      status: "Applied",
    };
    addApplication(newApplication);

    setCompany("");
    setRole("");
  };

  return (
    <form onSubmit={handleSubmit}>
      <input
        type="text"
        placeholder="company"
        value={company}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
          setCompany(e.target.value)
        }
      />
      <input
        type="text"
        placeholder="role"
        value={role}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
          setRole(e.target.value)
        }
      />
    </form>
  );
};

export default JobForm;
