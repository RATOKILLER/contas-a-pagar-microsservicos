import React from 'react';
import { FieldError } from 'react-hook-form';

interface CampoProps {
  label: string;
  erro?: FieldError;
  full?: boolean;
  dica?: string;
  children: React.ReactNode;
}

/** Rótulo + campo + mensagem de erro vinda do Zod (via React Hook Form). */
export const Campo: React.FC<CampoProps> = ({ label, erro, full, dica, children }) => (
  <div className={`form-group${full ? ' full-width' : ''}${erro ? ' has-error' : ''}`}>
    <label>{label}</label>
    {children}
    {erro ? <span className="field-error">{erro.message}</span> : dica ? <span className="field-hint">{dica}</span> : null}
  </div>
);
