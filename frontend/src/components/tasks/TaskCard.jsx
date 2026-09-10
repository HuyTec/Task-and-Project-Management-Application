import { Link } from 'react-router-dom'

const STATUSES = [['TODO', 'To do'], ['IN_PROGRESS', 'In progress'], ['IN_REVIEW', 'In review'], ['CHANGES_REQUESTED', 'Changes requested'], ['DONE', 'Done']]

function formatDate(value) {
  if (!value) return 'No due date'
  const date = new Date(`${value}T00:00:00`)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('en', { dateStyle: 'medium' }).format(date)
}

function TaskCard({ task, busy, onDelete, onDragStart, onEdit, onStatusChange }) {
  const workflowManaged = task.projectId != null
  const canManageProject = task.currentUserRole === 'OWNER' || task.currentUserRole === 'MANAGER'
  const canEdit = !workflowManaged || canManageProject
  const canReview = workflowManaged && task.status === 'IN_REVIEW'
  return (
    <article
      className={`task-card task-card--${task.priority?.toLowerCase()}`}
      draggable={!busy && !workflowManaged}
      onDragStart={(event) => {
        event.dataTransfer.effectAllowed = 'move'
        event.dataTransfer.setData('text/plain', String(task.id))
        onDragStart?.(task)
      }}
    >
      <div className="task-card__topline"><span className={`priority-badge priority-badge--${task.priority?.toLowerCase()}`}>{task.priority}</span></div>
      <h3>{task.title}</h3>
      <p className="task-card__date">{formatDate(task.dueDate)}</p>
      {workflowManaged ? (
        <div className="task-card__workflow-meta">
          <span className="status-badge">{task.status.replaceAll('_', ' ')}</span>
          <span className={`task-assignee ${task.assigneeUsername ? 'task-assignee--active' : ''}`}>{task.assigneeUsername ? `${task.assigneeDisplayName || task.assigneeUsername} · ${task.assignmentType}` : 'Open for claim'}</span>
        </div>
      ) : (
        <label className="compact-field"><span>Move to</span><select value={task.status} disabled={busy} onChange={(event) => onStatusChange(task, event.target.value)}>{STATUSES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      )}
      <div className="task-card__actions">
        <Link className={canReview ? 'primary-button task-review-link' : 'text-button'} to={`/tasks/${task.id}${canReview ? '#review' : ''}`}>{canReview ? 'Review' : 'Details'}</Link>
        {canEdit && <button className="text-button" type="button" disabled={busy} onClick={() => onEdit(task)}>Edit</button>}
        {canEdit && <button className="text-button action-button--delete" type="button" disabled={busy} onClick={() => onDelete(task)}>Delete</button>}
      </div>
    </article>
  )
}

export default TaskCard
