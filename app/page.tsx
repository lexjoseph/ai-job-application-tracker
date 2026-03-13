"use client";

import { useState } from "react";
import type { JobApplication } from "./types/jobs";
import JobForm from "./component/JobForm";

export default function Home() {
  const [applications, setApplication] = useState<JobApplication[]>([]);
  
  const addapplication = (application: JobApplication): void => {
    setApplication((prevApplication) => [application, ...prevApplication]);
  };
  return (
    <div>
      <h1 className="text-center">My Personal AI Job Application Tracker </h1>
      <JobForm addApplication={addapplication} />
      <p>Total Applications: {applications.length} </p>

      <ul>
        {applications.map((application) => (
          <li key={application.id}>
            {application.company} - {application.role} - {application.status}
          </li>
        ))}
      </ul>
    </div>
  );
}
