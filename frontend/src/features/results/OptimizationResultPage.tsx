import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { describeError, ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { Select } from '@/components/ui/Select';
import { showToast } from '@/components/ui/toast';
import { createApplication, renderResumePdf } from '@/services/applicationApi';
import { DashboardShell } from '@/features/dashboard/components/DashboardShell';
import { CopyIcon, DownloadIcon, SparkleIcon } from '@/features/dashboard/icons';
import { getAnalysis, getJdOptimization, type OptimizationData } from '@/services/jdApi';
import { getProfile } from '@/services/profileApi';
import { downloadTemplate, listTemplates, type TemplateResponse } from '@/services/templateApi';
import { NextSteps } from './components/NextSteps';
import { OptimizationPromptModal } from './components/OptimizationPromptModal';
import { ResultTopBar } from './components/ResultTopBar';

/** Evidence ids carry their kind in the prefix, which is what lets the emphasis list be grouped
 *  into Experience / Projects / Certifications without the backend sending a second field. */
const EVIDENCE_GROUPS = [
  { prefix: 'EXP', label: 'Relevant experience' },
  { prefix: 'PROJ', label: 'Relevant projects' },
  { prefix: 'CERT', label: 'Relevant certifications' },
  { prefix: 'EDU', label: 'Relevant education' },
  { prefix: 'SKILL', label: 'Relevant skills' },
  { prefix: 'ACH', label: 'Relevant achievements' },
] as const;

function Chip({ label, tone = 'neutral' }: { label: string; tone?: 'neutral' | 'good' | 'warn' | 'bad' }) {
  const styles = {
    neutral: 'bg-surface-2 text-ink-muted',
    good: 'bg-mint/10 text-mint',
    warn: 'bg-ember-soft/10 text-ember-soft',
    bad: 'bg-rose/10 text-rose',
  } as const;
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium ${styles[tone]}`}>
      {label}
    </span>
  );
}

/**
 * The JD-optimization result — the product's deliverable now that CareerForge no longer
 * generates a resume or cover letter (ADR-033).
 *
 * <p>Everything rendered comes from the persisted optimization. Two existing endpoints are
 * joined purely for legibility: the JD analysis supplies each requirement's text (the result
 * stores only `requirementId`), and the profile supplies each evidence item's own title (the
 * result stores only `evidenceId`). Neither join adds a fact — if a lookup misses, the raw id is
 * shown rather than a guess.
 */
export function OptimizationResultPage() {
  const { jdId = '' } = useParams<{ jdId: string }>();
  const [promptOpen, setPromptOpen] = useState(false);
  // '' means "no template selected" — a real select value, never undefined, so the <select>
  // stays a controlled component from the first render.
  const [selectedTemplateId, setSelectedTemplateId] = useState('');
  // Resume PDF generation is neither persisted nor idempotent-by-id (render-service stores
  // nothing — see ResumeRenderService's Javadoc), so the Application created for it is kept
  // here and reused for a re-click instead of creating a fresh one every time.
  const [resumeApplicationId, setResumeApplicationId] = useState<string | null>(null);
  const [generatingResume, setGeneratingResume] = useState(false);

  const optimizationQuery = useQuery({
    queryKey: ['jd-optimization', jdId],
    queryFn: () => getJdOptimization(jdId),
    enabled: Boolean(jdId),
  });

  // The user's saved template library (ADR-034) — selecting one here never uploads anything;
  // it only references a file already sitting in Profile → My Templates.
  const templatesQuery = useQuery({ queryKey: ['templates'], queryFn: listTemplates });

  // Pre-selects the user's default template the first time the library loads, without ever
  // overwriting a choice the user already made (e.g. after they explicitly pick "none").
  useEffect(() => {
    if (selectedTemplateId || !templatesQuery.data) return;
    const defaultTemplate = templatesQuery.data.find((t) => t.isDefault);
    if (defaultTemplate) setSelectedTemplateId(defaultTemplate.id);
  }, [templatesQuery.data, selectedTemplateId]);

  const selectedTemplate: TemplateResponse | null =
    templatesQuery.data?.find((t) => t.id === selectedTemplateId) ?? null;

  const downloadSelectedTemplate = async () => {
    if (!selectedTemplate) return;
    try {
      const blob = await downloadTemplate(selectedTemplate.id);
      const url = URL.createObjectURL(blob);
      const link = window.document.createElement('a');
      link.href = url;
      link.download = selectedTemplate.fileName;
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      showToast(describeError(error));
    }
  };

  // Requirement text lives on the analysis; the optimization stores ids only.
  const analysisQuery = useQuery({
    queryKey: ['jd-analysis', jdId],
    queryFn: () => getAnalysis(jdId),
    enabled: Boolean(jdId),
    retry: false,
  });

  const profileQuery = useQuery({ queryKey: ['profile'], queryFn: getProfile });

  /**
   * Builds a RESUME_ONLY application for this JD (or reuses the one already created this
   * visit) and renders it through application-service's `POST /{id}/resume/render` (ADR-036's
   * minimal integration: deterministic assembly from cited evidence, no AI call, no
   * rephrasing). Nothing is persisted on either side, so this can be clicked again any time
   * the optimization or profile has changed and it will re-render from scratch. templateId is
   * deliberately not passed: render-service has exactly one built-in layout today regardless
   * of which template is selected here, and passing one would also send it through
   * ApplicationService.create's template-existence check against a since-removed service.
   */
  const generateResumePdf = async () => {
    setGeneratingResume(true);
    try {
      let applicationId = resumeApplicationId;
      if (!applicationId) {
        const application = await createApplication(jdId, 'RESUME_ONLY');
        applicationId = application.id;
        setResumeApplicationId(applicationId);
      }
      const blob = await renderResumePdf(applicationId);
      const url = URL.createObjectURL(blob);
      const link = window.document.createElement('a');
      link.href = url;
      link.download = 'careerforge-resume.pdf';
      link.click();
      URL.revokeObjectURL(url);
      showToast('Resume PDF generated.');
    } catch (error) {
      showToast(describeError(error));
    } finally {
      setGeneratingResume(false);
    }
  };

  const requirementText = useMemo(() => {
    const map = new Map<string, string>();
    for (const requirement of analysisQuery.data?.requirements ?? []) {
      map.set(requirement.requirementId, requirement.text);
    }
    return map;
  }, [analysisQuery.data]);

  const evidenceLabel = useMemo(() => {
    const map = new Map<string, string>();
    const profile = profileQuery.data;
    if (!profile) return map;
    for (const item of profile.experiences ?? []) {
      map.set(item.evidenceId, [item.title, item.company].filter(Boolean).join(' — ') || item.evidenceId);
    }
    for (const item of profile.projects ?? []) map.set(item.evidenceId, item.name ?? item.evidenceId);
    for (const item of profile.skills ?? []) map.set(item.evidenceId, item.name ?? item.evidenceId);
    for (const item of profile.certifications ?? []) map.set(item.evidenceId, item.name ?? item.evidenceId);
    for (const item of profile.education ?? []) map.set(item.evidenceId, item.institution ?? item.evidenceId);
    for (const item of profile.achievements ?? []) map.set(item.evidenceId, item.title ?? item.evidenceId);
    return map;
  }, [profileQuery.data]);

  const labelFor = (evidenceId: string) => evidenceLabel.get(evidenceId) ?? evidenceId;

  if (optimizationQuery.isLoading) {
    return <FullScreenSpinner label="Loading your optimization…" />;
  }

  if (optimizationQuery.isError || !optimizationQuery.data) {
    return (
      <DashboardShell>
        {({ user, onLogout }) => (
          <>
            <ResultTopBar user={user} onLogout={onLogout} />
            <div className="mt-6">
              <ErrorBanner error={optimizationQuery.error ?? new Error('Optimization not found')} />
              <Link to="/generate" className="mt-4 inline-block text-sm text-ink-muted hover:text-ink">
                Start a new optimization
              </Link>
            </div>
          </>
        )}
      </DashboardShell>
    );
  }

  const result = optimizationQuery.data;
  const data: OptimizationData = result.optimisation;
  const json = JSON.stringify(data, null, 2);

  const required = (data.keywords ?? []).filter((k) => k.priority === 'REQUIRED');
  const preferred = (data.keywords ?? []).filter((k) => k.priority === 'PREFERRED');
  const strong = (data.requirementMatches ?? []).filter((m) => m.matchStrength === 'STRONG');
  const partial = (data.requirementMatches ?? []).filter((m) => m.matchStrength === 'PARTIAL');
  const missing = data.missingRequirements ?? [];
  const mapped = (data.keywords ?? []).filter((k) => k.evidenceIds.length > 0);

  const copyJson = async () => {
    try {
      await navigator.clipboard.writeText(json);
      showToast('Optimization JSON copied to clipboard.');
    } catch {
      showToast("Couldn't copy — try Download JSON instead.");
    }
  };

  const downloadJson = () => {
    const blob = new Blob([json], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = window.document.createElement('a');
    link.href = url;
    link.download = 'careerforge-jd-optimization.json';
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <DashboardShell>
      {({ user, onLogout }) => (
        <>
          {promptOpen && (
            <OptimizationPromptModal
              data={data}
              selectedTemplate={selectedTemplate}
              onDownloadTemplate={downloadSelectedTemplate}
              onClose={() => setPromptOpen(false)}
            />
          )}

          <ResultTopBar user={user} onLogout={onLogout} />

          <div className="mt-6 space-y-6">
            <Card className="!p-6 sm:!p-8">
              <p className="text-xs font-semibold uppercase tracking-wide text-ember-soft">Optimization ready</p>
              <h1 className="mt-2 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
                Your JD Optimization Is Ready
              </h1>
              <p className="mt-3 max-w-2xl text-sm leading-relaxed text-ink-muted">
                We've analyzed the job description against your verified profile and identified the
                skills, experience, keywords and gaps most relevant to this role.
              </p>
              {(data.targetRole || data.targetCompany) && (
                <p className="mt-3 text-sm text-ink">
                  <span className="text-ink-faint">Target: </span>
                  {data.targetRole ?? 'Role not stated'}
                  {data.targetCompany ? ` — ${data.targetCompany}` : ''}
                </p>
              )}

              <div className="mt-5 flex flex-col gap-2 sm:flex-row sm:flex-wrap">
                <Button variant="primary" onClick={() => setPromptOpen(true)}>
                  <SparkleIcon className="h-4 w-4" />
                  {selectedTemplate ? 'Create with ChatGPT' : 'Copy AI Prompt'}
                </Button>
                <Button variant="secondary" onClick={copyJson}>
                  <CopyIcon className="h-4 w-4" />
                  Copy Optimization JSON
                </Button>
                <Button variant="secondary" onClick={downloadJson}>
                  <DownloadIcon className="h-4 w-4" />
                  Download JSON
                </Button>
              </div>
              <p className="mt-3 text-xs text-ink-faint">
                Use these with any AI or document tool to create your resume externally — CareerForge
                produced the optimization data, not the finished document.
              </p>
            </Card>

            {/* Template selection (ADR-034) ------------------------------------ */}
            <Card>
              <h2 className="text-sm font-semibold text-ink">Choose your template</h2>
              <p className="mt-1.5 text-sm text-ink-muted">
                Pick a template from your saved library to carry into the handoff above — you never
                upload one here.
              </p>

              {templatesQuery.isLoading && <p className="mt-3 text-sm text-ink-faint">Loading your templates…</p>}

              {templatesQuery.data && templatesQuery.data.length === 0 && (
                <div className="mt-3 rounded-xl border border-dashed border-border px-4 py-5 text-sm">
                  <p className="text-ink-muted">No saved templates yet.</p>
                  <p className="mt-1 text-ink-faint">Add one from Profile → My Templates.</p>
                  <Link to="/profile/templates" className="mt-3 inline-block">
                    <Button variant="secondary" className="!px-4 !py-2 !text-xs">
                      Manage Templates
                    </Button>
                  </Link>
                </div>
              )}

              {templatesQuery.data && templatesQuery.data.length > 0 && (
                <div className="mt-3 flex flex-col gap-3 sm:flex-row sm:items-end">
                  <div className="sm:max-w-xs sm:flex-1">
                    <Select
                      label="Saved templates"
                      value={selectedTemplateId}
                      onChange={(event) => setSelectedTemplateId(event.target.value)}
                    >
                      <option value="">No template selected</option>
                      {templatesQuery.data.map((template) => (
                        <option key={template.id} value={template.id}>
                          {template.name}
                          {template.isDefault ? ' (default)' : ''}
                        </option>
                      ))}
                    </Select>
                  </div>
                  <Link to="/profile/templates">
                    <Button variant="secondary" className="!px-4 !py-2.5 !text-xs">
                      Manage Templates
                    </Button>
                  </Link>
                </div>
              )}
            </Card>

            {/* Resume PDF (ADR-036's minimal integration) ---------------------- */}
            <Card>
              <h2 className="text-sm font-semibold text-ink">Generate your resume PDF</h2>
              <p className="mt-1.5 text-sm text-ink-muted">
                Builds a PDF straight from this optimization's cited evidence and your profile —
                no AI rephrasing, every line verbatim from what you entered.
              </p>
              <div className="mt-4">
                <Button variant="primary" onClick={generateResumePdf} loading={generatingResume}>
                  <DownloadIcon className="h-4 w-4" />
                  Generate Resume PDF
                </Button>
              </div>
            </Card>

            {/* Keywords ------------------------------------------------------ */}
            <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
              <Card>
                <h2 className="text-sm font-semibold text-ink">Required skills &amp; keywords</h2>
                {required.length === 0 ? (
                  <p className="mt-2 text-sm text-ink-faint">None marked required for this role.</p>
                ) : (
                  <div className="mt-3 flex flex-wrap gap-2">
                    {required.map((k) => (
                      <Chip key={k.term} label={k.term} tone={k.evidenceIds.length > 0 ? 'good' : 'bad'} />
                    ))}
                  </div>
                )}
                <p className="mt-3 text-xs text-ink-faint">
                  Green terms are backed by your profile; red ones are not found in it.
                </p>
              </Card>

              <Card>
                <h2 className="text-sm font-semibold text-ink">Preferred skills &amp; keywords</h2>
                {preferred.length === 0 ? (
                  <p className="mt-2 text-sm text-ink-faint">None marked preferred for this role.</p>
                ) : (
                  <div className="mt-3 flex flex-wrap gap-2">
                    {preferred.map((k) => (
                      <Chip key={k.term} label={k.term} tone={k.evidenceIds.length > 0 ? 'good' : 'neutral'} />
                    ))}
                  </div>
                )}
              </Card>
            </div>

            {/* Matches -------------------------------------------------------- */}
            <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
              <Card>
                <h2 className="text-sm font-semibold text-ink">
                  Strong matches <span className="text-ink-faint">({strong.length})</span>
                </h2>
                <ul className="mt-3 space-y-2">
                  {strong.length === 0 && <li className="text-sm text-ink-faint">No strong matches yet.</li>}
                  {strong.map((m) => (
                    <li key={m.requirementId} className="text-sm text-ink">
                      <span aria-hidden="true" className="text-mint">✓ </span>
                      {requirementText.get(m.requirementId) ?? m.requirementId}
                      <span className="ml-1 text-xs text-ink-faint">
                        {m.evidenceIds.map(labelFor).join(', ')}
                      </span>
                    </li>
                  ))}
                </ul>
              </Card>

              <Card>
                <h2 className="text-sm font-semibold text-ink">
                  Partial matches <span className="text-ink-faint">({partial.length})</span>
                </h2>
                <ul className="mt-3 space-y-2">
                  {partial.length === 0 && <li className="text-sm text-ink-faint">No partial matches.</li>}
                  {partial.map((m) => (
                    <li key={m.requirementId} className="text-sm text-ink">
                      <span aria-hidden="true" className="text-ember-soft">~ </span>
                      {requirementText.get(m.requirementId) ?? m.requirementId}
                      <span className="ml-1 text-xs text-ink-faint">
                        {m.evidenceIds.map(labelFor).join(', ')}
                      </span>
                    </li>
                  ))}
                </ul>
              </Card>
            </div>

            {/* Missing -------------------------------------------------------- */}
            <Card className="border-ember/30 bg-ember/5">
              <h2 className="text-sm font-semibold text-ink">
                Missing requirements <span className="text-ink-faint">({missing.length})</span>
              </h2>
              <p className="mt-2 text-sm text-ink-muted">
                Not found in your verified profile. These are shown so you know where you stand —
                don't claim them. If you do have the experience, add it to your profile and re-run
                the optimization.
              </p>
              <ul className="mt-3 space-y-2">
                {missing.length === 0 && (
                  <li className="text-sm text-mint">Nothing missing — your profile covers every requirement.</li>
                )}
                {missing.map((m) => (
                  <li key={m.requirementId} className="text-sm text-ink">
                    <span aria-hidden="true" className="text-rose">✕ </span>
                    {requirementText.get(m.requirementId) ?? m.requirementId}
                    {m.note ? <span className="ml-1 text-xs text-ink-faint">{m.note}</span> : null}
                  </li>
                ))}
              </ul>
            </Card>

            {/* Emphasis, grouped by evidence kind ------------------------------ */}
            {(data.emphasis ?? []).length > 0 && (
              <Card>
                <h2 className="text-sm font-semibold text-ink">What to lead with</h2>
                <p className="mt-2 text-sm text-ink-muted">
                  Your own evidence, ranked for this role.
                </p>
                <div className="mt-4 grid grid-cols-1 gap-5 sm:grid-cols-2">
                  {EVIDENCE_GROUPS.map((group) => {
                    const items = [...(data.emphasis ?? [])]
                      .filter((e) => e.evidenceId.startsWith(`${group.prefix}-`))
                      .sort((a, b) => a.rank - b.rank);
                    if (items.length === 0) return null;
                    return (
                      <div key={group.prefix}>
                        <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">{group.label}</p>
                        <ul className="mt-2 space-y-1.5">
                          {items.map((item) => (
                            <li key={item.evidenceId} className="text-sm text-ink">
                              {labelFor(item.evidenceId)}
                              {item.rationale ? (
                                <span className="block text-xs text-ink-faint">{item.rationale}</span>
                              ) : null}
                            </li>
                          ))}
                        </ul>
                      </div>
                    );
                  })}
                </div>
              </Card>
            )}

            {/* Keyword → evidence --------------------------------------------- */}
            <Card>
              <h2 className="text-sm font-semibold text-ink">Keyword → evidence mapping</h2>
              <p className="mt-2 text-sm text-ink-muted">
                Which part of your profile backs each JD term you can safely emphasise.
              </p>
              {mapped.length === 0 ? (
                <p className="mt-3 text-sm text-ink-faint">No keyword is currently backed by your profile.</p>
              ) : (
                <div className="mt-4 overflow-x-auto">
                  <table className="w-full min-w-[32rem] text-left text-sm">
                    <thead>
                      <tr className="text-xs uppercase tracking-wide text-ink-faint">
                        <th className="pb-2 pr-4 font-medium">Keyword</th>
                        <th className="pb-2 pr-4 font-medium">Priority</th>
                        <th className="pb-2 font-medium">Backed by</th>
                      </tr>
                    </thead>
                    <tbody>
                      {mapped.map((k) => (
                        <tr key={k.term} className="border-t border-border">
                          <td className="py-2 pr-4 text-ink">{k.term}</td>
                          <td className="py-2 pr-4">
                            <Chip label={k.priority === 'REQUIRED' ? 'Required' : 'Preferred'}
                                  tone={k.priority === 'REQUIRED' ? 'warn' : 'neutral'} />
                          </td>
                          <td className="py-2 text-ink-muted">{k.evidenceIds.map(labelFor).join(', ')}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>

            <NextSteps />
          </div>
        </>
      )}
    </DashboardShell>
  );
}
