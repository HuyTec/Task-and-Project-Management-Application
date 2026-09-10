import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import AppShell from '../components/layout/AppShell'
import StatePanel from '../components/ui/StatePanel'
import { getSubmissionReview, saveSubmissionReviewDraft, submitSubmissionReview } from '../api/taskApi'
import getApiErrorMessage from '../utils/getApiErrorMessage'
import { formatDateTime, formatEnum } from '../utils/entityFormatters'

function evidencePreview(evidence) {
  if (!evidence.url) return <p className="workflow-note">Preview is unavailable until this protected upload provides an authorized source URL.</p>
  if (evidence.contentType?.startsWith('image/')) return <img src={evidence.url} alt={evidence.displayName} className="evidence-preview-image" />
  if (evidence.contentType === 'application/pdf' || evidence.provider === 'GOOGLE_DOCS' || evidence.provider === 'FIGMA') return <iframe title={evidence.displayName} src={evidence.url} className="evidence-preview-frame" sandbox="allow-scripts allow-same-origin allow-popups" />
  return <p className="workflow-note">This source cannot be previewed safely here. Open the source to use its own access controls.</p>
}

function SubmissionReviewPage() {
  const { submissionId } = useParams()
  const [screen, setScreen] = useState(null)
  const [criteria, setCriteria] = useState([])
  const [message, setMessage] = useState('')
  const [decision, setDecision] = useState('APPROVED')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const load = useCallback(async (signal) => {
    setLoading(true); setError('')
    try {
      const data = await getSubmissionReview(submissionId, signal)
      setScreen(data)
      const draft = data.currentReview
      setCriteria(draft?.criteria || data.history.find((item) => item.status === 'SUBMITTED')?.criteria || [])
      setMessage(draft?.message || '')
    } catch (apiError) { if (apiError.code !== 'ERR_CANCELED') setError(getApiErrorMessage(apiError, 'Unable to load this review.')) }
    finally { if (!signal?.aborted) setLoading(false) }
  }, [submissionId])
  useEffect(() => { const controller = new AbortController(); load(controller.signal); return () => controller.abort() }, [load])
  const updateCriterion = (id, patch) => setCriteria((items) => items.map((item) => item.criterionSnapshotId === id ? { ...item, ...patch } : item))
  async function save(draftOnly) {
    setBusy(draftOnly ? 'draft' : 'submit'); setError(''); setSuccess('')
    try {
      const payload = { message, criteria: criteria.map(({ criterionSnapshotId, result, comment }) => ({ criterionSnapshotId, result, comment })) }
      if (draftOnly) await saveSubmissionReviewDraft(submissionId, payload)
      else await submitSubmissionReview(submissionId, { ...payload, decision })
      setSuccess(draftOnly ? 'Review draft saved.' : 'Decision submitted and locked in history.')
      await load()
    } catch (apiError) { setError(getApiErrorMessage(apiError, 'Unable to save this review.')) }
    finally { setBusy('') }
  }
  if (loading) return <AppShell theme="tasks" wide><StatePanel tone="loading" title="Loading submission review" description="Preparing the protected review workspace." /></AppShell>
  if (!screen) return <AppShell theme="tasks" wide><StatePanel tone="error" title="Review unavailable" description={error || 'The submission could not be loaded.'} /></AppShell>
  const editable = screen.currentUserIsReviewer
  return <AppShell theme="tasks" wide>
    <div className="page-heading"><div><p className="eyebrow">Submission review</p><h1>{screen.taskTitle}</h1><p className="dashboard-lead">Submission #{screen.submission.sequenceNumber} · sent {formatDateTime(screen.submission.submittedAt)}</p></div><Link className="text-button" to={`/tasks/${screen.submission.taskId}`}>Back to task</Link></div>
    {error && <p className="form-alert form-alert--error" role="alert">{error}</p>}{success && <p className="form-alert form-alert--success">{success}</p>}
    <div className="submission-review-layout">
      <section className="workflow-card"><p className="eyebrow">1 · Submission</p><h2>Task and delivery</h2><p><strong>Assignee:</strong> @{screen.submission.assigneeUsername}</p><p><strong>Reviewer:</strong> @{screen.designatedReviewerUsername || 'Not assigned'}</p><p><strong>Status:</strong> {formatEnum(screen.submission.status)}</p></section>
      <section className="workflow-card"><p className="eyebrow">2 · Evidence preview</p><h2>Evidence</h2>{screen.submission.evidences.map((evidence) => <article className="review-entry" key={evidence.id}><strong>{evidence.displayName}</strong>{evidencePreview(evidence)}{evidence.url ? <a className="text-button" href={evidence.url} target="_blank" rel="noreferrer">Open source</a> : <span className="workflow-note">Source access is protected or unavailable.</span>}</article>)}{screen.submission.evidences.length === 0 && <p className="workflow-note">No evidence was attached.</p>}</section>
      <section className="workflow-card"><p className="eyebrow">3 · Criteria and decision</p><h2>Review</h2>{criteria.map((item) => <article className="criterion-row" key={item.criterionSnapshotId}><span className="criterion-indicator">{item.position + 1}</span><div className="criterion-content"><strong>{item.content}</strong><div><label><input type="radio" name={`result-${item.criterionSnapshotId}`} checked={item.result === 'PASSED'} disabled={!editable || Boolean(screen.history.find((entry) => entry.status === 'SUBMITTED'))} onChange={() => updateCriterion(item.criterionSnapshotId, { result: 'PASSED' })} /> Pass</label><label><input type="radio" name={`result-${item.criterionSnapshotId}`} checked={item.result === 'FAILED'} disabled={!editable || Boolean(screen.history.find((entry) => entry.status === 'SUBMITTED'))} onChange={() => updateCriterion(item.criterionSnapshotId, { result: 'FAILED' })} /> Fail</label></div><textarea rows="2" maxLength="2000" value={item.comment || ''} placeholder="Criterion comment" disabled={!editable || Boolean(screen.history.find((entry) => entry.status === 'SUBMITTED'))} onChange={(event) => updateCriterion(item.criterionSnapshotId, { comment: event.target.value })} /></div></article>)}<label className="form-field"><span>Overall review comment</span><textarea rows="4" maxLength="5000" value={message} disabled={!editable || Boolean(screen.history.find((entry) => entry.status === 'SUBMITTED'))} onChange={(event) => setMessage(event.target.value)} /></label><label className="form-field"><span>Decision</span><select value={decision} disabled={!editable || Boolean(screen.history.find((entry) => entry.status === 'SUBMITTED'))} onChange={(event) => setDecision(event.target.value)}><option value="APPROVED">Approve</option><option value="CHANGES_REQUESTED">Request changes</option></select></label>{editable && !screen.history.find((entry) => entry.status === 'SUBMITTED') && <div className="workflow-actions"><button className="text-button" disabled={Boolean(busy)} onClick={() => save(true)}>Save draft</button><button className="primary-button" disabled={Boolean(busy) || criteria.length === 0} onClick={() => save(false)}>Submit decision</button></div>}</section>
    </div>
  </AppShell>
}
export default SubmissionReviewPage
