import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import {
  addSubmissionLinkEvidence,
  createTaskSubmission,
  deleteSubmissionEvidence,
  getTaskSubmissions,
  submitTaskSubmission,
} from '../../api/taskApi'
import { formatDateTime, formatEnum } from '../../utils/entityFormatters'
import getApiErrorMessage from '../../utils/getApiErrorMessage'

const LINK_TYPES = ['EXTERNAL_LINK', 'GITHUB_COMMIT', 'GITHUB_PR']
const EXTERNAL_PROVIDERS = ['OTHER', 'GOOGLE_DRIVE', 'GOOGLE_DOCS', 'FIGMA']

function SubmissionPanel({ task, membership, onChanged }) {
  const isAssignee = task.activeAssignment?.assigneeUsername === membership.username
  const canCreate = isAssignee && ['IN_PROGRESS', 'CHANGES_REQUESTED'].includes(task.status)
  const [submissions, setSubmissions] = useState([])
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [form, setForm] = useState({ evidenceType: 'EXTERNAL_LINK', provider: 'OTHER', displayName: '', url: '' })

  const load = useCallback(async (signal) => {
    const data = await getTaskSubmissions(task.id, signal)
    setSubmissions(data)
  }, [task.id])

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal).catch((apiError) => {
      if (apiError.code !== 'ERR_CANCELED') setError(getApiErrorMessage(apiError, 'Unable to load submissions.'))
    })
    return () => controller.abort()
  }, [load, task.status])

  const draft = submissions.find((submission) => submission.status === 'DRAFT')

  async function run(name, action) {
    setBusy(name)
    setError('')
    try {
      await action()
      await load()
      await onChanged()
    } catch (apiError) {
      setError(getApiErrorMessage(apiError, 'Unable to update the submission.'))
    } finally {
      setBusy('')
    }
  }

  function changeType(event) {
    const evidenceType = event.target.value
    setForm((current) => ({
      ...current,
      evidenceType,
      provider: evidenceType.startsWith('GITHUB_') ? 'GITHUB' : 'OTHER',
    }))
  }

  function addLink(event) {
    event.preventDefault()
    run('add-link', async () => {
      await addSubmissionLinkEvidence(draft.id, {
        ...form,
        displayName: form.displayName.trim(),
        url: form.url.trim(),
      })
      setForm({ evidenceType: 'EXTERNAL_LINK', provider: 'OTHER', displayName: '', url: '' })
    })
  }

  return (
    <section className="workflow-card">
      <div className="workflow-card__heading">
        <div><p className="eyebrow">Work evidence</p><h2>Submissions</h2></div>
        <span>{submissions.length} version{submissions.length === 1 ? '' : 's'}</span>
      </div>

      {error && <p className="form-alert form-alert--error" role="alert">{error}</p>}
      {canCreate && !draft && task.status !== 'IN_REVIEW' && <button className="primary-button primary-button--fit" type="button" disabled={Boolean(busy)} onClick={() => run('create', () => createTaskSubmission(task.id))}>Create submission draft</button>}

      {draft && (
        <div className="review-controls">
          <p className="workflow-note">Submission #{draft.sequenceNumber} is editable until it is submitted.</p>
          <form className="criterion-add-form" onSubmit={addLink}>
            <label className="form-field"><span>Evidence type</span><select value={form.evidenceType} onChange={changeType}>{LINK_TYPES.map((type) => <option key={type} value={type}>{formatEnum(type)}</option>)}</select></label>
            {form.evidenceType === 'EXTERNAL_LINK' && <label className="form-field"><span>Provider</span><select value={form.provider} onChange={(event) => setForm((current) => ({ ...current, provider: event.target.value }))}>{EXTERNAL_PROVIDERS.map((provider) => <option key={provider} value={provider}>{formatEnum(provider)}</option>)}</select></label>}
            <label className="form-field"><span>Evidence name</span><input value={form.displayName} maxLength="255" required onChange={(event) => setForm((current) => ({ ...current, displayName: event.target.value }))} /></label>
            <label className="form-field"><span>URL</span><input type="url" value={form.url} maxLength="2048" required onChange={(event) => setForm((current) => ({ ...current, url: event.target.value }))} /></label>
            <button className="primary-button primary-button--fit" type="submit" disabled={Boolean(busy) || draft.evidences.length >= 10}>Add evidence link</button>
          </form>
          <p className="workflow-note">File and folder upload is waiting for the approved S3/Cloudinary adapter to be identified in this checkout.</p>
          <button className="primary-button" type="button" disabled={Boolean(busy) || draft.evidences.length === 0} onClick={() => run('submit', () => submitTaskSubmission(draft.id))}>Submit evidence for review</button>
        </div>
      )}

      <div className="review-history">
        {submissions.map((submission) => (
          <article className="review-entry" key={submission.id}>
            <span className={`review-decision review-decision--${submission.status.toLowerCase()}`}>#{submission.sequenceNumber} {formatEnum(submission.status)}</span>
            <div>
              <strong>@{submission.assigneeUsername}</strong>
              <small>{formatDateTime(submission.submittedAt || submission.createdAt)}</small>
              {submission.status === 'SUBMITTED' && task.reviewerUsername === membership.username && <Link className="primary-button primary-button--fit" to={`/submissions/${submission.id}/review`}>Open review</Link>}
              {submission.evidences.map((evidence) => <p key={evidence.id}><a href={evidence.url} target="_blank" rel="noreferrer">{evidence.displayName}</a> · {formatEnum(evidence.evidenceType)} {submission.status === 'DRAFT' && <button className="text-button action-button--delete" type="button" disabled={Boolean(busy)} onClick={() => run(`delete-${evidence.id}`, () => deleteSubmissionEvidence(submission.id, evidence.id))}>Remove</button>}</p>)}
            </div>
          </article>
        ))}
        {submissions.length === 0 && <p className="workflow-empty">No evidence submission has been created.</p>}
      </div>
    </section>
  )
}

export default SubmissionPanel
