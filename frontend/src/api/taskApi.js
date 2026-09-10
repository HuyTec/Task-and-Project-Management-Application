import apiClient from './apiClient'

export async function getAllTasks(params = {}, signal) {
  const response = await apiClient.get('/tasks', { params, signal })
  return response.data.data
}

export async function getTaskById(id, signal) {
  const response = await apiClient.get(`/tasks/${id}`, { signal })
  return response.data.data
}

export async function createTask(taskData, signal) {
  const response = await apiClient.post('/tasks', taskData, { signal })
  return response.data.data
}

export async function createProjectTask(projectId, taskData, signal) {
  const response = await apiClient.post(`/projects/${projectId}/tasks`, taskData, { signal })
  return response.data.data
}

export async function updateTaskById(taskId, taskData, signal) {
  const response = await apiClient.patch(`/tasks/${taskId}`, taskData, { signal })
  return response.data.data
}

export async function deleteTaskById(taskId, signal) {
  const response = await apiClient.delete(`/tasks/${taskId}`, { signal })
  return response.data.data
}

export async function getMyTasks(params = {}, signal) {
  const response = await apiClient.get('/tasks/me', { params, signal })
  return response.data.data
}

export async function claimTask(taskId, signal) {
  const response = await apiClient.post(`/tasks/${taskId}/claim`, null, { signal })
  return response.data.data
}

export async function releaseTaskClaim(taskId, signal) {
  const response = await apiClient.delete(`/tasks/${taskId}/claim`, { signal })
  return response.data.data
}

export async function assignTask(taskId, username, signal) {
  const response = await apiClient.put(`/tasks/${taskId}/assignee`, { username }, { signal })
  return response.data.data
}

export async function clearTaskAssignee(taskId, signal) {
  const response = await apiClient.delete(`/tasks/${taskId}/assignee`, { signal })
  return response.data.data
}

export async function assignTaskReviewer(taskId, username, signal) {
  const response = await apiClient.put(`/tasks/${taskId}/reviewer`, { username }, { signal })
  return response.data.data
}

export async function startTask(taskId, signal) {
  const response = await apiClient.post(`/tasks/${taskId}/start`, null, { signal })
  return response.data.data
}

export async function addTaskCriterion(taskId, criterionData, signal) {
  const response = await apiClient.post(`/tasks/${taskId}/criteria`, criterionData, { signal })
  return response.data.data
}

export async function updateTaskCriterion(taskId, criterionId, criterionData, signal) {
  const response = await apiClient.patch(`/tasks/${taskId}/criteria/${criterionId}`, criterionData, { signal })
  return response.data.data
}

export async function deleteTaskCriterion(taskId, criterionId, signal) {
  const response = await apiClient.delete(`/tasks/${taskId}/criteria/${criterionId}`, { signal })
  return response.data.data
}

export async function submitTaskForReview(taskId, signal) {
  const response = await apiClient.post(`/tasks/${taskId}/submit-review`, null, { signal })
  return response.data.data
}

export async function requestTaskChanges(taskId, message, signal) {
  const response = await apiClient.post(`/tasks/${taskId}/request-changes`, { message }, { signal })
  return response.data.data
}

export async function approveTask(taskId, signal) {
  const response = await apiClient.post(`/tasks/${taskId}/approve`, null, { signal })
  return response.data.data
}

export async function createTaskSubmission(taskId, signal) {
  const response = await apiClient.post(`/tasks/${taskId}/submissions`, null, { signal })
  return response.data.data
}

export async function getTaskSubmissions(taskId, signal) {
  const response = await apiClient.get(`/tasks/${taskId}/submissions`, { signal })
  return response.data.data
}

export async function addSubmissionLinkEvidence(submissionId, evidence, signal) {
  const response = await apiClient.post(`/submissions/${submissionId}/evidences/links`, evidence, { signal })
  return response.data.data
}

export async function deleteSubmissionEvidence(submissionId, evidenceId, signal) {
  const response = await apiClient.delete(`/submissions/${submissionId}/evidences/${evidenceId}`, { signal })
  return response.data.data
}

export async function submitTaskSubmission(submissionId, signal) {
  const response = await apiClient.post(`/submissions/${submissionId}/submit`, null, { signal })
  return response.data.data
}

export async function getSubmissionReview(submissionId, signal) {
  const response = await apiClient.get(`/submissions/${submissionId}/review`, { signal })
  return response.data.data
}

export async function saveSubmissionReviewDraft(submissionId, payload, signal) {
  const response = await apiClient.put(`/submissions/${submissionId}/review/draft`, payload, { signal })
  return response.data.data
}

export async function submitSubmissionReview(submissionId, payload, signal) {
  const response = await apiClient.post(`/submissions/${submissionId}/review/decision`, payload, { signal })
  return response.data.data
}
